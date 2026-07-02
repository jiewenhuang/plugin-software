package run.jiewen.software.finders.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.PageRequest;
import run.halo.app.extension.ReactiveExtensionClient;
import run.jiewen.software.extension.DownloadAsset;
import run.jiewen.software.extension.SoftwareApp;
import run.jiewen.software.extension.SoftwareCategory;
import run.jiewen.software.extension.SoftwareTag;
import run.jiewen.software.extension.SoftwareTopic;
import run.jiewen.software.extension.SoftwareVersion;

@ExtendWith(MockitoExtension.class)
class SoftwareConsoleServiceImplTest {

    @Mock
    ReactiveExtensionClient client;

    @InjectMocks
    SoftwareConsoleServiceImpl service;

    @Test
    void createsAppWithDeterministicMetadataNameAndUniqueSlug() {
        when(client.listBy(eq(SoftwareApp.class), any(ListOptions.class), any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(1, 1, 0, List.of())));
        when(client.create(any(SoftwareApp.class))).thenAnswer(invocation ->
            Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.createApp(newApp("cleanmymac-x")))
            .assertNext(app -> {
                assertThat(app.getMetadata().getName()).isEqualTo("software-app-cleanmymac-x");
                assertThat(app.getMetadata().getGenerateName()).isEqualTo("software-app-");
                assertThat(app.getSpec().getSlug()).isEqualTo("cleanmymac-x");
                assertThat(app.getSpec().getPublished()).isFalse();
            })
            .verifyComplete();

        var captor = ArgumentCaptor.forClass(SoftwareApp.class);
        verify(client).create(captor.capture());
        assertThat(captor.getValue().getMetadata().getName())
            .isEqualTo("software-app-cleanmymac-x");
        assertThat(captor.getValue().getMetadata().getGenerateName()).isEqualTo("software-app-");
    }

    @Test
    void rejectsAppCreateWithMissingCategoryReference() {
        lenient().when(client.listBy(eq(SoftwareApp.class), any(ListOptions.class),
                any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(1, 1, 0, List.of())));
        lenient().when(client.create(any(SoftwareApp.class))).thenAnswer(invocation ->
            Mono.just(invocation.getArgument(0)));
        when(client.fetch(SoftwareCategory.class, "missing-category")).thenReturn(Mono.empty());

        StepVerifier.create(service.createApp(
                appWithCategoryAndTags("missing-category", List.of("tag-1"))))
            .expectErrorMatches(error -> error instanceof ServerWebInputException
                && error.getMessage().contains("Software category does not exist"))
            .verify();

        verify(client, never()).create(any(SoftwareApp.class));
    }

    @Test
    void rejectsAppCreateWithMissingTagReference() {
        lenient().when(client.listBy(eq(SoftwareApp.class), any(ListOptions.class),
                any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(1, 1, 0, List.of())));
        lenient().when(client.create(any(SoftwareApp.class))).thenAnswer(invocation ->
            Mono.just(invocation.getArgument(0)));
        when(client.fetch(SoftwareCategory.class, "category-1"))
            .thenReturn(Mono.just(existingCategory("category-1", "utilities")));
        when(client.fetch(SoftwareTag.class, "missing-tag")).thenReturn(Mono.empty());

        StepVerifier.create(service.createApp(
                appWithCategoryAndTags("category-1", List.of("missing-tag"))))
            .expectErrorMatches(error -> error instanceof ServerWebInputException
                && error.getMessage().contains("Software tag does not exist"))
            .verify();

        verify(client, never()).create(any(SoftwareApp.class));
    }

    @Test
    void rejectsDuplicateSlugOnCreate() {
        when(client.listBy(eq(SoftwareApp.class), any(ListOptions.class), any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(1, 1, 1, List.of(existingApp("app-1")))));

        StepVerifier.create(service.createApp(newApp("cleanmymac-x")))
            .expectError(ServerWebInputException.class)
            .verify();

        verify(client, never()).create(any(SoftwareApp.class));
    }

    @Test
    void updatesAppByMetadataNameAndAllowsOwnSlug() {
        var current = existingApp("app-1");
        current.getMetadata().setVersion(7L);
        when(client.fetch(SoftwareApp.class, "app-1")).thenReturn(Mono.just(current));
        when(client.listBy(eq(SoftwareApp.class), any(ListOptions.class), any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(1, 2, 1, List.of(current))));
        when(client.update(any(SoftwareApp.class))).thenAnswer(invocation ->
            Mono.just(invocation.getArgument(0)));

        var update = newApp("cleanmymac-x");
        update.getSpec().setDisplayName("CleanMyMac X Updated");

        StepVerifier.create(service.updateApp("app-1", update))
            .assertNext(app -> {
                assertThat(app.getMetadata().getName()).isEqualTo("app-1");
                assertThat(app.getMetadata().getVersion()).isEqualTo(7L);
                assertThat(app.getSpec().getDisplayName()).isEqualTo("CleanMyMac X Updated");
            })
            .verifyComplete();
    }

    @Test
    void rejectsInvalidAppSlug() {
        StepVerifier.create(service.createApp(newApp("Clean My Mac")))
            .expectError(ServerWebInputException.class)
            .verify();

        verify(client, never()).create(any(SoftwareApp.class));
    }

    @Test
    void createsVersionOnlyForExistingApp() {
        when(client.fetch(SoftwareApp.class, "app-1")).thenReturn(Mono.just(existingApp("app-1")));
        when(client.create(any(SoftwareVersion.class))).thenAnswer(invocation ->
            Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.createVersion(newVersion("app-1", "1.0.0")))
            .assertNext(version -> {
                assertThat(version.getMetadata().getGenerateName()).isEqualTo("software-version-");
                assertThat(version.getSpec().getAppName()).isEqualTo("app-1");
            })
            .verifyComplete();
    }

    @Test
    void rejectsVersionForMissingApp() {
        when(client.fetch(SoftwareApp.class, "missing-app")).thenReturn(Mono.empty());

        StepVerifier.create(service.createVersion(newVersion("missing-app", "1.0.0")))
            .expectError(ServerWebInputException.class)
            .verify();

        verify(client, never()).create(any(SoftwareVersion.class));
    }

    @Test
    void createsDownloadAssetOnlyForExistingAppAndVersion() {
        when(client.fetch(SoftwareApp.class, "app-1")).thenReturn(Mono.just(existingApp("app-1")));
        when(client.fetch(SoftwareVersion.class, "version-1"))
            .thenReturn(Mono.just(existingVersion("version-1", "app-1")));
        when(client.create(any(DownloadAsset.class))).thenAnswer(invocation ->
            Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.createDownloadAsset(newDownloadAsset("app-1", "version-1")))
            .assertNext(asset -> {
                assertThat(asset.getMetadata().getGenerateName()).isEqualTo("download-asset-");
                assertThat(asset.getSpec().getEnabled()).isTrue();
                assertThat(asset.getSpec().getPriority()).isZero();
            })
            .verifyComplete();
    }

    @Test
    void rejectsDownloadAssetWhenVersionBelongsToAnotherApp() {
        when(client.fetch(SoftwareApp.class, "app-1")).thenReturn(Mono.just(existingApp("app-1")));
        when(client.fetch(SoftwareVersion.class, "version-1"))
            .thenReturn(Mono.just(existingVersion("version-1", "other-app")));

        StepVerifier.create(service.createDownloadAsset(newDownloadAsset("app-1", "version-1")))
            .expectError(ServerWebInputException.class)
            .verify();

        verify(client, never()).create(any(DownloadAsset.class));
    }

    @Test
    void rejectsDeletingAppWhenVersionsExist() {
        when(client.fetch(SoftwareApp.class, "app-1")).thenReturn(Mono.just(existingApp("app-1")));
        when(client.listBy(eq(SoftwareVersion.class), any(ListOptions.class),
            any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(1, 1, 1,
                List.of(existingVersion("version-1", "app-1")))));

        StepVerifier.create(service.deleteApp("app-1"))
            .expectError(ServerWebInputException.class)
            .verify();

        verify(client, never()).delete(any(SoftwareApp.class));
    }

    @Test
    void rejectsDeletingVersionWhenDownloadAssetsExist() {
        when(client.fetch(SoftwareVersion.class, "version-1"))
            .thenReturn(Mono.just(existingVersion("version-1", "app-1")));
        when(client.listBy(eq(DownloadAsset.class), any(ListOptions.class),
            any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(1, 1, 1,
                List.of(existingDownloadAsset("asset-1", "app-1", "version-1")))));

        StepVerifier.create(service.deleteVersion("version-1"))
            .expectError(ServerWebInputException.class)
            .verify();

        verify(client, never()).delete(any(SoftwareVersion.class));
    }

    @Test
    void createsCategoryWithUniqueSlugAndDefaults() {
        when(client.listBy(eq(SoftwareCategory.class), any(ListOptions.class),
            any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(1, 1, 0, List.of())));
        when(client.create(any(SoftwareCategory.class))).thenAnswer(invocation ->
            Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.createCategory(newCategory("utilities")))
            .assertNext(category -> {
                assertThat(category.getMetadata().getGenerateName()).isEqualTo("software-category-");
                assertThat(category.getSpec().getVisible()).isTrue();
                assertThat(category.getSpec().getPriority()).isZero();
            })
            .verifyComplete();
    }

    @Test
    void rejectsDuplicateCategorySlug() {
        when(client.listBy(eq(SoftwareCategory.class), any(ListOptions.class),
            any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(1, 1, 1,
                List.of(existingCategory("category-1", "utilities")))));

        StepVerifier.create(service.createCategory(newCategory("utilities")))
            .expectError(ServerWebInputException.class)
            .verify();

        verify(client, never()).create(any(SoftwareCategory.class));
    }

    @Test
    void rejectsDeletingReferencedCategory() {
        var category = existingCategory("category-1", "utilities");
        lenient().when(client.delete(any(SoftwareCategory.class))).thenAnswer(invocation ->
            Mono.just(invocation.getArgument(0)));
        when(client.fetch(SoftwareCategory.class, "category-1")).thenReturn(Mono.just(category));
        lenient().when(client.listBy(eq(SoftwareApp.class), any(ListOptions.class),
                any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(1, 1, 1,
                List.of(appWithCategoryAndTags("category-1", List.of())))));

        StepVerifier.create(service.deleteCategory("category-1"))
            .expectErrorMatches(error -> error instanceof ServerWebInputException
                && error.getMessage()
                    .contains("Please remove software category references before deleting"))
            .verify();

        verify(client, never()).delete(any(SoftwareCategory.class));
    }

    @Test
    void createsTagWithUniqueSlugAndDefaults() {
        when(client.listBy(eq(SoftwareTag.class), any(ListOptions.class), any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(1, 1, 0, List.of())));
        when(client.create(any(SoftwareTag.class))).thenAnswer(invocation ->
            Mono.just(invocation.getArgument(0)));
        var tag = newTag("mac");
        tag.getSpec().setColor("#FF8844");

        StepVerifier.create(service.createTag(tag))
            .assertNext(savedTag -> {
                assertThat(savedTag.getMetadata().getGenerateName()).isEqualTo("software-tag-");
                assertThat(savedTag.getSpec().getVisible()).isTrue();
                assertThat(savedTag.getSpec().getPriority()).isZero();
                assertThat(savedTag.getSpec().getColor()).isEqualTo("#ff8844");
            })
            .verifyComplete();
    }

    @Test
    void rejectsInvalidTagColor() {
        var tag = newTag("mac");
        tag.getSpec().setColor("blue");

        StepVerifier.create(service.createTag(tag))
            .expectErrorMatches(error -> error instanceof ServerWebInputException
                && error.getMessage().contains("Software tag color must be a hex color"))
            .verify();

        verify(client, never()).create(any(SoftwareTag.class));
    }

    @Test
    void rejectsDeletingReferencedTag() {
        var tag = existingTag("tag-1", "mac");
        lenient().when(client.delete(any(SoftwareTag.class))).thenAnswer(invocation ->
            Mono.just(invocation.getArgument(0)));
        when(client.fetch(SoftwareTag.class, "tag-1")).thenReturn(Mono.just(tag));
        lenient().when(client.listBy(eq(SoftwareApp.class), any(ListOptions.class),
                any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(1, 1, 1,
                List.of(appWithCategoryAndTags(null, List.of("tag-1"))))));

        StepVerifier.create(service.deleteTag("tag-1"))
            .expectErrorMatches(error -> error instanceof ServerWebInputException
                && error.getMessage()
                    .contains("Please remove software tag references before deleting"))
            .verify();

        verify(client, never()).delete(any(SoftwareTag.class));
    }

    @Test
    void aggregatesAppStatsAndPlatforms() {
        when(client.listAll(eq(SoftwareApp.class), any(ListOptions.class), any()))
            .thenReturn(reactor.core.publisher.Flux.just(
                appWithStats(true, 40L, "macOS Windows"),
                appWithStats(false, 2L, "Windows")));

        StepVerifier.create(service.getAppStats())
            .assertNext(stats -> {
                assertThat(stats.getTotal()).isEqualTo(2);
                assertThat(stats.getPublished()).isEqualTo(1);
                assertThat(stats.getDrafts()).isEqualTo(1);
                assertThat(stats.getDownloads()).isEqualTo(42);
                assertThat(stats.getPlatforms()).containsExactly("macOS", "Windows");
            })
            .verifyComplete();
    }

    @Test
    void createsTopicWithUniqueSlugAndDefaults() {
        when(client.listBy(eq(SoftwareTopic.class), any(ListOptions.class), any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(1, 1, 0, List.of())));
        when(client.create(any(SoftwareTopic.class))).thenAnswer(invocation ->
            Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.createTopic(newTopic("mac-starter")))
            .assertNext(topic -> {
                assertThat(topic.getMetadata().getGenerateName()).isEqualTo("software-topic-");
                assertThat(topic.getSpec().getPublished()).isFalse();
                assertThat(topic.getSpec().getFeatured()).isFalse();
                assertThat(topic.getSpec().getPriority()).isZero();
            })
            .verifyComplete();
    }

    private static SoftwareApp newApp(String slug) {
        var spec = new SoftwareApp.Spec();
        spec.setSlug(slug);
        spec.setDisplayName("CleanMyMac X");

        var app = new SoftwareApp();
        app.setSpec(spec);
        return app;
    }

    private static SoftwareApp appWithCategoryAndTags(String categoryName, List<String> tagNames) {
        var app = newApp("cleanmymac-x");
        app.getSpec().setCategoryName(categoryName);
        app.getSpec().setTagNames(tagNames);
        return app;
    }

    private static SoftwareApp appWithStats(boolean published, Long downloads, String platform) {
        var app = newApp(published ? "published-app" : "draft-app");
        app.getSpec().setPublished(published);
        app.getSpec().setPlatform(platform);
        var status = new SoftwareApp.Status();
        status.setDownloadCount(downloads);
        app.setStatus(status);
        return app;
    }

    private static SoftwareApp existingApp(String name) {
        var app = newApp("cleanmymac-x");
        var metadata = new Metadata();
        metadata.setName(name);
        app.setMetadata(metadata);
        return app;
    }

    private static SoftwareVersion newVersion(String appName, String versionText) {
        var spec = new SoftwareVersion.Spec();
        spec.setAppName(appName);
        spec.setVersion(versionText);

        var version = new SoftwareVersion();
        version.setSpec(spec);
        return version;
    }

    private static SoftwareVersion existingVersion(String name, String appName) {
        var version = newVersion(appName, "1.0.0");
        var metadata = new Metadata();
        metadata.setName(name);
        version.setMetadata(metadata);
        return version;
    }

    private static DownloadAsset newDownloadAsset(String appName, String versionName) {
        var spec = new DownloadAsset.Spec();
        spec.setAppName(appName);
        spec.setVersionName(versionName);
        spec.setTitle("Universal DMG");
        spec.setUrl("https://example.com/app.dmg");

        var asset = new DownloadAsset();
        asset.setSpec(spec);
        return asset;
    }

    private static DownloadAsset existingDownloadAsset(String name, String appName,
        String versionName) {
        var asset = newDownloadAsset(appName, versionName);
        var metadata = new Metadata();
        metadata.setName(name);
        asset.setMetadata(metadata);
        return asset;
    }

    private static SoftwareCategory newCategory(String slug) {
        var spec = new SoftwareCategory.Spec();
        spec.setSlug(slug);
        spec.setDisplayName("Utilities");

        var category = new SoftwareCategory();
        category.setSpec(spec);
        return category;
    }

    private static SoftwareCategory existingCategory(String name, String slug) {
        var category = newCategory(slug);
        var metadata = new Metadata();
        metadata.setName(name);
        category.setMetadata(metadata);
        return category;
    }

    private static SoftwareTag newTag(String slug) {
        var spec = new SoftwareTag.Spec();
        spec.setSlug(slug);
        spec.setDisplayName("Mac");

        var tag = new SoftwareTag();
        tag.setSpec(spec);
        return tag;
    }

    private static SoftwareTag existingTag(String name, String slug) {
        var tag = newTag(slug);
        var metadata = new Metadata();
        metadata.setName(name);
        tag.setMetadata(metadata);
        return tag;
    }

    private static SoftwareTopic newTopic(String slug) {
        var spec = new SoftwareTopic.Spec();
        spec.setSlug(slug);
        spec.setTitle("Mac Starter");

        var topic = new SoftwareTopic();
        topic.setSpec(spec);
        return topic;
    }
}
