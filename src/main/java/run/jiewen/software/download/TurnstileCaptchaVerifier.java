package run.jiewen.software.download;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Secret;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.jiewen.software.SoftwareSecurityOptions;

@Component
public class TurnstileCaptchaVerifier implements SoftwareCaptchaVerifier {

    private static final Duration PROVIDER_TIMEOUT = Duration.ofSeconds(5);

    private final ReactiveSettingFetcher settingFetcher;
    private final ReactiveExtensionClient client;
    private final WebClient.Builder webClientBuilder;

    @Autowired
    public TurnstileCaptchaVerifier(ReactiveSettingFetcher settingFetcher,
        ReactiveExtensionClient client) {
        this(settingFetcher, client, WebClient.builder());
    }

    TurnstileCaptchaVerifier(ReactiveSettingFetcher settingFetcher,
        ReactiveExtensionClient client, WebClient.Builder webClientBuilder) {
        this.settingFetcher = settingFetcher;
        this.client = client;
        this.webClientBuilder = webClientBuilder;
    }

    @Override
    public Mono<Boolean> verify(String responseToken, String remoteAddress) {
        if (responseToken == null || responseToken.isBlank()) {
            return Mono.just(false);
        }
        return settingFetcher.fetch(SoftwareSecurityOptions.GROUP, SoftwareSecurityOptions.class)
            .defaultIfEmpty(new SoftwareSecurityOptions())
            .map(SoftwareSecurityOptions::normalized)
            .flatMap(options -> {
                if (!SoftwareSecurityOptions.PROVIDER_TURNSTILE.equalsIgnoreCase(
                    options.getCaptchaProvider())) {
                    return Mono.error(unavailable("Unsupported captcha provider"));
                }
                if (options.getCaptchaSecretName() == null) {
                    return Mono.error(unavailable("Captcha Secret is not configured"));
                }
                return client.fetch(Secret.class, options.getCaptchaSecretName())
                    .switchIfEmpty(Mono.error(unavailable("Captcha Secret does not exist")))
                    .map(secret -> secretValue(secret, options.getCaptchaSecretKey()))
                    .filter(value -> !value.isBlank())
                    .switchIfEmpty(Mono.error(unavailable("Captcha Secret key is empty")))
                    .flatMap(secret -> verifyWithProvider(options, secret, responseToken,
                        remoteAddress));
            });
    }

    private Mono<Boolean> verifyWithProvider(SoftwareSecurityOptions options, String secret,
        String token, String remoteAddress) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("secret", secret);
        form.add("response", token);
        if (remoteAddress != null && !remoteAddress.isBlank()) {
            form.add("remoteip", remoteAddress);
        }
        return webClientBuilder.build()
            .post()
            .uri(options.getCaptchaVerifyUrl())
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .bodyValue(form)
            .retrieve()
            .bodyToMono(TurnstileResponse.class)
            .timeout(PROVIDER_TIMEOUT)
            .map(TurnstileResponse::success)
            .onErrorMap(error -> error instanceof ResponseStatusException
                ? error
                : unavailable("Captcha provider is unavailable"));
    }

    private static String secretValue(Secret secret, String key) {
        Map<String, byte[]> data = secret.getData();
        var value = data == null ? null : data.get(key);
        return value == null ? "" : new String(value, StandardCharsets.UTF_8);
    }

    private static ResponseStatusException unavailable(String reason) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, reason);
    }

    private record TurnstileResponse(
        boolean success,
        @JsonProperty("error-codes") List<String> errorCodes
    ) {
    }
}
