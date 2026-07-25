package run.jiewen.software.vo;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import run.jiewen.software.extension.SoftwareTopic;

@Value
@Builder
public class SoftwareTopicDetailVo {

    String name;

    String slug;

    String title;

    String subtitle;

    String description;

    String cover;

    List<SoftwareTopicItemVo> items;

    List<SoftwareAppVo> apps;

    Boolean featured;

    Integer priority;

    Integer appCount;

    public static SoftwareTopicDetailVo from(SoftwareTopic topic, List<SoftwareAppVo> apps) {
        return from(topic, apps, List.of());
    }

    public static SoftwareTopicDetailVo from(SoftwareTopic topic, List<SoftwareAppVo> apps,
        List<SoftwareTopicItemVo> items) {
        var metadata = topic.getMetadata();
        var spec = topic.getSpec();
        var status = topic.getStatus();
        return SoftwareTopicDetailVo.builder()
            .name(metadata == null ? null : metadata.getName())
            .slug(spec == null ? null : spec.getSlug())
            .title(spec == null ? null : spec.getTitle())
            .subtitle(spec == null ? null : spec.getSubtitle())
            .description(spec == null ? null : spec.getDescription())
            .cover(spec == null ? null : spec.getCover())
            .items(items == null ? List.of() : items)
            .apps(apps)
            .featured(spec == null ? null : spec.getFeatured())
            .priority(spec == null ? null : spec.getPriority())
            .appCount(status == null ? null : status.getAppCount())
            .build();
    }
}
