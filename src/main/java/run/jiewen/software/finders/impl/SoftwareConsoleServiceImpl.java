package run.jiewen.software.finders.impl;

import static run.halo.app.extension.index.query.Queries.equal;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.PageRequest;
import run.halo.app.extension.PageRequestImpl;
import run.halo.app.extension.ReactiveExtensionClient;
import run.jiewen.software.SoftwareConsoleQuery;
import run.jiewen.software.extension.DownloadAsset;
import run.jiewen.software.extension.SoftwareApp;
import run.jiewen.software.extension.SoftwareCategory;
import run.jiewen.software.extension.SoftwareTag;
import run.jiewen.software.extension.SoftwareTopic;
import run.jiewen.software.extension.SoftwareVersion;
import run.jiewen.software.finders.SoftwareConsoleService;
import run.jiewen.software.vo.SoftwareConsoleStatsVo;

@Component
public class SoftwareConsoleServiceImpl implements SoftwareConsoleService {

    private static final PageRequest FIRST_PAGE =
        PageRequestImpl.of(1, 1000, Sort.by("metadata.name").ascending());
    private static final Pattern SLUG_PATTERN = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Pattern HEX_COLOR_PATTERN =
        Pattern.compile("#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})");

    private final ReactiveExtensionClient client;

    public SoftwareConsoleServiceImpl(ReactiveExtensionClient client) {
        this.client = client;
    }

    @Override
    public Mono<ListResult<SoftwareApp>> listApps(SoftwareConsoleQuery query) {
        return client.listBy(SoftwareApp.class, query.toListOptions(), query.toPageRequest());
    }

    @Override
    public Mono<SoftwareConsoleStatsVo> getAppStats() {
        return client.listAll(SoftwareApp.class, ListOptions.builder().build(), Sort.unsorted())
            .collectList()
            .map(apps -> {
                var platforms = new LinkedHashSet<String>();
                long published = 0;
                long downloads = 0;
                for (var app : apps) {
                    var spec = app.getSpec();
                    if (spec != null && Boolean.TRUE.equals(spec.getPublished())) {
                        published++;
                    }
                    if (app.getStatus() != null && app.getStatus().getDownloadCount() != null) {
                        downloads += app.getStatus().getDownloadCount();
                    }
                    if (spec != null) {
                        platformValues(spec.getPlatform()).forEach(platforms::add);
                    }
                }
                return SoftwareConsoleStatsVo.builder()
                    .total(apps.size())
                    .published(published)
                    .drafts(apps.size() - published)
                    .downloads(downloads)
                    .platforms(List.copyOf(platforms))
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
            prepareMetadata(app, "software-app-", "software-app-" + app.getSpec().getSlug());
            return ensureAppRefs(app)
                .then(ensureSlugUnique(app.getSpec().getSlug(), null))
                .then(Mono.defer(() -> client.create(app)));
        });
    }

    @Override
    public Mono<SoftwareApp> updateApp(String name, SoftwareApp app) {
        return Mono.defer(() -> {
            normalizeApp(app);
            return getApp(name)
                .flatMap(existing -> ensureAppRefs(app)
                    .then(ensureSlugUnique(app.getSpec().getSlug(), name))
                    .then(Mono.defer(() -> {
                        app.setMetadata(existing.getMetadata());
                        return client.update(app);
                    })));
        });
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
            prepareMetadata(version, "software-version-");
            return ensureAppExists(version.getSpec().getAppName())
                .then(Mono.defer(() -> client.create(version)));
        });
    }

    @Override
    public Mono<SoftwareVersion> updateVersion(String name, SoftwareVersion version) {
        return Mono.defer(() -> {
            normalizeVersion(version);
            return fetchOrNotFound(SoftwareVersion.class, name, "Software version not found")
                .flatMap(existing -> ensureAppExists(version.getSpec().getAppName())
                    .then(Mono.defer(() -> {
                        version.setMetadata(existing.getMetadata());
                        return client.update(version);
                    })));
        });
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
    public Mono<DownloadAsset> createDownloadAsset(DownloadAsset asset) {
        return Mono.defer(() -> {
            normalizeDownloadAsset(asset);
            prepareMetadata(asset, "download-asset-");
            return ensureDownloadAssetRefs(asset)
                .then(Mono.defer(() -> client.create(asset)));
        });
    }

    @Override
    public Mono<DownloadAsset> updateDownloadAsset(String name, DownloadAsset asset) {
        return Mono.defer(() -> {
            normalizeDownloadAsset(asset);
            return fetchOrNotFound(DownloadAsset.class, name, "Download asset not found")
                .flatMap(existing -> ensureDownloadAssetRefs(asset)
                    .then(Mono.defer(() -> {
                        asset.setMetadata(existing.getMetadata());
                        return client.update(asset);
                    })));
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
            prepareMetadata(category, "software-category-");
            return ensureSlugUnique(SoftwareCategory.class, category.getSpec().getSlug(), null,
                SoftwareCategory::getSpec, SoftwareCategory.Spec::getSlug,
                "Software category slug already exists")
                .then(Mono.defer(() -> client.create(category)));
        });
    }

    @Override
    public Mono<SoftwareCategory> updateCategory(String name, SoftwareCategory category) {
        return Mono.defer(() -> {
            normalizeCategory(category);
            return fetchOrNotFound(SoftwareCategory.class, name, "Software category not found")
                .flatMap(existing -> ensureSlugUnique(SoftwareCategory.class,
                    category.getSpec().getSlug(), name, SoftwareCategory::getSpec,
                    SoftwareCategory.Spec::getSlug, "Software category slug already exists")
                    .then(Mono.defer(() -> {
                        category.setMetadata(existing.getMetadata());
                        return client.update(category);
                    })));
        });
    }

    @Override
    public Mono<SoftwareCategory> deleteCategory(String name) {
        return fetchOrNotFound(SoftwareCategory.class, name, "Software category not found")
            .flatMap(category -> ensureNoChildren(SoftwareApp.class, "spec.categoryName", name,
                "Please remove software category references before deleting")
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
            prepareMetadata(tag, "software-tag-");
            return ensureSlugUnique(SoftwareTag.class, tag.getSpec().getSlug(), null,
                SoftwareTag::getSpec, SoftwareTag.Spec::getSlug,
                "Software tag slug already exists")
                .then(Mono.defer(() -> client.create(tag)));
        });
    }

    @Override
    public Mono<SoftwareTag> updateTag(String name, SoftwareTag tag) {
        return Mono.defer(() -> {
            normalizeTag(tag);
            return fetchOrNotFound(SoftwareTag.class, name, "Software tag not found")
                .flatMap(existing -> ensureSlugUnique(SoftwareTag.class, tag.getSpec().getSlug(),
                    name, SoftwareTag::getSpec, SoftwareTag.Spec::getSlug,
                    "Software tag slug already exists")
                    .then(Mono.defer(() -> {
                        tag.setMetadata(existing.getMetadata());
                        return client.update(tag);
                    })));
        });
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
            prepareMetadata(topic, "software-topic-");
            return ensureSlugUnique(SoftwareTopic.class, topic.getSpec().getSlug(), null,
                SoftwareTopic::getSpec, SoftwareTopic.Spec::getSlug,
                "Software topic slug already exists")
                .then(Mono.defer(() -> client.create(topic)));
        });
    }

    @Override
    public Mono<SoftwareTopic> updateTopic(String name, SoftwareTopic topic) {
        return Mono.defer(() -> {
            normalizeTopic(topic);
            return fetchOrNotFound(SoftwareTopic.class, name, "Software topic not found")
                .flatMap(existing -> ensureSlugUnique(SoftwareTopic.class, topic.getSpec().getSlug(),
                    name, SoftwareTopic::getSpec, SoftwareTopic.Spec::getSlug,
                    "Software topic slug already exists")
                    .then(Mono.defer(() -> {
                        topic.setMetadata(existing.getMetadata());
                        return client.update(topic);
                    })));
        });
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
        return client.listBy(type, options, FIRST_PAGE)
            .flatMap(result -> {
                var duplicate = result.getItems()
                    .stream()
                    .filter(item -> Objects.equals(slugOf(item, specGetter, slugGetter), slug))
                    .filter(item -> !Objects.equals(nameOf(item), allowedName))
                    .findFirst();
                if (duplicate.isPresent()) {
                    return Mono.error(new ServerWebInputException(message));
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
            version.getSpec().setStatus("released");
        }
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
            asset.getSpec().setEnabled(true);
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
    }

    private Mono<Void> ensureAppExists(String appName) {
        return fetchOrInputError(SoftwareApp.class, appName, "Software app does not exist")
            .then();
    }

    private Mono<Void> ensureAppRefs(SoftwareApp app) {
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
        return categoryCheck.then(tagCheck);
    }

    private Mono<Void> ensureDownloadAssetRefs(DownloadAsset asset) {
        var spec = asset.getSpec();
        return fetchOrInputError(SoftwareApp.class, spec.getAppName(), "Software app does not exist")
            .then(fetchOrInputError(SoftwareVersion.class, spec.getVersionName(),
                "Software version does not exist"))
            .flatMap(version -> {
                var versionAppName = version.getSpec() == null ? null : version.getSpec().getAppName();
                if (!Objects.equals(versionAppName, spec.getAppName())) {
                    return Mono.error(new ServerWebInputException(
                        "Software version does not belong to app"));
                }
                return Mono.empty();
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
        return client.listBy(type, options, FIRST_PAGE)
            .flatMap(result -> {
                if (result.getItems() != null && !result.getItems().isEmpty()) {
                    return Mono.error(new ServerWebInputException(message));
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
            .switchIfEmpty(Mono.error(() -> new ServerWebInputException(message)));
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

    private static List<String> platformValues(String value) {
        if (!hasText(value)) {
            return List.of();
        }
        return java.util.Arrays.stream(value.trim().split("[,，/\\s]+"))
            .filter(SoftwareConsoleServiceImpl::hasText)
            .distinct()
            .toList();
    }
}
