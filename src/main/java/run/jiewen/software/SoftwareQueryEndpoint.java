package run.jiewen.software;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;
import static run.halo.app.extension.index.query.Queries.equal;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.PageRequest;
import run.jiewen.software.finders.SoftwarePublicQueryService;
import run.jiewen.software.vo.DownloadAssetVo;
import run.jiewen.software.vo.SoftwareAppDetailVo;
import run.jiewen.software.vo.SoftwareAppVo;
import run.jiewen.software.vo.SoftwareCategoryVo;
import run.jiewen.software.vo.SoftwareTagVo;
import run.jiewen.software.vo.SoftwareTopicDetailVo;
import run.jiewen.software.vo.SoftwareTopicVo;

@Component
public class SoftwareQueryEndpoint implements CustomEndpoint {

    private final SoftwarePublicQueryService softwarePublicQueryService;

    public SoftwareQueryEndpoint(SoftwarePublicQueryService softwarePublicQueryService) {
        this.softwarePublicQueryService = softwarePublicQueryService;
    }

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        final var appTag = "api.software.jiewen.run/v1alpha1/SoftwareApp";
        final var categoryTag = "api.software.jiewen.run/v1alpha1/SoftwareCategory";
        final var tagTag = "api.software.jiewen.run/v1alpha1/SoftwareTag";
        final var topicTag = "api.software.jiewen.run/v1alpha1/SoftwareTopic";
        return route()
            .GET("apps", this::listApps,
                builder -> {
                    builder.operationId("querySoftwareApps")
                        .description("List published software apps.")
                        .tag(appTag)
                        .response(responseBuilder()
                            .implementation(ListResult.generateGenericClass(SoftwareAppVo.class)));
                    SoftwarePublicQuery.buildParameters(builder);
                }
            )
            .GET("apps/{slug}", this::getAppBySlug,
                builder -> builder.operationId("querySoftwareAppBySlug")
                    .description("Get a published software app by public slug.")
                    .tag(appTag)
                    .parameter(parameterBuilder()
                        .in(ParameterIn.PATH)
                        .name("slug")
                        .description("Software app public slug.")
                        .required(true))
                    .response(responseBuilder().implementation(SoftwareAppDetailVo.class))
            )
            .GET("apps/{slug}/download", this::getDownloadAsset,
                builder -> builder.operationId("querySoftwareAppDownloadAsset")
                    .description("Get an enabled download asset for a published software app.")
                    .tag(appTag)
                    .parameter(parameterBuilder()
                        .in(ParameterIn.PATH)
                        .name("slug")
                        .description("Software app public slug.")
                        .required(true))
                    .parameter(parameterBuilder()
                        .in(ParameterIn.QUERY)
                        .name("asset")
                        .description("Optional DownloadAsset metadata.name.")
                        .required(false))
                    .response(responseBuilder().implementation(DownloadAssetVo.class))
            )
            .GET("categories", this::listCategories,
                builder -> builder.operationId("querySoftwareCategories")
                    .description("List visible software categories.")
                    .tag(categoryTag)
                    .response(responseBuilder()
                        .implementation(ListResult.generateGenericClass(SoftwareCategoryVo.class)))
            )
            .GET("tags", this::listTags,
                builder -> builder.operationId("querySoftwareTags")
                    .description("List visible software tags.")
                    .tag(tagTag)
                    .response(responseBuilder()
                        .implementation(ListResult.generateGenericClass(SoftwareTagVo.class)))
            )
            .GET("topics", this::listTopics,
                builder -> builder.operationId("querySoftwareTopics")
                    .description("List published software topics.")
                    .tag(topicTag)
                    .response(responseBuilder()
                        .implementation(ListResult.generateGenericClass(SoftwareTopicVo.class)))
            )
            .GET("topics/{slug}", this::getTopicBySlug,
                builder -> builder.operationId("querySoftwareTopicBySlug")
                    .description("Get a published software topic with its published software apps.")
                    .tag(topicTag)
                    .parameter(parameterBuilder()
                        .in(ParameterIn.PATH)
                        .name("slug")
                        .description("Software topic public slug.")
                        .required(true))
                    .response(responseBuilder().implementation(SoftwareTopicDetailVo.class))
            )
            .build();
    }

    private Mono<ServerResponse> listApps(ServerRequest request) {
        var query = new SoftwarePublicQuery(request.exchange());
        return softwarePublicQueryService.listApps(query.toListOptions(), query.toPageRequest())
            .flatMap(result -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(result));
    }

    private Mono<ServerResponse> getAppBySlug(ServerRequest request) {
        var slug = request.pathVariable("slug");
        return softwarePublicQueryService.getAppDetailBySlug(slug)
            .flatMap(app -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(app));
    }

    private Mono<ServerResponse> getDownloadAsset(ServerRequest request) {
        var slug = request.pathVariable("slug");
        var assetName = request.queryParam("asset").filter(value -> !value.isBlank());
        var selectedAsset = assetName
            .map(name -> softwarePublicQueryService.getDownloadAsset(slug, name))
            .orElseGet(() -> softwarePublicQueryService.getPrimaryDownloadAsset(slug));
        return selectedAsset
            .flatMap(asset -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(asset));
    }

    private Mono<ServerResponse> listCategories(ServerRequest request) {
        var options = ListOptions.builder()
            .andQuery(equal("spec.visible", true))
            .build();
        return softwarePublicQueryService.listCategories(options, toPageRequest(request))
            .flatMap(result -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(result));
    }

    private Mono<ServerResponse> listTags(ServerRequest request) {
        var options = ListOptions.builder()
            .andQuery(equal("spec.visible", true))
            .build();
        return softwarePublicQueryService.listTags(options, toPageRequest(request))
            .flatMap(result -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(result));
    }

    private Mono<ServerResponse> listTopics(ServerRequest request) {
        var options = ListOptions.builder()
            .andQuery(equal("spec.published", true))
            .build();
        return softwarePublicQueryService.listTopics(options, toPageRequest(request))
            .flatMap(result -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(result));
    }

    private Mono<ServerResponse> getTopicBySlug(ServerRequest request) {
        var slug = request.pathVariable("slug");
        return softwarePublicQueryService.getTopicDetailBySlug(slug)
            .flatMap(topic -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(topic));
    }

    private static PageRequest toPageRequest(ServerRequest request) {
        var query = new SoftwarePublicQuery(request.exchange());
        return query.toPageRequest();
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("api.software.jiewen.run/v1alpha1");
    }
}
