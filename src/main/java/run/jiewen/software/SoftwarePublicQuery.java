package run.jiewen.software;

import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static run.halo.app.extension.index.query.Queries.and;
import static run.halo.app.extension.index.query.Queries.contains;
import static run.halo.app.extension.index.query.Queries.empty;
import static run.halo.app.extension.index.query.Queries.equal;
import static run.halo.app.extension.router.selector.SelectorUtil.labelAndFieldSelectorToListOptions;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.util.List;
import org.springdoc.core.fn.builders.operation.Builder;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.ServerWebExchange;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.PageRequest;
import run.halo.app.extension.PageRequestImpl;
import run.halo.app.extension.index.query.Condition;
import run.halo.app.extension.router.IListRequest;
import run.halo.app.extension.router.SortableRequest;
import run.halo.app.extension.router.selector.FieldSelector;

public class SoftwarePublicQuery extends SortableRequest {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    public SoftwarePublicQuery(ServerWebExchange exchange) {
        super(exchange);
    }

    public String getKeyword() {
        return queryParams.getFirst("keyword");
    }

    public String getCategory() {
        return queryParams.getFirst("category");
    }

    public String getTag() {
        return queryParams.getFirst("tag");
    }

    public String getPlatform() {
        return queryParams.getFirst("platform");
    }

    public String getFeatured() {
        return queryParams.getFirst("featured");
    }

    public String getRecommended() {
        return queryParams.getFirst("recommended");
    }

    @Override
    public Integer getPage() {
        return parsePositiveInt("page", DEFAULT_PAGE, Integer.MAX_VALUE);
    }

    @Override
    public Integer getSize() {
        return parsePositiveInt("size", DEFAULT_SIZE, MAX_SIZE);
    }

    @Override
    public ListOptions toListOptions() {
        return buildListOptions(null);
    }

    public ListOptions toListOptions(List<String> categoryNames) {
        return buildListOptions(categoryNames);
    }

    private ListOptions buildListOptions(List<String> categoryNames) {
        var listOptions =
            labelAndFieldSelectorToListOptions(getLabelSelector(), getFieldSelector());
        Condition query = equal("spec.published", true);
        if (hasText(getKeyword())) {
            query = and(query, contains("spec.displayName", getKeyword()));
        }
        if (categoryNames != null && !categoryNames.isEmpty()) {
            query = and(query, SoftwareCategoryQuery.condition(categoryNames));
        } else if (categoryNames == null && hasText(getCategory())) {
            query = and(query, equal("spec.categoryName", getCategory()));
        }
        if (hasText(getTag())) {
            query = and(query, equal("spec.tagNames", getTag()));
        }
        if (hasText(getPlatform())) {
            query = and(query, run.halo.app.extension.index.query.Queries.or(
                equal("spec.platforms", getPlatform()),
                equal("spec.platform", getPlatform())));
        }
        if (hasText(getFeatured())) {
            query = and(query, equal("spec.featured", Boolean.parseBoolean(getFeatured())));
        }
        if (hasText(getRecommended())) {
            query = and(query, equal("spec.recommended", Boolean.parseBoolean(getRecommended())));
        }
        if (listOptions.getFieldSelector() != null) {
            query = and(query, (Condition) listOptions.getFieldSelector().query());
        }
        listOptions.setFieldSelector(FieldSelector.of(query));
        return listOptions;
    }

    @Override
    public PageRequest toPageRequest() {
        return PageRequestImpl.of(getPage(), getSize(), getSort());
    }

    public static void buildParameters(Builder builder) {
        IListRequest.buildParameters(builder);
        builder.parameter(parameterBuilder()
                .in(ParameterIn.QUERY)
                .name("sort")
                .implementation(String.class)
                .required(false)
                .description("Sort criteria: field,(asc|desc)."))
            .parameter(parameterBuilder()
                .in(ParameterIn.QUERY)
                .name("keyword")
                .implementation(String.class)
                .required(false)
                .description("Software display name keyword."))
            .parameter(parameterBuilder()
                .in(ParameterIn.QUERY)
                .name("category")
                .implementation(String.class)
                .required(false)
                .description("Software category metadata.name."))
            .parameter(parameterBuilder()
                .in(ParameterIn.QUERY)
                .name("tag")
                .implementation(String.class)
                .required(false)
                .description("Software tag metadata.name."))
            .parameter(parameterBuilder()
                .in(ParameterIn.QUERY)
                .name("platform")
                .implementation(String.class)
                .required(false)
                .description("Software platform."))
            .parameter(parameterBuilder()
                .in(ParameterIn.QUERY)
                .name("featured")
                .implementation(Boolean.class)
                .required(false)
                .description("Whether the software is featured."))
            .parameter(parameterBuilder()
                .in(ParameterIn.QUERY)
                .name("recommended")
                .implementation(Boolean.class)
                .required(false)
                .description("Whether the software is recommended."));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private int parsePositiveInt(String name, int defaultValue, int maxValue) {
        var raw = queryParams.getFirst(name);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            var value = Integer.parseInt(raw);
            if (value < 1) {
                throw new NumberFormatException(name + " must be positive");
            }
            return Math.min(value, maxValue);
        } catch (NumberFormatException e) {
            throw new ServerWebInputException("Invalid public pagination: " + name);
        }
    }
}
