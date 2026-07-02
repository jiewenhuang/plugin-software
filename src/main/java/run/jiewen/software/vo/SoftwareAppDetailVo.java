package run.jiewen.software.vo;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SoftwareAppDetailVo {

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

    List<SoftwareVersionVo> versions;

    List<DownloadAssetVo> downloadAssets;

    public static SoftwareAppDetailVo from(SoftwareAppVo app, List<SoftwareVersionVo> versions,
        List<DownloadAssetVo> downloadAssets) {
        return SoftwareAppDetailVo.builder()
            .name(app.getName())
            .slug(app.getSlug())
            .displayName(app.getDisplayName())
            .subtitle(app.getSubtitle())
            .officialWebsite(app.getOfficialWebsite())
            .icon(app.getIcon())
            .cover(app.getCover())
            .screenshots(app.getScreenshots())
            .summary(app.getSummary())
            .description(app.getDescription())
            .categoryName(app.getCategoryName())
            .tagNames(app.getTagNames())
            .permalink(app.getPermalink())
            .latestVersion(app.getLatestVersion())
            .latestVersionName(app.getLatestVersionName())
            .latestReleaseDate(app.getLatestReleaseDate())
            .minOsVersion(app.getMinOsVersion())
            .sizeBytes(app.getSizeBytes())
            .downloadCount(app.getDownloadCount())
            .likeCount(app.getLikeCount())
            .commentCount(app.getCommentCount())
            .platform(app.getPlatform())
            .licenseType(app.getLicenseType())
            .locale(app.getLocale())
            .featured(app.getFeatured())
            .recommended(app.getRecommended())
            .publishedAt(app.getPublishedAt())
            .seo(app.getSeo())
            .versions(versions == null ? List.of() : versions)
            .downloadAssets(downloadAssets == null ? List.of() : downloadAssets)
            .build();
    }
}
