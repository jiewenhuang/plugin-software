package run.jiewen.software.finders.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.dao.OptimisticLockingFailureException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.PageRequest;
import run.halo.app.extension.ReactiveExtensionClient;
import run.jiewen.software.SoftwareDomainException;
import run.jiewen.software.operations.SoftwareEditRevision;
import run.jiewen.software.api.SoftwareMutation;
import run.jiewen.software.api.SoftwareResourceKind;
import run.jiewen.software.extension.DownloadAsset;
import run.jiewen.software.extension.SoftwareApp;
import run.jiewen.software.extension.SoftwareCategory;
import run.jiewen.software.extension.SoftwareVersion;

class SoftwareResourceOperationsTest {
    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    private final SoftwareConsoleServiceImpl service = new SoftwareConsoleServiceImpl(client);
    private final Map<String, AbstractExtension> store = new LinkedHashMap<>();

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void storage() {
        doAnswer(call -> Mono.justOrEmpty(store.get(((Class<?>) call.getArgument(0)).getName()
            + "/" + call.getArgument(1)))).when(client).fetch(any(Class.class), anyString());
        doReturn(Mono.just(new ListResult<>(1, 20, 0, List.of())))
            .when(client).listBy(any(Class.class), any(ListOptions.class), any(PageRequest.class));
        doAnswer(call -> Mono.just(call.getArgument(0))).when(client).update(any(AbstractExtension.class));
        doAnswer(call -> Mono.just(call.getArgument(0))).when(client).delete(any(AbstractExtension.class));
        put(app());
        var version = new SoftwareVersion();
        version.setMetadata(metadata("version"));
        var spec = new SoftwareVersion.Spec();
        spec.setAppName("app");
        spec.setVersion("1.0.0");
        version.setSpec(spec);
        put(version);
    }

    @Test
    void explicitPatchKeepsUnspecifiedFieldsAndDoesNotMutateCachedResource() {
        var original = (SoftwareApp) store.get(SoftwareApp.class.getName() + "/app");
        original.getSpec().setSummary("keep");
        original.getMetadata().setCreationTimestamp(java.time.Instant.parse("2026-10-15T15:14:42.946691625Z"));
        original.getMetadata().setGenerateName("software-app-");
        original.getMetadata().setAnnotations(Map.of("preserved", "value"));
        original.getMetadata().setFinalizers(java.util.Set.of("software-cleanup"));
        var status = new SoftwareApp.Status();
        status.setObservedVersion(7L);
        original.setStatus(status);
        var seo = new SoftwareApp.Seo();
        seo.setTitle("keep title");
        seo.setDescription("clear description");
        seo.setKeywords(List.of("keep keyword"));
        original.getSpec().setSeo(seo);
        StepVerifier.create(service.patchResource(SoftwareResourceKind.APP, "app",
                mutation(Map.of("recommended", false, "tagNames", List.of()), List.of("seo.description"))))
            .assertNext(value -> {
                var result = (SoftwareApp) value;
                assertThat(result.getSpec().getSummary()).isEqualTo("keep");
                assertThat(result.getSpec().getRecommended()).isFalse();
                assertThat(result.getSpec().getSeo().getDescription()).isNull();
                assertThat(result.getSpec().getSeo().getTitle()).isEqualTo("keep title");
                assertThat(result.getSpec().getSeo().getKeywords()).containsExactly("keep keyword");
                assertThat(result.getMetadata().getVersion()).isEqualTo(7L);
                assertThat(result.getMetadata().getCreationTimestamp()).isEqualTo(original.getMetadata().getCreationTimestamp());
                assertThat(result.getMetadata().getAnnotations()).containsEntry("preserved", "value");
                assertThat(result.getMetadata().getFinalizers()).containsExactly("software-cleanup");
                assertThat(result.getStatus().getObservedVersion()).isEqualTo(7L);
                assertThat(result).isNotSameAs(original);
            }).verifyComplete();
        assertThat(original.getSpec().getSeo().getDescription()).isEqualTo("clear description");
    }

    @Test
    void staleVersionNeverReachesStorage() {
        StepVerifier.create(service.patchResource(SoftwareResourceKind.APP, "app",
                new SoftwareMutation(6, Map.of("summary", "overwrite"), List.of(), SoftwareMutation.Mode.CONTENT)))
            .expectErrorSatisfies(error -> assertThat(((SoftwareDomainException) error).code()).isEqualTo("CONFLICT"))
            .verify();
        verify(client, never()).update(any(AbstractExtension.class));
    }

    @Test
    void storageRaceIsNotRetriedAndOriginalVersionIsPassedToStorage() {
        when(client.update(any(SoftwareApp.class)))
            .thenReturn(Mono.error(new OptimisticLockingFailureException("concurrent update")));
        StepVerifier.create(service.patchResource(SoftwareResourceKind.APP, "app",
                mutation(Map.of("summary", "changed"), List.of())))
            .expectError(OptimisticLockingFailureException.class).verify();
        verify(client, times(1)).update(argThat((SoftwareApp app) -> app.getMetadata().getVersion() == 7L));
    }

    @Test
    void editRevisionToleratesStatisticsButRejectsChangedBusinessContent() {
        var original = (SoftwareApp) store.get(SoftwareApp.class.getName() + "/app");
        String revision = SoftwareEditRevision.of(SoftwareResourceKind.APP, original);
        original.getMetadata().setVersion(8L);
        var status = new SoftwareApp.Status(); status.setDownloadCount(100L); original.setStatus(status);
        var mutation = new SoftwareMutation(7, Map.of("summary", "edited"), List.of(),
            SoftwareMutation.Mode.CONTENT, revision);
        StepVerifier.create(service.patchResource(SoftwareResourceKind.APP, "app", mutation))
            .assertNext(result -> {
                assertThat(((SoftwareApp) result).getSpec().getSummary()).isEqualTo("edited");
                assertThat(((SoftwareApp) result).getStatus().getDownloadCount()).isEqualTo(100L);
                assertThat(result.getMetadata().getVersion()).isEqualTo(8L);
            }).verifyComplete();
        original.getSpec().setDisplayName("Another editor changed content");
        StepVerifier.create(service.patchResource(SoftwareResourceKind.APP, "app", mutation))
            .expectErrorSatisfies(error -> assertThat(((SoftwareDomainException) error).code()).isEqualTo("CONFLICT"))
            .verify();
    }

    @Test
    void statusRaceRetriesFromLatestResourceAndStopsAfterTwoRetries() {
        var original = (SoftwareApp) store.get(SoftwareApp.class.getName() + "/app");
        var mutation = new SoftwareMutation(7, Map.of("summary", "edited"), List.of(),
            SoftwareMutation.Mode.CONTENT, SoftwareEditRevision.of(SoftwareResourceKind.APP, original));
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        when(client.update(any(SoftwareApp.class))).thenAnswer(call -> {
            if (calls.getAndIncrement() == 0) {
                original.getMetadata().setVersion(8L);
                return Mono.error(new OptimisticLockingFailureException("stats changed"));
            }
            return Mono.just(call.getArgument(0));
        });
        StepVerifier.create(service.patchResource(SoftwareResourceKind.APP, "app", mutation))
            .assertNext(result -> assertThat(result.getMetadata().getVersion()).isEqualTo(8L)).verifyComplete();
        assertThat(calls.get()).isEqualTo(2);
        when(client.update(any(SoftwareApp.class))).thenReturn(Mono.error(new OptimisticLockingFailureException("continuous race")));
        clearInvocations(client);
        StepVerifier.create(service.patchResource(SoftwareResourceKind.APP, "app", mutation))
            .expectError(OptimisticLockingFailureException.class).verify();
        verify(client, times(3)).update(any(SoftwareApp.class));
    }

    @Test
    void businessRaceNeverGetsOverwrittenByRetry() {
        var original = (SoftwareApp) store.get(SoftwareApp.class.getName() + "/app");
        var mutation = new SoftwareMutation(7, Map.of("summary", "edited"), List.of(),
            SoftwareMutation.Mode.CONTENT, SoftwareEditRevision.of(SoftwareResourceKind.APP, original));
        when(client.update(any(SoftwareApp.class))).thenAnswer(call -> {
            original.getSpec().setSummary("concurrent content"); original.getMetadata().setVersion(8L);
            return Mono.error(new OptimisticLockingFailureException("content changed"));
        });
        StepVerifier.create(service.patchResource(SoftwareResourceKind.APP, "app", mutation))
            .expectErrorSatisfies(error -> assertThat(((SoftwareDomainException) error).code()).isEqualTo("CONFLICT"))
            .verify();
        verify(client, times(1)).update(any(SoftwareApp.class));
        assertThat(original.getSpec().getSummary()).isEqualTo("concurrent content");
    }

    @Test
    void ordinaryRevisionExcludesSecretsAndNeverAllowsSecretMutation() {
        var asset = asset();
        String revision = SoftwareEditRevision.of(SoftwareResourceKind.DOWNLOAD_ASSET, asset);
        asset.getSpec().setExtractionCode("new-secret");
        assertThat(SoftwareEditRevision.of(SoftwareResourceKind.DOWNLOAD_ASSET, asset)).isEqualTo(revision);
        assertThatThrownBy(() -> new SoftwareMutation(7, Map.of("url", "changed"), List.of(),
            SoftwareMutation.Mode.SECRETS, revision)).isInstanceOf(SoftwareDomainException.class);
        asset.getSpec().setTitle("Changed title");
        assertThat(SoftwareEditRevision.of(SoftwareResourceKind.DOWNLOAD_ASSET, asset)).isNotEqualTo(revision);
    }

    @Test
    void rejectsPublicationAndSystemFieldsInOrdinaryWrites() {
        for (var field : List.of("published", "publishedAt", "metadata", "status", "platform")) {
            StepVerifier.create(service.patchResource(SoftwareResourceKind.APP, "app",
                    mutation(Map.of(field, "injected"), List.of())))
                .expectError(SoftwareDomainException.class).verify();
        }
        verify(client, never()).update(any(AbstractExtension.class));
    }

    @Test
    void publicationIsSeparateAndKeepsTheOriginalPublishTime() {
        var original = (SoftwareApp) store.get(SoftwareApp.class.getName() + "/app");
        var publishedAt = java.time.Instant.parse("2026-01-01T00:00:00Z");
        original.getSpec().setPublishedAt(publishedAt);
        StepVerifier.create(service.patchResource(SoftwareResourceKind.APP, "app",
                new SoftwareMutation(7, Map.of("published", true), List.of(), SoftwareMutation.Mode.PUBLICATION)))
            .assertNext(value -> {
                assertThat(((SoftwareApp) value).getSpec().getPublished()).isTrue();
                assertThat(((SoftwareApp) value).getSpec().getPublishedAt()).isEqualTo(publishedAt);
            }).verifyComplete();
    }

    @Test
    void sensitivePatchPreservesOtherSecretsAndNormalWritesCannotReachThem() {
        var asset = asset();
        put(asset);
        StepVerifier.create(service.patchResource(SoftwareResourceKind.DOWNLOAD_ASSET, "asset",
                new SoftwareMutation(7, Map.of("extractionCode", "new-code"), List.of(), SoftwareMutation.Mode.SECRETS)))
            .assertNext(value -> {
                var result = ((DownloadAsset) value).getSpec();
                assertThat(result.getUrl()).isEqualTo("https://example.com/private.zip");
                assertThat(result.getStorageKey()).isEqualTo("stored-key");
                assertThat(result.getExtractionCode()).isEqualTo("new-code");
            }).verifyComplete();
        for (var field : SoftwareResourceKind.SECRET_FIELDS) {
            StepVerifier.create(service.patchResource(SoftwareResourceKind.DOWNLOAD_ASSET, "asset",
                    mutation(Map.of(field, "injected"), List.of())))
                .expectError(SoftwareDomainException.class).verify();
        }
        assertThat(asset.getSpec().getExtractionCode()).isEqualTo("old-code");
    }

    @Test
    void enabledSourceCannotLoseItsLastTarget() {
        var asset = asset();
        asset.getSpec().setEnabled(true);
        asset.getSpec().setStorageKey(null);
        put(asset);
        StepVerifier.create(service.patchResource(SoftwareResourceKind.DOWNLOAD_ASSET, "asset",
                new SoftwareMutation(7, Map.of(), List.of("url"), SoftwareMutation.Mode.SECRETS)))
            .expectError(org.springframework.web.server.ServerWebInputException.class).verify();
        assertThat(asset.getSpec().getUrl()).isEqualTo("https://example.com/private.zip");
        verify(client, never()).update(any(AbstractExtension.class));
    }

    @Test
    void rejectsImmutableOwnershipNullAndOverlappingClearPaths() {
        var spec = new SoftwareVersion.Spec();
        for (var bad : List.of(
            mutation(Map.of("appName", "another"), List.of()),
            mutation(Map.of("checksum", Map.of("md5", "a".repeat(32))), List.of("checksum")),
            mutation(Map.of(), List.of("sizeBytes")),
            mutation(Map.of(), List.of("checksum", "checksum.md5")))) {
            assertThatThrownBy(() -> bad.apply(SoftwareResourceKind.VERSION, spec))
                .isInstanceOf(SoftwareDomainException.class);
        }
        var explicitNull = new LinkedHashMap<String, Object>();
        explicitNull.put("build", null);
        assertThatThrownBy(() -> mutation(explicitNull, List.of()).apply(SoftwareResourceKind.VERSION, spec))
            .isInstanceOf(SoftwareDomainException.class);
    }

    @ParameterizedTest
    @EnumSource(SoftwareResourceKind.class)
    void allManagedKindsCanBeReadAndUnknownNamesReturn404(SoftwareResourceKind kind) {
        var resource = org.springframework.beans.BeanUtils.instantiateClass(kind.type());
        resource.setMetadata(metadata("lookup"));
        put(resource);
        StepVerifier.create(service.getResource(kind, "lookup")).expectNext(resource).verifyComplete();
        StepVerifier.create(service.getResource(kind, "missing"))
            .expectErrorSatisfies(error -> assertThat(((org.springframework.web.server.ResponseStatusException) error)
                .getStatusCode().value()).isEqualTo(404)).verify();
    }

    @Test
    void deletionChecksVersionAndExistingReferences() {
        StepVerifier.create(service.deleteResource(SoftwareResourceKind.APP, "app", 6L))
            .expectErrorSatisfies(error -> assertThat(((SoftwareDomainException) error).code()).isEqualTo("CONFLICT"))
            .verify();
        when(client.listBy(eq(SoftwareVersion.class), any(ListOptions.class), any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(1, 1, 1, List.of(new SoftwareVersion()))));
        StepVerifier.create(service.deleteResource(SoftwareResourceKind.APP, "app", 7L))
            .expectErrorSatisfies(error -> assertThat(((SoftwareDomainException) error).code()).isEqualTo("RESOURCE_IN_USE"))
            .verify();
        verify(client, never()).delete(any(AbstractExtension.class));
    }

    @Test
    void creationRejectsAnAppAlreadyBeingDeleted() {
        var app = store.get(SoftwareApp.class.getName() + "/app");
        app.getMetadata().setDeletionTimestamp(java.time.Instant.now());

        StepVerifier.create(service.prepareCreateResource(SoftwareResourceKind.VERSION,
                Map.of("appName", "app", "version", "2.0.0"), "new-version"))
            .expectErrorSatisfies(error -> assertThat(((SoftwareDomainException) error).code())
                .isEqualTo("REFERENCE_INVALID"))
            .verify();
        verify(client, never()).create(any(AbstractExtension.class));
    }

    @Test
    void publicationRejectsADeletingCategoryWithoutChangingTheApp() {
        var category = new SoftwareCategory();
        category.setMetadata(metadata("category"));
        category.getMetadata().setDeletionTimestamp(java.time.Instant.now());
        put(category);
        var app = (SoftwareApp) store.get(SoftwareApp.class.getName() + "/app");
        app.getSpec().setCategoryName("category");

        StepVerifier.create(service.patchResource(SoftwareResourceKind.APP, "app",
                new SoftwareMutation(7, Map.of("published", true), List.of(),
                    SoftwareMutation.Mode.PUBLICATION)))
            .expectErrorSatisfies(error -> assertThat(((SoftwareDomainException) error).code())
                .isEqualTo("REFERENCE_INVALID"))
            .verify();
        assertThat(app.getSpec().getPublished()).isNotEqualTo(true);
        verify(client, never()).update(any(AbstractExtension.class));
    }

    @Test
    void sensitiveUpdateRejectsASecretAlreadyBeingDeleted() {
        put(asset());
        var secret = new run.halo.app.extension.Secret();
        secret.setMetadata(metadata("secret"));
        secret.getMetadata().setDeletionTimestamp(java.time.Instant.now());
        put(secret);

        StepVerifier.create(service.patchResource(SoftwareResourceKind.DOWNLOAD_ASSET, "asset",
                new SoftwareMutation(7, Map.of("passwordSecretName", "secret"), List.of(),
                    SoftwareMutation.Mode.SECRETS)))
            .expectErrorSatisfies(error -> assertThat(((SoftwareDomainException) error).code())
                .isEqualTo("REFERENCE_INVALID"))
            .verify();
        verify(client, never()).update(any(AbstractExtension.class));
    }

    private void put(AbstractExtension value) {
        store.put(value.getClass().getName() + "/" + value.getMetadata().getName(), value);
    }

    private static SoftwareMutation mutation(Map<String, Object> patch, List<String> clear) {
        return new SoftwareMutation(7, patch, clear, SoftwareMutation.Mode.CONTENT);
    }

    private static Metadata metadata(String name) {
        var metadata = new Metadata();
        metadata.setName(name);
        metadata.setVersion(7L);
        return metadata;
    }

    private static SoftwareApp app() {
        var app = new SoftwareApp();
        app.setMetadata(metadata("app"));
        var spec = new SoftwareApp.Spec();
        spec.setSlug("example");
        spec.setDisplayName("Example");
        app.setSpec(spec);
        return app;
    }

    private static DownloadAsset asset() {
        var asset = new DownloadAsset();
        asset.setMetadata(metadata("asset"));
        var spec = new DownloadAsset.Spec();
        spec.setAppName("app");
        spec.setVersionName("version");
        spec.setTitle("Download");
        spec.setUrl("https://example.com/private.zip");
        spec.setStorageKey("stored-key");
        spec.setExtractionCode("old-code");
        spec.setEnabled(false);
        asset.setSpec(spec);
        return asset;
    }
}
