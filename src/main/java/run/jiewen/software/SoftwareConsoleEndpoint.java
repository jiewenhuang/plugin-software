package run.jiewen.software;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.core.fn.builders.schema.Builder.schemaBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ListResult;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.jiewen.software.extension.DownloadAsset;
import run.jiewen.software.extension.SoftwareApp;
import run.jiewen.software.extension.SoftwareCategory;
import run.jiewen.software.extension.SoftwareTag;
import run.jiewen.software.extension.SoftwareTopic;
import run.jiewen.software.extension.SoftwareVersion;
import run.jiewen.software.finders.SoftwareConsoleService;
import run.jiewen.software.api.DownloadAssetCommand;
import run.jiewen.software.api.DownloadAssetSecretCommand;
import run.jiewen.software.api.SoftwareAppCommand;
import run.jiewen.software.api.SoftwareCategoryCommand;
import run.jiewen.software.api.SoftwareTagCommand;
import run.jiewen.software.api.SoftwareTopicCommand;
import run.jiewen.software.api.SoftwareVersionCommand;
import run.jiewen.software.vo.DownloadAssetConsoleVo;
import run.jiewen.software.vo.DownloadAssetSecretVo;
import run.jiewen.software.vo.DownloadAssetStatsVo;
import run.jiewen.software.vo.SoftwareConsoleStatsVo;

@Component
public class SoftwareConsoleEndpoint implements CustomEndpoint {

    private final SoftwareConsoleService softwareConsoleService;
    private final ReactiveSettingFetcher settingFetcher;

    public SoftwareConsoleEndpoint(SoftwareConsoleService softwareConsoleService,
        ReactiveSettingFetcher settingFetcher) {
        this.softwareConsoleService = softwareConsoleService;
        this.settingFetcher = settingFetcher;
    }

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        final var appTag = "console.api.software.jiewen.run/v1alpha1/SoftwareApp";
        final var versionTag = "console.api.software.jiewen.run/v1alpha1/SoftwareVersion";
        final var assetTag = "console.api.software.jiewen.run/v1alpha1/DownloadAsset";
        final var categoryTag = "console.api.software.jiewen.run/v1alpha1/SoftwareCategory";
        final var tagTag = "console.api.software.jiewen.run/v1alpha1/SoftwareTag";
        final var topicTag = "console.api.software.jiewen.run/v1alpha1/SoftwareTopic";
        final var optionTag = "console.api.software.jiewen.run/v1alpha1/SoftwareOptions";
        return route()
            .GET("options", this::getOptions,
                builder -> builder.operationId("GetSoftwareOptions")
                    .description("Get software plugin option settings for Console.")
                    .tag(optionTag)
                    .response(responseBuilder().implementation(SoftwareOptions.class)))
            .GET("apps", this::listApps,
                builder -> {
                    builder.operationId("ListSoftwareApps")
                        .description("List software apps for Console.")
                        .tag(appTag)
                        .response(responseBuilder()
                            .implementation(ListResult.generateGenericClass(SoftwareApp.class)));
                    SoftwareConsoleQuery.buildAppParameters(builder);
                })
            .GET("apps/stats", this::getAppStats,
                builder -> builder.operationId("GetSoftwareAppStats")
                    .description("Get software app stats and list facets for Console.")
                    .tag(appTag)
                    .response(responseBuilder().implementation(SoftwareConsoleStatsVo.class)))
            .GET("apps/{name}", this::getApp,
                builder -> namedResource(builder, appTag, "GetSoftwareApp", SoftwareApp.class))
            .POST("apps", this::createApp,
                builder -> createBody(builder, appTag, "CreateSoftwareApp",
                    SoftwareAppCommand.class, SoftwareApp.class))
            .PUT("apps/{name}", this::replaceApp,
                builder -> namedBody(builder, appTag, "UpdateSoftwareApp",
                    SoftwareAppCommand.class, SoftwareApp.class))
            .PATCH("apps/{name}", this::updateApp,
                builder -> namedBody(builder, appTag, "PatchSoftwareApp",
                    SoftwareAppCommand.class, SoftwareApp.class))
            .DELETE("apps/{name}", this::deleteApp,
                builder -> namedResource(builder, appTag, "DeleteSoftwareApp", SoftwareApp.class))

            .GET("versions", this::listVersions,
                builder -> {
                    builder.operationId("ListSoftwareVersions")
                        .description("List software versions for Console.")
                        .tag(versionTag)
                        .response(responseBuilder()
                            .implementation(ListResult.generateGenericClass(SoftwareVersion.class)));
                    SoftwareConsoleQuery.buildVersionParameters(builder);
                })
            .POST("versions", this::createVersion,
                builder -> createBody(builder, versionTag, "CreateSoftwareVersion",
                    SoftwareVersionCommand.class, SoftwareVersion.class))
            .PUT("versions/{name}", this::replaceVersion,
                builder -> namedBody(builder, versionTag, "UpdateSoftwareVersion",
                    SoftwareVersionCommand.class, SoftwareVersion.class))
            .PATCH("versions/{name}", this::updateVersion,
                builder -> namedBody(builder, versionTag, "PatchSoftwareVersion",
                    SoftwareVersionCommand.class, SoftwareVersion.class))
            .DELETE("versions/{name}", this::deleteVersion,
                builder -> namedResource(builder, versionTag, "DeleteSoftwareVersion",
                    SoftwareVersion.class))

            .GET("download-assets", this::listDownloadAssets,
                builder -> {
                    builder.operationId("ListDownloadAssets")
                        .description("List secret-free download assets for Console.")
                        .tag(assetTag)
                        .response(responseBuilder().implementation(
                            ListResult.generateGenericClass(DownloadAssetConsoleVo.class)));
                    SoftwareConsoleQuery.buildDownloadAssetParameters(builder);
                })
            .GET("download-assets/stats", this::getDownloadAssetStats,
                builder -> builder.operationId("GetDownloadAssetStats")
                    .description("Get aggregate download asset statistics for Console.")
                    .tag(assetTag)
                    .response(responseBuilder().implementation(DownloadAssetStatsVo.class)))
            .POST("download-assets", this::createDownloadAsset,
                builder -> createBody(builder, assetTag, "CreateDownloadAsset",
                    DownloadAssetCommand.class, DownloadAssetConsoleVo.class))
            .PUT("download-assets/{name}", this::replaceDownloadAsset,
                builder -> namedBody(builder, assetTag, "UpdateDownloadAsset",
                    DownloadAssetCommand.class, DownloadAssetConsoleVo.class))
            .PATCH("download-assets/{name}", this::updateDownloadAsset,
                builder -> namedBody(builder, assetTag, "PatchDownloadAsset",
                    DownloadAssetCommand.class, DownloadAssetConsoleVo.class))
            .GET("download-assets/{name}/secrets", this::getDownloadAssetSecret,
                builder -> namedResource(builder, assetTag, "GetDownloadAssetSecret",
                    DownloadAssetSecretVo.class))
            .PUT("download-assets/{name}/secrets", this::updateDownloadAssetSecret,
                builder -> namedBody(builder, assetTag, "UpdateDownloadAssetSecret",
                    DownloadAssetSecretCommand.class, DownloadAssetSecretVo.class))
            .DELETE("download-assets/{name}", this::deleteDownloadAsset,
                builder -> namedResource(builder, assetTag, "DeleteDownloadAsset",
                    DownloadAssetConsoleVo.class))

            .GET("categories", this::listCategories,
                builder -> {
                    builder.operationId("ListSoftwareCategories")
                        .description("List software categories for Console.")
                        .tag(categoryTag)
                        .response(responseBuilder()
                            .implementation(ListResult.generateGenericClass(SoftwareCategory.class)));
                    SoftwareConsoleQuery.buildCategoryParameters(builder);
                })
            .POST("categories", this::createCategory,
                builder -> createBody(builder, categoryTag, "CreateSoftwareCategory",
                    SoftwareCategoryCommand.class, SoftwareCategory.class))
            .PUT("categories/{name}", this::replaceCategory,
                builder -> namedBody(builder, categoryTag, "UpdateSoftwareCategory",
                    SoftwareCategoryCommand.class, SoftwareCategory.class))
            .PATCH("categories/{name}", this::updateCategory,
                builder -> namedBody(builder, categoryTag, "PatchSoftwareCategory",
                    SoftwareCategoryCommand.class, SoftwareCategory.class))
            .DELETE("categories/{name}", this::deleteCategory,
                builder -> namedResource(builder, categoryTag, "DeleteSoftwareCategory",
                    SoftwareCategory.class))

            .GET("tags", this::listTags,
                builder -> {
                    builder.operationId("ListSoftwareTags")
                        .description("List software tags for Console.")
                        .tag(tagTag)
                        .response(responseBuilder()
                            .implementation(ListResult.generateGenericClass(SoftwareTag.class)));
                    SoftwareConsoleQuery.buildTagParameters(builder);
                })
            .GET("tags/{name}", this::getTag,
                builder -> namedResource(builder, tagTag, "GetSoftwareTag", SoftwareTag.class))
            .POST("tags", this::createTag,
                builder -> createBody(builder, tagTag, "CreateSoftwareTag",
                    SoftwareTagCommand.class, SoftwareTag.class))
            .PUT("tags/{name}", this::replaceTag,
                builder -> namedBody(builder, tagTag, "UpdateSoftwareTag",
                    SoftwareTagCommand.class, SoftwareTag.class))
            .PATCH("tags/{name}", this::updateTag,
                builder -> namedBody(builder, tagTag, "PatchSoftwareTag",
                    SoftwareTagCommand.class, SoftwareTag.class))
            .DELETE("tags/{name}", this::deleteTag,
                builder -> namedResource(builder, tagTag, "DeleteSoftwareTag", SoftwareTag.class))

            .GET("topics", this::listTopics,
                builder -> {
                    builder.operationId("ListSoftwareTopics")
                        .description("List software topics for Console.")
                        .tag(topicTag)
                        .response(responseBuilder()
                            .implementation(ListResult.generateGenericClass(SoftwareTopic.class)));
                    SoftwareConsoleQuery.buildTopicParameters(builder);
                })
            .POST("topics", this::createTopic,
                builder -> createBody(builder, topicTag, "CreateSoftwareTopic",
                    SoftwareTopicCommand.class, SoftwareTopic.class))
            .PUT("topics/{name}", this::replaceTopic,
                builder -> namedBody(builder, topicTag, "UpdateSoftwareTopic",
                    SoftwareTopicCommand.class, SoftwareTopic.class))
            .PATCH("topics/{name}", this::updateTopic,
                builder -> namedBody(builder, topicTag, "PatchSoftwareTopic",
                    SoftwareTopicCommand.class, SoftwareTopic.class))
            .DELETE("topics/{name}", this::deleteTopic,
                builder -> namedResource(builder, topicTag, "DeleteSoftwareTopic",
                    SoftwareTopic.class))
            .build();
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("console.api.software.jiewen.run/v1alpha1");
    }

    private Mono<ServerResponse> listApps(ServerRequest request) {
        return softwareConsoleService.listApps(new SoftwareConsoleQuery(request.exchange()))
            .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> getAppStats(ServerRequest request) {
        return softwareConsoleService.getAppStats()
            .flatMap(stats -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(stats));
    }

    private Mono<ServerResponse> getOptions(ServerRequest request) {
        return settingFetcher.fetch(SoftwareOptions.GROUP, SoftwareOptions.class)
            .defaultIfEmpty(new SoftwareOptions())
            .map(SoftwareOptions::normalized)
            .onErrorReturn(new SoftwareOptions())
            .flatMap(options -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(options));
    }

    private Mono<ServerResponse> getApp(ServerRequest request) {
        return softwareConsoleService.getApp(request.pathVariable("name"))
            .flatMap(app -> ServerResponse.ok().bodyValue(app));
    }

    private Mono<ServerResponse> createApp(ServerRequest request) {
        return request.bodyToMono(SoftwareAppCommand.class)
            .map(SoftwareAppCommand::toExtension)
            .flatMap(softwareConsoleService::createApp)
            .flatMap(app -> ServerResponse.ok().bodyValue(app));
    }

    private Mono<ServerResponse> updateApp(ServerRequest request) {
        return request.bodyToMono(SoftwareAppCommand.class)
            .map(SoftwareAppCommand::toExtension)
            .flatMap(app -> softwareConsoleService.updateApp(request.pathVariable("name"), app))
            .flatMap(app -> ServerResponse.ok().bodyValue(app));
    }

    private Mono<ServerResponse> replaceApp(ServerRequest request) {
        return request.bodyToMono(SoftwareAppCommand.class)
            .map(SoftwareAppCommand::toExtension)
            .flatMap(app -> softwareConsoleService.replaceApp(request.pathVariable("name"), app))
            .flatMap(app -> ServerResponse.ok().bodyValue(app));
    }

    private Mono<ServerResponse> deleteApp(ServerRequest request) {
        return softwareConsoleService.deleteApp(request.pathVariable("name"))
            .flatMap(app -> ServerResponse.ok().bodyValue(app));
    }

    private Mono<ServerResponse> listVersions(ServerRequest request) {
        return softwareConsoleService.listVersions(new SoftwareConsoleQuery(request.exchange()))
            .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> createVersion(ServerRequest request) {
        return request.bodyToMono(SoftwareVersionCommand.class)
            .map(SoftwareVersionCommand::toExtension)
            .flatMap(softwareConsoleService::createVersion)
            .flatMap(version -> ServerResponse.ok().bodyValue(version));
    }

    private Mono<ServerResponse> updateVersion(ServerRequest request) {
        return request.bodyToMono(SoftwareVersionCommand.class)
            .map(SoftwareVersionCommand::toExtension)
            .flatMap(version -> softwareConsoleService.updateVersion(
                request.pathVariable("name"), version))
            .flatMap(version -> ServerResponse.ok().bodyValue(version));
    }

    private Mono<ServerResponse> replaceVersion(ServerRequest request) {
        return request.bodyToMono(SoftwareVersionCommand.class)
            .map(SoftwareVersionCommand::toExtension)
            .flatMap(version -> softwareConsoleService.replaceVersion(
                request.pathVariable("name"), version))
            .flatMap(version -> ServerResponse.ok().bodyValue(version));
    }

    private Mono<ServerResponse> deleteVersion(ServerRequest request) {
        return softwareConsoleService.deleteVersion(request.pathVariable("name"))
            .flatMap(version -> ServerResponse.ok().bodyValue(version));
    }

    private Mono<ServerResponse> listDownloadAssets(ServerRequest request) {
        return softwareConsoleService.listDownloadAssets(
                new SoftwareConsoleQuery(request.exchange()))
            .map(result -> new ListResult<>(result.getPage(), result.getSize(), result.getTotal(),
                result.getItems().stream().map(DownloadAssetConsoleVo::from).toList()))
            .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> createDownloadAsset(ServerRequest request) {
        return request.bodyToMono(DownloadAssetCommand.class)
            .map(DownloadAssetCommand::toExtension)
            .flatMap(softwareConsoleService::createDownloadAsset)
            .map(DownloadAssetConsoleVo::from)
            .flatMap(asset -> ServerResponse.ok().bodyValue(asset));
    }

    private Mono<ServerResponse> getDownloadAssetStats(ServerRequest request) {
        return softwareConsoleService.getDownloadAssetStats()
            .flatMap(stats -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(stats));
    }

    private Mono<ServerResponse> updateDownloadAsset(ServerRequest request) {
        return request.bodyToMono(DownloadAssetCommand.class)
            .map(DownloadAssetCommand::toExtension)
            .flatMap(asset -> softwareConsoleService.updateDownloadAsset(
                request.pathVariable("name"), asset))
            .map(DownloadAssetConsoleVo::from)
            .flatMap(asset -> ServerResponse.ok().bodyValue(asset));
    }

    private Mono<ServerResponse> replaceDownloadAsset(ServerRequest request) {
        return request.bodyToMono(DownloadAssetCommand.class)
            .map(DownloadAssetCommand::toExtension)
            .flatMap(asset -> softwareConsoleService.replaceDownloadAsset(
                request.pathVariable("name"), asset))
            .map(DownloadAssetConsoleVo::from)
            .flatMap(asset -> ServerResponse.ok().bodyValue(asset));
    }

    private Mono<ServerResponse> getDownloadAssetSecret(ServerRequest request) {
        return softwareConsoleService.getDownloadAssetSecret(request.pathVariable("name"))
            .flatMap(secret -> ServerResponse.ok().bodyValue(secret));
    }

    private Mono<ServerResponse> updateDownloadAssetSecret(ServerRequest request) {
        return request.bodyToMono(DownloadAssetSecretCommand.class)
            .flatMap(command -> softwareConsoleService.updateDownloadAssetSecret(
                request.pathVariable("name"), command))
            .flatMap(secret -> ServerResponse.ok().bodyValue(secret));
    }

    private Mono<ServerResponse> deleteDownloadAsset(ServerRequest request) {
        return softwareConsoleService.deleteDownloadAsset(request.pathVariable("name"))
            .map(DownloadAssetConsoleVo::from)
            .flatMap(asset -> ServerResponse.ok().bodyValue(asset));
    }

    private Mono<ServerResponse> listCategories(ServerRequest request) {
        return softwareConsoleService.listCategories(new SoftwareConsoleQuery(request.exchange()))
            .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> createCategory(ServerRequest request) {
        return request.bodyToMono(SoftwareCategoryCommand.class)
            .map(SoftwareCategoryCommand::toExtension)
            .flatMap(softwareConsoleService::createCategory)
            .flatMap(category -> ServerResponse.ok().bodyValue(category));
    }

    private Mono<ServerResponse> updateCategory(ServerRequest request) {
        return request.bodyToMono(SoftwareCategoryCommand.class)
            .map(SoftwareCategoryCommand::toExtension)
            .flatMap(category -> softwareConsoleService.updateCategory(
                request.pathVariable("name"), category))
            .flatMap(category -> ServerResponse.ok().bodyValue(category));
    }

    private Mono<ServerResponse> replaceCategory(ServerRequest request) {
        return request.bodyToMono(SoftwareCategoryCommand.class)
            .map(SoftwareCategoryCommand::toExtension)
            .flatMap(category -> softwareConsoleService.replaceCategory(
                request.pathVariable("name"), category))
            .flatMap(category -> ServerResponse.ok().bodyValue(category));
    }

    private Mono<ServerResponse> deleteCategory(ServerRequest request) {
        return softwareConsoleService.deleteCategory(request.pathVariable("name"))
            .flatMap(category -> ServerResponse.ok().bodyValue(category));
    }

    private Mono<ServerResponse> listTags(ServerRequest request) {
        return softwareConsoleService.listTags(new SoftwareConsoleQuery(request.exchange()))
            .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> getTag(ServerRequest request) {
        return softwareConsoleService.getTag(request.pathVariable("name"))
            .flatMap(tag -> ServerResponse.ok().bodyValue(tag));
    }

    private Mono<ServerResponse> createTag(ServerRequest request) {
        return request.bodyToMono(SoftwareTagCommand.class)
            .map(SoftwareTagCommand::toExtension)
            .flatMap(softwareConsoleService::createTag)
            .flatMap(tag -> ServerResponse.ok().bodyValue(tag));
    }

    private Mono<ServerResponse> updateTag(ServerRequest request) {
        return request.bodyToMono(SoftwareTagCommand.class)
            .map(SoftwareTagCommand::toExtension)
            .flatMap(tag -> softwareConsoleService.updateTag(request.pathVariable("name"), tag))
            .flatMap(tag -> ServerResponse.ok().bodyValue(tag));
    }

    private Mono<ServerResponse> replaceTag(ServerRequest request) {
        return request.bodyToMono(SoftwareTagCommand.class)
            .map(SoftwareTagCommand::toExtension)
            .flatMap(tag -> softwareConsoleService.replaceTag(request.pathVariable("name"), tag))
            .flatMap(tag -> ServerResponse.ok().bodyValue(tag));
    }

    private Mono<ServerResponse> deleteTag(ServerRequest request) {
        return softwareConsoleService.deleteTag(request.pathVariable("name"))
            .flatMap(tag -> ServerResponse.ok().bodyValue(tag));
    }

    private Mono<ServerResponse> listTopics(ServerRequest request) {
        return softwareConsoleService.listTopics(new SoftwareConsoleQuery(request.exchange()))
            .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> createTopic(ServerRequest request) {
        return request.bodyToMono(SoftwareTopicCommand.class)
            .map(SoftwareTopicCommand::toExtension)
            .flatMap(softwareConsoleService::createTopic)
            .flatMap(topic -> ServerResponse.ok().bodyValue(topic));
    }

    private Mono<ServerResponse> updateTopic(ServerRequest request) {
        return request.bodyToMono(SoftwareTopicCommand.class)
            .map(SoftwareTopicCommand::toExtension)
            .flatMap(topic -> softwareConsoleService.updateTopic(
                request.pathVariable("name"), topic))
            .flatMap(topic -> ServerResponse.ok().bodyValue(topic));
    }

    private Mono<ServerResponse> replaceTopic(ServerRequest request) {
        return request.bodyToMono(SoftwareTopicCommand.class)
            .map(SoftwareTopicCommand::toExtension)
            .flatMap(topic -> softwareConsoleService.replaceTopic(
                request.pathVariable("name"), topic))
            .flatMap(topic -> ServerResponse.ok().bodyValue(topic));
    }

    private Mono<ServerResponse> deleteTopic(ServerRequest request) {
        return softwareConsoleService.deleteTopic(request.pathVariable("name"))
            .flatMap(topic -> ServerResponse.ok().bodyValue(topic));
    }

    private static void namedResource(org.springdoc.core.fn.builders.operation.Builder builder,
        String tag, String operationId, Class<?> type) {
        builder.operationId(operationId)
            .tag(tag)
            .parameter(parameterBuilder()
                .in(ParameterIn.PATH)
                .name("name")
                .required(true)
                .implementation(String.class))
            .response(responseBuilder().implementation(type));
    }

    private static void createBody(org.springdoc.core.fn.builders.operation.Builder builder,
        String tag, String operationId, Class<?> commandType, Class<?> responseType) {
        builder.operationId(operationId)
            .tag(tag)
            .requestBody(requestBodyBuilder()
                .required(true)
                .content(contentBuilder()
                    .mediaType(MediaType.APPLICATION_JSON_VALUE)
                    .schema(schemaBuilder().implementation(commandType))))
            .response(responseBuilder().implementation(responseType));
    }

    private static void namedBody(org.springdoc.core.fn.builders.operation.Builder builder,
        String tag, String operationId, Class<?> commandType, Class<?> responseType) {
        createBody(builder, tag, operationId, commandType, responseType);
        builder.parameter(parameterBuilder()
            .in(ParameterIn.PATH)
            .name("name")
            .required(true)
            .implementation(String.class));
    }
}
