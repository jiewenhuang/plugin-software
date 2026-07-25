package run.jiewen.software.vo;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import run.jiewen.software.extension.SoftwareCategory;

@Value
@Builder(toBuilder = true)
public class SoftwareCategoryVo {

    String name;

    String slug;

    String displayName;

    String parentName;

    String description;

    String icon;

    Integer priority;

    Integer appCount;

    @Builder.Default
    List<SoftwareCategoryVo> children = List.of();

    public static SoftwareCategoryVo from(SoftwareCategory category) {
        var metadata = category.getMetadata();
        var spec = category.getSpec();
        var status = category.getStatus();
        return SoftwareCategoryVo.builder()
            .name(metadata == null ? null : metadata.getName())
            .slug(spec == null ? null : spec.getSlug())
            .displayName(spec == null ? null : spec.getDisplayName())
            .parentName(spec == null ? null : spec.getParentName())
            .description(spec == null ? null : spec.getDescription())
            .icon(spec == null ? null : spec.getIcon())
            .priority(spec == null ? null : spec.getPriority())
            .appCount(status == null ? null : status.getAppCount())
            .build();
    }
}
