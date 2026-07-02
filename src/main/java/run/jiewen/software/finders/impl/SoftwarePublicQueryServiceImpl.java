package run.jiewen.software.finders.impl;

import static run.halo.app.extension.index.query.Queries.and;
import static run.halo.app.extension.index.query.Queries.equal;

import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.PageRequest;
import run.halo.app.extension.PageRequestImpl;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.infra.AnonymousUserConst;
import run.jiewen.software.extension.DownloadAsset;
import run.jiewen.software.extension.SoftwareApp;
import run.jiewen.software.extension.SoftwareCategory;
import run.jiewen.software.extension.SoftwareTag;
import run.jiewen.software.extension.SoftwareTopic;
import run.jiewen.software.extension.SoftwareVersion;
import run.jiewen.software.finders.SoftwarePublicQueryService;
import run.jiewen.software.vo.DownloadAssetVo;
import run.jiewen.software.vo.SoftwareAppDetailVo;
import run.jiewen.software.vo.SoftwareAppVo;
import run.jiewen.software.vo.SoftwareCategoryVo;
import run.jiewen.software.vo.SoftwareTagVo;
import run.jiewen.software.vo.SoftwareTopicDetailVo;
import run.jiewen.software.vo.SoftwareTopicVo;
import run.jiewen.software.vo.SoftwareVersionVo;

@Component
public class SoftwarePublicQueryServiceImpl implements SoftwarePublicQueryService {

    private final ReactiveExtensionClient client;

    public SoftwarePublicQueryServiceImpl(ReactiveExtensionClient client) {
        this.client = client;
    }

    @Override
    public Mono<ListResult<SoftwareAppVo>> listApps(ListOptions options, PageRequest page) {
        return client.listBy(SoftwareApp.class, options, page)
            .flatMap(result -> Flux.fromIterable(result.getItems())
                .map(SoftwareAppVo::from)
                .collectList()
                .map(items -> new ListResult<>(
                    result.getPage(), result.getSize(), result.getTotal(), items)));
    }

    @Override
    public Mono<SoftwareAppVo> getAppBySlug(String slug) {
        return getPublishedAppBySlug(slug).map(SoftwareAppVo::from);
    }

    @Override
    public Mono<SoftwareAppDetailVo> getAppDetailBySlug(String slug) {
        return getPublishedAppBySlug(slug)
            .flatMap(app -> Mono.zip(
                listVersions(app).collectList(),
                listDownloadAssets(app).collectList(),
                (versions, assets) -> SoftwareAppDetailVo.from(
                    SoftwareAppVo.from(app), versions, assets)
            ));
    }

    @Override
    public Mono<DownloadAssetVo> getPrimaryDownloadAsset(String slug) {
        return getDownloadAsset(slug, null);
    }

    @Override
    public Mono<DownloadAssetVo> getDownloadAsset(String slug, String assetName) {
        return getPublishedAppBySlug(slug)
            .flatMapMany(this::listDownloadAssetExtensions)
            .filter(asset -> assetName == null || assetName.isBlank()
                || (asset.getMetadata() != null && assetName.equals(asset.getMetadata().getName())))
            .next()
            .switchIfEmpty(Mono.error(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Download asset not found")))
            .flatMap(asset -> {
                var spec = asset.getSpec();
                if (spec != null && Boolean.TRUE.equals(spec.getRequiresLogin())) {
                    return currentUserName()
                        .switchIfEmpty(Mono.error(() -> new ResponseStatusException(
                            HttpStatus.UNAUTHORIZED, "Download requires login")))
                        .then(Mono.defer(() -> {
                            if (Boolean.TRUE.equals(spec.getRequiresCaptcha())) {
                                return Mono.error(new ResponseStatusException(
                                    HttpStatus.FORBIDDEN, "Download requires verification"));
                            }
                            return Mono.just(DownloadAssetVo.fromDownloadGate(asset));
                        }));
                }
                if (spec != null && Boolean.TRUE.equals(spec.getRequiresCaptcha())) {
                    return Mono.error(new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Download requires verification"));
                }
                return Mono.just(DownloadAssetVo.fromDownloadGate(asset));
            });
    }

    @Override
    public Mono<ListResult<SoftwareCategoryVo>> listCategories(ListOptions options,
        PageRequest page) {
        return client.listBy(SoftwareCategory.class, options, page)
            .flatMap(result -> Flux.fromIterable(result.getItems())
                .map(SoftwareCategoryVo::from)
                .collectList()
                .map(items -> new ListResult<>(
                    result.getPage(), result.getSize(), result.getTotal(), items)));
    }

    @Override
    public Mono<ListResult<SoftwareTagVo>> listTags(ListOptions options, PageRequest page) {
        return client.listBy(SoftwareTag.class, options, page)
            .flatMap(result -> Flux.fromIterable(result.getItems())
                .map(SoftwareTagVo::from)
                .collectList()
                .map(items -> new ListResult<>(
                    result.getPage(), result.getSize(), result.getTotal(), items)));
    }

    @Override
    public Mono<ListResult<SoftwareTopicVo>> listTopics(ListOptions options, PageRequest page) {
        return client.listBy(SoftwareTopic.class, options, page)
            .flatMap(result -> Flux.fromIterable(result.getItems())
                .map(SoftwareTopicVo::from)
                .collectList()
                .map(items -> new ListResult<>(
                    result.getPage(), result.getSize(), result.getTotal(), items)));
    }

    @Override
    public Mono<SoftwareTopicDetailVo> getTopicDetailBySlug(String slug) {
        return getPublishedTopicBySlug(slug)
            .flatMap(topic -> listTopicApps(topic).collectList()
                .map(apps -> SoftwareTopicDetailVo.from(topic, apps)));
    }

    private Mono<SoftwareApp> getPublishedAppBySlug(String slug) {
        var options = ListOptions.builder()
            .andQuery(and(equal("spec.slug", slug), equal("spec.published", true)))
            .build();
        return client.listBy(SoftwareApp.class, options,
                PageRequestImpl.of(1, 1, Sort.unsorted()))
            .map(ListResult::getItems)
            .flatMap(items -> Mono.justOrEmpty(first(items)))
            .switchIfEmpty(Mono.error(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Software app not found")));
    }

    private Mono<SoftwareTopic> getPublishedTopicBySlug(String slug) {
        var options = ListOptions.builder()
            .andQuery(and(equal("spec.slug", slug), equal("spec.published", true)))
            .build();
        return client.listBy(SoftwareTopic.class, options,
                PageRequestImpl.of(1, 1, Sort.unsorted()))
            .map(ListResult::getItems)
            .flatMap(items -> Mono.justOrEmpty(firstTopic(items)))
            .switchIfEmpty(Mono.error(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Software topic not found")));
    }

    private Flux<SoftwareAppVo> listTopicApps(SoftwareTopic topic) {
        var items = topic.getSpec() == null || topic.getSpec().getItems() == null
            ? List.<SoftwareTopic.Item>of()
            : topic.getSpec().getItems();
        return Flux.fromIterable(items)
            .sort(Comparator.comparing(item -> item.getPriority() == null ? 0 : item.getPriority()))
            .map(SoftwareTopic.Item::getAppName)
            .filter(appName -> appName != null && !appName.isBlank())
            .concatMap(appName -> client.fetch(SoftwareApp.class, appName))
            .filter(this::isPublished)
            .map(SoftwareAppVo::from);
    }

    private Flux<SoftwareVersionVo> listVersions(SoftwareApp app) {
        var options = ListOptions.builder()
            .andQuery(equal("spec.appName", app.getMetadata().getName()))
            .build();
        return client.listAll(SoftwareVersion.class, options, Sort.by("spec.releaseDate").descending())
            .map(SoftwareVersionVo::from);
    }

    private Flux<DownloadAssetVo> listDownloadAssets(SoftwareApp app) {
        return listDownloadAssetExtensions(app)
            .map(DownloadAssetVo::fromPublicList);
    }

    private Flux<DownloadAsset> listDownloadAssetExtensions(SoftwareApp app) {
        var options = ListOptions.builder()
            .andQuery(and(
                equal("spec.appName", app.getMetadata().getName()),
                equal("spec.enabled", true)))
            .build();
        return client.listAll(DownloadAsset.class, options, Sort.by("spec.priority").ascending());
    }

    private static SoftwareApp first(List<SoftwareApp> apps) {
        return apps.isEmpty() ? null : apps.get(0);
    }

    private static SoftwareTopic firstTopic(List<SoftwareTopic> topics) {
        return topics.isEmpty() ? null : topics.get(0);
    }

    private boolean isPublished(SoftwareApp app) {
        return app.getSpec() != null && Boolean.TRUE.equals(app.getSpec().getPublished());
    }

    private Mono<String> currentUserName() {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .flatMap(authentication -> Mono.justOrEmpty(
                authentication == null ? null : authentication.getName()))
            .filter(name -> !AnonymousUserConst.isAnonymousUser(name));
    }
}
