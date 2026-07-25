package run.jiewen.software;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import run.jiewen.software.vo.SoftwareCategoryVo;

class SoftwareCategoryQueryTest {

    @Test
    void parentCategoryIncludesAllDescendants() {
        var parent = category("media", null);
        var child = category("video-edit", "media");
        var grandchild = category("subtitle", "video-edit");
        var categories = List.of(parent, child, grandchild, category("audio", "media"));

        assertThat(SoftwareCategoryQuery.namesFor(parent, categories))
            .containsExactly("media", "video-edit", "audio", "subtitle");
        assertThat(SoftwareCategoryQuery.namesFor(child, categories))
            .containsExactly("video-edit", "subtitle");
    }

    @Test
    void categoryConditionMatchesEveryCollectedName() {
        assertThat(SoftwareCategoryQuery.condition(List.of("media", "video-edit")).toString())
            .contains("spec.categoryName", "media", "video-edit");
    }

    private static SoftwareCategoryVo category(String name, String parentName) {
        return SoftwareCategoryVo.builder()
            .name(name)
            .slug(name)
            .displayName(name)
            .parentName(parentName)
            .build();
    }
}
