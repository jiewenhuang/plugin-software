package run.jiewen.software.ai;

import reactor.core.publisher.Mono;
import run.jiewen.software.extension.SoftwareApp;

/** Optional editor-assistance capability implemented when AI Foundation is available. */
public interface SoftwareAiAssistant {

    Mono<Boolean> available();

    Mono<SoftwareAppAiSuggestion> suggestApp(SoftwareApp app, String instruction,
        SoftwareAiTarget target);
}
