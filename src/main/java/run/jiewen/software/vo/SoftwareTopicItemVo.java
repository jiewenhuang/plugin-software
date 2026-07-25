package run.jiewen.software.vo;

import lombok.Builder;
import lombok.Value;
import run.jiewen.software.extension.SoftwareTopic;

/** Public topic relation that intentionally omits the internal SoftwareApp metadata name. */
@Value
@Builder
public class SoftwareTopicItemVo {

    String slug;

    String displayName;

    String subtitle;

    String icon;

    String cover;

    String summary;

    String permalink;

    String reason;

    Integer priority;

    public static SoftwareTopicItemVo from(SoftwareTopic.Item item, SoftwareAppVo app) {
        return SoftwareTopicItemVo.builder()
            .slug(app == null ? null : app.getSlug())
            .displayName(app == null ? null : app.getDisplayName())
            .subtitle(app == null ? null : app.getSubtitle())
            .icon(app == null ? null : app.getIcon())
            .cover(app == null ? null : app.getCover())
            .summary(app == null ? null : app.getSummary())
            .permalink(app == null ? null : app.getPermalink())
            .reason(item == null ? null : item.getReason())
            .priority(item == null ? null : item.getPriority())
            .build();
    }
}
