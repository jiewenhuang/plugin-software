package run.jiewen.software.vo;

import lombok.Builder;
import lombok.Value;
import run.jiewen.software.extension.SoftwareTag;

@Value
@Builder
public class SoftwareTagVo {

    String name;

    String slug;

    String displayName;

    String description;

    String color;

    Integer priority;

    Integer appCount;

    public static SoftwareTagVo from(SoftwareTag tag) {
        var metadata = tag.getMetadata();
        var spec = tag.getSpec();
        var status = tag.getStatus();
        return SoftwareTagVo.builder()
            .name(metadata == null ? null : metadata.getName())
            .slug(spec == null ? null : spec.getSlug())
            .displayName(spec == null ? null : spec.getDisplayName())
            .description(spec == null ? null : spec.getDescription())
            .color(spec == null ? null : spec.getColor())
            .priority(spec == null ? null : spec.getPriority())
            .appCount(status == null ? null : status.getAppCount())
            .build();
    }
}
