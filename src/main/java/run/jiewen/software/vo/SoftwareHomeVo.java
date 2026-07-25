package run.jiewen.software.vo;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * Theme-neutral aggregation of public software-domain data for a home page. This model contains
 * no layout, card, color, or client-side view state.
 */
@Value
@Builder
public class SoftwareHomeVo {

    List<SoftwareAppVo> latestApps;

    List<SoftwareAppVo> popularApps;

    List<SoftwareAppVo> recommendedApps;

    long recommendedTotal;

    List<SoftwareAppVo> commentedApps;

    List<SoftwareTopicVo> topics;

    List<SoftwareCategoryVo> categories;
}
