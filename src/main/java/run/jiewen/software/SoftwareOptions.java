package run.jiewen.software;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
public class SoftwareOptions {

    public static final String GROUP = "options";

    private List<OptionItem> platforms;
    private List<OptionItem> architectures;
    private List<OptionItem> versionStatuses;
    private List<OptionItem> downloadTypes;
    private List<OptionItem> downloadProviders;
    private List<OptionItem> releaseChannels;
    private List<OptionItem> locales;

    public SoftwareOptions() {
        this.platforms = defaultPlatforms();
        this.architectures = defaultArchitectures();
        this.versionStatuses = defaultVersionStatuses();
        this.downloadTypes = defaultDownloadTypes();
        this.downloadProviders = defaultDownloadProviders();
        this.releaseChannels = defaultReleaseChannels();
        this.locales = defaultLocales();
    }

    public SoftwareOptions normalized() {
        var defaults = new SoftwareOptions();
        this.platforms = normalize(platforms, defaults.platforms);
        this.architectures = normalize(architectures, defaults.architectures);
        this.versionStatuses = normalize(versionStatuses, defaults.versionStatuses);
        this.downloadTypes = normalize(downloadTypes, defaults.downloadTypes);
        this.downloadProviders = normalize(downloadProviders, defaults.downloadProviders);
        this.releaseChannels = normalize(releaseChannels, defaults.releaseChannels);
        this.locales = normalize(locales, defaults.locales);
        return this;
    }

    private static List<OptionItem> normalize(List<OptionItem> configured,
        List<OptionItem> fallback) {
        if (configured == null) {
            return new ArrayList<>(fallback);
        }
        Map<String, OptionItem> options = new LinkedHashMap<>();
        for (OptionItem option : configured) {
            if (option == null) {
                continue;
            }
            var label = StringUtils.trimToEmpty(option.getLabel());
            var value = StringUtils.trimToEmpty(option.getValue());
            if (StringUtils.isBlank(label) && StringUtils.isBlank(value)) {
                continue;
            }
            if (StringUtils.isBlank(label)) {
                label = value;
            }
            var normalized = new OptionItem();
            normalized.setLabel(label);
            normalized.setValue(value);
            normalized.setShortLabel(StringUtils.trimToNull(option.getShortLabel()));
            normalized.setClassName(StringUtils.trimToNull(option.getClassName()));
            options.put(value, normalized);
        }
        return new ArrayList<>(options.values());
    }

    private static List<OptionItem> defaultPlatforms() {
        return List.of(
            option("macOS", "macOS", "M", "macos"),
            option("Windows", "Windows", "W", "windows"),
            option("Linux", "Linux", "L", "linux"),
            option("iOS", "iOS", "i", "ios"),
            option("Android", "Android", "A", "android"),
            option("Web", "Web", "Web", "web")
        );
    }

    private static List<OptionItem> defaultArchitectures() {
        return List.of(
            option("Apple Silicon", "arm64"),
            option("Intel x64", "x64"),
            option("Universal", "universal")
        );
    }

    private static List<OptionItem> defaultVersionStatuses() {
        return List.of(
            option("已发布", "released"),
            option("草稿", "draft"),
            option("Beta", "beta"),
            option("已废弃", "deprecated")
        );
    }

    private static List<OptionItem> defaultDownloadTypes() {
        return List.of(
            option("DMG", "dmg"),
            option("ZIP", "zip"),
            option("PKG", "pkg"),
            option("EXE", "exe"),
            option("MSI", "msi"),
            option("APK", "apk"),
            option("IPA", "ipa"),
            option("其他", "other")
        );
    }

    private static List<OptionItem> defaultDownloadProviders() {
        return List.of(
            option("外部链接", "external"),
            option("官方源", "official"),
            option("附件存储", "attachment"),
            option("GitHub", "github"),
            option("百度网盘", "baidu"),
            option("阿里云盘", "aliyun"),
            option("夸克网盘", "quark"),
            option("其他", "other")
        );
    }

    private static List<OptionItem> defaultReleaseChannels() {
        return List.of(
            option("未设置", ""),
            option("Stable", "stable"),
            option("Beta", "beta"),
            option("Alpha", "alpha"),
            option("Nightly", "nightly")
        );
    }

    private static List<OptionItem> defaultLocales() {
        return List.of(
            option("未设置", ""),
            option("简体中文", "zh-CN"),
            option("繁体中文", "zh-TW"),
            option("English", "en-US"),
            option("日本語", "ja-JP"),
            option("通用", "global")
        );
    }

    private static OptionItem option(String label, String value) {
        return option(label, value, null, null);
    }

    private static OptionItem option(String label, String value, String shortLabel,
        String className) {
        var option = new OptionItem();
        option.setLabel(label);
        option.setValue(value);
        option.setShortLabel(shortLabel);
        option.setClassName(className);
        return option;
    }

    @Data
    public static class OptionItem {
        private String label;
        private String value;
        private String shortLabel;
        private String className;
    }
}
