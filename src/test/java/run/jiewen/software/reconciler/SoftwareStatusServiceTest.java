package run.jiewen.software.reconciler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import run.halo.app.core.extension.Counter;
import run.halo.app.extension.ExtensionClient;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.jiewen.software.extension.DownloadAsset;
import run.jiewen.software.extension.SoftwareApp;
import run.jiewen.software.extension.SoftwareCategory;
import run.jiewen.software.extension.SoftwareTag;
import run.jiewen.software.extension.SoftwareTopic;
import run.jiewen.software.extension.SoftwareVersion;

@ExtendWith(MockitoExtension.class)
class SoftwareStatusServiceTest {

    @Mock
    ExtensionClient client;

    @Test
    void reconcilesAssetHealthAndObservedVersion() {
        var asset = asset("asset-1", "app-1", "version-1", true, 10, 0L);
        asset.getMetadata().setVersion(7L);
        when(client.fetch(DownloadAsset.class, "asset-1")).thenReturn(Optional.of(asset));
        var service = new SoftwareStatusService(client);

        service.reconcileAsset("asset-1");

        assertThat(asset.getStatus().getHealth()).isEqualTo("ready");
        assertThat(asset.getStatus().getDownloadCount()).isZero();
        assertThat(asset.getStatus().getLastCheckedAt()).isNotNull();
        assertThat(asset.getStatus().getObservedAppName()).isEqualTo("app-1");
        assertThat(asset.getStatus().getObservedVersionName()).isEqualTo("version-1");
        assertThat(asset.getStatus().getObservedVersion()).isEqualTo(8L);
        verify(client).update(asset);
    }

    @Test
    void doesNotRewriteAssetWhenStatusAlreadyObservesCurrentVersion() {
        var asset = asset("asset-1", "app-1", "version-1", true, 10, 3L);
        asset.getMetadata().setVersion(8L);
        asset.getStatus().setHealth("ready");
        asset.getStatus().setObservedAppName("app-1");
        asset.getStatus().setObservedVersionName("version-1");
        asset.getStatus().setObservedVersion(8L);
        asset.getStatus().setLastCheckedAt(Instant.parse("2026-07-26T00:00:00Z"));
        when(client.fetch(DownloadAsset.class, "asset-1")).thenReturn(Optional.of(asset));
        var service = new SoftwareStatusService(client);

        service.reconcileAsset("asset-1");

        verify(client, never()).update(any(DownloadAsset.class));
    }

    @Test
    void reconcilesVersionFromEnabledAssetsOnly() {
        var version = version("version-1", "app-1", "1.0.0", LocalDate.of(2026, 7, 1));
        version.getMetadata().setVersion(2L);
        var primary = asset("asset-primary", "app-1", "version-1", true, 1, -4L);
        var mirror = asset("asset-mirror", "app-1", "version-1", true, 20, 9L);
        var disabled = asset("asset-disabled", "app-1", "version-1", false, 0, 100L);
        when(client.fetch(SoftwareVersion.class, "version-1")).thenReturn(Optional.of(version));
        when(client.listAll(eq(DownloadAsset.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(List.of(mirror, disabled, primary));
        var service = new SoftwareStatusService(client);

        service.reconcileVersion("version-1");

        assertThat(version.getStatus().getAssetCount()).isEqualTo(2);
        assertThat(version.getStatus().getDownloadCount()).isEqualTo(9L);
        assertThat(version.getStatus().getPrimaryAssetName()).isEqualTo("asset-primary");
        assertThat(version.getStatus().getObservedAppName()).isEqualTo("app-1");
        assertThat(version.getStatus().getObservedVersion()).isEqualTo(3L);
        verify(client).update(version);
    }

    @Test
    void reconcilesAppLatestReleaseCountersHealthAndLegacyPlatforms() {
        var app = app("app-1", true, "Windows macOS");
        app.getMetadata().setVersion(10L);
        var oldVersion = version("version-old", "app-1", "1.0.0",
            LocalDate.of(2026, 6, 1));
        var latestVersion = version("version-latest", "app-1", "2.0.0",
            LocalDate.of(2026, 7, 1));
        latestVersion.getSpec().setMinOsVersion("13.0");
        latestVersion.getSpec().setSizeBytes(1024L);
        var latestAsset = asset("asset-latest", "app-1", "version-latest", true, 10, 12L);
        var oldAsset = asset("asset-old", "app-1", "version-old", true, 10, 5L);
        var unrelatedAsset = asset("asset-draft", "app-1", "version-draft", true, 1, 99L);
        var counter = Counter.emptyCounter(SoftwareStatusService.COUNTER_PREFIX + "app-1");
        counter.setUpvote(7);
        counter.setApprovedComment(3);
        when(client.fetch(SoftwareApp.class, "app-1")).thenReturn(Optional.of(app));
        when(client.listAll(eq(SoftwareVersion.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(List.of(oldVersion, latestVersion));
        when(client.listAll(eq(DownloadAsset.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(List.of(latestAsset, oldAsset, unrelatedAsset));
        when(client.fetch(Counter.class, SoftwareStatusService.COUNTER_PREFIX + "app-1"))
            .thenReturn(Optional.of(counter));
        var service = new SoftwareStatusService(client);

        service.reconcileApp("app-1");

        assertThat(app.getSpec().getPlatforms()).containsExactly("Windows", "macOS");
        assertThat(app.getSpec().getPlatform()).isEqualTo("Windows, macOS");
        assertThat(app.getStatus().getLatestVersionName()).isEqualTo("version-latest");
        assertThat(app.getStatus().getLatestVersion()).isEqualTo("2.0.0");
        assertThat(app.getStatus().getLatestReleaseDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(app.getStatus().getMinOsVersion()).isEqualTo("13.0");
        assertThat(app.getStatus().getSizeBytes()).isEqualTo(1024L);
        assertThat(app.getStatus().getDownloadCount()).isEqualTo(17L);
        assertThat(app.getStatus().getLikeCount()).isEqualTo(7L);
        assertThat(app.getStatus().getCommentCount()).isEqualTo(3L);
        assertThat(app.getStatus().getHealth()).isEqualTo("ready");
        assertThat(app.getStatus().getObservedVersion()).isEqualTo(11L);
        verify(client).update(app);
    }

    @Test
    void reconcilesAllTaxonomyCountersFromOnePublishedAppScan() {
        var first = app("app-1", true, null);
        first.getSpec().setCategoryName("category-1");
        first.getSpec().setTagNames(List.of("tag-1", "tag-1", "tag-2"));
        var second = app("app-2", true, null);
        second.getSpec().setCategoryName("category-1");
        second.getSpec().setTagNames(List.of("tag-2"));
        var category = category("category-1");
        var tagOne = tag("tag-1");
        var tagTwo = tag("tag-2");
        var topic = topic("topic-1", List.of("app-1", "app-1", "missing-app"));
        when(client.listAll(eq(SoftwareApp.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(List.of(first, second));
        when(client.listAll(eq(SoftwareCategory.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(List.of(category));
        when(client.listAll(eq(SoftwareTag.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(List.of(tagOne, tagTwo));
        when(client.listAll(eq(SoftwareTopic.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(List.of(topic));
        var service = new SoftwareStatusService(client);

        service.reconcileTaxonomies();

        assertThat(category.getStatus().getAppCount()).isEqualTo(2);
        assertThat(tagOne.getStatus().getAppCount()).isEqualTo(1);
        assertThat(tagTwo.getStatus().getAppCount()).isEqualTo(2);
        assertThat(topic.getStatus().getAppCount()).isEqualTo(1);
        verify(client).update(category);
        verify(client).update(tagOne);
        verify(client).update(tagTwo);
        verify(client).update(topic);
    }

    private static SoftwareApp app(String name, boolean published, String platform) {
        var spec = new SoftwareApp.Spec();
        spec.setSlug(name);
        spec.setDisplayName(name);
        spec.setPublished(published);
        spec.setPlatform(platform);
        var app = new SoftwareApp();
        app.setMetadata(metadata(name));
        app.setSpec(spec);
        return app;
    }

    private static SoftwareVersion version(String name, String appName, String value,
        LocalDate releaseDate) {
        var spec = new SoftwareVersion.Spec();
        spec.setAppName(appName);
        spec.setVersion(value);
        spec.setReleaseDate(releaseDate);
        spec.setStatus("released");
        var version = new SoftwareVersion();
        version.setMetadata(metadata(name));
        version.getMetadata().setCreationTimestamp(
            releaseDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
        version.setSpec(spec);
        return version;
    }

    private static DownloadAsset asset(String name, String appName, String versionName,
        boolean enabled, int priority, long downloads) {
        var spec = new DownloadAsset.Spec();
        spec.setAppName(appName);
        spec.setVersionName(versionName);
        spec.setTitle(name);
        spec.setUrl("https://example.com/" + name);
        spec.setEnabled(enabled);
        spec.setPriority(priority);
        var status = new DownloadAsset.Status();
        status.setDownloadCount(downloads);
        var asset = new DownloadAsset();
        asset.setMetadata(metadata(name));
        asset.setSpec(spec);
        asset.setStatus(status);
        return asset;
    }

    private static SoftwareCategory category(String name) {
        var category = new SoftwareCategory();
        category.setMetadata(metadata(name));
        category.setSpec(new SoftwareCategory.Spec());
        return category;
    }

    private static SoftwareTag tag(String name) {
        var tag = new SoftwareTag();
        tag.setMetadata(metadata(name));
        tag.setSpec(new SoftwareTag.Spec());
        return tag;
    }

    private static SoftwareTopic topic(String name, List<String> appNames) {
        var spec = new SoftwareTopic.Spec();
        spec.setItems(appNames.stream().map(appName -> {
            var item = new SoftwareTopic.Item();
            item.setAppName(appName);
            return item;
        }).toList());
        var topic = new SoftwareTopic();
        topic.setMetadata(metadata(name));
        topic.setSpec(spec);
        return topic;
    }

    private static Metadata metadata(String name) {
        var metadata = new Metadata();
        metadata.setName(name);
        return metadata;
    }
}
