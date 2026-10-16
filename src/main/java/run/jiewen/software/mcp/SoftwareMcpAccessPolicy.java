package run.jiewen.software.mcp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;

@Slf4j
final class SoftwareMcpAccessPolicy {
    record Options(Boolean enabled, Boolean exposeSensitiveTools) {
        boolean active() { return !Boolean.FALSE.equals(enabled); }
        boolean sensitive() { return Boolean.TRUE.equals(exposeSensitiveTools); }
    }

    private final ReactiveSettingFetcher settings;
    private final AuthenticationTrustResolverImpl trust = new AuthenticationTrustResolverImpl();

    SoftwareMcpAccessPolicy(ReactiveSettingFetcher settings) { this.settings = settings; }

    Mono<Options> options() {
        return Mono.defer(() -> settings.fetch("mcp", Options.class))
            .defaultIfEmpty(new Options(true, false))
            .onErrorResume(error -> {
                log.warn("Software MCP settings unavailable ({})", error.getClass().getSimpleName());
                return Mono.just(new Options(false, false));
            });
    }

    Mono<Boolean> permitted(boolean sensitive) {
        return options().flatMap(options -> {
            if (!options.active() || sensitive && !options.sensitive()) { return Mono.just(false); }
            return ReactiveSecurityContextHolder.getContext()
                .map(context -> trust.isAuthenticated(context.getAuthentication()))
                .defaultIfEmpty(false);
        });
    }
}
