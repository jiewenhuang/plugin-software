package run.jiewen.software;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.jiewen.software.finders.SoftwarePublicQueryService;
import run.jiewen.software.vo.DownloadAssetVo;
import run.jiewen.software.vo.SoftwareAppDetailVo;
import run.jiewen.software.vo.SoftwareAppVo;
import run.jiewen.software.vo.SoftwareCategoryVo;

class SoftwareQueryEndpointTest {

    SoftwarePublicQueryService service = org.mockito.Mockito.mock(SoftwarePublicQueryService.class);

    WebTestClient client = WebTestClient
        .bindToRouterFunction(new SoftwareQueryEndpoint(service).endpoint())
        .build();

    @Test
    void listsApps() {
        when(service.listApps(any(), any()))
            .thenReturn(Mono.just(new ListResult<>(1, 20, 1, List.of(appVo()))));

        client.get()
            .uri("/apps")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.items[0].slug").isEqualTo("cleanmymac-x")
            .jsonPath("$.items[0].permalink").isEqualTo("/soft/cleanmymac-x");
    }

    @Test
    void expandsParentCategoryWhenListingApps() {
        when(service.listCategories(any(), any()))
            .thenReturn(Mono.just(new ListResult<>(1, 100, 2, List.of(
                category("media", null), category("video-edit", "media")))));
        when(service.listApps(any(), any()))
            .thenReturn(Mono.just(new ListResult<>(1, 20, 1, List.of(appVo()))));

        client.get()
            .uri("/apps?category=media")
            .exchange()
            .expectStatus().isOk();

        var options = org.mockito.ArgumentCaptor.forClass(ListOptions.class);
        verify(service).listApps(options.capture(), any());
        assertThat(options.getValue().toString())
            .contains("spec.categoryName", "media", "video-edit");
    }

    @Test
    void getsAppBySlug() {
        when(service.getAppDetailBySlug("cleanmymac-x"))
            .thenReturn(Mono.just(appDetailVo()));

        client.get()
            .uri("/apps/cleanmymac-x")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.name").isEqualTo("software-generated-name")
            .jsonPath("$.slug").isEqualTo("cleanmymac-x")
            .jsonPath("$.downloadAssets[0].name").isEqualTo("asset-1");
    }

    @Test
    void getsPrimaryDownloadAssetBySlug() {
        when(service.getPrimaryDownloadAsset("cleanmymac-x"))
            .thenReturn(Mono.just(DownloadAssetVo.builder()
                .name("asset-1")
                .title("Universal DMG")
                .url("https://example.com/cleanmymac.dmg")
                .build()));

        client.get()
            .uri("/apps/cleanmymac-x/download")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.name").isEqualTo("asset-1")
            .jsonPath("$.url").isEqualTo("https://example.com/cleanmymac.dmg");
    }

    @Test
    void getsSelectedDownloadAssetByName() {
        when(service.getDownloadAsset("cleanmymac-x", "asset-2"))
            .thenReturn(Mono.just(DownloadAssetVo.builder()
                .name("asset-2")
                .title("Mirror DMG")
                .url("https://example.com/cleanmymac-mirror.dmg")
                .build()));

        client.get()
            .uri("/apps/cleanmymac-x/download?asset=asset-2")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.name").isEqualTo("asset-2")
            .jsonPath("$.url").isEqualTo("https://example.com/cleanmymac-mirror.dmg");
    }

    @Test
    void returnsForbiddenWhenPrimaryDownloadRequiresVerification() {
        when(service.getPrimaryDownloadAsset("cleanmymac-x"))
            .thenReturn(Mono.error(new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Download requires verification")));

        client.get()
            .uri("/apps/cleanmymac-x/download")
            .exchange()
            .expectStatus().isForbidden();
    }

    @Test
    void rejectsInvalidPaginationForPublicFacetLists() {
        client.get()
            .uri("/categories?page=abc&size=bad")
            .exchange()
            .expectStatus().isBadRequest();
    }

    private static SoftwareAppVo appVo() {
        return SoftwareAppVo.builder()
            .name("software-generated-name")
            .slug("cleanmymac-x")
            .displayName("CleanMyMac X")
            .permalink("/soft/cleanmymac-x")
            .latestVersion("4.15.0")
            .downloadCount(42L)
            .build();
    }

    private static SoftwareCategoryVo category(String name, String parentName) {
        return SoftwareCategoryVo.builder()
            .name(name)
            .slug(name)
            .displayName(name)
            .parentName(parentName)
            .build();
    }

    private static SoftwareAppDetailVo appDetailVo() {
        return SoftwareAppDetailVo.from(appVo(), List.of(), List.of(DownloadAssetVo.builder()
            .name("asset-1")
            .title("Universal DMG")
            .url("https://example.com/cleanmymac.dmg")
            .build()));
    }
}
