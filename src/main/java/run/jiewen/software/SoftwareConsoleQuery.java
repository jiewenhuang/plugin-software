package run.jiewen.software;

import static run.halo.app.extension.index.query.Queries.and;
import static run.halo.app.extension.index.query.Queries.contains;
import static run.halo.app.extension.index.query.Queries.equal;
import static run.halo.app.extension.index.query.Queries.greaterThan;
import static run.halo.app.extension.index.query.Queries.isNull;
import static run.halo.app.extension.index.query.Queries.or;
import static run.halo.app.extension.router.selector.SelectorUtil.labelAndFieldSelectorToListOptions;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static run.halo.app.extension.router.QueryParamBuildUtil.sortParameter;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import org.springdoc.core.fn.builders.operation.Builder;
import org.springframework.web.server.ServerWebExchange;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.PageRequest;
import run.halo.app.extension.PageRequestImpl;
import run.halo.app.extension.index.query.Condition;
import run.halo.app.extension.router.SortableRequest;
import run.halo.app.extension.router.IListRequest;
import run.halo.app.extension.router.selector.FieldSelector;

public class SoftwareConsoleQuery extends SortableRequest {

    public SoftwareConsoleQuery(ServerWebExchange exchange) {
        super(exchange);
    }

    public String getKeyword() {
        return queryParams.getFirst("keyword");
    }

    public String getPublished() {
        return queryParams.getFirst("published");
    }

    public String getCategory() {
        return queryParams.getFirst("category");
    }

    public String getPlatform() {
        return queryParams.getFirst("platform");
    }

    public String getAppName() {
        return queryParams.getFirst("appName");
    }

    public String getVersionName() {
        return queryParams.getFirst("versionName");
    }

    public String getVisible() {
        return queryParams.getFirst("visible");
    }

    public String getFeatured() {
        return queryParams.getFirst("featured");
    }

    public String getUsage() {
        return queryParams.getFirst("usage");
    }

    public String getParentName() {
        return queryParams.getFirst("parentName");
    }

    public String getType() {
        return queryParams.getFirst("type");
    }

    public String getEnabled() {
        return queryParams.getFirst("enabled");
    }

    public String getProtection() {
        return queryParams.getFirst("protection");
    }

    public ListOptions toListOptions() {
        return toListOptions("spec.displayName", true);
    }

    public ListOptions toListOptions(String keywordField) {
        return toListOptions(keywordField, false);
    }

    public ListOptions toListOptions(String keywordField, boolean includeSlug) {
        var listOptions =
            labelAndFieldSelectorToListOptions(getLabelSelector(), getFieldSelector());
        Condition query = null;
        if (hasText(getKeyword())) {
            var keywordQuery = contains(keywordField, getKeyword());
            if (includeSlug) {
                keywordQuery = or(keywordQuery, contains("spec.slug", getKeyword()));
            }
            query = append(query, keywordQuery);
        }
        if (hasText(getPublished())) {
            query = append(query, equal("spec.published", Boolean.parseBoolean(getPublished())));
        }
        if (hasText(getCategory())) {
            query = append(query, equal("spec.categoryName", getCategory()));
        }
        if (hasText(getPlatform())) {
            query = append(query, or(
                equal("spec.platforms", getPlatform()),
                equal("spec.platform", getPlatform())));
        }
        if (hasText(getAppName())) {
            query = append(query, equal("spec.appName", getAppName()));
        }
        if (hasText(getVersionName())) {
            query = append(query, equal("spec.versionName", getVersionName()));
        }
        if (hasText(getVisible())) {
            query = append(query, equal("spec.visible", Boolean.parseBoolean(getVisible())));
        }
        if (hasText(getFeatured())) {
            query = append(query, equal("spec.featured", Boolean.parseBoolean(getFeatured())));
        }
        if (hasText(getParentName())) {
            query = append(query, equal("spec.parentName", getParentName()));
        }
        if (hasText(getType())) {
            query = append(query, equal("spec.type", getType()));
        }
        if (hasText(getEnabled())) {
            query = append(query, equal("spec.enabled", Boolean.parseBoolean(getEnabled())));
        }
        if ("protected".equalsIgnoreCase(getProtection())) {
            query = append(query, equal("spec.protected", true));
        }
        if ("used".equalsIgnoreCase(getUsage())) {
            query = append(query, greaterThan("status.appCount", 0));
        } else if ("unused".equalsIgnoreCase(getUsage())) {
            query = append(query, or(equal("status.appCount", 0), isNull("status.appCount")));
        }
        if (listOptions.getFieldSelector() != null) {
            query = append(query, (Condition) listOptions.getFieldSelector().query());
        }
        if (query != null) {
            listOptions.setFieldSelector(FieldSelector.of(query));
        }
        return listOptions;
    }

    public PageRequest toPageRequest() {
        return PageRequestImpl.of(getPage(), getSize(), getSort());
    }

    public static void buildAppParameters(Builder builder) {
        buildBaseParameters(builder);
        builder.parameter(queryParameter("keyword", String.class,
                "Software display-name or slug keyword."))
            .parameter(queryParameter("published", Boolean.class, "Published state."))
            .parameter(queryParameter("category", String.class, "Category metadata.name."))
            .parameter(queryParameter("platform", String.class, "Normalized platform value."));
    }

    public static void buildVersionParameters(Builder builder) {
        buildBaseParameters(builder);
        builder.parameter(queryParameter("keyword", String.class, "Version keyword."))
            .parameter(queryParameter("appName", String.class, "SoftwareApp metadata.name."));
    }

    public static void buildDownloadAssetParameters(Builder builder) {
        buildBaseParameters(builder);
        builder.parameter(queryParameter("keyword", String.class, "Download title keyword."))
            .parameter(queryParameter("appName", String.class, "SoftwareApp metadata.name."))
            .parameter(queryParameter("versionName", String.class,
                "SoftwareVersion metadata.name."))
            .parameter(queryParameter("type", String.class, "Download asset type."))
            .parameter(queryParameter("enabled", Boolean.class,
                "Download asset enabled state."))
            .parameter(queryParameter("protection", String.class,
                "Download protection state: protected."));
    }

    public static void buildCategoryParameters(Builder builder) {
        buildBaseParameters(builder);
        builder.parameter(queryParameter("keyword", String.class,
                "Category display-name or slug keyword."))
            .parameter(queryParameter("visible", Boolean.class, "Visible state."))
            .parameter(queryParameter("parentName", String.class,
                "SoftwareCategory parent metadata.name."));
    }

    public static void buildTagParameters(Builder builder) {
        buildBaseParameters(builder);
        builder.parameter(queryParameter("keyword", String.class,
                "Tag display-name or slug keyword."))
            .parameter(queryParameter("visible", Boolean.class, "Visible state."))
            .parameter(queryParameter("usage", String.class, "Usage state: used or unused."));
    }

    public static void buildTopicParameters(Builder builder) {
        buildBaseParameters(builder);
        builder.parameter(queryParameter("keyword", String.class,
                "Topic title or slug keyword."))
            .parameter(queryParameter("published", Boolean.class, "Published state."))
            .parameter(queryParameter("featured", Boolean.class, "Featured state."));
    }

    private static void buildBaseParameters(Builder builder) {
        IListRequest.buildParameters(builder);
        builder.parameter(sortParameter());
    }

    private static org.springdoc.core.fn.builders.parameter.Builder queryParameter(String name,
        Class<?> type, String description) {
        return parameterBuilder()
            .in(ParameterIn.QUERY)
            .name(name)
            .description(description)
            .implementation(type)
            .required(false);
    }

    private static Condition append(Condition base, Condition next) {
        return base == null ? next : and(base, next);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
