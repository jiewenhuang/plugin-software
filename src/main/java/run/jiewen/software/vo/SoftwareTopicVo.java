package run.jiewen.software.vo;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import run.jiewen.software.extension.SoftwareTopic;

@Value
@Builder
public class SoftwareTopicVo {

    String name;

    String slug;

    String title;

    String subtitle;

    String description;

    String cover;

    List<SoftwareTopicItemVo> items;

    Boolean featured;

    Integer priority;

    Integer appCount;

    public static SoftwareTopicVo from(SoftwareTopic topic) {
        var metadata = topic.getMetadata();
        var spec = topic.getSpec();
        var status = topic.getStatus();
        return SoftwareTopicVo.builder()
            .name(metadata == null ? null : metadata.getName())
            .slug(spec == null ? null : spec.getSlug())
            .title(spec == null ? null : spec.getTitle())
            .subtitle(spec == null ? null : spec.getSubtitle())
            .description(spec == null ? null : spec.getDescription())
            .cover(spec == null ? null : spec.getCover())
            // Raw app metadata names are intentionally not exposed from public topic summaries.
            .items(List.of())
            .featured(spec == null ? null : spec.getFeatured())
            .priority(spec == null ? null : spec.getPriority())
            .appCount(status == null ? null : status.getAppCount())
            .build();
    }

    public static SoftwareTopicVo from(SoftwareTopicDetailVo detail) {
        return SoftwareTopicVo.builder()
            .name(detail.getName())
            .slug(detail.getSlug())
            .title(detail.getTitle())
            .subtitle(detail.getSubtitle())
            .description(detail.getDescription())
            .cover(detail.getCover())
            .items(detail.getItems())
            .featured(detail.getFeatured())
            .priority(detail.getPriority())
            .appCount(detail.getAppCount())
            .build();
    }
}
