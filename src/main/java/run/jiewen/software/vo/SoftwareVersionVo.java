package run.jiewen.software.vo;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import run.jiewen.software.extension.SoftwareVersion;

@Value
@Builder
public class SoftwareVersionVo {

    String name;

    String version;

    String build;

    LocalDate releaseDate;

    Long sizeBytes;

    String minOsVersion;

    String maxOsVersion;

    List<String> architectures;

    String locale;

    String changelog;

    String installGuide;

    List<String> installWarnings;

    String status;

    Integer assetCount;

    Long downloadCount;

    String primaryAssetName;

    String sizeText;

    String localeLabel;

    String minOsDisplay;

    String releaseDateText;

    public static SoftwareVersionVo from(SoftwareVersion version) {
        var metadata = version.getMetadata();
        var spec = version.getSpec();
        var status = version.getStatus();
        return SoftwareVersionVo.builder()
            .name(metadata == null ? null : metadata.getName())
            .version(spec == null ? null : spec.getVersion())
            .build(spec == null ? null : spec.getBuild())
            .releaseDate(spec == null ? null : spec.getReleaseDate())
            .sizeBytes(spec == null ? null : spec.getSizeBytes())
            .minOsVersion(spec == null ? null : spec.getMinOsVersion())
            .maxOsVersion(spec == null ? null : spec.getMaxOsVersion())
            .architectures(spec == null ? null : spec.getArchitectures())
            .locale(spec == null ? null : spec.getLocale())
            .changelog(spec == null ? null : spec.getChangelog())
            .installGuide(spec == null ? null : spec.getInstallGuide())
            .installWarnings(spec == null ? null : spec.getInstallWarnings())
            .status(spec == null ? null : spec.getStatus())
            .assetCount(status == null ? null : status.getAssetCount())
            .downloadCount(status == null ? null : status.getDownloadCount())
            .primaryAssetName(status == null ? null : status.getPrimaryAssetName())
            .sizeText(formatBytes(spec == null ? null : spec.getSizeBytes()))
            .localeLabel(localeLabel(spec == null ? null : spec.getLocale()))
            .minOsDisplay(spec == null || spec.getMinOsVersion() == null
                ? "macOS" : "macOS " + spec.getMinOsVersion() + " 或更高版本")
            .releaseDateText(spec == null || spec.getReleaseDate() == null
                ? "未知" : spec.getReleaseDate().toString())
            .build();
    }

    private static String formatBytes(Long bytes) {
        if (bytes == null || bytes <= 0) {
            return "未知";
        }
        var units = List.of("B", "KB", "MB", "GB", "TB");
        var size = bytes.doubleValue();
        var unit = 0;
        while (size >= 1024 && unit < units.size() - 1) {
            size /= 1024;
            unit++;
        }
        var scale = size >= 100 || size == Math.rint(size) ? 0 : 1;
        return BigDecimal.valueOf(size).setScale(scale, RoundingMode.HALF_UP)
            .stripTrailingZeros().toPlainString() + " " + units.get(unit);
    }

    private static String localeLabel(String locale) {
        if (locale == null || locale.isBlank()) {
            return "未知";
        }
        return switch (locale.trim().toLowerCase()) {
            case "zh", "zh-cn", "zh-hans" -> "中文";
            case "en", "en-us" -> "英文";
            case "ja" -> "日文";
            case "ko" -> "韩文";
            default -> locale;
        };
    }
}
