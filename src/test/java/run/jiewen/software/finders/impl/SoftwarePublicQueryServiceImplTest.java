package run.jiewen.software.finders.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.PageRequest;
import run.halo.app.extension.ReactiveExtensionClient;
import run.jiewen.software.extension.DownloadAsset;
import run.jiewen.software.extension.SoftwareApp;
import run.jiewen.software.extension.SoftwareVersion;
import run.jiewen.software.vo.DownloadAssetVo;
import run.jiewen.software.vo.SoftwareAppVo;

@ExtendWith(MockitoExtension.class)
class SoftwarePublicQueryServiceImplTest {

    @Mock
    ReactiveExtensionClient client;

    @InjectMocks
    SoftwarePublicQueryServiceImpl service;

    @Test
    void getAppBySlugQueriesSpecSlugAndReturnsPublicDto() {
        when(client.listBy(eq(SoftwareApp.class), any(ListOptions.class), any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(1, 1, 1, List.of(app()))));

        StepVerifier.create(service.getAppBySlug("cleanmymac-x"))
            .assertNext(app -> {
                assertThat(app.getName()).isEqualTo("software-generated-name");
                assertThat(app.getSlug()).isEqualTo("cleanmymac-x");
                assertThat(app.getDisplayName()).isEqualTo("CleanMyMac X");
                assertThat(app.getPermalink()).isEqualTo("/apps/cleanmymac-x");
                assertThat(app.getScreenshots()).containsExactly("/upload/screen-1.png",
                    "/upload/screen-2.png");
                assertThat(app.getLatestVersion()).isEqualTo("4.15.0");
                assertThat(app.getDownloadCount()).isEqualTo(42L);
            })
            .verifyComplete();

        var optionsCaptor = ArgumentCaptor.forClass(ListOptions.class);
        verify(client).listBy(eq(SoftwareApp.class), optionsCaptor.capture(),
            any(PageRequest.class));
        assertThat(optionsCaptor.getValue().toString()).contains("spec.slug", "cleanmymac-x");
    }

    @Test
    void publicAppDtoDoesNotExposeDownloadSecrets() {
        assertThat(List.of(SoftwareAppVo.class.getDeclaredFields())
            .stream()
            .map(Field::getName))
            .doesNotContain("url", "storageKey", "extractionCode", "passwordSecretName",
                "downloadAssets");
    }

    @Test
    void getAppDetailBySlugIncludesVersionsAndSafeDownloadAssets() {
        when(client.listBy(eq(SoftwareApp.class), any(ListOptions.class), any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(1, 1, 1, List.of(app()))));
        when(client.listAll(eq(SoftwareVersion.class), any(ListOptions.class), any()))
            .thenReturn(reactor.core.publisher.Flux.just(version()));
        when(client.listAll(eq(DownloadAsset.class), any(ListOptions.class), any()))
            .thenReturn(reactor.core.publisher.Flux.just(downloadAsset()));

        StepVerifier.create(service.getAppDetailBySlug("cleanmymac-x"))
            .assertNext(detail -> {
                assertThat(detail.getName()).isEqualTo("software-generated-name");
                assertThat(detail.getScreenshots()).containsExactly("/upload/screen-1.png",
                    "/upload/screen-2.png");
                assertThat(detail.getVersions()).hasSize(1);
                assertThat(detail.getVersions().get(0).getVersion()).isEqualTo("4.15.0");
                assertThat(detail.getDownloadAssets()).hasSize(1);
                DownloadAssetVo asset = detail.getDownloadAssets().get(0);
                assertThat(asset.getName()).isEqualTo("asset-1");
                assertThat(asset.getTitle()).isEqualTo("Universal DMG");
                assertThat(asset.getUrl()).isNull();
                assertThat(asset.getRequiresLogin()).isFalse();
                assertThat(asset.getRequiresCaptcha()).isFalse();
            })
            .verifyComplete();
    }

    @Test
    void loginRequiredDownloadReturnsUnauthorizedForAnonymousUser() {
        when(client.listBy(eq(SoftwareApp.class), any(ListOptions.class), any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(1, 1, 1, List.of(app()))));
        when(client.listAll(eq(DownloadAsset.class), any(ListOptions.class), any()))
            .thenReturn(reactor.core.publisher.Flux.just(loginRequiredDownloadAsset()));

        StepVerifier.create(service.getPrimaryDownloadAsset("cleanmymac-x"))
            .expectErrorSatisfies(error -> assertThat(error)
                .isInstanceOfSatisfying(ResponseStatusException.class, statusError -> {
                    assertThat(statusError.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(statusError.getReason()).isEqualTo("Download requires login");
                }))
            .verify();
    }

    @Test
    void loginRequiredDownloadReturnsUrlForAuthenticatedUser() {
        when(client.listBy(eq(SoftwareApp.class), any(ListOptions.class), any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(1, 1, 1, List.of(app()))));
        when(client.listAll(eq(DownloadAsset.class), any(ListOptions.class), any()))
            .thenReturn(reactor.core.publisher.Flux.just(loginRequiredDownloadAsset()));

        var authentication = new UsernamePasswordAuthenticationToken("member", "n/a");

        StepVerifier.create(service.getPrimaryDownloadAsset("cleanmymac-x")
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)))
            .assertNext(asset -> {
                assertThat(asset.getName()).isEqualTo("asset-1");
                assertThat(asset.getUrl()).isEqualTo("https://example.com/cleanmymac.dmg");
            })
            .verifyComplete();
    }

    @Test
    void resolvesPrimaryDownloadAssetBySlug() {
        when(client.listBy(eq(SoftwareApp.class), any(ListOptions.class), any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(1, 1, 1, List.of(app()))));
        when(client.listAll(eq(DownloadAsset.class), any(ListOptions.class), any()))
            .thenReturn(reactor.core.publisher.Flux.just(downloadAsset()));

        StepVerifier.create(service.getPrimaryDownloadAsset("cleanmymac-x"))
            .assertNext(asset -> {
                assertThat(asset.getName()).isEqualTo("asset-1");
                assertThat(asset.getUrl()).isEqualTo("https://example.com/cleanmymac.dmg");
            })
            .verifyComplete();
    }

    @Test
    void resolvesSelectedDownloadAssetByName() {
        var mirror = downloadAsset();
        mirror.getMetadata().setName("asset-2");
        mirror.getSpec().setTitle("Mirror DMG");
        mirror.getSpec().setUrl("https://mirror.example.com/cleanmymac.dmg");
        when(client.listBy(eq(SoftwareApp.class), any(ListOptions.class), any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(1, 1, 1, List.of(app()))));
        when(client.listAll(eq(DownloadAsset.class), any(ListOptions.class), any()))
            .thenReturn(reactor.core.publisher.Flux.just(downloadAsset(), mirror));

        StepVerifier.create(service.getDownloadAsset("cleanmymac-x", "asset-2"))
            .assertNext(asset -> {
                assertThat(asset.getName()).isEqualTo("asset-2");
                assertThat(asset.getTitle()).isEqualTo("Mirror DMG");
                assertThat(asset.getUrl()).isEqualTo("https://mirror.example.com/cleanmymac.dmg");
            })
            .verifyComplete();
    }

    private static SoftwareApp app() {
        var metadata = new Metadata();
        metadata.setName("software-generated-name");

        var spec = new SoftwareApp.Spec();
        spec.setSlug("cleanmymac-x");
        spec.setDisplayName("CleanMyMac X");
        spec.setSubtitle("Mac cleaner");
        spec.setIcon("/upload/icon.png");
        spec.setCover("/upload/cover.png");
        spec.setScreenshots(List.of("/upload/screen-1.png", "/upload/screen-2.png"));
        spec.setSummary("Clean, optimize and maintain your Mac.");
        spec.setPublished(true);
        spec.setCategoryName("system-tools");
        spec.setTagNames(List.of("cleaner", "utility"));

        var status = new SoftwareApp.Status();
        status.setLatestVersion("4.15.0");
        status.setDownloadCount(42L);

        var app = new SoftwareApp();
        app.setMetadata(metadata);
        app.setSpec(spec);
        app.setStatus(status);
        return app;
    }

    private static SoftwareVersion version() {
        var metadata = new Metadata();
        metadata.setName("version-1");

        var spec = new SoftwareVersion.Spec();
        spec.setAppName("software-generated-name");
        spec.setVersion("4.15.0");
        spec.setReleaseDate(LocalDate.of(2026, 6, 1));
        spec.setStatus("released");

        var version = new SoftwareVersion();
        version.setMetadata(metadata);
        version.setSpec(spec);
        return version;
    }

    private static DownloadAsset downloadAsset() {
        var metadata = new Metadata();
        metadata.setName("asset-1");

        var spec = new DownloadAsset.Spec();
        spec.setAppName("software-generated-name");
        spec.setVersionName("version-1");
        spec.setTitle("Universal DMG");
        spec.setType("dmg");
        spec.setProvider("external");
        spec.setUrl("https://example.com/cleanmymac.dmg");
        spec.setStorageKey("secret-storage-key");
        spec.setExtractionCode("secret-code");
        spec.setPasswordSecretName("secret-name");
        spec.setEnabled(true);
        spec.setRequiresLogin(false);
        spec.setRequiresCaptcha(false);
        spec.setPriority(0);

        var asset = new DownloadAsset();
        asset.setMetadata(metadata);
        asset.setSpec(spec);
        return asset;
    }

    private static DownloadAsset protectedDownloadAsset() {
        var asset = downloadAsset();
        asset.getSpec().setRequiresLogin(true);
        asset.getSpec().setRequiresCaptcha(true);
        return asset;
    }

    private static DownloadAsset loginRequiredDownloadAsset() {
        var asset = downloadAsset();
        asset.getSpec().setRequiresLogin(true);
        return asset;
    }
}
