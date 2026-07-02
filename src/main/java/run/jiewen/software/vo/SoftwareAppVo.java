package run.jiewen.software.vo;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import run.jiewen.software.extension.SoftwareApp;

@Value
@Builder
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

    List<String> tagNames;

    String permalink;

    String latestVersion;

    String latestVersionName;

    LocalDate latestReleaseDate;

    String minOsVersion;

    Long sizeBytes;

    Long downloadCount;

    Long likeCount;

    Long commentCount;

    String platform;

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
            .permalink(slug == null ? null : "/apps/" + slug)
            .latestVersion(status == null ? null : status.getLatestVersion())
            .latestVersionName(status == null ? null : status.getLatestVersionName())
            .latestReleaseDate(status == null ? null : status.getLatestReleaseDate())
            .minOsVersion(status == null ? null : status.getMinOsVersion())
            .sizeBytes(status == null ? null : status.getSizeBytes())
            .downloadCount(status == null ? null : status.getDownloadCount())
            .likeCount(status == null ? null : status.getLikeCount())
            .commentCount(status == null ? null : status.getCommentCount())
            .platform(spec == null ? null : spec.getPlatform())
            .licenseType(spec == null ? null : spec.getLicenseType())
            .locale(spec == null ? null : spec.getLocale())
            .featured(spec == null ? null : spec.getFeatured())
            .recommended(spec == null ? null : spec.getRecommended())
            .publishedAt(spec == null ? null : spec.getPublishedAt())
            .seo(spec == null ? null : SoftwareSeoVo.from(spec.getSeo()))
            .build();
    }
}
