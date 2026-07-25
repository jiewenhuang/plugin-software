package run.jiewen.software.reconciler;

import static run.halo.app.extension.index.query.Queries.and;
import static run.halo.app.extension.index.query.Queries.equal;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import run.halo.app.core.extension.Counter;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.ExtensionClient;
import run.halo.app.extension.ExtensionUtil;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Secret;
import run.jiewen.software.download.DownloadTargetPolicy;
import run.jiewen.software.extension.DownloadAsset;
import run.jiewen.software.extension.SoftwareApp;
import run.jiewen.software.extension.SoftwareCategory;
import run.jiewen.software.extension.SoftwareTag;
import run.jiewen.software.extension.SoftwareTopic;
import run.jiewen.software.extension.SoftwareVersion;

/** Calculates all server-owned software status fields from current desired state. */
@Component
public class SoftwareStatusService {

    static final String COUNTER_PREFIX = "softwareapps.software.jiewen.run/";

    private final ExtensionClient client;

    public SoftwareStatusService(ExtensionClient client) {
        this.client = client;
    }

    public void reconcileAsset(String name) {
        client.fetch(DownloadAsset.class, name)
            .filter(SoftwareStatusService::isLive)
            .ifPresent(asset -> {
                var previous = asset.getStatus();
                var next = previous == null ? new DownloadAsset.Status() : previous;
                var health = assetHealth(asset.getSpec());
                var appName = asset.getSpec() == null ? null : asset.getSpec().getAppName();
                var versionName = asset.getSpec() == null
                    ? null : asset.getSpec().getVersionName();
                var specChanged = !observedCurrent(previous == null ? null
                    : previous.getObservedVersion(), asset);
                var changed = previous == null
                    || !Objects.equals(previous.getHealth(), health)
                    || previous.getDownloadCount() == null
                    || previous.getDownloadCount() < 0
                    || !Objects.equals(previous.getObservedAppName(), appName)
                    || !Objects.equals(previous.getObservedVersionName(), versionName)
                    || specChanged;
                if (!changed) {
                    return;
                }
                next.setHealth(health);
                next.setDownloadCount(Math.max(0L,
                    next.getDownloadCount() == null ? 0L : next.getDownloadCount()));
                next.setLastCheckedAt(Instant.now());
                next.setObservedAppName(appName);
                next.setObservedVersionName(versionName);
                next.setObservedVersion(nextObservedVersion(asset));
                asset.setStatus(next);
                client.update(asset);
            });
    }

    public void reconcileVersion(String name) {
        client.fetch(SoftwareVersion.class, name)
            .filter(SoftwareStatusService::isLive)
            .ifPresent(version -> {
                var assets = assetsForVersion(name).stream()
                    .filter(SoftwareStatusService::isLive)
                    .toList();
                var enabledAssets = assets.stream()
                    .filter(asset -> asset.getSpec() != null
                        && Boolean.TRUE.equals(asset.getSpec().getEnabled()))
                    .sorted(SoftwareStatusService::compareAssetPriority)
                    .toList();
                var count = enabledAssets.size();
                var downloads = enabledAssets.stream()
                    .map(DownloadAsset::getStatus)
                    .filter(Objects::nonNull)
                    .map(DownloadAsset.Status::getDownloadCount)
                    .filter(Objects::nonNull)
                    .mapToLong(value -> Math.max(0L, value))
                    .sum();
                var primary = enabledAssets.isEmpty()
                    ? null : nameOf(enabledAssets.get(0));
                var previous = version.getStatus();
                var appName = version.getSpec() == null ? null : version.getSpec().getAppName();
                var changed = previous == null
                    || !Objects.equals(previous.getAssetCount(), count)
                    || !Objects.equals(previous.getDownloadCount(), downloads)
                    || !Objects.equals(previous.getPrimaryAssetName(), primary)
                    || !Objects.equals(previous.getObservedAppName(), appName)
                    || !observedCurrent(previous.getObservedVersion(), version);
                if (!changed) {
                    return;
                }
                var next = previous == null ? new SoftwareVersion.Status() : previous;
                next.setAssetCount(count);
                next.setDownloadCount(downloads);
                next.setPrimaryAssetName(primary);
                next.setObservedAppName(appName);
                next.setObservedVersion(nextObservedVersion(version));
                version.setStatus(next);
                client.update(version);
            });
    }

    public void reconcileApp(String name) {
        client.fetch(SoftwareApp.class, name)
            .filter(SoftwareStatusService::isLive)
            .ifPresent(app -> {
                var platformsMigrated = migratePlatforms(app);
                var versions = releasedVersionsForApp(name).stream()
                    .filter(SoftwareStatusService::isLive)
                    .sorted(SoftwareStatusService::compareVersionRecency)
                    .toList();
                var versionNames = versions.stream()
                    .map(SoftwareStatusService::nameOf)
                    .filter(Objects::nonNull)
                    .collect(java.util.stream.Collectors.toSet());
                var assets = assetsForApp(name).stream()
                    .filter(SoftwareStatusService::isLive)
                    .filter(asset -> asset.getSpec() != null
                        && Boolean.TRUE.equals(asset.getSpec().getEnabled())
                        && versionNames.contains(asset.getSpec().getVersionName()))
                    .toList();
                var latest = versions.isEmpty() ? null : versions.get(0);
                var latestSpec = latest == null ? null : latest.getSpec();
                var downloads = assets.stream()
                    .map(DownloadAsset::getStatus)
                    .filter(Objects::nonNull)
                    .map(DownloadAsset.Status::getDownloadCount)
                    .filter(Objects::nonNull)
                    .mapToLong(value -> Math.max(0L, value))
                    .sum();
                var counter = client.fetch(Counter.class, COUNTER_PREFIX + name).orElse(null);
                var likes = counter == null || counter.getUpvote() == null
                    ? 0L : Math.max(0, counter.getUpvote());
                var comments = counter == null || counter.getApprovedComment() == null
                    ? 0L : Math.max(0, counter.getApprovedComment());
                var health = appHealth(app, versions, assets);
                var previous = app.getStatus();
                var changed = previous == null
                    || !Objects.equals(previous.getLatestVersionName(), nameOf(latest))
                    || !Objects.equals(previous.getLatestVersion(),
                        latestSpec == null ? null : latestSpec.getVersion())
                    || !Objects.equals(previous.getLatestReleaseDate(),
                        latestSpec == null ? null : latestSpec.getReleaseDate())
                    || !Objects.equals(previous.getMinOsVersion(),
                        latestSpec == null ? null : latestSpec.getMinOsVersion())
                    || !Objects.equals(previous.getSizeBytes(),
                        latestSpec == null ? null : latestSpec.getSizeBytes())
                    || !Objects.equals(previous.getDownloadCount(), downloads)
                    || !Objects.equals(previous.getLikeCount(), likes)
                    || !Objects.equals(previous.getCommentCount(), comments)
                    || !Objects.equals(previous.getHealth(), health)
                    || !observedCurrent(previous.getObservedVersion(), app)
                    || platformsMigrated;
                if (!changed) {
                    return;
                }
                var next = previous == null ? new SoftwareApp.Status() : previous;
                next.setLatestVersionName(nameOf(latest));
                next.setLatestVersion(latestSpec == null ? null : latestSpec.getVersion());
                next.setLatestReleaseDate(latestSpec == null ? null : latestSpec.getReleaseDate());
                next.setMinOsVersion(latestSpec == null ? null : latestSpec.getMinOsVersion());
                next.setSizeBytes(latestSpec == null ? null : latestSpec.getSizeBytes());
                next.setDownloadCount(downloads);
                next.setLikeCount(likes);
                next.setCommentCount(comments);
                next.setHealth(health);
                next.setObservedVersion(nextObservedVersion(app));
                app.setStatus(next);
                client.update(app);
            });
    }

    public void reconcileCategory(String name) {
        client.fetch(SoftwareCategory.class, name)
            .filter(SoftwareStatusService::isLive)
            .ifPresent(category -> updateCategoryStatus(category,
                publishedApps().stream()
                    .filter(app -> app.getSpec() != null
                        && Objects.equals(name, app.getSpec().getCategoryName()))
                    .mapToInt(ignored -> 1)
                    .sum()));
    }

    public void reconcileTag(String name) {
        client.fetch(SoftwareTag.class, name)
            .filter(SoftwareStatusService::isLive)
            .ifPresent(tag -> updateTagStatus(tag,
                publishedApps().stream()
                    .filter(app -> app.getSpec() != null && app.getSpec().getTagNames() != null
                        && app.getSpec().getTagNames().contains(name))
                    .mapToInt(ignored -> 1)
                    .sum()));
    }

    public void reconcileTopic(String name) {
        client.fetch(SoftwareTopic.class, name)
            .filter(SoftwareStatusService::isLive)
            .ifPresent(topic -> updateTopicStatus(topic,
                publishedTopicAppCount(topic, publishedAppNames())));
    }

    /** Reuses one app scan to keep all taxonomy counters coherent after app changes/deletes. */
    public void reconcileTaxonomies() {
        var apps = publishedApps();
        var appNames = apps.stream().map(SoftwareStatusService::nameOf)
            .filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        Map<String, Integer> categoryCounts = new HashMap<>();
        Map<String, Integer> tagCounts = new HashMap<>();
        for (var app : apps) {
            var spec = app.getSpec();
            if (hasText(spec.getCategoryName())) {
                categoryCounts.merge(spec.getCategoryName(), 1, Integer::sum);
            }
            if (spec.getTagNames() != null) {
                new HashSet<>(spec.getTagNames()).stream()
                    .filter(SoftwareStatusService::hasText)
                    .forEach(tag -> tagCounts.merge(tag, 1, Integer::sum));
            }
        }
        client.listAll(SoftwareCategory.class, ListOptions.builder().build(), Sort.unsorted())
            .stream().filter(SoftwareStatusService::isLive)
            .forEach(category -> updateCategoryStatus(category,
                categoryCounts.getOrDefault(nameOf(category), 0)));
        client.listAll(SoftwareTag.class, ListOptions.builder().build(), Sort.unsorted())
            .stream().filter(SoftwareStatusService::isLive)
            .forEach(tag -> updateTagStatus(tag, tagCounts.getOrDefault(nameOf(tag), 0)));
        client.listAll(SoftwareTopic.class, ListOptions.builder().build(), Sort.unsorted())
            .stream().filter(SoftwareStatusService::isLive)
            .forEach(topic -> updateTopicStatus(topic, publishedTopicAppCount(topic, appNames)));
    }

    public void reconcileAllVersionsAndApps() {
        client.listAll(SoftwareVersion.class, ListOptions.builder().build(), Sort.unsorted())
            .stream().filter(SoftwareStatusService::isLive)
            .map(SoftwareStatusService::nameOf).filter(Objects::nonNull)
            .forEach(this::reconcileVersion);
        reconcileAllApps();
    }

    public void reconcileAllApps() {
        client.listAll(SoftwareApp.class, ListOptions.builder().build(), Sort.unsorted())
            .stream().filter(SoftwareStatusService::isLive)
            .map(SoftwareStatusService::nameOf).filter(Objects::nonNull)
            .forEach(this::reconcileApp);
        reconcileTaxonomies();
    }

    private List<SoftwareApp> publishedApps() {
        return client.listAll(SoftwareApp.class,
            ListOptions.builder().andQuery(equal("spec.published", true)).build(),
            Sort.unsorted()).stream().filter(SoftwareStatusService::isLive).toList();
    }

    private Set<String> publishedAppNames() {
        return publishedApps().stream().map(SoftwareStatusService::nameOf)
            .filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
    }

    private List<SoftwareVersion> releasedVersionsForApp(String appName) {
        return client.listAll(SoftwareVersion.class, ListOptions.builder()
                .andQuery(and(equal("spec.appName", appName), equal("spec.status", "released")))
                .build(), Sort.unsorted());
    }

    private List<DownloadAsset> assetsForApp(String appName) {
        return client.listAll(DownloadAsset.class,
            ListOptions.builder().andQuery(equal("spec.appName", appName)).build(),
            Sort.unsorted());
    }

    private List<DownloadAsset> assetsForVersion(String versionName) {
        return client.listAll(DownloadAsset.class,
            ListOptions.builder().andQuery(equal("spec.versionName", versionName)).build(),
            Sort.unsorted());
    }

    private void updateCategoryStatus(SoftwareCategory category, int count) {
        var previous = category.getStatus();
        if (previous != null && Objects.equals(previous.getAppCount(), count)
            && observedCurrent(previous.getObservedVersion(), category)) {
            return;
        }
        var next = previous == null ? new SoftwareCategory.Status() : previous;
        next.setAppCount(count);
        next.setObservedVersion(nextObservedVersion(category));
        category.setStatus(next);
        client.update(category);
    }

    private void updateTagStatus(SoftwareTag tag, int count) {
        var previous = tag.getStatus();
        if (previous != null && Objects.equals(previous.getAppCount(), count)
            && observedCurrent(previous.getObservedVersion(), tag)) {
            return;
        }
        var next = previous == null ? new SoftwareTag.Status() : previous;
        next.setAppCount(count);
        next.setObservedVersion(nextObservedVersion(tag));
        tag.setStatus(next);
        client.update(tag);
    }

    private void updateTopicStatus(SoftwareTopic topic, int count) {
        var previous = topic.getStatus();
        if (previous != null && Objects.equals(previous.getAppCount(), count)
            && observedCurrent(previous.getObservedVersion(), topic)) {
            return;
        }
        var next = previous == null ? new SoftwareTopic.Status() : previous;
        next.setAppCount(count);
        next.setObservedVersion(nextObservedVersion(topic));
        topic.setStatus(next);
        client.update(topic);
    }

    private static int publishedTopicAppCount(SoftwareTopic topic, Set<String> publishedNames) {
        if (topic.getSpec() == null || topic.getSpec().getItems() == null) {
            return 0;
        }
        return (int) topic.getSpec().getItems().stream()
            .filter(Objects::nonNull)
            .map(SoftwareTopic.Item::getAppName)
            .filter(publishedNames::contains)
            .distinct()
            .count();
    }

    private String assetHealth(DownloadAsset.Spec spec) {
        if (spec == null) {
            return "misconfigured";
        }
        if (!Boolean.TRUE.equals(spec.getEnabled())) {
            return "disabled";
        }
        if (DownloadTargetPolicy.firstUsable(spec.getUrl(), spec.getStorageKey()) != null) {
            return "ready";
        }
        if (!hasText(spec.getPasswordSecretName())) {
            return "misconfigured";
        }
        return client.fetch(Secret.class, spec.getPasswordSecretName())
            .map(secret -> DownloadTargetPolicy.isUsable(secretValue(secret, "url"))
                ? "ready" : "misconfigured")
            .orElse("misconfigured");
    }

    private static String appHealth(SoftwareApp app, List<SoftwareVersion> versions,
        List<DownloadAsset> assets) {
        if (app.getSpec() == null || !Boolean.TRUE.equals(app.getSpec().getPublished())) {
            return "draft";
        }
        if (versions.isEmpty()) {
            return "missing-version";
        }
        return assets.isEmpty() ? "missing-download" : "ready";
    }

    private static int compareAssetPriority(DownloadAsset left, DownloadAsset right) {
        var leftPriority = left.getSpec() == null || left.getSpec().getPriority() == null
            ? 0 : left.getSpec().getPriority();
        var rightPriority = right.getSpec() == null || right.getSpec().getPriority() == null
            ? 0 : right.getSpec().getPriority();
        var priority = Integer.compare(leftPriority, rightPriority);
        return priority != 0 ? priority : nullSafe(nameOf(left)).compareTo(nullSafe(nameOf(right)));
    }

    private static int compareVersionRecency(SoftwareVersion left, SoftwareVersion right) {
        var leftDate = left.getSpec() == null ? null : left.getSpec().getReleaseDate();
        var rightDate = right.getSpec() == null ? null : right.getSpec().getReleaseDate();
        if (!Objects.equals(leftDate, rightDate)) {
            if (leftDate == null) {
                return 1;
            }
            if (rightDate == null) {
                return -1;
            }
            return rightDate.compareTo(leftDate);
        }
        var leftCreated = left.getMetadata() == null ? null
            : left.getMetadata().getCreationTimestamp();
        var rightCreated = right.getMetadata() == null ? null
            : right.getMetadata().getCreationTimestamp();
        if (leftCreated == null || rightCreated == null) {
            return nullSafe(nameOf(right)).compareTo(nullSafe(nameOf(left)));
        }
        return rightCreated.compareTo(leftCreated);
    }

    private static boolean observedCurrent(Long observedVersion, AbstractExtension extension) {
        return Objects.equals(observedVersion, currentVersion(extension));
    }

    private static long nextObservedVersion(AbstractExtension extension) {
        return currentVersion(extension) + 1;
    }

    private static long currentVersion(AbstractExtension extension) {
        return extension.getMetadata() == null || extension.getMetadata().getVersion() == null
            ? 0L : extension.getMetadata().getVersion();
    }

    private static boolean isLive(AbstractExtension extension) {
        return extension != null && !ExtensionUtil.isDeleted(extension);
    }

    private static String nameOf(AbstractExtension extension) {
        return extension == null || extension.getMetadata() == null
            ? null : extension.getMetadata().getName();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String secretValue(Secret secret, String key) {
        var data = secret.getData();
        var value = data == null ? null : data.get(key);
        return value == null ? null : new String(value, StandardCharsets.UTF_8);
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static boolean migratePlatforms(SoftwareApp app) {
        var spec = app.getSpec();
        if (spec == null || spec.getPlatforms() != null) {
            return false;
        }
        var values = hasText(spec.getPlatform())
            ? java.util.Arrays.stream(spec.getPlatform().trim().split("[,，/\\s]+"))
                .filter(SoftwareStatusService::hasText)
                .distinct()
                .toList()
            : List.<String>of();
        spec.setPlatforms(values);
        spec.setPlatform(values.isEmpty() ? null : String.join(", ", values));
        return true;
    }
}
