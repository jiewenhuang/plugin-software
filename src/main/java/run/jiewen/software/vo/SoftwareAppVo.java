package run.jiewen.software.vo;

import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import run.jiewen.software.extension.SoftwareApp;

@Value
@Builder(toBuilder = true)
public class SoftwareAppVo {

    String name;

    String slug;

    String displayName;

    String subtitle;

    String officialWebsite;

    String icon;

    String cover;

    List<String> screenshots;

    String summary;

    String description;

    String categoryName;

    String categoryDisplayName;

    String categorySlug;

    List<String> tagNames;

    List<String> tagDisplayNames;

    String permalink;

    String latestVersion;

    String latestVersionName;

    LocalDate latestReleaseDate;

    String minOsVersion;

    Long sizeBytes;

    Long downloadCount;

    Long likeCount;

    Long commentCount;

    String downloadCountText;

    String likeCountText;

    String commentCountText;

    String sizeText;

    String sizeValue;

    String sizeUnit;

    String minOsDisplay;

    String latestReleaseDateText;

    String latestReleaseDateShort;

    String latestReleaseWeekday;

    String localeValue;

    String localeUnit;

    String localeLabel;

    String platform;

    List<String> architectures;

    String licenseType;

    String locale;

    Boolean featured;

    Boolean recommended;

    Instant publishedAt;

    SoftwareSeoVo seo;

    public static SoftwareAppVo from(SoftwareApp app) {
        var metadata = app.getMetadata();
        var spec = app.getSpec();
        var status = app.getStatus();
        var slug = spec == null ? null : spec.getSlug();
        return SoftwareAppVo.builder()
            .name(metadata == null ? null : metadata.getName())
            .slug(slug)
            .displayName(spec == null ? null : spec.getDisplayName())
            .subtitle(spec == null ? null : spec.getSubtitle())
            .officialWebsite(spec == null ? null : spec.getOfficialWebsite())
            .icon(spec == null ? null : spec.getIcon())
            .cover(spec == null ? null : spec.getCover())
            .screenshots(spec == null ? null : spec.getScreenshots())
            .summary(spec == null ? null : spec.getSummary())
            .description(spec == null ? null : spec.getDescription())
            .categoryName(spec == null ? null : spec.getCategoryName())
            .tagNames(spec == null ? null : spec.getTagNames())
            .permalink(slug == null ? null : "/soft/" + slug)
            .latestVersion(status == null ? null : status.getLatestVersion())
            .latestVersionName(status == null ? null : status.getLatestVersionName())
            .latestReleaseDate(status == null ? null : status.getLatestReleaseDate())
            .minOsVersion(status == null ? null : status.getMinOsVersion())
            .sizeBytes(status == null ? null : status.getSizeBytes())
            .downloadCount(status == null ? null : status.getDownloadCount())
            .likeCount(status == null ? null : status.getLikeCount())
            .commentCount(status == null ? null : status.getCommentCount())
            .downloadCountText(compactCount(status == null ? null : status.getDownloadCount()))
            .likeCountText(plainCount(status == null ? null : status.getLikeCount()))
            .commentCountText(plainCount(status == null ? null : status.getCommentCount()))
            .sizeText(formatBytes(status == null ? null : status.getSizeBytes()))
            .sizeValue(sizePart(status == null ? null : status.getSizeBytes(), true))
            .sizeUnit(sizePart(status == null ? null : status.getSizeBytes(), false))
            .minOsDisplay(status == null || status.getMinOsVersion() == null
                ? "macOS" : ">=" + status.getMinOsVersion())
            .latestReleaseDateText(dateText(status == null ? null : status.getLatestReleaseDate()))
            .latestReleaseDateShort(shortDate(status == null ? null : status.getLatestReleaseDate()))
            .latestReleaseWeekday(weekday(status == null ? null : status.getLatestReleaseDate()))
            .platform(spec == null ? null : spec.getPlatform())
            .architectures(spec == null ? null : spec.getArchitectures())
            .licenseType(spec == null ? null : spec.getLicenseType())
            .locale(spec == null ? null : spec.getLocale())
            .localeValue(localeLabel(spec == null ? null : spec.getLocale()))
            .localeUnit(localeUnit(spec == null ? null : spec.getLocale()))
            .localeLabel(localeLabel(spec == null ? null : spec.getLocale()))
            .featured(spec == null ? null : spec.getFeatured())
            .recommended(spec == null ? null : spec.getRecommended())
            .publishedAt(spec == null ? null : spec.getPublishedAt())
            .seo(spec == null ? null : SoftwareSeoVo.from(spec.getSeo()))
            .build();
    }

    private static String compactCount(Long value) {
        var count = value == null ? 0L : value;
        if (count >= 10_000) {
            return decimal(count / 10_000.0, 2) + "w";
        }
        if (count >= 1_000) {
            return decimal(count / 1_000.0, 2) + "k";
        }
        return Long.toString(Math.max(0L, count));
    }

    private static String plainCount(Long value) {
        return Long.toString(Math.max(0L, value == null ? 0L : value));
    }

    private static String formatBytes(Long bytes) {
        return sizePart(bytes, true) + (sizePart(bytes, false).isEmpty()
            ? "" : " " + sizePart(bytes, false));
    }

    private static String sizePart(Long bytes, boolean valuePart) {
        if (bytes == null || bytes <= 0) {
            return valuePart ? "未知" : "";
        }
        var units = List.of("B", "KB", "MB", "GB", "TB");
        var size = bytes.doubleValue();
        var unit = 0;
        while (size >= 1024 && unit < units.size() - 1) {
            size /= 1024;
            unit++;
        }
        return valuePart ? decimal(size, size >= 100 || size == Math.rint(size) ? 0 : 1)
            : units.get(unit);
    }

    private static String decimal(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP)
            .stripTrailingZeros().toPlainString();
    }

    private static String dateText(LocalDate value) {
        return value == null ? "未知" : value.toString();
    }

    private static String shortDate(LocalDate value) {
        return value == null ? "--" : String.format("%02d-%02d", value.getMonthValue(),
            value.getDayOfMonth());
    }

    private static String weekday(LocalDate value) {
        if (value == null) {
            return "";
        }
        return switch (value.getDayOfWeek()) {
            case MONDAY -> "星期一";
            case TUESDAY -> "星期二";
            case WEDNESDAY -> "星期三";
            case THURSDAY -> "星期四";
            case FRIDAY -> "星期五";
            case SATURDAY -> "星期六";
            case SUNDAY -> "星期日";
        };
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

    private static String localeUnit(String locale) {
        if (locale == null || locale.isBlank()) {
            return "ALL";
        }
        var normalized = locale.trim().toLowerCase();
        return normalized.startsWith("zh") ? "ZH" : normalized.toUpperCase();
    }
}
