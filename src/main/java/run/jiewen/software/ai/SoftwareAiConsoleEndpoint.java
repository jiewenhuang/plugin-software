package run.jiewen.software.ai;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.core.fn.builders.schema.Builder.schemaBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

@Component
@ConditionalOnClass(name = "run.halo.aifoundation.AiModelService")
public class SoftwareAiConsoleEndpoint implements CustomEndpoint {

    private final SoftwareAiAssistant softwareAiAssistant;

    public SoftwareAiConsoleEndpoint(SoftwareAiAssistant softwareAiAssistant) {
        this.softwareAiAssistant = softwareAiAssistant;
    }

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        final var aiTag = "console.api.software.jiewen.run/v1alpha1/SoftwareAi";
        return route()
            .GET("ai/capabilities", this::getCapabilities,
                builder -> builder.operationId("GetSoftwareAiCapabilities")
                    .description("Report whether optional AI editor assistance is available.")
                    .tag(aiTag)
                    .response(responseBuilder().implementation(Map.class)))
            .POST("ai/apps/suggestions", this::suggestApp,
                builder -> builder.operationId("SuggestSoftwareAppByAi")
                    .description("Generate software app SEO and content suggestions by AI.")
                    .tag(aiTag)
                    .requestBody(requestBodyBuilder()
                        .required(true)
                        .content(contentBuilder()
                            .mediaType(MediaType.APPLICATION_JSON_VALUE)
                            .schema(schemaBuilder()
                                .implementation(SoftwareAppAiSuggestionRequest.class))))
                    .response(responseBuilder().implementation(SoftwareAppAiSuggestion.class)))
            .build();
    }

    private Mono<ServerResponse> getCapabilities(ServerRequest request) {
        return softwareAiAssistant.available()
            .flatMap(available -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("available", available)));
    }

    private Mono<ServerResponse> suggestApp(ServerRequest request) {
        return request.bodyToMono(SoftwareAppAiSuggestionRequest.class)
            .flatMap(body -> softwareAiAssistant.suggestApp(body.getApp(), body.getInstruction(),
                body.target()))
            .flatMap(suggestion -> ServerResponse.ok().bodyValue(suggestion));
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("console.api.software.jiewen.run/v1alpha1");
    }
}
