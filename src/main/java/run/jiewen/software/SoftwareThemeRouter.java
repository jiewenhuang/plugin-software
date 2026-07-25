package run.jiewen.software;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static run.halo.app.extension.index.query.Queries.and;
import static run.halo.app.extension.index.query.Queries.equal;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.PageRequestImpl;
import run.jiewen.software.finders.SoftwarePublicQueryService;
import run.jiewen.software.vo.SoftwareCategoryVo;
import run.jiewen.software.vo.SoftwareTagVo;

@Component
public class SoftwareThemeRouter {

    static final String DETAIL_TEMPLATE_ID = "software-detail";
    static final String LIST_TEMPLATE_ID = "software-list";
    static final String SPECIAL_DETAIL_TEMPLATE_ID = "software-special";

    private static final int LIST_PAGE_SIZE = 20;
    private static final int RESOURCE_PAGE_SIZE = 100;

    private final SoftwarePublicQueryService softwarePublicQueryService;

    public SoftwareThemeRouter(SoftwarePublicQueryService softwarePublicQueryService) {
        this.softwarePublicQueryService = softwarePublicQueryService;
    }

    @org.springframework.context.annotation.Bean
    RouterFunction<ServerResponse> softwareThemeRoutes() {
        return route()
            .GET("/soft", request -> redirect("/soft/all/p1"))
            .GET("/soft/all/p{page}", request -> renderSoftwareList(
                request, parsePage(request), null, null))
            .GET("/soft/tag/{slug}/p{page}", request ->
                softwarePublicQueryService.getTagBySlug(request.pathVariable("slug"))
                    .flatMap(tag -> renderSoftwareList(
                        request, parsePage(request), null, tag)))
            .GET("/soft/{category}/p{page}", request ->
                softwarePublicQueryService.getCategoryBySlug(request.pathVariable("category"))
                    .flatMap(category -> renderSoftwareList(
                        request, parsePage(request), category, null)))
            .GET("/special/{slug}", this::renderSpecialDetail)
            .GET("/soft/{slug}", this::renderSoftwareDetail)
            .build();
    }

    private Mono<ServerResponse> renderSoftwareDetail(ServerRequest request) {
        var slug = request.pathVariable("slug");
        return softwarePublicQueryService.getAppDetailBySlug(slug)
            .flatMap(software -> {
                var model = baseModel(DETAIL_TEMPLATE_ID);
                model.put("software", software);
                return ServerResponse.ok().render(DETAIL_TEMPLATE_ID, model);
            });
    }

    private Mono<ServerResponse> renderSoftwareList(ServerRequest request, int page,
        SoftwareCategoryVo category, SoftwareTagVo tag) {
        var query = new SoftwarePublicQuery(request.exchange());
        var options = query.toListOptions();
        if (tag != null) {
            options.setFieldSelector(options.getFieldSelector()
                .andQuery(equal("spec.tagNames", tag.getName())));
        }
        var selectedSort = normalizeSort(request.queryParam("sort").orElse(null));
        if ("recommended".equals(selectedSort)) {
            options.setFieldSelector(options.getFieldSelector()
                .andQuery(equal("spec.recommended", true)));
        }
        var pageRequest = PageRequestImpl.of(page, LIST_PAGE_SIZE, listSort(selectedSort));

        var visibleCategories = ListOptions.builder()
            .andQuery(equal("spec.visible", true))
            .build();
        var visibleTags = ListOptions.builder()
            .andQuery(equal("spec.visible", true))
            .build();
        var recommendedApps = ListOptions.builder()
            .andQuery(and(
                equal("spec.published", true),
                equal("spec.recommended", true)))
            .build();

        return softwarePublicQueryService.listCategories(visibleCategories,
                PageRequestImpl.of(1, RESOURCE_PAGE_SIZE,
                    Sort.by("spec.priority").ascending()))
            .flatMap(categories -> {
                if (category != null) {
                    options.setFieldSelector(options.getFieldSelector()
                        .andQuery(SoftwareCategoryQuery.condition(
                            SoftwareCategoryQuery.namesFor(category, categories.getItems()))));
                }
                return Mono.zip(
                    softwarePublicQueryService.listApps(options, pageRequest),
                    softwarePublicQueryService.listTags(visibleTags,
                        PageRequestImpl.of(1, RESOURCE_PAGE_SIZE,
                            Sort.by("spec.priority").ascending())),
                    softwarePublicQueryService.listApps(recommendedApps,
                        PageRequestImpl.of(1, 6,
                            stableDescending("status.latestReleaseDate"))))
                    .flatMap(result -> {
                var model = baseModel(LIST_TEMPLATE_ID);
                model.put("softwareApps", result.getT1());
                model.put("softwareCategories", categoryTree(categories.getItems()));
                model.put("softwareTags", result.getT2().getItems());
                model.put("softwareRecommendations", result.getT3().getItems());
                model.put("softwareActiveCategory", category);
                model.put("softwareActiveTag", tag);
                model.put("softwareListTitle", listTitle(category, tag));
                model.put("softwarePageBase", pageBase(category, tag));
                model.put("softwareKeyword", query.getKeyword());
                model.put("softwareSort", selectedSort);
                model.put("softwareCurrentPage", result.getT1().getPage());
                model.put("softwareTotalPages", Math.max(1,
                    (int) Math.ceil((double) result.getT1().getTotal()
                        / Math.max(1, result.getT1().getSize()))));
                model.put("softwareQuerySuffix",
                    querySuffix(selectedSort, query.getKeyword()));
                model.put("softwareKeywordQueryPart",
                    keywordQueryPart(query.getKeyword()));
                model.put("softwareActiveParentName", activeParentName(category));
                return ServerResponse.ok().render(LIST_TEMPLATE_ID, model);
            });
            });
    }

    private Mono<ServerResponse> renderSpecialDetail(ServerRequest request) {
        return Mono.zip(
                softwarePublicQueryService.getTopicDetailBySlug(
                    request.pathVariable("slug")),
                softwarePublicQueryService.publishedTopics())
            .flatMap(result -> {
                var model = baseModel(SPECIAL_DETAIL_TEMPLATE_ID);
                model.put("softwareTopic", result.getT1());
                model.put("softwareTopics", result.getT2());
                return ServerResponse.ok().render(SPECIAL_DETAIL_TEMPLATE_ID, model);
            });
    }

    private static LinkedHashMap<String, Object> baseModel(String templateId) {
        var model = new LinkedHashMap<String, Object>();
        model.put("_templateId", templateId);
        return model;
    }

    private static Mono<ServerResponse> redirect(String path) {
        return ServerResponse.permanentRedirect(URI.create(path)).build();
    }

    private static int parsePage(ServerRequest request) {
        var rawPage = request.pathVariable("page");
        try {
            var page = Integer.parseInt(rawPage);
            if (page < 1) {
                throw new NumberFormatException("page must be positive");
            }
            return page;
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid page");
        }
    }

    private static String listTitle(SoftwareCategoryVo category, SoftwareTagVo tag) {
        if (category != null) {
            return category.getDisplayName();
        }
        if (tag != null) {
            return tag.getDisplayName();
        }
        return "全部软件";
    }

    private static String pageBase(SoftwareCategoryVo category, SoftwareTagVo tag) {
        if (category != null) {
            return "/soft/" + category.getSlug();
        }
        if (tag != null) {
            return "/soft/tag/" + tag.getSlug();
        }
        return "/soft/all";
    }

    private static String normalizeSort(String value) {
        return switch (value == null ? "" : value) {
            case "downloads", "下载" -> "downloads";
            case "likes", "喜欢" -> "likes";
            case "comments", "评论" -> "comments";
            case "recommended", "推荐" -> "recommended";
            case "latest", "最新" -> "latest";
            default -> "latest";
        };
    }

    private static Sort listSort(String value) {
        return switch (value) {
            case "downloads" -> stableDescending("status.downloadCount");
            case "likes" -> stableDescending("status.likeCount");
            case "comments" -> stableDescending("status.commentCount");
            case "recommended" -> Sort.by(
                Sort.Order.desc("spec.recommended"),
                Sort.Order.desc("status.latestReleaseDate"),
                Sort.Order.desc("metadata.creationTimestamp"));
            default -> stableDescending("status.latestReleaseDate");
        };
    }

    private static Sort stableDescending(String property) {
        return Sort.by(
            Sort.Order.desc(property),
            Sort.Order.desc("metadata.creationTimestamp"));
    }

    private static List<SoftwareCategoryVo> categoryTree(List<SoftwareCategoryVo> categories) {
        var sorted = categories.stream()
            .sorted(Comparator
                .comparing((SoftwareCategoryVo item) ->
                    item.getPriority() == null ? 0 : item.getPriority())
                .thenComparing(item -> item.getDisplayName() == null
                    ? "" : item.getDisplayName()))
            .toList();
        Map<String, List<SoftwareCategoryVo>> childrenByParent = new HashMap<>();
        for (var category : sorted) {
            if (category.getParentName() != null && !category.getParentName().isBlank()) {
                childrenByParent.computeIfAbsent(category.getParentName(), ignored -> new ArrayList<>())
                    .add(category.toBuilder().children(List.of()).build());
            }
        }
        var roots = new ArrayList<SoftwareCategoryVo>();
        roots.add(SoftwareCategoryVo.builder()
            .name("all")
            .slug("all")
            .displayName("全部软件")
            .children(List.of())
            .build());
        for (var category : sorted) {
            if (category.getParentName() == null || category.getParentName().isBlank()
                || sorted.stream().noneMatch(parent ->
                    parent.getName() != null && parent.getName().equals(category.getParentName()))) {
                roots.add(category.toBuilder()
                    .children(List.copyOf(childrenByParent.getOrDefault(
                        category.getName(), List.of())))
                    .build());
            }
        }
        return List.copyOf(roots);
    }

    private static String activeParentName(SoftwareCategoryVo category) {
        if (category == null) {
            return "all";
        }
        return category.getParentName() == null || category.getParentName().isBlank()
            ? category.getName() : category.getParentName();
    }

    private static String querySuffix(String sort, String keyword) {
        return queryHref(sort, keyword);
    }

    private static String queryHref(String sort, String keyword) {
        var suffix = "?sort=" + encode(sort);
        if (keyword != null && !keyword.isBlank()) {
            suffix += "&keyword=" + encode(keyword);
        }
        return suffix;
    }

    private static String keywordQueryPart(String keyword) {
        return keyword == null || keyword.isBlank()
            ? "" : "&keyword=" + encode(keyword);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
