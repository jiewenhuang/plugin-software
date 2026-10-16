package run.jiewen.software.query;

import static run.halo.app.extension.index.query.Queries.*;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.domain.Sort;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.PageRequest;
import run.halo.app.extension.PageRequestImpl;
import run.halo.app.extension.index.query.Condition;
import run.halo.app.extension.router.selector.FieldSelector;

/** HTTP-independent query values. Transport adapters enforce their own input contracts. */
public record SoftwareListCriteria(Map<String, Object> filters, int page, int size, Sort sort) {
    public SoftwareListCriteria {
        filters = Map.copyOf(filters);
    }

    public ListOptions toListOptions(String keywordField, boolean includeSlug,
        ListOptions options) {
        Condition query = null;
        var keyword = filters.get("keyword");
        if (keyword instanceof String value && !value.isBlank()) {
            query = contains(keywordField, value);
            if (includeSlug) {
                query = or(query, contains("spec.slug", value));
            }
        }
        var fields = new LinkedHashMap<String, String>();
        for (var name : new String[]{"slug", "published", "categoryName", "appName", "versionName",
            "visible", "featured", "recommended", "parentName", "type", "enabled", "provider",
            "version", "status"}) {
            fields.put(name, "spec." + name);
        }
        fields.put("tagName", "spec.tagNames");
        for (var field : fields.entrySet()) {
            if (filters.containsKey(field.getKey())) {
                query = append(query, equal(field.getValue(), filters.get(field.getKey())));
            }
        }
        if (filters.containsKey("platform")) {
            query = append(query, or(equal("spec.platforms", filters.get("platform")),
                equal("spec.platform", filters.get("platform"))));
        }
        if ("protected".equalsIgnoreCase(String.valueOf(filters.get("protection")))) {
            query = append(query, equal("spec.protected", true));
        }
        if ("used".equalsIgnoreCase(String.valueOf(filters.get("usage")))) {
            query = append(query, greaterThan("status.appCount", 0));
        } else if ("unused".equalsIgnoreCase(String.valueOf(filters.get("usage")))) {
            query = append(query, or(equal("status.appCount", 0), isNull("status.appCount")));
        }
        if (options.getFieldSelector() != null) {
            query = append(query, (Condition) options.getFieldSelector().query());
        }
        if (query != null) {
            options.setFieldSelector(FieldSelector.of(query));
        }
        return options;
    }

    public PageRequest toPageRequest() {
        return PageRequestImpl.of(page, size, sort);
    }

    private static Condition append(Condition base, Condition next) {
        return base == null ? next : and(base, next);
    }
}
