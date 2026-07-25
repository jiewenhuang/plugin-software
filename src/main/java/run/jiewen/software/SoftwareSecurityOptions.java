package run.jiewen.software;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
public class SoftwareSecurityOptions {

    public static final String GROUP = "security";
    public static final String PROVIDER_TURNSTILE = "turnstile";
    public static final String TURNSTILE_VERIFY_URL =
        "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private String captchaProvider = PROVIDER_TURNSTILE;
    private String captchaVerifyUrl = TURNSTILE_VERIFY_URL;
    private String captchaSecretName;
    private String captchaSecretKey = "secret";
    private String captchaSiteKey;

    public SoftwareSecurityOptions normalized() {
        captchaProvider = StringUtils.defaultIfBlank(
            StringUtils.trimToNull(captchaProvider), PROVIDER_TURNSTILE);
        // The provider endpoint is fixed to prevent an administrator-controlled URL from turning
        // captcha verification into a server-side request forgery primitive.
        captchaVerifyUrl = TURNSTILE_VERIFY_URL;
        captchaSecretName = StringUtils.trimToNull(captchaSecretName);
        captchaSecretKey = StringUtils.defaultIfBlank(
            StringUtils.trimToNull(captchaSecretKey), "secret");
        captchaSiteKey = StringUtils.trimToNull(captchaSiteKey);
        return this;
    }
}
