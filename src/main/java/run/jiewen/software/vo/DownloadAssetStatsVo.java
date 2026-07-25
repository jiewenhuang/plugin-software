package run.jiewen.software.vo;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DownloadAssetStatsVo {

    long total;

    long enabled;

    long disabled;

    long protectedCount;

    long downloads;
}
