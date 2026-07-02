package run.jiewen.software;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
public class SoftwareAiOptions {

    public static final String GROUP = "ai";

    private Boolean enabled = true;

    private String languageModelName;

    private Double temperature = 0.4;

    private Integer maxOutputTokens = 1200;

    public SoftwareAiOptions normalized() {
        enabled = enabled == null || enabled;
        languageModelName = StringUtils.trimToNull(languageModelName);
        if (temperature == null) {
            temperature = 0.4;
        }
        temperature = Math.max(0, Math.min(1, temperature));
        if (maxOutputTokens == null) {
            maxOutputTokens = 1200;
        }
        maxOutputTokens = Math.max(256, Math.min(4096, maxOutputTokens));
        return this;
    }
}
