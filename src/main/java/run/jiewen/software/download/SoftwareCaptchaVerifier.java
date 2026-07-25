package run.jiewen.software.download;

import reactor.core.publisher.Mono;

public interface SoftwareCaptchaVerifier {

    Mono<Boolean> verify(String responseToken, String remoteAddress);

    static SoftwareCaptchaVerifier denying() {
        return (responseToken, remoteAddress) -> Mono.just(false);
    }
}
