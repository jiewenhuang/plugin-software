package run.jiewen.software;

import static run.halo.app.extension.index.query.Queries.and;
import static run.halo.app.extension.index.query.Queries.contains;
import static run.halo.app.extension.index.query.Queries.equal;
import static run.halo.app.extension.index.query.Queries.or;
import static run.halo.app.extension.router.selector.SelectorUtil.labelAndFieldSelectorToListOptions;

import org.springframework.web.server.ServerWebExchange;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.PageRequest;
import run.halo.app.extension.PageRequestImpl;
import run.halo.app.extension.index.query.Condition;
import run.halo.app.extension.router.SortableRequest;
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
            query = append(query, equal("spec.platform", getPlatform()));
        }
        if (hasText(getAppName())) {
            query = append(query, equal("spec.appName", getAppName()));
        }
        if (hasText(getVersionName())) {
            query = append(query, equal("spec.versionName", getVersionName()));
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

    private static Condition append(Condition base, Condition next) {
        return base == null ? next : and(base, next);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
