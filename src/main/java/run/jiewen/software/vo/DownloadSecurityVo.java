package run.jiewen.software.vo;

import lombok.Builder;
import lombok.Value;

/** Public captcha bootstrap data. Secret identifiers and values are deliberately excluded. */
@Value
@Builder
public class DownloadSecurityVo {

    String captchaProvider;

    String captchaSiteKey;

    boolean captchaConfigured;
}
