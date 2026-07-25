package run.jiewen.software.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class SoftwareAiConsoleEndpointTest {

    SoftwareAiAssistant assistant = mock(SoftwareAiAssistant.class);
    WebTestClient client = WebTestClient
        .bindToRouterFunction(new SoftwareAiConsoleEndpoint(assistant).endpoint())
        .build();

    @Test
    void reportsAvailableCapability() {
        when(assistant.available()).thenReturn(Mono.just(true));

        client.get()
            .uri("/ai/capabilities")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.available").isEqualTo(true);
    }

    @Test
    void reportsUnavailableCapabilityWhenAiExtensionIsDisabled() {
        when(assistant.available()).thenReturn(Mono.just(false));

        client.get()
            .uri("/ai/capabilities")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.available").isEqualTo(false);
    }

    @Test
    void delegatesSuggestionRequestsToOptionalAssistant() {
        var suggestion = new SoftwareAppAiSuggestion();
        suggestion.setSummary("Generated summary");
        when(assistant.suggestApp(any(), any(), any())).thenReturn(Mono.just(suggestion));

        client.post()
            .uri("/ai/apps/suggestions")
            .bodyValue("""
                {
                  "app": {"spec": {"slug": "demo", "displayName": "Demo"}},
                  "instruction": "concise",
                  "target": "basic"
                }
                """)
            .header("Content-Type", "application/json")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.summary").isEqualTo("Generated summary");

        assertThat(assistant).isNotNull();
    }
}
