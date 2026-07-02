package run.jiewen.software.ai;

import org.apache.commons.lang3.StringUtils;

public enum SoftwareAiTarget {
    ALL,
    BASIC,
    DESCRIPTION,
    SEO;

    public static SoftwareAiTarget from(String value) {
        if (!StringUtils.isNotBlank(value)) {
            return ALL;
        }
        try {
            return SoftwareAiTarget.valueOf(value.trim().replace('-', '_').toUpperCase());
        } catch (IllegalArgumentException e) {
            return ALL;
        }
    }

    public boolean includesSummary() {
        return this == ALL || this == BASIC;
    }

    public boolean includesDescription() {
        return this == ALL || this == DESCRIPTION;
    }

    public boolean includesSeo() {
        return this == ALL || this == SEO;
    }
}
