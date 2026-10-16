package run.jiewen.software;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import run.halo.app.extension.Scheme;
import run.halo.app.extension.SchemeManager;
import run.halo.app.extension.index.IndexSpecs;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;
import run.jiewen.software.extension.DownloadAsset;
import run.jiewen.software.extension.SoftwareCreateOperation;
import run.jiewen.software.extension.SoftwareApp;
import run.jiewen.software.extension.SoftwareCategory;
import run.jiewen.software.extension.SoftwareTag;
import run.jiewen.software.extension.SoftwareTopic;
import run.jiewen.software.extension.SoftwareVersion;

/**
 * <p>Plugin main class to manage the lifecycle of the plugin.</p>
 * <p>This class must be public and have a public constructor.</p>
 * <p>Only one main class extending {@link BasePlugin} is allowed per plugin.</p>
 *
 * @author jiewenhuang
 * @since 1.0.0
 */
@Component
public class SoftwarePlugin extends BasePlugin {

    private final SchemeManager schemeManager;

    public SoftwarePlugin(PluginContext pluginContext, SchemeManager schemeManager) {
        super(pluginContext);
        this.schemeManager = schemeManager;
    }

    @Override
    public void start() {
        schemeManager.register(SoftwareCreateOperation.class);
        schemeManager.register(SoftwareApp.class, indexSpecs -> {
            indexSpecs.add(IndexSpecs.<SoftwareApp, String>single("spec.slug", String.class)
                .indexFunc(app -> app.getSpec().getSlug()));
            indexSpecs.add(IndexSpecs.<SoftwareApp, String>single("spec.displayName", String.class)
                .indexFunc(app -> app.getSpec().getDisplayName()));
            indexSpecs.add(IndexSpecs.<SoftwareApp, String>single("spec.categoryName", String.class)
                .indexFunc(app -> app.getSpec().getCategoryName()));
            indexSpecs.add(IndexSpecs.<SoftwareApp, String>multi("spec.tagNames", String.class)
                .indexFunc(app -> app.getSpec().getTagNames() == null
                    ? Set.of()
                    : app.getSpec().getTagNames().stream().collect(Collectors.toSet())));
            indexSpecs.add(IndexSpecs.<SoftwareApp, String>multi("spec.relatedAppNames", String.class)
                .indexFunc(app -> app.getSpec().getRelatedAppNames() == null ? Set.of()
                    : app.getSpec().getRelatedAppNames().stream().filter(Objects::nonNull).collect(Collectors.toSet())));
            indexSpecs.add(IndexSpecs.<SoftwareApp, String>single("spec.platform", String.class)
                .indexFunc(app -> app.getSpec().getPlatform()));
            indexSpecs.add(IndexSpecs.<SoftwareApp, String>multi("spec.platforms", String.class)
                .indexFunc(app -> app.getSpec().getPlatforms() == null
                    ? Set.of()
                    : app.getSpec().getPlatforms().stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet())));
            indexSpecs.add(IndexSpecs.<SoftwareApp, Boolean>single("spec.published", Boolean.class)
                .indexFunc(app -> app.getSpec().getPublished()));
            indexSpecs.add(IndexSpecs.<SoftwareApp, Boolean>single("spec.featured", Boolean.class)
                .indexFunc(app -> app.getSpec().getFeatured()));
            indexSpecs.add(IndexSpecs.<SoftwareApp, Boolean>single("spec.recommended", Boolean.class)
                .indexFunc(app -> app.getSpec().getRecommended()));
            indexSpecs.add(IndexSpecs.<SoftwareApp, String>single("spec.publishedAt", String.class)
                .indexFunc(app -> app.getSpec().getPublishedAt() == null
                    ? null
                    : app.getSpec().getPublishedAt().toString()));
            indexSpecs.add(IndexSpecs.<SoftwareApp, String>single("status.latestReleaseDate",
                    String.class)
                .indexFunc(app -> app.getStatus() == null
                    || app.getStatus().getLatestReleaseDate() == null
                    ? null
                    : app.getStatus().getLatestReleaseDate().toString()));
            indexSpecs.add(IndexSpecs.<SoftwareApp, Long>single("status.downloadCount", Long.class)
                .indexFunc(app -> app.getStatus() == null
                    ? null
                    : app.getStatus().getDownloadCount()));
            indexSpecs.add(IndexSpecs.<SoftwareApp, Long>single("status.likeCount", Long.class)
                .indexFunc(app -> app.getStatus() == null
                    ? null
                    : app.getStatus().getLikeCount()));
            indexSpecs.add(IndexSpecs.<SoftwareApp, Long>single("status.commentCount", Long.class)
                .indexFunc(app -> app.getStatus() == null
                    ? null
                    : app.getStatus().getCommentCount()));
        });
        schemeManager.register(SoftwareVersion.class, indexSpecs -> {
            indexSpecs.add(IndexSpecs.<SoftwareVersion, String>single("spec.appName", String.class)
                .indexFunc(version -> version.getSpec().getAppName()));
            indexSpecs.add(IndexSpecs.<SoftwareVersion, String>single("spec.version", String.class)
                .indexFunc(version -> version.getSpec().getVersion()));
            indexSpecs.add(IndexSpecs.<SoftwareVersion, String>single("spec.status", String.class)
                .indexFunc(version -> version.getSpec().getStatus()));
            indexSpecs.add(IndexSpecs.<SoftwareVersion, String>single("spec.releaseDate", String.class)
                .indexFunc(version -> version.getSpec().getReleaseDate() == null
                    ? null
                    : version.getSpec().getReleaseDate().toString()));
        });
        schemeManager.register(DownloadAsset.class, indexSpecs -> {
            indexSpecs.add(IndexSpecs.<DownloadAsset, String>single("spec.appName", String.class)
                .indexFunc(asset -> asset.getSpec().getAppName()));
            indexSpecs.add(IndexSpecs.<DownloadAsset, String>single("spec.versionName", String.class)
                .indexFunc(asset -> asset.getSpec().getVersionName()));
            indexSpecs.add(IndexSpecs.<DownloadAsset, String>single("spec.title", String.class)
                .indexFunc(asset -> asset.getSpec().getTitle()));
            indexSpecs.add(IndexSpecs.<DownloadAsset, String>single("spec.type", String.class)
                .indexFunc(asset -> asset.getSpec().getType()));
            indexSpecs.add(IndexSpecs.<DownloadAsset, String>single("spec.provider", String.class)
                .indexFunc(asset -> asset.getSpec().getProvider()));
            indexSpecs.add(IndexSpecs.<DownloadAsset, Boolean>single("spec.enabled", Boolean.class)
                .indexFunc(asset -> asset.getSpec().getEnabled()));
            indexSpecs.add(IndexSpecs.<DownloadAsset, Boolean>single("spec.protected", Boolean.class)
                .indexFunc(asset -> {
                    var spec = asset.getSpec();
                    return Boolean.TRUE.equals(spec.getRequiresLogin())
                        || Boolean.TRUE.equals(spec.getRequiresCaptcha())
                        || hasText(spec.getExtractionCode())
                        || hasText(spec.getPasswordSecretName());
                }));
            indexSpecs.add(IndexSpecs.<DownloadAsset, Integer>single("spec.priority", Integer.class)
                .indexFunc(asset -> asset.getSpec().getPriority()));
            indexSpecs.add(IndexSpecs.<DownloadAsset, Long>single("status.downloadCount", Long.class)
                .indexFunc(asset -> asset.getStatus() == null
                    ? null : asset.getStatus().getDownloadCount()));
            indexSpecs.add(IndexSpecs.<DownloadAsset, String>single("status.lastCheckedAt", String.class)
                .indexFunc(asset -> asset.getStatus() == null
                    || asset.getStatus().getLastCheckedAt() == null
                    ? null : asset.getStatus().getLastCheckedAt().toString()));
        });
        schemeManager.register(SoftwareCategory.class, indexSpecs -> {
            indexSpecs.add(IndexSpecs.<SoftwareCategory, String>single("spec.slug", String.class)
                .indexFunc(category -> category.getSpec().getSlug()));
            indexSpecs.add(IndexSpecs.<SoftwareCategory, String>single("spec.displayName",
                    String.class)
                .indexFunc(category -> category.getSpec().getDisplayName()));
            indexSpecs.add(IndexSpecs.<SoftwareCategory, String>single("spec.parentName", String.class)
                .indexFunc(category -> category.getSpec().getParentName()));
            indexSpecs.add(IndexSpecs.<SoftwareCategory, Boolean>single("spec.visible", Boolean.class)
                .indexFunc(category -> category.getSpec().getVisible()));
            indexSpecs.add(IndexSpecs.<SoftwareCategory, Integer>single("spec.priority", Integer.class)
                .indexFunc(category -> category.getSpec().getPriority()));
            indexSpecs.add(IndexSpecs.<SoftwareCategory, Integer>single("status.appCount", Integer.class)
                .indexFunc(category -> category.getStatus() == null
                    ? null : category.getStatus().getAppCount()));
        });
        schemeManager.register(SoftwareTag.class, indexSpecs -> {
            indexSpecs.add(IndexSpecs.<SoftwareTag, String>single("spec.slug", String.class)
                .indexFunc(tag -> tag.getSpec().getSlug()));
            indexSpecs.add(IndexSpecs.<SoftwareTag, String>single("spec.displayName", String.class)
                .indexFunc(tag -> tag.getSpec().getDisplayName()));
            indexSpecs.add(IndexSpecs.<SoftwareTag, Boolean>single("spec.visible", Boolean.class)
                .indexFunc(tag -> tag.getSpec().getVisible()));
            indexSpecs.add(IndexSpecs.<SoftwareTag, Integer>single("spec.priority", Integer.class)
                .indexFunc(tag -> tag.getSpec().getPriority()));
            indexSpecs.add(IndexSpecs.<SoftwareTag, Integer>single("status.appCount", Integer.class)
                .indexFunc(tag -> tag.getStatus() == null ? null : tag.getStatus().getAppCount()));
        });
        schemeManager.register(SoftwareTopic.class, indexSpecs -> {
            indexSpecs.add(IndexSpecs.<SoftwareTopic, String>multi("spec.items.appName", String.class)
                .indexFunc(topic -> topic.getSpec().getItems() == null ? Set.of()
                    : topic.getSpec().getItems().stream().filter(Objects::nonNull)
                        .map(SoftwareTopic.Item::getAppName).filter(Objects::nonNull).collect(Collectors.toSet())));
            indexSpecs.add(IndexSpecs.<SoftwareTopic, String>single("spec.slug", String.class)
                .indexFunc(topic -> topic.getSpec().getSlug()));
            indexSpecs.add(IndexSpecs.<SoftwareTopic, String>single("spec.title", String.class)
                .indexFunc(topic -> topic.getSpec().getTitle()));
            indexSpecs.add(IndexSpecs.<SoftwareTopic, Boolean>single("spec.published", Boolean.class)
                .indexFunc(topic -> topic.getSpec().getPublished()));
            indexSpecs.add(IndexSpecs.<SoftwareTopic, Boolean>single("spec.featured", Boolean.class)
                .indexFunc(topic -> topic.getSpec().getFeatured()));
            indexSpecs.add(IndexSpecs.<SoftwareTopic, Integer>single("spec.priority", Integer.class)
                .indexFunc(topic -> topic.getSpec().getPriority()));
            indexSpecs.add(IndexSpecs.<SoftwareTopic, Integer>single("status.appCount", Integer.class)
                .indexFunc(topic -> topic.getStatus() == null
                    ? null : topic.getStatus().getAppCount()));
        });
    }

    @Override
    public void stop() {
        unregister(SoftwareCreateOperation.class);
        unregister(SoftwareApp.class);
        unregister(SoftwareVersion.class);
        unregister(DownloadAsset.class);
        unregister(SoftwareCategory.class);
        unregister(SoftwareTag.class);
        unregister(SoftwareTopic.class);
    }

    private void unregister(Class<? extends run.halo.app.extension.Extension> type) {
        schemeManager.unregister(Scheme.buildFromType(Objects.requireNonNull(type)));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
