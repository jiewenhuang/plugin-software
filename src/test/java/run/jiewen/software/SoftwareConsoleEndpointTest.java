package run.jiewen.software;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import run.halo.app.extension.Metadata;
import run.jiewen.software.extension.DownloadAsset;
import run.jiewen.software.extension.SoftwareApp;
import run.jiewen.software.finders.SoftwareConsoleService;
import run.jiewen.software.vo.DownloadAssetStatsVo;
import run.jiewen.software.vo.SoftwareConsoleStatsVo;

class SoftwareConsoleEndpointTest {

    SoftwareConsoleService service = org.mockito.Mockito.mock(SoftwareConsoleService.class);
    run.halo.app.plugin.ReactiveSettingFetcher settingFetcher =
        org.mockito.Mockito.mock(run.halo.app.plugin.ReactiveSettingFetcher.class);

    WebTestClient client = WebTestClient
        .bindToRouterFunction(new SoftwareConsoleEndpoint(service, settingFetcher)
            .endpoint())
        .build();

    @Test
    void getsAppStats() {
        when(service.getAppStats()).thenReturn(Mono.just(SoftwareConsoleStatsVo.builder()
            .total(2)
            .published(1)
            .drafts(1)
            .downloads(42)
            .platforms(List.of("macOS", "Windows"))
            .build()));

        client.get()
            .uri("/apps/stats")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.total").isEqualTo(2)
            .jsonPath("$.published").isEqualTo(1)
            .jsonPath("$.drafts").isEqualTo(1)
            .jsonPath("$.downloads").isEqualTo(42)
            .jsonPath("$.platforms[0]").isEqualTo("macOS")
            .jsonPath("$.platforms[1]").isEqualTo("Windows");
    }

    @Test
    void getsDownloadAssetStats() {
        when(service.getDownloadAssetStats()).thenReturn(Mono.just(DownloadAssetStatsVo.builder()
            .total(3)
            .enabled(2)
            .disabled(1)
            .protectedCount(2)
            .downloads(18)
            .build()));

        client.get()
            .uri("/download-assets/stats")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType("application/json")
            .expectBody()
            .jsonPath("$.total").isEqualTo(3)
            .jsonPath("$.enabled").isEqualTo(2)
            .jsonPath("$.disabled").isEqualTo(1)
            .jsonPath("$.protectedCount").isEqualTo(2)
            .jsonPath("$.downloads").isEqualTo(18);
    }

    @Test
    void deletingDownloadAssetReturnsOnlySecretFreeProjection() {
        var asset = new DownloadAsset();
        var metadata = new Metadata();
        metadata.setName("asset-1");
        asset.setMetadata(metadata);
        var spec = new DownloadAsset.Spec();
        spec.setAppName("app-1");
        spec.setVersionName("version-1");
        spec.setTitle("Private mirror");
        spec.setUrl("https://downloads.example.com/private.dmg");
        spec.setStorageKey("/private/app.dmg");
        spec.setExtractionCode("1234");
        spec.setPasswordSecretName("download-secret");
        asset.setSpec(spec);
        when(service.deleteDownloadAsset("asset-1")).thenReturn(Mono.just(asset));

        client.delete()
            .uri("/download-assets/asset-1")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.metadata.name").isEqualTo("asset-1")
            .jsonPath("$.spec.hasUrl").isEqualTo(true)
            .jsonPath("$.spec.hasStorageKey").isEqualTo(true)
            .jsonPath("$.spec.hasExtractionCode").isEqualTo(true)
            .jsonPath("$.spec.hasPasswordSecret").isEqualTo(true)
            .jsonPath("$.spec.url").doesNotExist()
            .jsonPath("$.spec.storageKey").doesNotExist()
            .jsonPath("$.spec.extractionCode").doesNotExist()
            .jsonPath("$.spec.passwordSecretName").doesNotExist();
    }

    @Test
    void routesPutToReplacementAndPatchToMerge() {
        var saved = new SoftwareApp();
        var metadata = new Metadata();
        metadata.setName("app-1");
        saved.setMetadata(metadata);
        var spec = new SoftwareApp.Spec();
        spec.setSlug("app-one");
        spec.setDisplayName("App One");
        saved.setSpec(spec);
        when(service.replaceApp(eq("app-1"), any(SoftwareApp.class)))
            .thenReturn(Mono.just(saved));
        when(service.updateApp(eq("app-1"), any(SoftwareApp.class)))
            .thenReturn(Mono.just(saved));
        var body = Map.of("spec", Map.of("slug", "app-one", "displayName", "App One"));

        client.put()
            .uri("/apps/app-1")
            .bodyValue(body)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.metadata.name").isEqualTo("app-1");
        client.patch()
            .uri("/apps/app-1")
            .bodyValue(body)
            .exchange()
            .expectStatus().isOk();

        verify(service).replaceApp(eq("app-1"), any(SoftwareApp.class));
        verify(service).updateApp(eq("app-1"), any(SoftwareApp.class));
    }
}
