package run.jiewen.software.operations;

import static run.halo.app.extension.index.query.Queries.equal;
import static run.halo.app.extension.index.query.Queries.isNull;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.PageRequestImpl;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Secret;
import run.jiewen.software.SoftwareDomainException;
import run.jiewen.software.api.SoftwareResourceKind;
import run.jiewen.software.download.DownloadTargetPolicy;
import run.jiewen.software.extension.DownloadAsset;
import run.jiewen.software.extension.SoftwareApp;
import run.jiewen.software.extension.SoftwareTopic;
import run.jiewen.software.extension.SoftwareVersion;
import run.jiewen.software.finders.SoftwareConsoleService;

/** Read-only, bounded snapshots. Execution still repeats domain validation; this is not a reservation. */
@Component
public class SoftwarePreflightService {
    private static final int SCAN_LIMIT = 200;
    private static final int EXAMPLE_LIMIT = 20;
    private final ReactiveExtensionClient client;
    private final SoftwareConsoleService service;

    public record Issue(String code, String severity, String message) {}
    public record Dependency(String relation, String resourceType, long count, List<String> names,
        boolean truncated, boolean blocking) {}
    public record Report(String name, String resourceType, long resourceVersion, String editRevision,
        String action, boolean allowed, boolean ready, String checkedAt,
        List<Issue> issues, List<Dependency> dependencies) {}
    private record Relation(SoftwareResourceKind kind, String field, String label, boolean blocking) {}

    public SoftwarePreflightService(ReactiveExtensionClient client, SoftwareConsoleService service) {
        this.client = client;
        this.service = service;
    }

    public Mono<Report> deletion(SoftwareResourceKind kind, String name) {
        return service.getResource(kind, name).flatMap(resource -> {
            var relations = switch (kind) {
                case APP -> List.of(new Relation(SoftwareResourceKind.VERSION, "spec.appName", "versions", true),
                    new Relation(SoftwareResourceKind.DOWNLOAD_ASSET, "spec.appName", "download_assets", true),
                    new Relation(SoftwareResourceKind.TOPIC, "spec.items.appName", "topic_references", false),
                    new Relation(SoftwareResourceKind.APP, "spec.relatedAppNames", "related_app_references", false));
                case VERSION -> List.of(new Relation(SoftwareResourceKind.DOWNLOAD_ASSET, "spec.versionName", "download_assets", true));
                case CATEGORY -> List.of(new Relation(SoftwareResourceKind.CATEGORY, "spec.parentName", "child_categories", true),
                    new Relation(SoftwareResourceKind.APP, "spec.categoryName", "apps", true));
                case TAG -> List.of(new Relation(SoftwareResourceKind.APP, "spec.tagNames", "apps", true));
                default -> List.<Relation>of();
            };
            return Flux.fromIterable(relations).concatMap(relation -> client.listBy(relation.kind().type(),
                    ListOptions.builder().andQuery(equal(relation.field(), name)).build(),
                    PageRequestImpl.of(1, EXAMPLE_LIMIT, Sort.by("metadata.name")))
                .map(found -> new Dependency(relation.label(), relation.kind().singular(), found.getTotal(),
                    found.getItems().stream().map(item -> item.getMetadata().getName()).toList(),
                    found.getTotal() > found.getItems().size(), relation.blocking())))
                .collectList().map(dependencies -> {
                    var issues = new ArrayList<Issue>();
                    for (var dependency : dependencies) {
                        if (dependency.count() == 0) { continue; }
                        issues.add(dependency.blocking()
                            ? new Issue("RESOURCE_IN_USE", "error", "Resolve " + dependency.relation() + " before deletion")
                            : new Issue("REFERENCES_RETAINED", "warning", dependency.relation()
                                + " will retain references; public queries omit missing software"));
                    }
                    if (kind == SoftwareResourceKind.TOPIC) {
                        issues.add(new Issue("SOFTWARE_RETAINED", "info", "Deleting a topic does not delete its software"));
                    }
                    return report(kind, resource, "delete", issues, dependencies);
                });
        });
    }

    public Mono<Report> publication(SoftwareResourceKind kind, String name) {
        if (!kind.publishable()) {
            return Mono.error(new SoftwareDomainException("INVALID_ARGUMENT", "Resource does not support publication"));
        }
        return service.getResource(kind, name).flatMap(resource -> {
            var issues = new ArrayList<Issue>();
            return service.validateResourceState(resource)
                .onErrorResume(ServerWebInputException.class, error -> {
                    issues.add(new Issue(error instanceof SoftwareDomainException domain ? domain.code() : "INVALID_ARGUMENT",
                        "error", "Stored content fails domain validation; correct its required fields and references"));
                    return Mono.empty();
                })
                .then(Mono.defer(() -> {
                    if (kind.spec(resource) == null) { return Mono.empty(); }
                    return resource instanceof SoftwareApp app ? appReadiness(app, issues)
                        : topicReadiness((SoftwareTopic) resource, issues);
                }))
                .then(Mono.fromSupplier(() -> report(kind, resource, "publish", issues, List.of())));
        });
    }

    private Mono<Void> appReadiness(SoftwareApp app, List<Issue> issues) {
        String name = app.getMetadata().getName();
        return client.listBy(SoftwareVersion.class,
                live("spec.appName", name).andQuery(equal("spec.status", "released")).build(),
                PageRequestImpl.of(1, 1, Sort.by(Sort.Order.desc("spec.releaseDate"),
                    Sort.Order.desc("metadata.creationTimestamp"), Sort.Order.asc("metadata.name"))))
            .flatMap(versions -> {
                if (versions.getItems().isEmpty()) {
                    issues.add(new Issue("NO_RELEASED_VERSION", "warning", "No released version is available to visitors"));
                    return Mono.empty();
                }
                String versionName = versions.getItems().getFirst().getMetadata().getName();
                return client.listBy(DownloadAsset.class, live("spec.appName", name)
                        .andQuery(equal("spec.versionName", versionName)).andQuery(equal("spec.enabled", true)).build(),
                        PageRequestImpl.of(1, SCAN_LIMIT, Sort.by(Sort.Order.asc("spec.priority"), Sort.Order.asc("metadata.name"))))
                    .flatMap(assets -> {
                        if (assets.getTotal() > SCAN_LIMIT) { issues.add(incomplete()); }
                        if (assets.getItems().isEmpty()) {
                            issues.add(new Issue("NO_ENABLED_DOWNLOAD", "warning", "The latest released version has no enabled download"));
                            return Mono.empty();
                        }
                        return Flux.fromIterable(assets.getItems()).concatMap(this::usableTarget)
                            .collectList().doOnNext(usable -> {
                                if (!usable.getFirst()) {
                                    issues.add(new Issue("PRIMARY_DOWNLOAD_UNUSABLE", "warning",
                                        "The first download of the latest released version has no usable target or its Secret is missing"));
                                }
                                if (usable.stream().skip(1).anyMatch(value -> !value)) {
                                    issues.add(new Issue("OTHER_DOWNLOADS_UNUSABLE", "warning", "Some additional enabled downloads have no usable target"));
                                }
                            }).then();
                    });
            });
    }

    private Mono<Void> topicReadiness(SoftwareTopic topic, List<Issue> issues) {
        var items = topic.getSpec().getItems() == null ? List.<SoftwareTopic.Item>of() : topic.getSpec().getItems();
        if (items.isEmpty()) {
            issues.add(new Issue("EMPTY_TOPIC", "warning", "The topic contains no software"));
            return Mono.empty();
        }
        if (items.size() > SCAN_LIMIT) { issues.add(incomplete()); }
        // Legacy data may contain null entries; Reactor rejects null before an item mapper runs.
        return Flux.range(0, Math.min(items.size(), SCAN_LIMIT)).concatMap(index -> {
            var item = items.get(index);
            if (item == null || item.getAppName() == null) { return Mono.just(false); }
            return client.fetch(SoftwareApp.class, item.getAppName())
                .map(app -> app.getMetadata().getDeletionTimestamp() == null && app.getSpec() != null
                    && Boolean.TRUE.equals(app.getSpec().getPublished())).defaultIfEmpty(false);
        }).filter(visible -> !visible).count().doOnNext(count -> {
            if (count > 0) { issues.add(new Issue("HIDDEN_TOPIC_ITEMS", "warning",
                count + " checked topic items are missing, deleting, or unpublished and will not be visible")); }
        }).then();
    }

    private Mono<Boolean> usableTarget(DownloadAsset asset) {
        var spec = asset.getSpec();
        boolean direct = DownloadTargetPolicy.firstUsable(spec.getUrl(), spec.getStorageKey()) != null;
        if (spec.getPasswordSecretName() == null || spec.getPasswordSecretName().isBlank()) { return Mono.just(direct); }
        return client.fetch(Secret.class, spec.getPasswordSecretName())
            .filter(secret -> secret.getMetadata().getDeletionTimestamp() == null)
            .map(secret -> direct || DownloadTargetPolicy.isUsable(secretUrl(secret))).defaultIfEmpty(false);
    }

    private static String secretUrl(Secret secret) {
        var data = secret.getData();
        return data == null || data.get("url") == null ? null : new String(data.get("url"), StandardCharsets.UTF_8);
    }

    private static ListOptions.ListOptionsBuilder live(String field, String name) {
        return ListOptions.builder().andQuery(equal(field, name)).andQuery(isNull("metadata.deletionTimestamp"));
    }

    private static Issue incomplete() {
        return new Issue("CHECK_INCOMPLETE", "warning", "Only the first 200 records per collection were checked");
    }

    private static Report report(SoftwareResourceKind kind, AbstractExtension resource, String action,
        List<Issue> issues, List<Dependency> dependencies) {
        boolean allowed = issues.stream().noneMatch(issue -> issue.severity().equals("error"));
        boolean ready = allowed && issues.stream().noneMatch(issue -> issue.severity().equals("warning"));
        return new Report(resource.getMetadata().getName(), kind.singular(), resource.getMetadata().getVersion(),
            SoftwareEditRevision.of(kind, resource), action, allowed, ready, Instant.now().toString(),
            List.copyOf(issues), List.copyOf(dependencies));
    }
}
