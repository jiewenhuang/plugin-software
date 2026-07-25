package run.jiewen.software.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Secret;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.jiewen.software.SoftwareSecurityOptions;

@ExtendWith(MockitoExtension.class)
class TurnstileCaptchaVerifierTest {

    @Mock
    ReactiveSettingFetcher settingFetcher;

    @Mock
    ReactiveExtensionClient client;

    @Test
    void blankTokenFailsWithoutLoadingConfiguration() {
        var verifier = new TurnstileCaptchaVerifier(settingFetcher, client, WebClient.builder());

        StepVerifier.create(verifier.verify("  ", "203.0.113.8"))
            .expectNext(false)
            .verifyComplete();

        verify(settingFetcher, never()).fetch(SoftwareSecurityOptions.GROUP,
            SoftwareSecurityOptions.class);
    }

    @Test
    void verifiesAgainstFixedCloudflareEndpoint() {
        var options = new SoftwareSecurityOptions();
        options.setCaptchaProvider("turnstile");
        options.setCaptchaSecretName("turnstile-secret");
        options.setCaptchaSecretKey("secret");
        options.setCaptchaVerifyUrl("https://attacker.example/collect");
        when(settingFetcher.fetch(SoftwareSecurityOptions.GROUP, SoftwareSecurityOptions.class))
            .thenReturn(Mono.just(options));
        when(client.fetch(Secret.class, "turnstile-secret"))
            .thenReturn(Mono.just(secret("turnstile-key")));
        var requestedUri = new AtomicReference<URI>();
        var requestedMethod = new AtomicReference<HttpMethod>();
        var builder = WebClient.builder().exchangeFunction(request -> {
            requestedUri.set(request.url());
            requestedMethod.set(request.method());
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body("{\"success\":true,\"error-codes\":[]}")
                .build());
        });
        var verifier = new TurnstileCaptchaVerifier(settingFetcher, client, builder);

        StepVerifier.create(verifier.verify("valid-token", "203.0.113.8"))
            .expectNext(true)
            .verifyComplete();

        assertThat(requestedUri).hasValue(URI.create(SoftwareSecurityOptions.TURNSTILE_VERIFY_URL));
        assertThat(requestedMethod).hasValue(HttpMethod.POST);
    }

    @Test
    void missingSecretFailsClosedAsServiceUnavailable() {
        var options = new SoftwareSecurityOptions();
        options.setCaptchaSecretName("missing-secret");
        when(settingFetcher.fetch(SoftwareSecurityOptions.GROUP, SoftwareSecurityOptions.class))
            .thenReturn(Mono.just(options));
        when(client.fetch(Secret.class, "missing-secret")).thenReturn(Mono.empty());
        var verifier = new TurnstileCaptchaVerifier(settingFetcher, client, WebClient.builder());

        StepVerifier.create(verifier.verify("token", null))
            .expectErrorSatisfies(error -> assertThat(error)
                .isInstanceOfSatisfying(ResponseStatusException.class, statusError -> {
                    assertThat(statusError.getStatusCode())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(statusError.getReason()).contains("does not exist");
                }))
            .verify();
    }

    @Test
    void providerFailureIsMappedToServiceUnavailable() {
        var options = new SoftwareSecurityOptions();
        options.setCaptchaSecretName("turnstile-secret");
        when(settingFetcher.fetch(SoftwareSecurityOptions.GROUP, SoftwareSecurityOptions.class))
            .thenReturn(Mono.just(options));
        when(client.fetch(Secret.class, "turnstile-secret"))
            .thenReturn(Mono.just(secret("turnstile-key")));
        var builder = WebClient.builder().exchangeFunction(request ->
            Mono.just(ClientResponse.create(HttpStatus.BAD_GATEWAY).build()));
        var verifier = new TurnstileCaptchaVerifier(settingFetcher, client, builder);

        StepVerifier.create(verifier.verify("token", null))
            .expectErrorSatisfies(error -> assertThat(error)
                .isInstanceOfSatisfying(ResponseStatusException.class, statusError ->
                    assertThat(statusError.getStatusCode())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)))
            .verify();
    }

    private static Secret secret(String value) {
        var secret = new Secret();
        secret.setData(Map.of("secret", value.getBytes(StandardCharsets.UTF_8)));
        return secret;
    }
}
