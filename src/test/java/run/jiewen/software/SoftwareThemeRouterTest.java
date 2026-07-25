package run.jiewen.software;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.result.view.View;
import org.springframework.web.reactive.result.view.ViewResolver;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListResult;
import run.jiewen.software.finders.SoftwarePublicQueryService;
import run.jiewen.software.vo.SoftwareAppDetailVo;
import run.jiewen.software.vo.SoftwareAppVo;
import run.jiewen.software.vo.SoftwareCategoryVo;
import run.jiewen.software.vo.SoftwareTagVo;
import run.jiewen.software.vo.SoftwareTopicDetailVo;
import run.jiewen.software.vo.SoftwareTopicVo;

class SoftwareThemeRouterTest {

    private final SoftwarePublicQueryService service = mock(SoftwarePublicQueryService.class);
    private WebTestClient client;

    @BeforeEach
    void setUp() {
        var router = new SoftwareThemeRouter(service);
        var strategies = HandlerStrategies.builder()
            .viewResolver(new StubViewResolver())
            .build();
        client = WebTestClient.bindToRouterFunction(router.softwareThemeRoutes())
            .handlerStrategies(strategies)
            .configureClient()
            .build();
    }

    @Test
    void rendersPublishedSoftwareDetail() {
        when(service.getAppDetailBySlug("cleanmymac-x"))
            .thenReturn(Mono.just(detail()));

        client.get()
            .uri("/soft/cleanmymac-x")
            .exchange()
            .expectStatus().isOk();

        verify(service).getAppDetailBySlug("cleanmymac-x");
    }

    @Test
    void keepsNotFoundStatusFromPublicQueryService() {
        when(service.getAppDetailBySlug("missing"))
            .thenReturn(Mono.error(new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Software app not found")));

        client.get()
            .uri("/soft/missing")
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    void redirectsSoftwareRoot() {
        client.get().uri("/soft")
            .exchange()
            .expectStatus().isPermanentRedirect()
            .expectHeader().location("/soft/all/p1");
    }

    @Test
    void rendersSoftwareListCategoryTagAndQueryVariants() {
        stubListDependencies();
        when(service.getCategoryBySlug("system"))
            .thenReturn(Mono.just(category()));
        when(service.getTagBySlug("utility"))
            .thenReturn(Mono.just(tag()));

        client.get().uri("/soft/all/p1?sort=%E4%B8%8B%E8%BD%BD&keyword=clean")
            .exchange()
            .expectStatus().isOk();
        client.get().uri("/soft/system/p1")
            .exchange()
            .expectStatus().isOk();
        client.get().uri("/soft/tag/utility/p1")
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    void rejectsInvalidPageNumbersAsNotFound() {
        client.get().uri("/soft/all/p0")
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    void rendersSpecialDetail() {
        var detail = SoftwareTopicDetailVo.builder()
            .name("topic-novice")
            .slug("novice")
            .title("新人必备")
            .apps(List.of(app()))
            .build();
        when(service.publishedTopics()).thenReturn(Mono.just(List.of(topic())));
        when(service.getTopicDetailBySlug("novice")).thenReturn(Mono.just(detail));

        client.get().uri("/special/novice").exchange().expectStatus().isOk();
    }

    @Test
    void leavesHaloOwnedThemeRoutesUnmatched() {
        for (var path : List.of(
            "/article",
            "/article/all/p1",
            "/special",
            "/perfect",
            "/must",
            "/recommend",
            "/genuine",
            "/feedback",
            "/reward",
            "/about",
            "/privacy")) {
            client.get().uri(path).exchange().expectStatus().isNotFound();
        }
    }

    @Test
    void leavesUnknownRoutesUnmatched() {
        client.get().uri("/not-a-theme-route")
            .exchange()
            .expectStatus().isNotFound();
    }

    private void stubListDependencies() {
        when(service.listApps(any(), any()))
            .thenReturn(Mono.just(new ListResult<>(1, 20, 1, List.of(app()))));
        when(service.listCategories(any(), any()))
            .thenReturn(Mono.just(new ListResult<>(1, 100, 1, List.of(category()))));
        when(service.listTags(any(), any()))
            .thenReturn(Mono.just(new ListResult<>(1, 100, 1, List.of(tag()))));
    }

    private static SoftwareAppVo app() {
        return SoftwareAppVo.builder()
            .name("software-generated-name")
            .slug("cleanmymac-x")
            .displayName("CleanMyMac X")
            .permalink("/soft/cleanmymac-x")
            .build();
    }

    private static SoftwareCategoryVo category() {
        return SoftwareCategoryVo.builder()
            .name("category-system")
            .slug("system")
            .displayName("系统工具")
            .children(List.of())
            .build();
    }

    private static SoftwareTagVo tag() {
        return SoftwareTagVo.builder()
            .name("tag-utility")
            .slug("utility")
            .displayName("实用工具")
            .build();
    }

    private static SoftwareTopicVo topic() {
        return SoftwareTopicVo.builder()
            .name("topic-novice")
            .slug("novice")
            .title("新人必备")
            .appCount(1)
            .build();
    }

    private static SoftwareAppDetailVo detail() {
        return SoftwareAppDetailVo.from(app(), List.of(), List.of());
    }

    private static class StubViewResolver implements ViewResolver {

        @Override
        public Mono<View> resolveViewName(String viewName, Locale locale) {
            return Mono.just(new StubView());
        }
    }

    private static class StubView implements View {

        @Override
        public List<MediaType> getSupportedMediaTypes() {
            return List.of(MediaType.TEXT_HTML);
        }

        @Override
        public Mono<Void> render(java.util.Map<String, ?> model, MediaType contentType,
            org.springframework.web.server.ServerWebExchange exchange) {
            return Mono.empty();
        }
    }
}
