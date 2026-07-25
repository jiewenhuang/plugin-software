package run.jiewen.software.finders.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.PageRequest;
import run.halo.app.extension.ReactiveExtensionClient;
import run.jiewen.software.ai.SoftwareHtmlSanitizer;
import run.jiewen.software.extension.DownloadAsset;
import run.jiewen.software.extension.SoftwareApp;
import run.jiewen.software.extension.SoftwareCategory;
import run.jiewen.software.extension.SoftwareTag;
import run.jiewen.software.extension.SoftwareTopic;
import run.jiewen.software.extension.SoftwareVersion;
import run.jiewen.software.vo.DownloadAssetVo;
import run.jiewen.software.vo.SoftwareAppVo;

@ExtendWith(MockitoExtension.class)
class SoftwarePublicQueryServiceImplTest {

    @Mock
    ReactiveExtensionClient client;

    @Spy
    SoftwareHtmlSanitizer htmlSanitizer = new SoftwareHtmlSanitizer();

    @InjectMocks
    SoftwarePublicQueryServiceImpl service;

    @Test
    void getAppBySlugQueriesSpecSlugAndReturnsPublicDto() {
        when(client.listBy(eq(SoftwareApp.class), any(ListOptions.class), any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(1, 1, 1, List.of(app()))));

        StepVerifier.create(service.getAppBySlug("cleanmymac-x"))
            .assertNext(app -> {
                assertThat(app.getName()).isEqualTo("software-generated-name");
                assertThat(app.getSlug()).isEqualTo("cleanmymac-x");
                assertThat(app.getDisplayName()).isEqualTo("CleanMyMac X");
                assertThat(app.getPermalink()).isEqualTo("/soft/cleanmymac-x");
                assertThat(app.getScreenshots()).containsExactly("/upload/screen-1.png",
                    "/upload/screen-2.png");
                assertThat(app.getLatestVersion()).isEqualTo("4.15.0");
                assertThat(app.getDownloadCount()).isEqualTo(42L);
            })
            .verifyComplete();

        var optionsCaptor = ArgumentCaptor.forClass(ListOptions.class);
        verify(client).listBy(eq(SoftwareApp.class), optionsCaptor.capture(),
            any(PageRequest.class));
        assertThat(optionsCaptor.getValue().toString()).contains("spec.slug", "cleanmymac-x");
    }

    @Test
    void sanitizesRichTextReturnedByPublicQueries() {
        var app = app();
        app.getSpec().setDescription(
            "<p>Safe copy</p><script>alert(1)</script><a href=\"javascript:alert(1)\">link</a>");
        when(client.listBy(eq(SoftwareApp.class), any(ListOptions.class), any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(1, 1, 1, List.of(app))));

        StepVerifier.create(service.getAppBySlug("cleanmymac-x"))
            .assertNext(result -> {
                assertThat(result.getDescription()).contains("<p>Safe copy</p>");
                assertThat(result.getDescription()).doesNotContain("script", "javascript:");
            })
            .verifyComplete();
    }

    @Test
    void publicAppDtoDoesNotExposeDownloadSecrets() {
        assertThat(List.of(SoftwareAppVo.class.getDeclaredFields())
            .stream()
            .map(Field::getName))
            .doesNotContain("url", "storageKey", "extractionCode", "passwordSecretName",
                "downloadAssets");
    }

    @Test
    void getAppDetailBySlugIncludesVersionsAndSafeDownloadAssets() {
        when(client.listBy(eq(SoftwareApp.class), any(ListOptions.class), any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(1, 1, 1, List.of(app()))));
        when(client.listAll(eq(SoftwareVersion.class), any(ListOptions.class), any()))
            .thenReturn(reactor.core.publisher.Flux.just(version()));
        when(client.listAll(eq(DownloadAsset.class), any(ListOptions.class), any()))
            .thenReturn(reactor.core.publisher.Flux.just(downloadAsset()));
        when(client.fetch(SoftwareCategory.class, "system-tools"))
            .thenReturn(Mono.just(category("系统工具")));
        when(client.fetch(SoftwareTag.class, "cleaner"))
            .thenReturn(Mono.just(tag("系统清理")));
        when(client.fetch(SoftwareTag.class, "utility"))
            .thenReturn(Mono.empty());

        StepVerifier.create(service.getAppDetailBySlug("cleanmymac-x"))
            .assertNext(detail -> {
                assertThat(detail.getName()).isEqualTo("software-generated-name");
                assertThat(detail.getScreenshots()).containsExactly("/upload/screen-1.png",
                    "/upload/screen-2.png");
                assertThat(detail.getCategoryName()).isEqualTo("system-tools");
                assertThat(detail.getCategoryDisplayName()).isEqualTo("系统工具");
                assertThat(detail.getCategorySlug()).isEqualTo("system-tools");
                assertThat(detail.getTagNames()).containsExactly("cleaner", "utility");
                assertThat(detail.getTagDisplayNames()).containsExactly("系统清理", "utility");
                assertThat(detail.getDownloadCountText()).isEqualTo("42");
                assertThat(detail.getLikeCountText()).isEqualTo("8");
                assertThat(detail.getCommentCountText()).isEqualTo("3");
                assertThat(detail.getSizeText()).isEqualTo("100 MB");
                assertThat(detail.getSizeValue()).isEqualTo("100");
                assertThat(detail.getSizeUnit()).isEqualTo("MB");
                assertThat(detail.getLocaleValue()).isEqualTo("中文");
                assertThat(detail.getLocaleUnit()).isEqualTo("ZH");
                assertThat(detail.getMinOsDisplay()).isEqualTo(">=13.0");
                assertThat(detail.getLatestReleaseDateText()).isEqualTo("2026-06-01");
                assertThat(detail.getLatestReleaseDateShort()).isEqualTo("06-01");
                assertThat(detail.getLatestReleaseWeekday()).isEqualTo("星期一");
                assertThat(detail.getVersions()).hasSize(1);
                assertThat(detail.getVersions().get(0).getVersion()).isEqualTo("4.15.0");
                assertThat(detail.getVersions().get(0).getSizeText()).isEqualTo("100 MB");
                assertThat(detail.getVersions().get(0).getLocaleLabel()).isEqualTo("中文");
                assertThat(detail.getVersions().get(0).getMinOsDisplay())
                    .isEqualTo("macOS 13.0 或更高版本");
                assertThat(detail.getVersions().get(0).getReleaseDateText())
                    .isEqualTo("2026-06-01");
                assertThat(detail.getDownloadAssets()).hasSize(1);
                DownloadAssetVo asset = detail.getDownloadAssets().get(0);
                assertThat(asset.getName()).isEqualTo("asset-1");
                assertThat(asset.getTitle()).isEqualTo("Universal DMG");
                assertThat(asset.getUrl()).isNull();
                assertThat(asset.getRequiresLogin()).isFalse();
                assertThat(asset.getRequiresCaptcha()).isFalse();
            })
            .verifyComplete();
    }

    @Test
    void getHomeReturnsAllSourceTabsTopicsAndCategories() {
        when(client.listBy(eq(SoftwareApp.class), any(ListOptions.class), any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(1, 12, 1, List.of(app()))))
            .thenReturn(Mono.just(new ListResult<>(1, 12, 1, List.of(app()))))
            .thenReturn(Mono.just(new ListResult<>(1, 12, 23, List.of(app()))))
            .thenReturn(Mono.just(new ListResult<>(1, 12, 1, List.of(app()))));
        when(client.listBy(eq(SoftwareTopic.class), any(ListOptions.class), any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(1, 4, 1, List.of(topic()))));
        when(client.listBy(eq(SoftwareCategory.class), any(ListOptions.class),
            any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(1, 100, 1, List.of(category("系统工具")))));

        StepVerifier.create(service.getHome())
            .assertNext(home -> {
                assertThat(home.getLatestApps()).extracting(SoftwareAppVo::getSlug)
                    .containsExactly("cleanmymac-x");
                assertThat(home.getPopularApps()).hasSize(1);
                assertThat(home.getRecommendedApps()).hasSize(1);
                assertThat(home.getRecommendedTotal()).isEqualTo(23);
                assertThat(home.getCommentedApps()).hasSize(1);
                assertThat(home.getTopics()).extracting("slug").containsExactly("novice");
                assertThat(home.getCategories()).extracting("slug")
                    .containsExactly("system-tools");
            })
            .verifyComplete();
    }

    @Test
    void recommendedAppsExposesAThemeFriendlyResult() {
        when(client.listBy(eq(SoftwareApp.class), any(ListOptions.class), any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(1, 8, 23, List.of(app()))));

        StepVerifier.create(service.recommendedApps(8))
            .assertNext(apps -> {
                assertThat(apps.getTotal()).isEqualTo(23);
                assertThat(apps.getItems()).extracting(SoftwareAppVo::getSlug)
                    .containsExactly("cleanmymac-x");
            })
            .verifyComplete();

        var optionsCaptor = ArgumentCaptor.forClass(ListOptions.class);
        verify(client).listBy(eq(SoftwareApp.class), optionsCaptor.capture(),
            any(PageRequest.class));
        assertThat(optionsCaptor.getValue().toString())
            .contains("spec.published", "spec.recommended");
    }

    @Test
    void publishedTopicsExposesAThemeFriendlyList() {
        when(client.listBy(eq(SoftwareTopic.class), any(ListOptions.class), any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(1, 100, 1, List.of(topic()))));

        StepVerifier.create(service.publishedTopics())
            .assertNext(topics -> assertThat(topics)
                .extracting("slug")
                .containsExactly("novice"))
            .verifyComplete();

        var optionsCaptor = ArgumentCaptor.forClass(ListOptions.class);
        verify(client).listBy(eq(SoftwareTopic.class), optionsCaptor.capture(),
            any(PageRequest.class));
        assertThat(optionsCaptor.getValue().toString()).contains("spec.published");
    }

    @Test
    void topicDetailsLoadsTopicsAndAppsInTwoBatchQueries() {
        when(client.listAll(eq(SoftwareTopic.class), any(ListOptions.class), any()))
            .thenReturn(Flux.just(topicWithApp()));
        when(client.listAll(eq(SoftwareApp.class), any(ListOptions.class), any()))
            .thenReturn(Flux.just(app()));

        StepVerifier.create(service.topicDetails(List.of("novice")))
            .assertNext(topics -> {
                assertThat(topics).hasSize(1);
                assertThat(topics.get(0).getSlug()).isEqualTo("novice");
                assertThat(topics.get(0).getApps())
                    .extracting(SoftwareAppVo::getSlug)
                    .containsExactly("cleanmymac-x");
            })
            .verifyComplete();

        verify(client, never()).fetch(eq(SoftwareApp.class), anyString());
    }

    @Test
    void topicDetailsPreservesRequestedTopicAndConfiguredAppOrder() {
        var firstApp = app();
        var secondApp = app();
        secondApp.getMetadata().setName("software-second");
        secondApp.getSpec().setSlug("alfred");
        secondApp.getSpec().setDisplayName("Alfred");

        var novice = topicWithApp();
        var power = topic();
        power.getMetadata().setName("topic-power");
        power.getSpec().setSlug("power");
        power.getSpec().setTitle("效率工具");
        var lowerPriority = new SoftwareTopic.Item();
        lowerPriority.setAppName("software-generated-name");
        lowerPriority.setPriority(20);
        var higherPriority = new SoftwareTopic.Item();
        higherPriority.setAppName("software-second");
        higherPriority.setPriority(10);
        power.getSpec().setItems(List.of(lowerPriority, higherPriority));

        when(client.listAll(eq(SoftwareTopic.class), any(ListOptions.class), any()))
            .thenReturn(Flux.just(novice, power));
        when(client.listAll(eq(SoftwareApp.class), any(ListOptions.class), any()))
            .thenReturn(Flux.just(firstApp, secondApp));

        StepVerifier.create(service.topicDetails(List.of("power", "novice")))
            .assertNext(topics -> {
                assertThat(topics).extracting("slug").containsExactly("power", "novice");
                assertThat(topics.get(0).getApps())
                    .extracting(SoftwareAppVo::getSlug)
                    .containsExactly("alfred", "cleanmymac-x");
            })
            .verifyComplete();
    }

    @Test
    void topicDetailBySlugReusesBatchQueriesAndKeepsNotFoundSemantics() {
        when(client.listAll(eq(SoftwareTopic.class), any(ListOptions.class), any()))
            .thenReturn(Flux.just(topicWithApp()));
        when(client.listAll(eq(SoftwareApp.class), any(ListOptions.class), any()))
            .thenReturn(Flux.just(app()));

        StepVerifier.create(service.getTopicDetailBySlug("novice"))
            .assertNext(topic -> assertThat(topic.getApps())
                .extracting(SoftwareAppVo::getSlug)
                .containsExactly("cleanmymac-x"))
            .verifyComplete();

        verify(client, never()).fetch(eq(SoftwareApp.class), anyString());
    }

    @Test
    void topicDetailBySlugReturnsNotFoundWhenNoPublishedTopicMatches() {
        when(client.listAll(eq(SoftwareTopic.class), any(ListOptions.class), any()))
            .thenReturn(Flux.empty());

        StepVerifier.create(service.getTopicDetailBySlug("missing"))
            .expectErrorSatisfies(error -> assertThat(error)
                .isInstanceOfSatisfying(ResponseStatusException.class, statusError ->
                    assertThat(statusError.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)))
            .verify();
    }

    @Test
    void resolvesVisibleCategoryAndTagBySlug() {
        when(client.listBy(eq(SoftwareCategory.class), any(ListOptions.class),
            any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(1, 1, 1, List.of(category("系统工具")))));
        when(client.listBy(eq(SoftwareTag.class), any(ListOptions.class), any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(1, 1, 1, List.of(tag("系统清理")))));

        StepVerifier.create(service.getCategoryBySlug("system-tools"))
            .assertNext(category -> {
                assertThat(category.getName()).isEqualTo("system-tools");
                assertThat(category.getSlug()).isEqualTo("system-tools");
                assertThat(category.getDisplayName()).isEqualTo("系统工具");
                assertThat(category.getChildren()).isEmpty();
            })
            .verifyComplete();

        StepVerifier.create(service.getTagBySlug("cleaner"))
            .assertNext(tag -> {
                assertThat(tag.getName()).isEqualTo("cleaner");
                assertThat(tag.getSlug()).isEqualTo("cleaner");
                assertThat(tag.getDisplayName()).isEqualTo("系统清理");
            })
            .verifyComplete();
    }

    @Test
    void loginRequiredDownloadReturnsUnauthorizedForAnonymousUser() {
        when(client.listBy(eq(SoftwareApp.class), any(ListOptions.class), any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(1, 1, 1, List.of(app()))));
        when(client.listAll(eq(DownloadAsset.class), any(ListOptions.class), any()))
            .thenReturn(reactor.core.publisher.Flux.just(loginRequiredDownloadAsset()));

        StepVerifier.create(service.getPrimaryDownloadAsset("cleanmymac-x"))
            .expectErrorSatisfies(error -> assertThat(error)
                .isInstanceOfSatisfying(ResponseStatusException.class, statusError -> {
                    assertThat(statusError.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(statusError.getReason()).isEqualTo("Download requires login");
                }))
            .verify();
    }

    @Test
    void loginRequiredDownloadReturnsUrlForAuthenticatedUser() {
        when(client.listBy(eq(SoftwareApp.class), any(ListOptions.class), any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(1, 1, 1, List.of(app()))));
        when(client.listAll(eq(DownloadAsset.class), any(ListOptions.class), any()))
            .thenReturn(reactor.core.publisher.Flux.just(loginRequiredDownloadAsset()));

        var authentication = new UsernamePasswordAuthenticationToken("member", "n/a");

        StepVerifier.create(service.getPrimaryDownloadAsset("cleanmymac-x")
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)))
            .assertNext(asset -> {
                assertThat(asset.getName()).isEqualTo("asset-1");
                assertThat(asset.getUrl()).isEqualTo("https://example.com/cleanmymac.dmg");
            })
            .verifyComplete();
    }

    @Test
    void resolvesPrimaryDownloadAssetBySlug() {
        when(client.listBy(eq(SoftwareApp.class), any(ListOptions.class), any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(1, 1, 1, List.of(app()))));
        when(client.listAll(eq(DownloadAsset.class), any(ListOptions.class), any()))
            .thenReturn(reactor.core.publisher.Flux.just(downloadAsset()));

        StepVerifier.create(service.getPrimaryDownloadAsset("cleanmymac-x"))
            .assertNext(asset -> {
                assertThat(asset.getName()).isEqualTo("asset-1");
                assertThat(asset.getUrl()).isEqualTo("https://example.com/cleanmymac.dmg");
            })
            .verifyComplete();
    }

    @Test
    void resolvesSelectedDownloadAssetByName() {
        var mirror = downloadAsset();
        mirror.getMetadata().setName("asset-2");
        mirror.getSpec().setTitle("Mirror DMG");
        mirror.getSpec().setUrl("https://mirror.example.com/cleanmymac.dmg");
        when(client.listBy(eq(SoftwareApp.class), any(ListOptions.class), any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(1, 1, 1, List.of(app()))));
        when(client.listAll(eq(DownloadAsset.class), any(ListOptions.class), any()))
            .thenReturn(reactor.core.publisher.Flux.just(downloadAsset(), mirror));

        StepVerifier.create(service.getDownloadAsset("cleanmymac-x", "asset-2"))
            .assertNext(asset -> {
                assertThat(asset.getName()).isEqualTo("asset-2");
                assertThat(asset.getTitle()).isEqualTo("Mirror DMG");
                assertThat(asset.getUrl()).isEqualTo("https://mirror.example.com/cleanmymac.dmg");
            })
            .verifyComplete();
    }

    private static SoftwareApp app() {
        var metadata = new Metadata();
        metadata.setName("software-generated-name");

        var spec = new SoftwareApp.Spec();
        spec.setSlug("cleanmymac-x");
        spec.setDisplayName("CleanMyMac X");
        spec.setSubtitle("Mac cleaner");
        spec.setIcon("/upload/icon.png");
        spec.setCover("/upload/cover.png");
        spec.setScreenshots(List.of("/upload/screen-1.png", "/upload/screen-2.png"));
        spec.setSummary("Clean, optimize and maintain your Mac.");
        spec.setPublished(true);
        spec.setRecommended(true);
        spec.setCategoryName("system-tools");
        spec.setTagNames(List.of("cleaner", "utility"));
        spec.setLocale("zh-CN");

        var status = new SoftwareApp.Status();
        status.setLatestVersion("4.15.0");
        status.setLatestReleaseDate(LocalDate.of(2026, 6, 1));
        status.setMinOsVersion("13.0");
        status.setSizeBytes(100L * 1024 * 1024);
        status.setDownloadCount(42L);
        status.setLikeCount(8L);
        status.setCommentCount(3L);

        var app = new SoftwareApp();
        app.setMetadata(metadata);
        app.setSpec(spec);
        app.setStatus(status);
        return app;
    }

    private static SoftwareVersion version() {
        var metadata = new Metadata();
        metadata.setName("version-1");

        var spec = new SoftwareVersion.Spec();
        spec.setAppName("software-generated-name");
        spec.setVersion("4.15.0");
        spec.setReleaseDate(LocalDate.of(2026, 6, 1));
        spec.setSizeBytes(100L * 1024 * 1024);
        spec.setLocale("zh-CN");
        spec.setMinOsVersion("13.0");
        spec.setStatus("released");

        var version = new SoftwareVersion();
        version.setMetadata(metadata);
        version.setSpec(spec);
        return version;
    }

    private static SoftwareCategory category(String displayName) {
        var spec = new SoftwareCategory.Spec();
        spec.setSlug("system-tools");
        spec.setDisplayName(displayName);

        var category = new SoftwareCategory();
        category.setMetadata(new Metadata());
        category.getMetadata().setName("system-tools");
        category.setSpec(spec);
        return category;
    }

    private static SoftwareTag tag(String displayName) {
        var spec = new SoftwareTag.Spec();
        spec.setSlug("cleaner");
        spec.setDisplayName(displayName);

        var tag = new SoftwareTag();
        tag.setMetadata(new Metadata());
        tag.getMetadata().setName("cleaner");
        tag.setSpec(spec);
        return tag;
    }

    private static SoftwareTopic topic() {
        var spec = new SoftwareTopic.Spec();
        spec.setSlug("novice");
        spec.setTitle("新人必备");
        spec.setPublished(true);
        spec.setFeatured(true);

        var topic = new SoftwareTopic();
        topic.setMetadata(new Metadata());
        topic.getMetadata().setName("topic-novice");
        topic.setSpec(spec);
        return topic;
    }

    private static SoftwareTopic topicWithApp() {
        var topic = topic();
        var item = new SoftwareTopic.Item();
        item.setAppName("software-generated-name");
        item.setPriority(0);
        topic.getSpec().setItems(List.of(item));
        return topic;
    }

    private static DownloadAsset downloadAsset() {
        var metadata = new Metadata();
        metadata.setName("asset-1");

        var spec = new DownloadAsset.Spec();
        spec.setAppName("software-generated-name");
        spec.setVersionName("version-1");
        spec.setTitle("Universal DMG");
        spec.setType("dmg");
        spec.setProvider("external");
        spec.setUrl("https://example.com/cleanmymac.dmg");
        spec.setStorageKey("secret-storage-key");
        spec.setExtractionCode("secret-code");
        spec.setPasswordSecretName("secret-name");
        spec.setEnabled(true);
        spec.setRequiresLogin(false);
        spec.setRequiresCaptcha(false);
        spec.setPriority(0);

        var asset = new DownloadAsset();
        asset.setMetadata(metadata);
        asset.setSpec(spec);
        return asset;
    }

    private static DownloadAsset protectedDownloadAsset() {
        var asset = downloadAsset();
        asset.getSpec().setRequiresLogin(true);
        asset.getSpec().setRequiresCaptcha(true);
        return asset;
    }

    private static DownloadAsset loginRequiredDownloadAsset() {
        var asset = downloadAsset();
        asset.getSpec().setRequiresLogin(true);
        return asset;
    }
}
