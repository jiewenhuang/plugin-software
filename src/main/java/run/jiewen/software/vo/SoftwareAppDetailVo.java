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

    List<String> platforms;

    List<String> architectures;

    String licenseType;

    String locale;

    Boolean featured;

    Boolean recommended;

    Instant publishedAt;

    SoftwareSeoVo seo;

    List<SoftwareVersionVo> versions;

    List<DownloadAssetVo> downloadAssets;

    List<SoftwareAppVo> relatedApps;

    List<SoftwareRelatedPostVo> relatedPosts;

    public static SoftwareAppDetailVo from(SoftwareAppVo app, List<SoftwareVersionVo> versions,
        List<DownloadAssetVo> downloadAssets) {
        return from(app, versions, downloadAssets, List.of(), List.of());
    }

    public static SoftwareAppDetailVo from(SoftwareAppVo app, List<SoftwareVersionVo> versions,
        List<DownloadAssetVo> downloadAssets, List<SoftwareAppVo> relatedApps,
        List<SoftwareRelatedPostVo> relatedPosts) {
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
            .categoryDisplayName(app.getCategoryDisplayName())
            .categorySlug(app.getCategorySlug())
            .tagNames(app.getTagNames())
            .tagDisplayNames(app.getTagDisplayNames() == null
                ? List.of() : app.getTagDisplayNames())
            .permalink(app.getPermalink())
            .latestVersion(app.getLatestVersion())
            .latestVersionName(app.getLatestVersionName())
            .latestReleaseDate(app.getLatestReleaseDate())
            .minOsVersion(app.getMinOsVersion())
            .sizeBytes(app.getSizeBytes())
            .downloadCount(app.getDownloadCount())
            .likeCount(app.getLikeCount())
            .commentCount(app.getCommentCount())
            .downloadCountText(app.getDownloadCountText())
            .likeCountText(app.getLikeCountText())
            .commentCountText(app.getCommentCountText())
            .sizeText(app.getSizeText())
            .sizeValue(app.getSizeValue())
            .sizeUnit(app.getSizeUnit())
            .minOsDisplay(app.getMinOsDisplay())
            .latestReleaseDateText(app.getLatestReleaseDateText())
            .latestReleaseDateShort(app.getLatestReleaseDateShort())
            .latestReleaseWeekday(app.getLatestReleaseWeekday())
            .localeValue(app.getLocaleValue())
            .localeUnit(app.getLocaleUnit())
            .localeLabel(app.getLocaleLabel())
            .platform(app.getPlatform())
            .platforms(app.getPlatforms())
            .architectures(app.getArchitectures())
            .licenseType(app.getLicenseType())
            .locale(app.getLocale())
            .featured(app.getFeatured())
            .recommended(app.getRecommended())
            .publishedAt(app.getPublishedAt())
            .seo(app.getSeo())
            .versions(versions == null ? List.of() : versions)
            .downloadAssets(downloadAssets == null ? List.of() : downloadAssets)
            .relatedApps(relatedApps == null ? List.of() : relatedApps)
            .relatedPosts(relatedPosts == null ? List.of() : relatedPosts)
            .build();
    }
}
