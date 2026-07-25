package run.jiewen.software.finders.impl;

import static run.halo.app.extension.index.query.Queries.and;
import static run.halo.app.extension.index.query.Queries.equal;
import static run.halo.app.extension.index.query.Queries.in;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.PageRequest;
import run.halo.app.extension.PageRequestImpl;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.infra.AnonymousUserConst;
import run.halo.app.theme.finders.Finder;
import run.jiewen.software.ai.SoftwareHtmlSanitizer;
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
import run.jiewen.software.vo.SoftwareHomeVo;
import run.jiewen.software.vo.SoftwareTagVo;
import run.jiewen.software.vo.SoftwareTopicDetailVo;
import run.jiewen.software.vo.SoftwareTopicVo;
import run.jiewen.software.vo.SoftwareVersionVo;

@Finder("softwareFinder")
public class SoftwarePublicQueryServiceImpl implements SoftwarePublicQueryService {

    private static final int DEFAULT_HOME_SECTION_SIZE = 12;
    private static final int MAX_HOME_SECTION_SIZE = 12;
    private static final int MAX_PUBLIC_QUERY_SIZE = 100;

    private final ReactiveExtensionClient client;
    private final SoftwareHtmlSanitizer htmlSanitizer;

    public SoftwarePublicQueryServiceImpl(ReactiveExtensionClient client,
        SoftwareHtmlSanitizer htmlSanitizer) {
        this.client = client;
        this.htmlSanitizer = htmlSanitizer;
    }

    @Override
    public Mono<ListResult<SoftwareAppVo>> listApps(ListOptions options, PageRequest page) {
        return client.listBy(SoftwareApp.class, options, page)
            .flatMap(result -> Flux.fromIterable(result.getItems())
                .map(this::toPublicVo)
                .collectList()
                .map(items -> new ListResult<>(
                    result.getPage(), result.getSize(), result.getTotal(), items)));
    }

    @Override
    public Mono<SoftwareAppVo> getAppBySlug(String slug) {
        return getPublishedAppBySlug(slug).map(this::toPublicVo);
    }

    @Override
    public Mono<SoftwareAppDetailVo> getAppDetailBySlug(String slug) {
        return getPublishedAppBySlug(slug)
            .flatMap(app -> Mono.zip(
                    enrichTaxonomyNames(toPublicVo(app)),
                    listVersions(app).collectList(),
                    listDownloadAssets(app).collectList())
                .map(result -> SoftwareAppDetailVo.from(
                    result.getT1(), result.getT2(), result.getT3())));
    }

    @Override
    public Mono<SoftwareHomeVo> getHome() {
        return getHome(DEFAULT_HOME_SECTION_SIZE);
    }

    @Override
    public Mono<SoftwareHomeVo> getHome(int size) {
        var normalizedSize = Math.max(1, Math.min(size, MAX_HOME_SECTION_SIZE));
        return Mono.zip(
                listHomeApps(ListOptions.builder()
                    .andQuery(equal("spec.published", true))
                    .build(), stableDescending("status.latestReleaseDate"), normalizedSize),
                listHomeApps(ListOptions.builder()
                    .andQuery(equal("spec.published", true))
                    .build(), stableDescending("status.downloadCount"), normalizedSize),
                recommendedApps(normalizedSize),
                listHomeApps(ListOptions.builder()
                    .andQuery(equal("spec.published", true))
                    .build(), stableDescending("status.commentCount"), normalizedSize),
                publishedTopics(),
                listHomeCategories())
            .map(result -> SoftwareHomeVo.builder()
                .latestApps(result.getT1())
                .popularApps(result.getT2())
                .recommendedApps(result.getT3().getItems())
                .recommendedTotal(result.getT3().getTotal())
                .commentedApps(result.getT4())
                .topics(result.getT5())
                .categories(result.getT6())
                .build());
    }

    @Override
    public Mono<ListResult<SoftwareAppVo>> recommendedApps(int size) {
        var options = ListOptions.builder()
            .andQuery(and(
                equal("spec.published", true),
                equal("spec.recommended", true)))
            .build();
        var normalizedSize = Math.max(1, Math.min(size, MAX_PUBLIC_QUERY_SIZE));
        return listApps(options, PageRequestImpl.of(1, normalizedSize,
            stableDescending("status.latestReleaseDate")));
    }

    @Override
    public Mono<List<SoftwareTopicVo>> publishedTopics() {
        var options = ListOptions.builder()
            .andQuery(equal("spec.published", true))
            .build();
        return listTopics(options, PageRequestImpl.of(1, MAX_PUBLIC_QUERY_SIZE,
                Sort.by(
                    Sort.Order.asc("spec.priority"),
                    Sort.Order.asc("metadata.creationTimestamp"))))
            .map(ListResult::getItems);
    }

    @Override
    public Mono<List<SoftwareTopicDetailVo>> topicDetails(List<String> slugs) {
        var selectedSlugs = slugs == null ? List.<String>of() : slugs.stream()
            .filter(slug -> slug != null && !slug.isBlank())
            .distinct()
            .limit(MAX_PUBLIC_QUERY_SIZE)
            .toList();
        var topicOptions = ListOptions.builder()
            .andQuery(equal("spec.published", true));
        if (!selectedSlugs.isEmpty()) {
            topicOptions.andQuery(in("spec.slug",
                selectedSlugs.stream().map(slug -> (Object) slug).toList()));
        }

        return client.listAll(SoftwareTopic.class, topicOptions.build(), Sort.by(
                Sort.Order.asc("spec.priority"),
                Sort.Order.asc("metadata.creationTimestamp")))
            .take(MAX_PUBLIC_QUERY_SIZE)
            .collectList()
            .flatMap(topics -> {
                var orderedTopics = orderTopics(topics, selectedSlugs);
                var appNames = orderedTopics.stream()
                    .flatMap(topic -> topic.getSpec() == null || topic.getSpec().getItems() == null
                        ? List.<SoftwareTopic.Item>of().stream()
                        : topic.getSpec().getItems().stream())
                    .map(SoftwareTopic.Item::getAppName)
                    .filter(name -> name != null && !name.isBlank())
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
                if (appNames.isEmpty()) {
                    return Mono.just(orderedTopics.stream()
                        .map(topic -> SoftwareTopicDetailVo.from(topic, List.of()))
                        .toList());
                }

                var appOptions = ListOptions.builder()
                    .andQuery(and(
                        equal("spec.published", true),
                        in("metadata.name", appNames.stream()
                            .map(name -> (Object) name)
                            .toList())))
                    .build();
                return client.listAll(SoftwareApp.class, appOptions, Sort.unsorted())
                    .collectMap(app -> app.getMetadata().getName(), this::toPublicVo)
                    .map(appsByName -> orderedTopics.stream()
                        .map(topic -> SoftwareTopicDetailVo.from(
                            topic, appsForTopic(topic, appsByName)))
                        .toList());
            });
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
    public Mono<SoftwareCategoryVo> getCategoryBySlug(String slug) {
        var options = ListOptions.builder()
            .andQuery(and(equal("spec.slug", slug), equal("spec.visible", true)))
            .build();
        return client.listBy(SoftwareCategory.class, options,
                PageRequestImpl.of(1, 1, Sort.unsorted()))
            .map(ListResult::getItems)
            .flatMap(items -> Mono.justOrEmpty(items.isEmpty() ? null : items.get(0)))
            .map(SoftwareCategoryVo::from)
            .switchIfEmpty(Mono.error(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Software category not found")));
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
    public Mono<SoftwareTagVo> getTagBySlug(String slug) {
        var options = ListOptions.builder()
            .andQuery(and(equal("spec.slug", slug), equal("spec.visible", true)))
            .build();
        return client.listBy(SoftwareTag.class, options,
                PageRequestImpl.of(1, 1, Sort.unsorted()))
            .map(ListResult::getItems)
            .flatMap(items -> Mono.justOrEmpty(items.isEmpty() ? null : items.get(0)))
            .map(SoftwareTagVo::from)
            .switchIfEmpty(Mono.error(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Software tag not found")));
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
        if (slug == null || slug.isBlank()) {
            return Mono.error(new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Software topic not found"));
        }
        return topicDetails(List.of(slug))
            .flatMap(details -> Mono.justOrEmpty(details.isEmpty() ? null : details.get(0)))
            .switchIfEmpty(Mono.error(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Software topic not found")));
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

    private Mono<List<SoftwareAppVo>> listHomeApps(ListOptions options, Sort sort, int size) {
        return client.listBy(SoftwareApp.class, options, PageRequestImpl.of(1, size, sort))
            .flatMap(result -> Flux.fromIterable(result.getItems())
                .map(this::toPublicVo)
                .collectList());
    }

    private static List<SoftwareTopic> orderTopics(List<SoftwareTopic> topics,
        List<String> selectedSlugs) {
        if (selectedSlugs.isEmpty()) {
            return topics;
        }
        return selectedSlugs.stream()
            .map(slug -> topics.stream()
                .filter(topic -> topic.getSpec() != null
                    && slug.equals(topic.getSpec().getSlug()))
                .findFirst()
                .orElse(null))
            .filter(Objects::nonNull)
            .toList();
    }

    private static List<SoftwareAppVo> appsForTopic(SoftwareTopic topic,
        Map<String, SoftwareAppVo> appsByName) {
        if (topic.getSpec() == null || topic.getSpec().getItems() == null) {
            return List.of();
        }
        return topic.getSpec().getItems().stream()
            .sorted(Comparator.comparing(item -> item.getPriority() == null
                ? 0 : item.getPriority()))
            .map(SoftwareTopic.Item::getAppName)
            .map(appsByName::get)
            .filter(Objects::nonNull)
            .toList();
    }

    private static Sort stableDescending(String property) {
        return Sort.by(
            Sort.Order.desc(property),
            Sort.Order.desc("metadata.creationTimestamp"));
    }

    private Mono<List<SoftwareCategoryVo>> listHomeCategories() {
        var options = ListOptions.builder()
            .andQuery(equal("spec.visible", true))
            .build();
        return client.listBy(SoftwareCategory.class, options,
                PageRequestImpl.of(1, 100, Sort.by("spec.priority").ascending()))
            .flatMap(result -> Flux.fromIterable(result.getItems())
                .map(SoftwareCategoryVo::from)
                .collectList());
    }

    private static SoftwareApp first(List<SoftwareApp> apps) {
        return apps.isEmpty() ? null : apps.get(0);
    }

    private SoftwareAppVo toPublicVo(SoftwareApp app) {
        var publicVo = SoftwareAppVo.from(app);
        return publicVo.toBuilder()
            .description(htmlSanitizer.sanitize(publicVo.getDescription()))
            .build();
    }

    private Mono<SoftwareAppVo> enrichTaxonomyNames(SoftwareAppVo app) {
        return Mono.zip(
                resolveCategoryDisplayName(app.getCategoryName()),
                resolveCategorySlug(app.getCategoryName()),
                resolveTagDisplayNames(app.getTagNames()))
            .map(names -> app.toBuilder()
                .categoryDisplayName(names.getT1())
                .categorySlug(names.getT2())
                .tagDisplayNames(names.getT3())
                .build());
    }

    private Mono<String> resolveCategoryDisplayName(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            return Mono.just("");
        }
        return client.fetch(SoftwareCategory.class, categoryName)
            .flatMap(category -> Mono.justOrEmpty(category.getSpec()))
            .flatMap(spec -> Mono.justOrEmpty(spec.getDisplayName()))
            .filter(displayName -> !displayName.isBlank())
            .defaultIfEmpty(categoryName);
    }

    private Mono<String> resolveCategorySlug(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            return Mono.just("all");
        }
        return client.fetch(SoftwareCategory.class, categoryName)
            .flatMap(category -> Mono.justOrEmpty(category.getSpec()))
            .flatMap(spec -> Mono.justOrEmpty(spec.getSlug()))
            .filter(slug -> !slug.isBlank())
            .defaultIfEmpty(categoryName);
    }

    private Mono<List<String>> resolveTagDisplayNames(List<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) {
            return Mono.just(List.of());
        }
        var validTagNames = tagNames.stream()
            .filter(tagName -> tagName != null && !tagName.isBlank())
            .toList();
        return Flux.fromIterable(validTagNames)
            .concatMap(tagName -> client.fetch(SoftwareTag.class, tagName)
                .flatMap(tag -> Mono.justOrEmpty(tag.getSpec()))
                .flatMap(spec -> Mono.justOrEmpty(spec.getDisplayName()))
                .filter(displayName -> !displayName.isBlank())
                .defaultIfEmpty(tagName))
            .collectList();
    }

    private Mono<String> currentUserName() {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .flatMap(authentication -> Mono.justOrEmpty(
                authentication == null ? null : authentication.getName()))
            .filter(name -> !AnonymousUserConst.isAnonymousUser(name));
    }
}
