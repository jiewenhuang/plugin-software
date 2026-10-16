package run.jiewen.software.finders.impl;

import static run.halo.app.extension.index.query.Queries.and;
import static run.halo.app.extension.index.query.Queries.equal;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.data.domain.Sort;
import org.springframework.dao.OptimisticLockingFailureException;
import reactor.util.retry.Retry;
import run.jiewen.software.operations.SoftwareEditRevision;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.MetadataOperator;
import run.halo.app.extension.PageRequest;
import run.halo.app.extension.PageRequestImpl;
import run.halo.app.extension.ReactiveExtensionClient;
import run.jiewen.software.SoftwareConsoleQuery;
import run.jiewen.software.SoftwareDomainException;
import run.jiewen.software.api.SoftwareMutation;
import run.jiewen.software.api.SoftwareResourceKind;
import run.jiewen.software.query.SoftwareListCriteria;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.annotation.JsonDeserialize;
import run.jiewen.software.api.DownloadAssetSecretCommand;
import run.jiewen.software.download.DownloadTargetPolicy;
import run.jiewen.software.extension.DownloadAsset;
import run.jiewen.software.extension.SoftwareApp;
import run.jiewen.software.extension.SoftwareCategory;
import run.jiewen.software.extension.SoftwareTag;
import run.jiewen.software.extension.SoftwareTopic;
import run.jiewen.software.extension.SoftwareVersion;
import run.halo.app.extension.Secret;
import run.jiewen.software.finders.SoftwareConsoleService;
import run.jiewen.software.vo.SoftwareConsoleStatsVo;
import run.jiewen.software.vo.DownloadAssetSecretVo;
import run.jiewen.software.vo.DownloadAssetStatsVo;

@Component
public class SoftwareConsoleServiceImpl implements SoftwareConsoleService {

    // Halo's runtime MetadataOperator may only carry the Jackson 2 annotation.
    // Supply its concrete type explicitly for our Jackson 3 resource copies.
    private static final JsonMapper RESOURCE_JSON = JsonMapper.builder()
        .addMixIn(MetadataOperator.class, ConcreteMetadata.class).build();

    @JsonDeserialize(as = Metadata.class)
    private interface ConcreteMetadata {}

    private static final PageRequest FIRST_MATCH =
        PageRequestImpl.of(1, 2, Sort.by("metadata.name").ascending());
    private static final Pattern SLUG_PATTERN = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Pattern HEX_COLOR_PATTERN =
        Pattern.compile("#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})");
    private static final Pattern SHA256_PATTERN = Pattern.compile("[0-9a-fA-F]{64}");
    private static final Pattern MD5_PATTERN = Pattern.compile("[0-9a-fA-F]{32}");

    private final ReactiveExtensionClient client;

    public SoftwareConsoleServiceImpl(ReactiveExtensionClient client) {
        this.client = client;
    }

    @Override
    public Mono<ListResult<AbstractExtension>> listResources(SoftwareResourceKind kind,
        SoftwareListCriteria criteria) {
        String keyword = switch (kind) {
            case VERSION -> "spec.version";
            case DOWNLOAD_ASSET, TOPIC -> "spec.title";
            default -> "spec.displayName";
        };
        var options = criteria.toListOptions(keyword,
            kind != SoftwareResourceKind.VERSION && kind != SoftwareResourceKind.DOWNLOAD_ASSET,
            ListOptions.builder().andQuery(
                run.halo.app.extension.index.query.Queries.isNull("metadata.deletionTimestamp"))
                .build());
        return client.listBy(kind.type(), options, criteria.toPageRequest())
            .map(result -> new ListResult<>(result.getPage(), result.getSize(), result.getTotal(),
                result.getItems().stream().map(AbstractExtension.class::cast).toList()));
    }

    @Override
    public Mono<AbstractExtension> getResource(SoftwareResourceKind kind, String name) {
        return fetchOrNotFound(kind.type(), name, "Software resource not found")
            .filter(resource -> resource.getMetadata().getDeletionTimestamp() == null)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Software resource is being deleted")))
            .cast(AbstractExtension.class);
    }

    @Override
    public Mono<AbstractExtension> createResource(SoftwareResourceKind kind,
        Map<String, Object> spec) {
        return Mono.defer(() -> {
            var resource = BeanUtils.instantiateClass(kind.type());
            kind.setSpec(resource, SoftwareMutation.createSpec(kind, spec));
            return switch (resource) {
                case SoftwareApp app -> createApp(app).cast(AbstractExtension.class);
                case SoftwareVersion version -> createVersion(version).cast(AbstractExtension.class);
                case DownloadAsset asset -> createDownloadAsset(asset).cast(AbstractExtension.class);
                case SoftwareCategory category -> createCategory(category).cast(AbstractExtension.class);
                case SoftwareTag tag -> createTag(tag).cast(AbstractExtension.class);
                case SoftwareTopic topic -> createTopic(topic).cast(AbstractExtension.class);
                default -> Mono.error(new IllegalArgumentException("Unsupported software resource"));
            };
        });
    }

    @Override
    public Mono<AbstractExtension> prepareCreateResource(SoftwareResourceKind kind,
        Map<String, Object> spec, String name) {
        return Mono.defer(() -> {
            var resource = BeanUtils.instantiateClass(kind.type());
            var metadata = new Metadata();
            metadata.setName(name);
            resource.setMetadata(metadata);
            kind.setSpec(resource, SoftwareMutation.createSpec(kind, spec));
            return validateResource(resource).thenReturn(resource);
        });
    }

    @Override
    public Mono<AbstractExtension> patchResource(SoftwareResourceKind kind, String name,
        SoftwareMutation mutation) {
        var write = Mono.defer(() -> getResource(kind, name)
            .flatMap(resource -> {
                if (mutation.expectedEditRevision() == null) {
                    checkVersion(resource, mutation.expectedVersion());
                } else if (resource.getMetadata().getVersion() < mutation.expectedVersion()
                    || !mutation.expectedEditRevision().equals(SoftwareEditRevision.of(kind, resource))) {
                    throw new SoftwareDomainException("CONFLICT",
                        "Business content changed; read it again before editing");
                }
                // Do not mutate the object returned by the extension cache on a failed validation.
                var copy = RESOURCE_JSON.convertValue(resource, kind.type());
                kind.setSpec(copy, mutation.apply(kind, kind.spec(copy)));
                return validateResource(copy)
                    .then(Mono.defer(() -> client.update(copy))).cast(AbstractExtension.class);
            }));
        // A retry always re-reads and re-validates the original business revision and all references.
        return mutation.expectedEditRevision() == null ? write : write.retryWhen(Retry.max(2)
            .filter(OptimisticLockingFailureException.class::isInstance)
            .onRetryExhaustedThrow((spec, signal) -> signal.failure()));
    }

    @Override
    public Mono<Void> validateResourceState(AbstractExtension resource) {
        return Mono.defer(() -> validateResource(RESOURCE_JSON.convertValue(resource, resource.getClass())));
    }

    private Mono<Void> validateResource(AbstractExtension resource) {
        String name = nameOf(resource);
        return switch (resource) {
            case SoftwareApp app -> {
                normalizeApp(app);
                yield ensureAppRefs(app, name).then(ensureSlugUnique(app.getSpec().getSlug(), name));
            }
            case SoftwareVersion version -> {
                normalizeVersion(version);
                yield ensureAppExists(version.getSpec().getAppName())
                    .then(Mono.defer(() -> ensureVersionUnique(version.getSpec().getAppName(),
                        version.getSpec().getVersion(), name)));
            }
            case DownloadAsset asset -> {
                normalizeDownloadAsset(asset);
                yield ensureDownloadAssetRefs(asset);
            }
            case SoftwareCategory category -> {
                normalizeCategory(category);
                yield ensureCategoryParent(category.getSpec().getParentName(), name)
                    .then(ensureSlugUnique(SoftwareCategory.class, category.getSpec().getSlug(),
                        name, SoftwareCategory::getSpec, SoftwareCategory.Spec::getSlug,
                        "Software category slug already exists"));
            }
            case SoftwareTag tag -> {
                normalizeTag(tag);
                yield ensureSlugUnique(SoftwareTag.class, tag.getSpec().getSlug(), name,
                    SoftwareTag::getSpec, SoftwareTag.Spec::getSlug, "Software tag slug already exists");
            }
            case SoftwareTopic topic -> {
                normalizeTopic(topic);
                yield ensureSlugUnique(SoftwareTopic.class, topic.getSpec().getSlug(), name,
                    SoftwareTopic::getSpec, SoftwareTopic.Spec::getSlug,
                    "Software topic slug already exists").then(ensureTopicRefs(topic));
            }
            default -> Mono.error(new IllegalArgumentException("Unsupported software resource"));
        };
    }

    @Override
    public Mono<AbstractExtension> deleteResource(SoftwareResourceKind kind, String name,
        Long expectedVersion) {
        return Mono.defer(() -> getResource(kind, name).flatMap(resource -> {
            if (expectedVersion != null) {
                checkVersion(resource, expectedVersion);
            }
            Mono<Void> checks = switch (kind) {
                case APP -> ensureNoVersionsForApp(name)
                    .then(Mono.defer(() -> ensureNoDownloadAssetsForApp(name)));
                case VERSION -> ensureNoDownloadAssetsForVersion(name);
                case CATEGORY -> ensureNoChildren(SoftwareApp.class, "spec.categoryName", name,
                    "Please remove software category references before deleting")
                    .then(Mono.defer(() -> ensureNoChildren(SoftwareCategory.class,
                        "spec.parentName", name, "Please delete or move child categories before deleting")));
                case TAG -> ensureNoChildren(SoftwareApp.class, "spec.tagNames", name,
                    "Please remove software tag references before deleting");
                default -> Mono.empty();
            };
            return checks.then(Mono.defer(() -> client.delete(
                RESOURCE_JSON.convertValue(resource, kind.type()))))
                .cast(AbstractExtension.class);
        }));
    }

    private static void checkVersion(AbstractExtension resource, long expected) {
        if (!Objects.equals(resource.getMetadata().getVersion(), expected)) {
            throw new SoftwareDomainException("CONFLICT",
                "Resource version changed; read the resource again before retrying");
        }
    }

    @Override
    public Mono<ListResult<SoftwareApp>> listApps(SoftwareConsoleQuery query) {
        return client.listBy(SoftwareApp.class, query.toListOptions(), query.toPageRequest());
    }

    @Override
    public Mono<SoftwareConsoleStatsVo> getAppStats() {
        return client.listAll(SoftwareApp.class, ListOptions.builder().build(), Sort.unsorted())
            .collect(AppStatsAccumulator::new, AppStatsAccumulator::accept)
            .map(stats -> {
                return SoftwareConsoleStatsVo.builder()
                    .total(stats.total)
                    .published(stats.published)
                    .drafts(stats.total - stats.published)
                    .downloads(stats.downloads)
                    .platforms(List.copyOf(stats.platforms))
                    .build();
            });
    }

    @Override
    public Mono<SoftwareApp> getApp(String name) {
        return fetchOrNotFound(SoftwareApp.class, name, "Software app not found");
    }

    @Override
    public Mono<SoftwareApp> createApp(SoftwareApp app) {
        return Mono.defer(() -> {
            normalizeApp(app);
            app.setStatus(null);
            prepareMetadata(app, "software-app-", "software-app-" + app.getSpec().getSlug());
            return ensureAppRefs(app, app.getMetadata().getName())
                .then(ensureSlugUnique(app.getSpec().getSlug(), null))
                .then(Mono.defer(() -> client.create(app)));
        });
    }

    @Override
    public Mono<SoftwareApp> replaceApp(String name, SoftwareApp app) {
        return Mono.defer(() -> getApp(name)
            .flatMap(existing -> {
                existing.setSpec(requiredSpec(app == null ? null : app.getSpec(),
                    "Software app spec is required"));
                normalizeApp(existing);
                return ensureAppRefs(existing, name)
                    .then(ensureSlugUnique(existing.getSpec().getSlug(), name))
                    .then(Mono.defer(() -> client.update(existing)));
            }));
    }

    @Override
    public Mono<SoftwareApp> updateApp(String name, SoftwareApp app) {
        return Mono.defer(() -> getApp(name)
            .flatMap(existing -> {
                mergeApp(app, existing);
                normalizeApp(existing);
                return ensureAppRefs(existing, name)
                    .then(ensureSlugUnique(existing.getSpec().getSlug(), name))
                    .then(Mono.defer(() -> client.update(existing)));
            }));
    }

    @Override
    public Mono<SoftwareApp> deleteApp(String name) {
        return getApp(name)
            .flatMap(app -> ensureNoVersionsForApp(name)
                .then(Mono.defer(() -> ensureNoDownloadAssetsForApp(name)))
                .then(Mono.defer(() -> client.delete(app))));
    }

    @Override
    public Mono<ListResult<SoftwareVersion>> listVersions(SoftwareConsoleQuery query) {
        return client.listBy(SoftwareVersion.class, query.toListOptions("spec.version"),
            query.toPageRequest());
    }

    @Override
    public Mono<SoftwareVersion> createVersion(SoftwareVersion version) {
        return Mono.defer(() -> {
            normalizeVersion(version);
            version.setStatus(null);
            prepareMetadata(version, "software-version-");
            return ensureAppExists(version.getSpec().getAppName())
                .then(Mono.defer(() -> ensureVersionUnique(version.getSpec().getAppName(),
                    version.getSpec().getVersion(), null)))
                .then(Mono.defer(() -> client.create(version)));
        });
    }

    @Override
    public Mono<SoftwareVersion> replaceVersion(String name, SoftwareVersion version) {
        return Mono.defer(() ->
            fetchOrNotFound(SoftwareVersion.class, name, "Software version not found")
                .flatMap(existing -> {
                    existing.setSpec(requiredSpec(version == null ? null : version.getSpec(),
                        "Software version spec is required"));
                    normalizeVersion(existing);
                    return ensureAppExists(existing.getSpec().getAppName())
                        .then(Mono.defer(() -> ensureVersionUnique(
                            existing.getSpec().getAppName(),
                            existing.getSpec().getVersion(), name)))
                        .then(Mono.defer(() -> client.update(existing)));
                }));
    }

    @Override
    public Mono<SoftwareVersion> updateVersion(String name, SoftwareVersion version) {
        return Mono.defer(() ->
            fetchOrNotFound(SoftwareVersion.class, name, "Software version not found")
                .flatMap(existing -> {
                    mergeVersion(version, existing);
                    normalizeVersion(existing);
                    return ensureAppExists(existing.getSpec().getAppName())
                        .then(Mono.defer(() -> ensureVersionUnique(
                            existing.getSpec().getAppName(),
                            existing.getSpec().getVersion(), name)))
                        .then(Mono.defer(() -> client.update(existing)));
                }));
    }

    @Override
    public Mono<SoftwareVersion> deleteVersion(String name) {
        return fetchOrNotFound(SoftwareVersion.class, name, "Software version not found")
            .flatMap(version -> ensureNoDownloadAssetsForVersion(name)
                .then(Mono.defer(() -> client.delete(version))));
    }

    @Override
    public Mono<ListResult<DownloadAsset>> listDownloadAssets(SoftwareConsoleQuery query) {
        return client.listBy(DownloadAsset.class, query.toListOptions("spec.title"),
            query.toPageRequest());
    }

    @Override
    public Mono<DownloadAssetStatsVo> getDownloadAssetStats() {
        return client.listAll(DownloadAsset.class, ListOptions.builder().build(), Sort.unsorted())
            .collect(DownloadAssetStatsAccumulator::new, DownloadAssetStatsAccumulator::accept)
            .map(DownloadAssetStatsAccumulator::toVo);
    }

    @Override
    public Mono<DownloadAsset> createDownloadAsset(DownloadAsset asset) {
        return Mono.defer(() -> {
            normalizeDownloadAsset(asset);
            asset.setStatus(null);
            prepareMetadata(asset, "download-asset-");
            return ensureDownloadAssetRefs(asset)
                .then(Mono.defer(() -> client.create(asset)));
        });
    }

    @Override
    public Mono<DownloadAsset> replaceDownloadAsset(String name, DownloadAsset asset) {
        return Mono.defer(() ->
            fetchOrNotFound(DownloadAsset.class, name, "Download asset not found")
                .flatMap(existing -> {
                    var stored = requiredSpec(existing.getSpec(),
                        "Stored download asset spec is missing");
                    var replacement = requiredSpec(asset == null ? null : asset.getSpec(),
                        "Download asset spec is required");
                    replacement.setUrl(stored.getUrl());
                    replacement.setStorageKey(stored.getStorageKey());
                    replacement.setExtractionCode(stored.getExtractionCode());
                    replacement.setPasswordSecretName(stored.getPasswordSecretName());
                    existing.setSpec(replacement);
                    normalizeDownloadAsset(existing);
                    return ensureDownloadAssetRefs(existing)
                        .then(Mono.defer(() -> client.update(existing)));
                }));
    }

    @Override
    public Mono<DownloadAsset> updateDownloadAsset(String name, DownloadAsset asset) {
        return Mono.defer(() ->
            fetchOrNotFound(DownloadAsset.class, name, "Download asset not found")
                .flatMap(existing -> {
                    mergeSpec(asset == null ? null : asset.getSpec(), existing.getSpec());
                    normalizeDownloadAsset(existing);
                    return ensureDownloadAssetRefs(existing)
                        .then(Mono.defer(() -> client.update(existing)));
                }));
    }

    @Override
    public Mono<DownloadAssetSecretVo> getDownloadAssetSecret(String name) {
        return fetchOrNotFound(DownloadAsset.class, name, "Download asset not found")
            .map(DownloadAssetSecretVo::from);
    }

    @Override
    public Mono<DownloadAssetSecretVo> updateDownloadAssetSecret(String name,
        DownloadAssetSecretCommand command) {
        if (command == null) {
            return Mono.error(new ServerWebInputException("Download asset secret body is required"));
        }
        return fetchOrNotFound(DownloadAsset.class, name, "Download asset not found")
            .flatMap(asset -> {
                var spec = asset.getSpec();
                if (spec == null) {
                    return Mono.error(new ServerWebInputException("Download asset spec is required"));
                }
                spec.setUrl(normalizeOptional(command.url()));
                spec.setStorageKey(normalizeOptional(command.storageKey()));
                spec.setExtractionCode(normalizeOptional(command.extractionCode()));
                spec.setPasswordSecretName(normalizeOptional(command.passwordSecretName()));
                validateDownloadTarget(spec);
                return ensureDownloadSecretRef(spec)
                    .then(Mono.defer(() -> client.update(asset)))
                    .map(DownloadAssetSecretVo::from);
            });
    }

    @Override
    public Mono<DownloadAsset> deleteDownloadAsset(String name) {
        return fetchOrNotFound(DownloadAsset.class, name, "Download asset not found")
            .flatMap(client::delete);
    }

    @Override
    public Mono<ListResult<SoftwareCategory>> listCategories(SoftwareConsoleQuery query) {
        return client.listBy(SoftwareCategory.class,
            query.toListOptions("spec.displayName", true), query.toPageRequest());
    }

    @Override
    public Mono<SoftwareCategory> createCategory(SoftwareCategory category) {
        return Mono.defer(() -> {
            normalizeCategory(category);
            category.setStatus(null);
            prepareMetadata(category, "software-category-");
            return ensureCategoryParent(category.getSpec().getParentName(), null)
                .then(ensureSlugUnique(SoftwareCategory.class, category.getSpec().getSlug(), null,
                    SoftwareCategory::getSpec, SoftwareCategory.Spec::getSlug,
                    "Software category slug already exists"))
                .then(Mono.defer(() -> client.create(category)));
        });
    }

    @Override
    public Mono<SoftwareCategory> replaceCategory(String name, SoftwareCategory category) {
        return Mono.defer(() ->
            fetchOrNotFound(SoftwareCategory.class, name, "Software category not found")
                .flatMap(existing -> {
                    existing.setSpec(requiredSpec(category == null ? null : category.getSpec(),
                        "Software category spec is required"));
                    normalizeCategory(existing);
                    return ensureCategoryParent(existing.getSpec().getParentName(), name)
                        .then(ensureSlugUnique(SoftwareCategory.class,
                            existing.getSpec().getSlug(), name, SoftwareCategory::getSpec,
                            SoftwareCategory.Spec::getSlug,
                            "Software category slug already exists"))
                        .then(Mono.defer(() -> client.update(existing)));
                }));
    }

    @Override
    public Mono<SoftwareCategory> updateCategory(String name, SoftwareCategory category) {
        return Mono.defer(() ->
            fetchOrNotFound(SoftwareCategory.class, name, "Software category not found")
                .flatMap(existing -> {
                    mergeSpec(category == null ? null : category.getSpec(), existing.getSpec());
                    normalizeCategory(existing);
                    return ensureCategoryParent(existing.getSpec().getParentName(), name)
                        .then(ensureSlugUnique(SoftwareCategory.class,
                            existing.getSpec().getSlug(), name, SoftwareCategory::getSpec,
                            SoftwareCategory.Spec::getSlug,
                            "Software category slug already exists"))
                        .then(Mono.defer(() -> client.update(existing)));
                }));
    }

    @Override
    public Mono<SoftwareCategory> deleteCategory(String name) {
        return fetchOrNotFound(SoftwareCategory.class, name, "Software category not found")
            .flatMap(category -> ensureNoChildren(SoftwareApp.class, "spec.categoryName", name,
                "Please remove software category references before deleting")
                .then(Mono.defer(() -> ensureNoChildren(SoftwareCategory.class,
                    "spec.parentName", name,
                    "Please delete or move child categories before deleting")))
                .then(Mono.defer(() -> client.delete(category))));
    }

    @Override
    public Mono<ListResult<SoftwareTag>> listTags(SoftwareConsoleQuery query) {
        return client.listBy(SoftwareTag.class,
            query.toListOptions("spec.displayName", true), query.toPageRequest());
    }

    @Override
    public Mono<SoftwareTag> getTag(String name) {
        return fetchOrNotFound(SoftwareTag.class, name, "Software tag not found");
    }

    @Override
    public Mono<SoftwareTag> createTag(SoftwareTag tag) {
        return Mono.defer(() -> {
            normalizeTag(tag);
            tag.setStatus(null);
            prepareMetadata(tag, "software-tag-");
            return ensureSlugUnique(SoftwareTag.class, tag.getSpec().getSlug(), null,
                SoftwareTag::getSpec, SoftwareTag.Spec::getSlug,
                "Software tag slug already exists")
                .then(Mono.defer(() -> client.create(tag)));
        });
    }

    @Override
    public Mono<SoftwareTag> replaceTag(String name, SoftwareTag tag) {
        return Mono.defer(() -> fetchOrNotFound(SoftwareTag.class, name, "Software tag not found")
            .flatMap(existing -> {
                existing.setSpec(requiredSpec(tag == null ? null : tag.getSpec(),
                    "Software tag spec is required"));
                normalizeTag(existing);
                return ensureSlugUnique(SoftwareTag.class, existing.getSpec().getSlug(), name,
                    SoftwareTag::getSpec, SoftwareTag.Spec::getSlug,
                    "Software tag slug already exists")
                    .then(Mono.defer(() -> client.update(existing)));
            }));
    }

    @Override
    public Mono<SoftwareTag> updateTag(String name, SoftwareTag tag) {
        return Mono.defer(() -> fetchOrNotFound(SoftwareTag.class, name, "Software tag not found")
            .flatMap(existing -> {
                mergeSpec(tag == null ? null : tag.getSpec(), existing.getSpec());
                normalizeTag(existing);
                return ensureSlugUnique(SoftwareTag.class, existing.getSpec().getSlug(), name,
                    SoftwareTag::getSpec, SoftwareTag.Spec::getSlug,
                    "Software tag slug already exists")
                    .then(Mono.defer(() -> client.update(existing)));
            }));
    }

    @Override
    public Mono<SoftwareTag> deleteTag(String name) {
        return fetchOrNotFound(SoftwareTag.class, name, "Software tag not found")
            .flatMap(tag -> ensureNoChildren(SoftwareApp.class, "spec.tagNames", name,
                "Please remove software tag references before deleting")
                .then(Mono.defer(() -> client.delete(tag))));
    }

    @Override
    public Mono<ListResult<SoftwareTopic>> listTopics(SoftwareConsoleQuery query) {
        return client.listBy(SoftwareTopic.class,
            query.toListOptions("spec.title", true), query.toPageRequest());
    }

    @Override
    public Mono<SoftwareTopic> createTopic(SoftwareTopic topic) {
        return Mono.defer(() -> {
            normalizeTopic(topic);
            topic.setStatus(null);
            prepareMetadata(topic, "software-topic-");
            return ensureSlugUnique(SoftwareTopic.class, topic.getSpec().getSlug(), null,
                SoftwareTopic::getSpec, SoftwareTopic.Spec::getSlug,
                "Software topic slug already exists")
                .then(ensureTopicRefs(topic))
                .then(Mono.defer(() -> client.create(topic)));
        });
    }

    @Override
    public Mono<SoftwareTopic> replaceTopic(String name, SoftwareTopic topic) {
        return Mono.defer(() ->
            fetchOrNotFound(SoftwareTopic.class, name, "Software topic not found")
                .flatMap(existing -> {
                    existing.setSpec(requiredSpec(topic == null ? null : topic.getSpec(),
                        "Software topic spec is required"));
                    normalizeTopic(existing);
                    return ensureSlugUnique(SoftwareTopic.class, existing.getSpec().getSlug(),
                        name, SoftwareTopic::getSpec, SoftwareTopic.Spec::getSlug,
                        "Software topic slug already exists")
                        .then(ensureTopicRefs(existing))
                        .then(Mono.defer(() -> client.update(existing)));
                }));
    }

    @Override
    public Mono<SoftwareTopic> updateTopic(String name, SoftwareTopic topic) {
        return Mono.defer(() ->
            fetchOrNotFound(SoftwareTopic.class, name, "Software topic not found")
                .flatMap(existing -> {
                    mergeSpec(topic == null ? null : topic.getSpec(), existing.getSpec());
                    normalizeTopic(existing);
                    return ensureSlugUnique(SoftwareTopic.class, existing.getSpec().getSlug(),
                        name, SoftwareTopic::getSpec, SoftwareTopic.Spec::getSlug,
                        "Software topic slug already exists")
                        .then(ensureTopicRefs(existing))
                        .then(Mono.defer(() -> client.update(existing)));
                }));
    }

    @Override
    public Mono<SoftwareTopic> deleteTopic(String name) {
        return fetchOrNotFound(SoftwareTopic.class, name, "Software topic not found")
            .flatMap(client::delete);
    }

    private Mono<Void> ensureSlugUnique(String slug, String allowedName) {
        return ensureSlugUnique(SoftwareApp.class, slug, allowedName, SoftwareApp::getSpec,
            SoftwareApp.Spec::getSlug, "Software slug already exists");
    }

    private <E extends AbstractExtension, S> Mono<Void> ensureSlugUnique(Class<E> type,
        String slug, String allowedName, Function<E, S> specGetter, Function<S, String> slugGetter,
        String message) {
        var options = ListOptions.builder()
            .andQuery(equal("spec.slug", slug))
            .build();
        return client.listBy(type, options, FIRST_MATCH)
            .flatMap(result -> {
                var duplicate = result.getItems()
                    .stream()
                    .filter(item -> Objects.equals(slugOf(item, specGetter, slugGetter), slug))
                    .filter(item -> !Objects.equals(nameOf(item), allowedName))
                    .findFirst();
                if (duplicate.isPresent()) {
                    return Mono.error(new SoftwareDomainException("ALREADY_EXISTS", message));
                }
                return Mono.empty();
            });
    }

    private static void normalizeApp(SoftwareApp app) {
        if (app.getSpec() == null) {
            throw new ServerWebInputException("Software app spec is required");
        }
        if (!hasText(app.getSpec().getSlug())) {
            throw new ServerWebInputException("Software slug is required");
        }
        validateSlug(app.getSpec().getSlug(), "Software slug");
        if (!hasText(app.getSpec().getDisplayName())) {
            throw new ServerWebInputException("Software displayName is required");
        }
        if (app.getSpec().getPublished() == null) {
            app.getSpec().setPublished(false);
        }
        if (app.getSpec().getFeatured() == null) {
            app.getSpec().setFeatured(false);
        }
        if (app.getSpec().getRecommended() == null) {
            app.getSpec().setRecommended(false);
        }
        normalizePlatforms(app.getSpec());
        app.getSpec().setTagNames(normalizeValues(app.getSpec().getTagNames()));
        app.getSpec().setArchitectures(normalizeValues(app.getSpec().getArchitectures()));
        app.getSpec().setRelatedAppNames(normalizeValues(app.getSpec().getRelatedAppNames()));
        app.getSpec().setRelatedPostNames(normalizeValues(app.getSpec().getRelatedPostNames()));
        if (Boolean.TRUE.equals(app.getSpec().getPublished())
            && app.getSpec().getPublishedAt() == null) {
            app.getSpec().setPublishedAt(Instant.now());
        }
    }

    private static void normalizeVersion(SoftwareVersion version) {
        if (version.getSpec() == null) {
            throw new ServerWebInputException("Software version spec is required");
        }
        if (!hasText(version.getSpec().getAppName())) {
            throw new ServerWebInputException("Software version appName is required");
        }
        if (!hasText(version.getSpec().getVersion())) {
            throw new ServerWebInputException("Software version is required");
        }
        if (!hasText(version.getSpec().getStatus())) {
            version.getSpec().setStatus("draft");
        }
        var normalizedStatus = version.getSpec().getStatus().trim().toLowerCase(Locale.ROOT);
        if (!List.of("draft", "released", "beta", "deprecated").contains(normalizedStatus)) {
            throw new ServerWebInputException("Unsupported software version status");
        }
        version.getSpec().setStatus(normalizedStatus);
        version.getSpec().setBuild(normalizeOptional(version.getSpec().getBuild()));
        version.getSpec().setMinOsVersion(normalizeOptional(version.getSpec().getMinOsVersion()));
        version.getSpec().setMaxOsVersion(normalizeOptional(version.getSpec().getMaxOsVersion()));
        version.getSpec().setLocale(normalizeOptional(version.getSpec().getLocale()));
        version.getSpec().setChangelog(normalizeOptional(version.getSpec().getChangelog()));
        version.getSpec().setInstallGuide(normalizeOptional(
            version.getSpec().getInstallGuide()));
        if (version.getSpec().getSizeBytes() != null && version.getSpec().getSizeBytes() < 0) {
            throw new ServerWebInputException("Software version sizeBytes must not be negative");
        }
        version.getSpec().setArchitectures(normalizeValues(version.getSpec().getArchitectures()));
        version.getSpec().setInstallWarnings(normalizeValues(version.getSpec().getInstallWarnings()));
        normalizeChecksum(version.getSpec().getChecksum());
    }

    private static void normalizeChecksum(SoftwareVersion.Checksum checksum) {
        if (checksum == null) {
            return;
        }
        checksum.setSha256(normalizeDigest(checksum.getSha256(), SHA256_PATTERN, "SHA-256"));
        checksum.setMd5(normalizeDigest(checksum.getMd5(), MD5_PATTERN, "MD5"));
    }

    private static String normalizeDigest(String value, Pattern pattern, String label) {
        var normalized = normalizeOptional(value);
        if (normalized == null) {
            return null;
        }
        if (!pattern.matcher(normalized).matches()) {
            throw new ServerWebInputException(label + " checksum must be a hexadecimal digest");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static void normalizeDownloadAsset(DownloadAsset asset) {
        if (asset.getSpec() == null) {
            throw new ServerWebInputException("Download asset spec is required");
        }
        if (!hasText(asset.getSpec().getAppName())) {
            throw new ServerWebInputException("Download asset appName is required");
        }
        if (!hasText(asset.getSpec().getVersionName())) {
            throw new ServerWebInputException("Download asset versionName is required");
        }
        if (!hasText(asset.getSpec().getTitle())) {
            throw new ServerWebInputException("Download asset title is required");
        }
        if (asset.getSpec().getEnabled() == null) {
            asset.getSpec().setEnabled(false);
        }
        if (asset.getSpec().getPriority() == null) {
            asset.getSpec().setPriority(0);
        }
        if (asset.getSpec().getRequiresLogin() == null) {
            asset.getSpec().setRequiresLogin(false);
        }
        if (asset.getSpec().getRequiresCaptcha() == null) {
            asset.getSpec().setRequiresCaptcha(false);
        }
        if (asset.getSpec().getPriority() < 0) {
            throw new ServerWebInputException("Download asset priority must not be negative");
        }
        validateDownloadTarget(asset.getSpec());
    }

    private static void normalizeCategory(SoftwareCategory category) {
        if (category.getSpec() == null) {
            throw new ServerWebInputException("Software category spec is required");
        }
        if (!hasText(category.getSpec().getSlug())) {
            throw new ServerWebInputException("Software category slug is required");
        }
        validateSlug(category.getSpec().getSlug(), "Software category slug");
        if (!hasText(category.getSpec().getDisplayName())) {
            throw new ServerWebInputException("Software category displayName is required");
        }
        if (category.getSpec().getVisible() == null) {
            category.getSpec().setVisible(true);
        }
        if (category.getSpec().getPriority() == null) {
            category.getSpec().setPriority(0);
        }
        if (category.getSpec().getPriority() < 0) {
            throw new ServerWebInputException("Software category priority must not be negative");
        }
    }

    private static void normalizeTag(SoftwareTag tag) {
        if (tag.getSpec() == null) {
            throw new ServerWebInputException("Software tag spec is required");
        }
        if (!hasText(tag.getSpec().getSlug())) {
            throw new ServerWebInputException("Software tag slug is required");
        }
        validateSlug(tag.getSpec().getSlug(), "Software tag slug");
        if (!hasText(tag.getSpec().getDisplayName())) {
            throw new ServerWebInputException("Software tag displayName is required");
        }
        if (tag.getSpec().getVisible() == null) {
            tag.getSpec().setVisible(true);
        }
        if (tag.getSpec().getPriority() == null) {
            tag.getSpec().setPriority(0);
        }
        if (tag.getSpec().getPriority() < 0) {
            throw new ServerWebInputException("Software tag priority must not be negative");
        }
        tag.getSpec().setColor(normalizeHexColor(tag.getSpec().getColor(), "Software tag color"));
    }

    private static void normalizeTopic(SoftwareTopic topic) {
        if (topic.getSpec() == null) {
            throw new ServerWebInputException("Software topic spec is required");
        }
        if (!hasText(topic.getSpec().getSlug())) {
            throw new ServerWebInputException("Software topic slug is required");
        }
        validateSlug(topic.getSpec().getSlug(), "Software topic slug");
        if (!hasText(topic.getSpec().getTitle())) {
            throw new ServerWebInputException("Software topic title is required");
        }
        if (topic.getSpec().getPublished() == null) {
            topic.getSpec().setPublished(false);
        }
        if (topic.getSpec().getFeatured() == null) {
            topic.getSpec().setFeatured(false);
        }
        if (topic.getSpec().getPriority() == null) {
            topic.getSpec().setPriority(0);
        }
        if (topic.getSpec().getPriority() < 0) {
            throw new ServerWebInputException("Software topic priority must not be negative");
        }
        if (topic.getSpec().getItems() != null) {
            var seen = new LinkedHashSet<String>();
            topic.getSpec().setItems(topic.getSpec().getItems().stream()
                .filter(Objects::nonNull)
                .filter(item -> hasText(item.getAppName()))
                .filter(item -> seen.add(item.getAppName()))
                .peek(item -> {
                    if (item.getPriority() == null || item.getPriority() < 0) {
                        item.setPriority(0);
                    }
                })
                .toList());
        }
    }

    private Mono<Void> ensureAppExists(String appName) {
        return fetchOrInputError(SoftwareApp.class, appName, "Software app does not exist")
            .then();
    }

    private Mono<Void> ensureAppRefs(SoftwareApp app, String currentName) {
        var spec = app.getSpec();
        var categoryCheck = hasText(spec.getCategoryName())
            ? fetchOrInputError(SoftwareCategory.class, spec.getCategoryName(),
                "Software category does not exist").then()
            : Mono.<Void>empty();
        var tagNames = spec.getTagNames() == null ? List.<String>of() : spec.getTagNames();
        var tagCheck = reactor.core.publisher.Flux.fromIterable(tagNames)
            .filter(SoftwareConsoleServiceImpl::hasText)
            .flatMap(tagName -> fetchOrInputError(SoftwareTag.class, tagName,
                "Software tag does not exist"))
            .then();
        var relatedApps = spec.getRelatedAppNames() == null
            ? List.<String>of() : spec.getRelatedAppNames();
        var relatedAppCheck = reactor.core.publisher.Flux.fromIterable(relatedApps)
            .flatMap(relatedName -> {
                if (Objects.equals(currentName, relatedName)) {
                    return Mono.error(new SoftwareDomainException("REFERENCE_INVALID",
                        "Software app cannot relate to itself"));
                }
                return fetchOrInputError(SoftwareApp.class, relatedName,
                    "Related software app does not exist");
            })
            .then();
        var relatedPostNames = spec.getRelatedPostNames() == null
            ? List.<String>of() : spec.getRelatedPostNames();
        var relatedPostCheck = reactor.core.publisher.Flux.fromIterable(relatedPostNames)
            .flatMap(postName -> fetchOrInputError(
                run.halo.app.core.extension.content.Post.class, postName,
                "Related post does not exist"))
            .then();
        return categoryCheck.then(tagCheck).then(relatedAppCheck).then(relatedPostCheck);
    }

    private Mono<Void> ensureTopicRefs(SoftwareTopic topic) {
        var items = topic.getSpec().getItems() == null
            ? List.<SoftwareTopic.Item>of() : topic.getSpec().getItems();
        return reactor.core.publisher.Flux.fromIterable(items)
            .flatMap(item -> fetchOrInputError(SoftwareApp.class, item.getAppName(),
                "Software topic app does not exist"))
            .then();
    }

    private Mono<Void> ensureCategoryParent(String parentName, String currentName) {
        if (!hasText(parentName)) {
            return Mono.empty();
        }
        if (Objects.equals(parentName, currentName)) {
            return Mono.error(new SoftwareDomainException("REFERENCE_INVALID",
                "Software category cannot be its own parent"));
        }
        return fetchOrInputError(SoftwareCategory.class, parentName,
            "Software parent category does not exist")
            .flatMap(parent -> ensureCategoryAncestor(parent, currentName, new HashSet<>()));
    }

    private Mono<Void> ensureCategoryAncestor(SoftwareCategory category, String currentName,
        HashSet<String> visited) {
        var name = nameOf(category);
        if (!visited.add(name)) {
            return Mono.error(new SoftwareDomainException("REFERENCE_INVALID", "Software category hierarchy has a cycle"));
        }
        if (Objects.equals(name, currentName)) {
            return Mono.error(new SoftwareDomainException("REFERENCE_INVALID",
                "Software category parent cannot be one of its descendants"));
        }
        var parentName = category.getSpec() == null ? null : category.getSpec().getParentName();
        if (!hasText(parentName)) {
            return Mono.empty();
        }
        return fetchOrInputError(SoftwareCategory.class, parentName,
            "Software parent category does not exist")
            .flatMap(parent -> ensureCategoryAncestor(parent, currentName, visited));
    }

    private Mono<Void> ensureVersionUnique(String appName, String version, String allowedName) {
        var options = ListOptions.builder()
            .andQuery(and(equal("spec.appName", appName), equal("spec.version", version)))
            .build();
        return client.listBy(SoftwareVersion.class, options, FIRST_MATCH)
            .flatMap(result -> result.getItems().stream()
                .filter(item -> !Objects.equals(nameOf(item), allowedName))
                .findFirst()
                .<Mono<Void>>map(ignored -> Mono.error(
                    new SoftwareDomainException("ALREADY_EXISTS", "Software version already exists for app")))
                .orElseGet(Mono::empty));
    }

    private Mono<Void> ensureDownloadAssetRefs(DownloadAsset asset) {
        var spec = asset.getSpec();
        return fetchOrInputError(SoftwareApp.class, spec.getAppName(), "Software app does not exist")
            .then(fetchOrInputError(SoftwareVersion.class, spec.getVersionName(),
                "Software version does not exist"))
            .flatMap(version -> {
                var versionAppName = version.getSpec() == null ? null : version.getSpec().getAppName();
                if (!Objects.equals(versionAppName, spec.getAppName())) {
                    return Mono.error(new SoftwareDomainException("REFERENCE_INVALID",
                        "Software version does not belong to app"));
                }
                return Mono.empty();
            })
            .then(ensureDownloadSecretRef(spec));
    }

    private Mono<Void> ensureDownloadSecretRef(DownloadAsset.Spec spec) {
        var secretName = spec.getPasswordSecretName();
        if (!hasText(secretName)) {
            return Mono.empty();
        }
        return fetchOrInputError(Secret.class, secretName, "Download password Secret does not exist")
            .flatMap(secret -> {
                if (!Boolean.TRUE.equals(spec.getEnabled())
                    || DownloadTargetPolicy.firstUsable(spec.getUrl(), spec.getStorageKey()) != null
                    || DownloadTargetPolicy.isUsable(secretValue(secret, "url"))) {
                    return Mono.empty();
                }
                return Mono.error(new SoftwareDomainException("REFERENCE_INVALID",
                    "Enabled download asset requires a usable URL"));
            });
    }

    private Mono<Void> ensureNoVersionsForApp(String appName) {
        return ensureNoChildren(SoftwareVersion.class, "spec.appName", appName,
            "Please delete software versions before deleting the app");
    }

    private Mono<Void> ensureNoDownloadAssetsForApp(String appName) {
        return ensureNoChildren(DownloadAsset.class, "spec.appName", appName,
            "Please delete download assets before deleting the app");
    }

    private Mono<Void> ensureNoDownloadAssetsForVersion(String versionName) {
        return ensureNoChildren(DownloadAsset.class, "spec.versionName", versionName,
            "Please delete download assets before deleting the version");
    }

    private <E extends AbstractExtension> Mono<Void> ensureNoChildren(Class<E> type,
        String fieldName, String value, String message) {
        var options = ListOptions.builder()
            .andQuery(equal(fieldName, value))
            .build();
        return client.listBy(type, options,
                PageRequestImpl.of(1, 1, Sort.by("metadata.name").ascending()))
            .flatMap(result -> {
                if (result.getItems() != null && !result.getItems().isEmpty()) {
                    return Mono.error(new SoftwareDomainException("RESOURCE_IN_USE", message));
                }
                return Mono.empty();
            });
    }

    private static <E extends AbstractExtension> void prepareMetadata(E extension,
        String generateName) {
        var metadata = new Metadata();
        metadata.setGenerateName(generateName);
        extension.setMetadata(metadata);
    }

    private static <E extends AbstractExtension> void prepareMetadata(E extension,
        String generateName, String name) {
        var metadata = new Metadata();
        metadata.setName(name);
        metadata.setGenerateName(generateName);
        extension.setMetadata(metadata);
    }

    private <E extends AbstractExtension> Mono<E> fetchOrNotFound(Class<E> type, String name,
        String message) {
        return client.fetch(type, name)
            .switchIfEmpty(Mono.error(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, message)));
    }

    private <E extends AbstractExtension> Mono<E> fetchOrInputError(Class<E> type, String name,
        String message) {
        return client.fetch(type, name)
            .filter(resource -> resource.getMetadata() != null
                && resource.getMetadata().getDeletionTimestamp() == null)
            .switchIfEmpty(Mono.error(() -> new SoftwareDomainException("REFERENCE_INVALID", message)));
    }

    private static <E extends AbstractExtension, S> String slugOf(E extension,
        Function<E, S> specGetter, Function<S, String> slugGetter) {
        var spec = specGetter.apply(extension);
        return spec == null ? null : slugGetter.apply(spec);
    }

    private static String nameOf(AbstractExtension extension) {
        return extension.getMetadata() == null ? null : extension.getMetadata().getName();
    }

    private static void validateSlug(String slug, String fieldName) {
        if (!SLUG_PATTERN.matcher(slug).matches()) {
            throw new ServerWebInputException(fieldName
                + " must use lowercase letters, numbers, and hyphens");
        }
    }

    private static String normalizeHexColor(String color, String fieldName) {
        if (!hasText(color)) {
            return null;
        }
        var normalized = color.trim().toLowerCase(Locale.ROOT);
        if (!HEX_COLOR_PATTERN.matcher(normalized).matches()) {
            throw new ServerWebInputException(fieldName + " must be a hex color like #2563eb");
        }
        if (normalized.length() == 4) {
            return "#%s%s%s%s%s%s".formatted(
                normalized.charAt(1), normalized.charAt(1),
                normalized.charAt(2), normalized.charAt(2),
                normalized.charAt(3), normalized.charAt(3));
        }
        return normalized;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static List<String> platformValues(SoftwareApp.Spec spec) {
        if (spec.getPlatforms() != null) {
            return normalizeValues(spec.getPlatforms());
        }
        return splitPlatformValues(spec.getPlatform());
    }

    private static void normalizePlatforms(SoftwareApp.Spec spec) {
        var values = spec.getPlatforms() == null
            ? splitPlatformValues(spec.getPlatform())
            : normalizeValues(spec.getPlatforms());
        spec.setPlatforms(values);
        spec.setPlatform(values.isEmpty() ? null : String.join(", ", values));
    }

    private static List<String> splitPlatformValues(String value) {
        if (!hasText(value)) {
            return List.of();
        }
        return java.util.Arrays.stream(value.trim().split("[,，/\\s]+"))
            .filter(SoftwareConsoleServiceImpl::hasText)
            .distinct()
            .toList();
    }

    private static List<String> normalizeValues(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(SoftwareConsoleServiceImpl::hasText)
            .distinct()
            .toList();
    }

    private static String normalizeOptional(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private static <T> T requiredSpec(T spec, String message) {
        if (spec == null) {
            throw new ServerWebInputException(message);
        }
        return spec;
    }

    private static void validateDownloadTarget(DownloadAsset.Spec spec) {
        if (!Boolean.TRUE.equals(spec.getEnabled())) {
            return;
        }
        if (DownloadTargetPolicy.firstUsable(spec.getUrl(), spec.getStorageKey()) != null
            || hasText(spec.getPasswordSecretName())) {
            return;
        }
        if (!hasText(spec.getUrl()) && !hasText(spec.getStorageKey())) {
            throw new ServerWebInputException(
                "Enabled download asset requires a URL, storage key, or password Secret");
        }
        throw new ServerWebInputException(
            "Download URL must use http, https, or a safe absolute site path");
    }

    private static String secretValue(Secret secret, String key) {
        var data = secret.getData();
        var value = data == null ? null : data.get(key);
        return value == null ? null : new String(value, StandardCharsets.UTF_8);
    }

    private static void mergeApp(SoftwareApp source, SoftwareApp target) {
        if (source == null || source.getSpec() == null) {
            throw new ServerWebInputException("Software app spec is required");
        }
        if (target.getSpec() == null) {
            target.setSpec(source.getSpec());
            return;
        }
        var existingSeo = target.getSpec().getSeo();
        mergeSpec(source.getSpec(), target.getSpec());
        if (source.getSpec().getSeo() != null && existingSeo != null) {
            mergeSpec(source.getSpec().getSeo(), existingSeo);
            target.getSpec().setSeo(existingSeo);
        }
    }

    private static void mergeVersion(SoftwareVersion source, SoftwareVersion target) {
        if (source == null || source.getSpec() == null) {
            throw new ServerWebInputException("Software version spec is required");
        }
        if (target.getSpec() == null) {
            target.setSpec(source.getSpec());
            return;
        }
        var existingChecksum = target.getSpec().getChecksum();
        mergeSpec(source.getSpec(), target.getSpec());
        if (source.getSpec().getChecksum() != null && existingChecksum != null) {
            mergeSpec(source.getSpec().getChecksum(), existingChecksum);
            target.getSpec().setChecksum(existingChecksum);
        }
    }

    private static <T> void mergeSpec(T source, T target) {
        if (source == null) {
            throw new ServerWebInputException("Resource spec is required");
        }
        if (target == null) {
            throw new ServerWebInputException("Stored resource spec is missing");
        }
        var wrapper = new BeanWrapperImpl(source);
        var nullProperties = java.util.Arrays.stream(wrapper.getPropertyDescriptors())
            .map(descriptor -> descriptor.getName())
            .filter(property -> wrapper.getPropertyValue(property) == null)
            .toArray(String[]::new);
        BeanUtils.copyProperties(source, target, nullProperties);
    }

    private static final class AppStatsAccumulator {

        private long total;
        private long published;
        private long downloads;
        private final LinkedHashSet<String> platforms = new LinkedHashSet<>();

        private void accept(SoftwareApp app) {
            total++;
            var spec = app.getSpec();
            if (spec != null && Boolean.TRUE.equals(spec.getPublished())) {
                published++;
            }
            if (app.getStatus() != null && app.getStatus().getDownloadCount() != null) {
                downloads += app.getStatus().getDownloadCount();
            }
            if (spec != null) {
                platformValues(spec).forEach(platforms::add);
            }
        }
    }

    private static final class DownloadAssetStatsAccumulator {

        private long total;
        private long enabled;
        private long protectedCount;
        private long downloads;

        private void accept(DownloadAsset asset) {
            total++;
            var spec = asset.getSpec();
            if (spec != null && Boolean.TRUE.equals(spec.getEnabled())) {
                enabled++;
            }
            if (spec != null && (Boolean.TRUE.equals(spec.getRequiresLogin())
                || Boolean.TRUE.equals(spec.getRequiresCaptcha())
                || hasText(spec.getExtractionCode())
                || hasText(spec.getPasswordSecretName()))) {
                protectedCount++;
            }
            if (asset.getStatus() != null && asset.getStatus().getDownloadCount() != null) {
                downloads += Math.max(0L, asset.getStatus().getDownloadCount());
            }
        }

        private DownloadAssetStatsVo toVo() {
            return DownloadAssetStatsVo.builder()
                .total(total)
                .enabled(enabled)
                .disabled(total - enabled)
                .protectedCount(protectedCount)
                .downloads(downloads)
                .build();
        }
    }
}
