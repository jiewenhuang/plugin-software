package run.jiewen.software;

import static run.halo.app.extension.index.query.Queries.equal;
import static run.halo.app.extension.index.query.Queries.or;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import run.halo.app.extension.index.query.Condition;
import run.jiewen.software.vo.SoftwareCategoryVo;

final class SoftwareCategoryQuery {

    private SoftwareCategoryQuery() {
    }

    static List<String> namesFor(SoftwareCategoryVo category,
        List<SoftwareCategoryVo> categories) {
        return category == null
            ? List.of()
            : namesFor(category.getName(), categories);
    }

    static List<String> namesFor(String categoryName,
        List<SoftwareCategoryVo> categories) {
        if (categoryName == null || categoryName.isBlank()) {
            return List.of();
        }
        var names = new LinkedHashSet<String>();
        var pending = new ArrayDeque<String>();
        names.add(categoryName);
        pending.add(categoryName);
        while (!pending.isEmpty()) {
            var parentName = pending.removeFirst();
            for (var category : categories) {
                if (category == null || category.getName() == null
                    || category.getName().isBlank()) {
                    continue;
                }
                if (parentName.equals(category.getParentName())
                    && names.add(category.getName())) {
                    pending.addLast(category.getName());
                }
            }
        }
        return List.copyOf(names);
    }

    static Condition condition(List<String> categoryNames) {
        var names = categoryNames.stream()
            .filter(name -> name != null && !name.isBlank())
            .distinct()
            .toList();
        if (names.isEmpty()) {
            throw new IllegalArgumentException("At least one software category is required");
        }
        Condition condition = equal("spec.categoryName", names.get(0));
        for (var index = 1; index < names.size(); index++) {
            condition = or(condition, equal("spec.categoryName", names.get(index)));
        }
        return condition;
    }
}
