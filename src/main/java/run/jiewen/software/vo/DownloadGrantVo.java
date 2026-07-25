package run.jiewen.software.vo;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

/** Sensitive, non-cacheable result returned only after all download gates pass. */
@Value
@Builder
public class DownloadGrantVo {

    String assetName;
    String versionName;
    String title;
    String url;
    String extractionCode;
    String password;
    Instant expiresAt;
}
