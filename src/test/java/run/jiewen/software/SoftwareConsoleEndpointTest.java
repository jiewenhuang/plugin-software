package run.jiewen.software;

import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import run.jiewen.software.ai.SoftwareAiService;
import run.jiewen.software.finders.SoftwareConsoleService;
import run.jiewen.software.vo.SoftwareConsoleStatsVo;

class SoftwareConsoleEndpointTest {

    SoftwareConsoleService service = org.mockito.Mockito.mock(SoftwareConsoleService.class);
    SoftwareAiService aiService = org.mockito.Mockito.mock(SoftwareAiService.class);
    run.halo.app.plugin.ReactiveSettingFetcher settingFetcher =
        org.mockito.Mockito.mock(run.halo.app.plugin.ReactiveSettingFetcher.class);

    WebTestClient client = WebTestClient
        .bindToRouterFunction(new SoftwareConsoleEndpoint(service, aiService, settingFetcher)
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
}
