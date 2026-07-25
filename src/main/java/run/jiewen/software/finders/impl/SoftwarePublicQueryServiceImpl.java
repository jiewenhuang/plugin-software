package run.jiewen.software.finders.impl;

import static run.halo.app.extension.index.query.Queries.and;
import static run.halo.app.extension.index.query.Queries.equal;
import static run.halo.app.extension.index.query.Queries.in;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.PageRequest;
import run.halo.app.extension.PageRequestImpl;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Secret;
import run.halo.app.extension.ExtensionUtil;
import run.halo.app.core.extension.content.Post;
import run.halo.app.infra.AnonymousUserConst;
import run.halo.app.theme.finders.Finder;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.jiewen.software.SoftwareSecurityOptions;
import run.jiewen.software.ai.SoftwareHtmlSanitizer;
import run.jiewen.software.api.DownloadGrantRequest;
import run.jiewen.software.download.SoftwareCaptchaVerifier;
import run.jiewen.software.download.DownloadTargetPolicy;
import run.jiewen.software.extension.DownloadAsset;
import run.jiewen.software.extension.SoftwareApp;
import run.jiewen.software.extension.SoftwareCategory;
import run.jiewen.software.extension.SoftwareTag;
import run.jiewen.software.extension.SoftwareTopic;
import run.jiewen.software.extension.SoftwareVersion;
import run.jiewen.software.finders.SoftwarePublicQueryService;
import run.jiewen.software.vo.DownloadAssetVo;
import run.jiewen.software.vo.DownloadGrantVo;
import run.jiewen.software.vo.DownloadSecurityVo;
import run.jiewen.software.vo.SoftwareAppDetailVo;
import run.jiewen.software.vo.SoftwareAppVo;
import run.jiewen.software.vo.SoftwareCategoryVo;
import run.jiewen.software.vo.SoftwareHomeVo;
import run.jiewen.software.vo.SoftwareRelatedPostVo;
import run.jiewen.software.vo.SoftwareTagVo;
import run.jiewen.software.vo.SoftwareTopicDetailVo;
import run.jiewen.software.vo.SoftwareTopicItemVo;
import run.jiewen.software.vo.SoftwareTopicVo;
import run.jiewen.software.vo.SoftwareVersionVo;

@Finder("softwareFinder")
public class SoftwarePublicQueryServiceImpl implements SoftwarePublicQueryService {

    private static final int DEFAULT_HOME_SECTION_SIZE = 12;
    private static final int MAX_HOME_SECTION_SIZE = 12;
    private static final int MAX_PUBLIC_QUERY_SIZE = 100;
    private static final long DOWNLOAD_GRANT_TTL_SECONDS = 60;

    private final ReactiveExtensionClient client;
    private final SoftwareHtmlSanitizer htmlSanitizer;
    private final SoftwareCaptchaVerifier captchaVerifier;
    private final ReactiveSettingFetcher settingFetcher;

    public SoftwarePublicQueryServiceImpl(ReactiveExtensionClient client,
        SoftwareHtmlSanitizer htmlSanitizer) {
        this(client, htmlSanitizer, SoftwareCaptchaVerifier.denying(), null);
    }

    public SoftwarePublicQueryServiceImpl(ReactiveExtensionClient client,
        SoftwareHtmlSanitizer htmlSanitizer, SoftwareCaptchaVerifier captchaVerifier) {
        this(client, htmlSanitizer, captchaVerifier, null);
    }

    @Autowired
    public SoftwarePublicQueryServiceImpl(ReactiveExtensionClient client,
        SoftwareHtmlSanitizer htmlSanitizer, SoftwareCaptchaVerifier captchaVerifier,
        ReactiveSettingFetcher settingFetcher) {
        this.client = client;
        this.htmlSanitizer = htmlSanitizer;
        this.captchaVerifier = captchaVerifier;
        this.settingFetcher = settingFetcher;
    }

    @Override
    public Mono<ListResult<SoftwareAppVo>> listApps(ListOptions options, PageRequest page) {
        return client.listBy(SoftwareApp.class, publishedAppOptions(options), page)
            .flatMap(result -> Flux.fromIterable(result.getItems())
                .filter(SoftwarePublicQueryServiceImpl::isPublishedApp)
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
                    listDownloadAssets(app).collectList(),
                    listRelatedApps(app),
                    listRelatedPosts(app))
                .map(result -> SoftwareAppDetailVo.from(
                    result.getT1(), result.getT2(), result.getT3(), result.getT4(),
                    result.getT5())));
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
            .filter(SoftwarePublicQueryServiceImpl::isPublishedTopic)
            .take(MAX_PUBLIC_QUERY_SIZE)
            .collectList()
            .map(topics -> orderTopics(topics, selectedSlugs))
            .flatMap(this::enrichTopics);
    }

    @Override
    public Mono<DownloadAssetVo> getPrimaryDownloadAsset(String slug) {
        return getDownloadAsset(slug, null);
    }

    @Override
    public Mono<DownloadAssetVo> getDownloadAsset(String slug, String assetName) {
        return findDownloadAsset(slug, assetName)
            .flatMap(asset -> enforceDownloadAccess(asset, null, null)
                .then(resolveDownloadGrant(asset))
                .map(grant -> DownloadAssetVo.fromDownloadGate(asset, grant.getUrl())))
            .delayUntil(asset -> recordDownload(asset.getName()));
    }

    @Override
    public Mono<DownloadGrantVo> grantDownload(DownloadGrantRequest request,
        String remoteAddress) {
        if (request == null || request.slug() == null || request.slug().isBlank()) {
            return Mono.error(new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Software slug is required"));
        }
        return findDownloadAsset(request.slug(), request.assetName())
            .flatMap(asset -> enforceDownloadAccess(asset, request.captchaToken(), remoteAddress)
                .then(resolveDownloadGrant(asset)))
            .delayUntil(grant -> recordDownload(grant.getAssetName()));
    }

    @Override
    public Mono<DownloadSecurityVo> getDownloadSecurityOptions() {
        if (settingFetcher == null) {
            return Mono.just(downloadSecurityVo(new SoftwareSecurityOptions(), false));
        }
        return settingFetcher.fetch(SoftwareSecurityOptions.GROUP, SoftwareSecurityOptions.class)
            .defaultIfEmpty(new SoftwareSecurityOptions())
            .map(SoftwareSecurityOptions::normalized)
            .flatMap(options -> {
                if (!SoftwareSecurityOptions.PROVIDER_TURNSTILE.equalsIgnoreCase(
                        options.getCaptchaProvider())
                    || !hasText(options.getCaptchaSiteKey())
                    || !hasText(options.getCaptchaSecretName())) {
                    return Mono.just(downloadSecurityVo(options, false));
                }
                return client.fetch(Secret.class, options.getCaptchaSecretName())
                    .map(secret -> downloadSecurityVo(options,
                        hasText(secretValue(secret, options.getCaptchaSecretKey()))))
                    .defaultIfEmpty(downloadSecurityVo(options, false));
            })
            .onErrorReturn(downloadSecurityVo(new SoftwareSecurityOptions(), false));
    }

    private Mono<DownloadAsset> findDownloadAsset(String slug, String assetName) {
        return getPublishedAppBySlug(slug)
            .flatMap(app -> {
                if (hasText(assetName)) {
                    return findSelectedDownloadAsset(app, assetName);
                }
                return listReleasedVersionExtensions(app)
                    .next()
                    .flatMap(version -> listDownloadAssetExtensions(
                        app, List.of(version.getMetadata().getName())).next());
            })
            .switchIfEmpty(Mono.error(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Download asset not found")));
    }

    private Mono<DownloadAsset> findSelectedDownloadAsset(SoftwareApp app, String assetName) {
        var appName = app.getMetadata().getName();
        return client.fetch(DownloadAsset.class, assetName)
            .filter(asset -> asset.getMetadata() != null
                && Objects.equals(assetName, asset.getMetadata().getName())
                && !ExtensionUtil.isDeleted(asset)
                && asset.getSpec() != null
                && hasText(asset.getSpec().getVersionName())
                && Boolean.TRUE.equals(asset.getSpec().getEnabled())
                && Objects.equals(appName, asset.getSpec().getAppName()))
            .flatMap(asset -> client.fetch(SoftwareVersion.class, asset.getSpec().getVersionName())
                .filter(version -> version.getMetadata() != null
                    && Objects.equals(asset.getSpec().getVersionName(),
                        version.getMetadata().getName())
                    && !ExtensionUtil.isDeleted(version)
                    && version.getSpec() != null
                    && "released".equals(version.getSpec().getStatus())
                    && Objects.equals(appName, version.getSpec().getAppName()))
                .map(ignored -> asset));
    }

    private Mono<Void> enforceDownloadAccess(DownloadAsset asset, String captchaToken,
        String remoteAddress) {
        var spec = asset.getSpec();
        Mono<Void> loginCheck = spec != null && Boolean.TRUE.equals(spec.getRequiresLogin())
            ? currentUserName()
                .switchIfEmpty(Mono.error(() -> new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Download requires login")))
                .then()
            : Mono.empty();
        Mono<Void> captchaCheck = spec != null && Boolean.TRUE.equals(spec.getRequiresCaptcha())
            ? captchaVerifier.verify(captchaToken, remoteAddress)
                .flatMap(valid -> valid
                    ? Mono.empty()
                    : Mono.error(new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Download verification failed")))
            : Mono.empty();
        return loginCheck.then(captchaCheck);
    }

    private Mono<DownloadGrantVo> resolveDownloadGrant(DownloadAsset asset) {
        var spec = asset.getSpec();
        if (spec == null) {
            return Mono.error(new ResponseStatusException(
                HttpStatus.CONFLICT, "Download asset is not configured"));
        }
        var secretName = spec.getPasswordSecretName();
        var credentials = secretName == null || secretName.isBlank()
            ? Mono.just(ResolvedSecret.empty())
            : client.fetch(Secret.class, secretName)
                .map(SoftwarePublicQueryServiceImpl::resolvedSecret)
                .defaultIfEmpty(ResolvedSecret.empty());
        return credentials.flatMap(secret -> {
            var url = DownloadTargetPolicy.firstUsable(
                secret.url(), spec.getUrl(), spec.getStorageKey());
            if (url == null) {
                return Mono.error(new ResponseStatusException(
                    HttpStatus.CONFLICT, "Download asset has no usable URL"));
            }
            return Mono.just(DownloadGrantVo.builder()
                .assetName(asset.getMetadata().getName())
                .versionName(spec.getVersionName())
                .title(spec.getTitle())
                .url(url)
                .extractionCode(firstText(secret.extractionCode(), spec.getExtractionCode()))
                .password(secret.password())
                .expiresAt(Instant.now().plusSeconds(DOWNLOAD_GRANT_TTL_SECONDS))
                .build());
        });
    }

    private Mono<Void> recordDownload(String assetName) {
        if (assetName == null || assetName.isBlank()) {
            return Mono.empty();
        }
        return Mono.defer(() -> client.fetch(DownloadAsset.class, assetName))
            .flatMap(asset -> {
                var status = asset.getStatus();
                if (status == null) {
                    status = new DownloadAsset.Status();
                    asset.setStatus(status);
                }
                status.setDownloadCount(Math.max(0L,
                    status.getDownloadCount() == null ? 0L : status.getDownloadCount()) + 1);
                return client.update(asset);
            })
            // An optimistic-lock race is expected when multiple grants are consumed together.
            .retry(3)
            // Metrics must never prevent an already-authorized download.
            .onErrorResume(error -> Mono.empty())
            .then();
    }

    private static ResolvedSecret resolvedSecret(Secret secret) {
        return new ResolvedSecret(
            secretValue(secret, "url"),
            secretValue(secret, "extractionCode"),
            secretValue(secret, "password"));
    }

    private static String secretValue(Secret secret, String key) {
        var data = secret.getData();
        var value = data == null ? null : data.get(key);
        return value == null ? null : new String(value, StandardCharsets.UTF_8);
    }

    private static String firstText(String... values) {
        for (var value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static DownloadSecurityVo downloadSecurityVo(SoftwareSecurityOptions options,
        boolean configured) {
        return DownloadSecurityVo.builder()
            .captchaProvider(options.getCaptchaProvider())
            .captchaSiteKey(options.getCaptchaSiteKey())
            .captchaConfigured(configured)
            .build();
    }

    private record ResolvedSecret(String url, String extractionCode, String password) {
        static ResolvedSecret empty() {
            return new ResolvedSecret(null, null, null);
        }
    }

    @Override
    public Mono<ListResult<SoftwareCategoryVo>> listCategories(ListOptions options,
        PageRequest page) {
        return client.listBy(SoftwareCategory.class,
                requiredOptions(options, equal("spec.visible", true)), page)
            .flatMap(result -> Flux.fromIterable(result.getItems())
                .filter(SoftwarePublicQueryServiceImpl::isVisibleCategory)
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
            .flatMapMany(Flux::fromIterable)
            .filter(SoftwarePublicQueryServiceImpl::isVisibleCategory)
            .next()
            .map(SoftwareCategoryVo::from)
            .switchIfEmpty(Mono.error(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Software category not found")));
    }

    @Override
    public Mono<ListResult<SoftwareTagVo>> listTags(ListOptions options, PageRequest page) {
        return client.listBy(SoftwareTag.class,
                requiredOptions(options, equal("spec.visible", true)), page)
            .flatMap(result -> Flux.fromIterable(result.getItems())
                .filter(SoftwarePublicQueryServiceImpl::isVisibleTag)
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
            .flatMapMany(Flux::fromIterable)
            .filter(SoftwarePublicQueryServiceImpl::isVisibleTag)
            .next()
            .map(SoftwareTagVo::from)
            .switchIfEmpty(Mono.error(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Software tag not found")));
    }

    @Override
    public Mono<ListResult<SoftwareTopicVo>> listTopics(ListOptions options, PageRequest page) {
        return client.listBy(SoftwareTopic.class,
                requiredOptions(options, equal("spec.published", true)), page)
            .flatMap(result -> enrichTopics(result.getItems().stream()
                    .filter(SoftwarePublicQueryServiceImpl::isPublishedTopic)
                    .toList())
                .map(details -> details.stream().map(SoftwareTopicVo::from).toList())
                .map(items -> new ListResult<>(result.getPage(), result.getSize(),
                    result.getTotal(), items)));
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
            .flatMapMany(Flux::fromIterable)
            .filter(SoftwarePublicQueryServiceImpl::isPublishedApp)
            .next()
            .switchIfEmpty(Mono.error(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Software app not found")));
    }

    private Flux<SoftwareVersionVo> listVersions(SoftwareApp app) {
        return listReleasedVersionExtensions(app).map(SoftwareVersionVo::from);
    }

    private Mono<List<SoftwareAppVo>> listRelatedApps(SoftwareApp app) {
        var names = relatedNames(app, true);
        if (names.isEmpty()) {
            return Mono.just(List.of());
        }
        var options = ListOptions.builder()
            .andQuery(and(
                equal("spec.published", true),
                in("metadata.name", names.stream().map(name -> (Object) name).toList())))
            .build();
        return client.listAll(SoftwareApp.class, options, Sort.unsorted())
            .filter(SoftwarePublicQueryServiceImpl::isPublishedApp)
            .collectMap(related -> related.getMetadata().getName(), this::toPublicVo)
            .map(byName -> names.stream().map(byName::get).filter(Objects::nonNull).toList());
    }

    private Mono<List<SoftwareRelatedPostVo>> listRelatedPosts(SoftwareApp app) {
        var names = relatedNames(app, false);
        if (names.isEmpty()) {
            return Mono.just(List.of());
        }
        var options = ListOptions.builder()
            .andQuery(in("metadata.name", names.stream().map(name -> (Object) name).toList()))
            .build();
        return client.listAll(Post.class, options, Sort.unsorted())
            .filter(SoftwarePublicQueryServiceImpl::isPublicPost)
            .collectMap(post -> post.getMetadata().getName(), post -> {
                var excerpt = post.getStatus() == null ? null : post.getStatus().getExcerpt();
                return SoftwareRelatedPostVo.from(post, htmlSanitizer.sanitize(excerpt));
            })
            .map(byName -> names.stream().map(byName::get).filter(Objects::nonNull).toList());
    }

    private static List<String> relatedNames(SoftwareApp app, boolean apps) {
        if (app == null || app.getSpec() == null) {
            return List.of();
        }
        var values = apps ? app.getSpec().getRelatedAppNames()
            : app.getSpec().getRelatedPostNames();
        if (values == null) {
            return List.of();
        }
        return values.stream()
            .filter(name -> name != null && !name.isBlank())
            .distinct()
            .limit(MAX_PUBLIC_QUERY_SIZE)
            .toList();
    }

    private static boolean isPublicPost(Post post) {
        return post != null
            && post.getMetadata() != null
            && post.getSpec() != null
            && Boolean.TRUE.equals(post.getSpec().getPublish())
            && !Boolean.TRUE.equals(post.getSpec().getDeleted())
            && Post.isPublic(post.getSpec())
            && Post.isPublished(post.getMetadata());
    }

    private Flux<SoftwareVersion> listReleasedVersionExtensions(SoftwareApp app) {
        var options = ListOptions.builder()
            .andQuery(and(
                equal("spec.appName", app.getMetadata().getName()),
                equal("spec.status", "released")))
            .build();
        return client.listAll(SoftwareVersion.class, options, Sort.by(
            Sort.Order.desc("spec.releaseDate"),
            Sort.Order.desc("metadata.creationTimestamp")))
            .filter(version -> version != null && version.getSpec() != null
                && version.getMetadata() != null
                && hasText(version.getMetadata().getName())
                && !ExtensionUtil.isDeleted(version)
                && "released".equals(version.getSpec().getStatus())
                && app.getMetadata() != null
                && Objects.equals(app.getMetadata().getName(), version.getSpec().getAppName()));
    }

    private Flux<DownloadAssetVo> listDownloadAssets(SoftwareApp app) {
        return listDownloadAssetExtensions(app)
            .map(DownloadAssetVo::fromPublicList);
    }

    private Flux<DownloadAsset> listDownloadAssetExtensions(SoftwareApp app) {
        return listReleasedVersionExtensions(app)
            .map(version -> version.getMetadata().getName())
            .collectList()
            .flatMapMany(versionNames -> listDownloadAssetExtensions(app, versionNames));
    }

    private Flux<DownloadAsset> listDownloadAssetExtensions(SoftwareApp app,
        List<String> versionNames) {
        if (versionNames.isEmpty()) {
            return Flux.empty();
        }
        var options = ListOptions.builder()
            .andQuery(and(
                equal("spec.appName", app.getMetadata().getName()),
                equal("spec.enabled", true),
                in("spec.versionName", versionNames.stream().map(name -> (Object) name).toList())))
            .build();
        return client.listAll(DownloadAsset.class, options, Sort.by(
            Sort.Order.asc("spec.priority"),
            Sort.Order.asc("metadata.creationTimestamp")))
            .filter(asset -> asset != null && asset.getSpec() != null
                && asset.getMetadata() != null
                && hasText(asset.getMetadata().getName())
                && !ExtensionUtil.isDeleted(asset)
                && Boolean.TRUE.equals(asset.getSpec().getEnabled())
                && app.getMetadata() != null
                && Objects.equals(app.getMetadata().getName(), asset.getSpec().getAppName())
                && versionNames.contains(asset.getSpec().getVersionName()));
    }

    private Mono<List<SoftwareAppVo>> listHomeApps(ListOptions options, Sort sort, int size) {
        return client.listBy(SoftwareApp.class, options, PageRequestImpl.of(1, size, sort))
            .flatMap(result -> Flux.fromIterable(result.getItems())
                .filter(SoftwarePublicQueryServiceImpl::isPublishedApp)
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

    private Mono<List<SoftwareTopicDetailVo>> enrichTopics(List<SoftwareTopic> topics) {
        var appNames = topics.stream()
            .flatMap(topic -> topic.getSpec() == null || topic.getSpec().getItems() == null
                ? List.<SoftwareTopic.Item>of().stream()
                : topic.getSpec().getItems().stream())
            .map(SoftwareTopic.Item::getAppName)
            .filter(name -> name != null && !name.isBlank())
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (appNames.isEmpty()) {
            return Mono.just(topics.stream()
                .map(topic -> SoftwareTopicDetailVo.from(topic, List.of(), List.of()))
                .toList());
        }

        var options = ListOptions.builder()
            .andQuery(and(
                equal("spec.published", true),
                in("metadata.name", appNames.stream().map(name -> (Object) name).toList())))
            .build();
        return client.listAll(SoftwareApp.class, options, Sort.unsorted())
            .filter(SoftwarePublicQueryServiceImpl::isPublishedApp)
            .collectMap(app -> app.getMetadata().getName(), this::toPublicVo)
            .map(appsByName -> topics.stream()
                .map(topic -> SoftwareTopicDetailVo.from(topic,
                    appsForTopic(topic, appsByName), topicItems(topic, appsByName)))
                .toList());
    }

    private static List<SoftwareTopicItemVo> topicItems(SoftwareTopic topic,
        Map<String, SoftwareAppVo> appsByName) {
        if (topic.getSpec() == null || topic.getSpec().getItems() == null) {
            return List.of();
        }
        return topic.getSpec().getItems().stream()
            .sorted(Comparator.comparing(item -> item.getPriority() == null
                ? 0 : item.getPriority()))
            .map(item -> {
                var app = appsByName.get(item.getAppName());
                return app == null ? null : SoftwareTopicItemVo.from(item, app);
            })
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
                .filter(SoftwarePublicQueryServiceImpl::isVisibleCategory)
                .map(SoftwareCategoryVo::from)
                .collectList());
    }

    private static ListOptions publishedAppOptions(ListOptions options) {
        return requiredOptions(options, equal("spec.published", true));
    }

    private static ListOptions requiredOptions(ListOptions options,
        run.halo.app.extension.index.query.Condition condition) {
        return ListOptions.builder(options == null ? ListOptions.builder().build() : options)
            .andQuery(condition)
            .build();
    }

    private static SoftwareApp first(List<SoftwareApp> apps) {
        return apps.isEmpty() ? null : apps.get(0);
    }

    private static boolean isPublishedApp(SoftwareApp app) {
        return app != null && app.getMetadata() != null && app.getSpec() != null
            && !run.halo.app.extension.ExtensionUtil.isDeleted(app)
            && Boolean.TRUE.equals(app.getSpec().getPublished());
    }

    private static boolean isVisibleCategory(SoftwareCategory category) {
        return category != null && category.getMetadata() != null && category.getSpec() != null
            && !run.halo.app.extension.ExtensionUtil.isDeleted(category)
            && Boolean.TRUE.equals(category.getSpec().getVisible());
    }

    private static boolean isVisibleTag(SoftwareTag tag) {
        return tag != null && tag.getMetadata() != null && tag.getSpec() != null
            && !run.halo.app.extension.ExtensionUtil.isDeleted(tag)
            && Boolean.TRUE.equals(tag.getSpec().getVisible());
    }

    private static boolean isPublishedTopic(SoftwareTopic topic) {
        return topic != null && topic.getMetadata() != null && topic.getSpec() != null
            && !run.halo.app.extension.ExtensionUtil.isDeleted(topic)
            && Boolean.TRUE.equals(topic.getSpec().getPublished());
    }

    private SoftwareAppVo toPublicVo(SoftwareApp app) {
        var publicVo = SoftwareAppVo.from(app);
        return publicVo.toBuilder()
            .description(htmlSanitizer.sanitize(publicVo.getDescription()))
            .build();
    }

    private Mono<SoftwareAppVo> enrichTaxonomyNames(SoftwareAppVo app) {
        return Mono.zip(
                resolveCategory(app.getCategoryName()),
                resolveTagDisplayNames(app.getTagNames()))
            .map(names -> app.toBuilder()
                .categoryDisplayName(names.getT1().displayName())
                .categorySlug(names.getT1().slug())
                .tagDisplayNames(names.getT2())
                .build());
    }

    private Mono<CategoryLabels> resolveCategory(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            return Mono.just(new CategoryLabels("", "all"));
        }
        return client.fetch(SoftwareCategory.class, categoryName)
            .flatMap(category -> Mono.justOrEmpty(category.getSpec()))
            .filter(spec -> Boolean.TRUE.equals(spec.getVisible()))
            .map(spec -> new CategoryLabels(
                spec.getDisplayName() == null || spec.getDisplayName().isBlank()
                    ? categoryName : spec.getDisplayName(),
                spec.getSlug() == null || spec.getSlug().isBlank()
                    ? categoryName : spec.getSlug()))
            .defaultIfEmpty(new CategoryLabels("", "all"));
    }

    private Mono<List<String>> resolveTagDisplayNames(List<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) {
            return Mono.just(List.of());
        }
        var validTagNames = tagNames.stream()
            .filter(tagName -> tagName != null && !tagName.isBlank())
            .toList();
        if (validTagNames.isEmpty()) {
            return Mono.just(List.of());
        }
        var options = ListOptions.builder()
            .andQuery(and(
                equal("spec.visible", true),
                in("metadata.name", validTagNames.stream()
                    .map(name -> (Object) name).toList())))
            .build();
        return client.listAll(SoftwareTag.class, options, Sort.unsorted())
            .collectMap(tag -> tag.getMetadata().getName(), tag -> {
                var spec = tag.getSpec();
                return spec == null || spec.getDisplayName() == null
                    || spec.getDisplayName().isBlank()
                    ? tag.getMetadata().getName() : spec.getDisplayName();
            })
            .map(byName -> validTagNames.stream()
                .map(name -> byName.getOrDefault(name, name))
                .toList());
    }

    private Mono<String> currentUserName() {
        return ReactiveSecurityContextHolder.getContext()
            .flatMap(context -> Mono.justOrEmpty(context.getAuthentication()))
            .filter(authentication -> authentication.isAuthenticated())
            .flatMap(authentication -> Mono.justOrEmpty(authentication.getName()))
            .filter(name -> !AnonymousUserConst.isAnonymousUser(name));
    }

    private record CategoryLabels(String displayName, String slug) {
    }
}
