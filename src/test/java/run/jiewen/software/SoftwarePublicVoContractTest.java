package run.jiewen.software;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import run.jiewen.software.finders.SoftwarePublicQueryService;
import run.jiewen.software.vo.DownloadAssetVo;
import run.jiewen.software.vo.SoftwareAppDetailVo;
import run.jiewen.software.vo.SoftwareAppVo;
import run.jiewen.software.vo.SoftwareCategoryVo;
import run.jiewen.software.vo.SoftwareHomeVo;
import run.jiewen.software.vo.SoftwareTagVo;
import run.jiewen.software.vo.SoftwareTopicDetailVo;
import run.jiewen.software.vo.SoftwareTopicVo;
import run.jiewen.software.vo.SoftwareVersionVo;

class SoftwarePublicVoContractTest {

    @Test
    void publicAppFieldsRemainBackwardCompatibleAndSecretFree() {
        assertThat(fields(SoftwareAppVo.class)).contains(
            "name", "slug", "displayName", "subtitle", "officialWebsite", "icon", "cover",
            "screenshots", "summary", "description", "categoryName", "categoryDisplayName",
            "categorySlug", "tagNames", "tagDisplayNames", "permalink", "latestVersion",
            "latestVersionName", "latestReleaseDate", "minOsVersion", "sizeBytes",
            "downloadCount", "likeCount", "commentCount", "downloadCountText",
            "likeCountText", "commentCountText", "sizeText", "minOsDisplay", "platform",
            "architectures", "licenseType", "locale", "featured", "recommended", "publishedAt",
            "seo");
        assertThat(fields(SoftwareAppVo.class)).doesNotContain(
            "url", "storageKey", "extractionCode", "passwordSecretName", "downloadAssets",
            "softwareViewMode", "softwareListViewHref", "softwareGridViewHref");

        assertThat(fields(SoftwareAppDetailVo.class)).containsAll(fields(SoftwareAppVo.class));
        assertThat(fields(SoftwareAppDetailVo.class)).contains("versions", "downloadAssets");
    }

    @Test
    void finderAndHomeAggregationKeepTheirThemeNeutralContract() throws Exception {
        assertThat(SoftwarePublicQueryService.class.getMethod("getHome")).isNotNull();
        assertThat(SoftwarePublicQueryService.class.getMethod("getHome", int.class)).isNotNull();
        assertThat(fields(SoftwareHomeVo.class)).containsExactlyInAnyOrder(
            "latestApps", "popularApps", "recommendedApps", "recommendedTotal", "commentedApps",
            "topics", "categories");
        assertThat(fields(SoftwareHomeVo.class))
            .noneMatch(name -> name.toLowerCase().contains("view")
                || name.toLowerCase().contains("grid")
                || name.toLowerCase().contains("listhref"));
    }

    @Test
    void taxonomyTopicVersionAndDownloadFieldsRemainAvailable() {
        assertThat(fields(SoftwareCategoryVo.class)).contains(
            "name", "slug", "displayName", "parentName", "description", "icon", "priority",
            "appCount", "children");
        assertThat(fields(SoftwareTagVo.class)).contains(
            "name", "slug", "displayName", "description", "color", "priority", "appCount");
        assertThat(fields(SoftwareTopicVo.class)).contains(
            "name", "slug", "title", "subtitle", "description", "cover", "items", "featured",
            "priority", "appCount");
        assertThat(fields(SoftwareTopicDetailVo.class)).containsAll(fields(SoftwareTopicVo.class));
        assertThat(fields(SoftwareTopicDetailVo.class)).contains("apps");
        assertThat(fields(SoftwareVersionVo.class)).contains(
            "name", "version", "build", "releaseDate", "sizeBytes", "minOsVersion",
            "maxOsVersion", "architectures", "locale", "changelog", "installGuide",
            "installWarnings", "status", "assetCount", "downloadCount", "primaryAssetName");
        assertThat(fields(DownloadAssetVo.class)).contains(
            "name", "versionName", "title", "type", "provider", "url", "requiresLogin",
            "requiresCaptcha", "region", "priority", "downloadCount");
        assertThat(fields(DownloadAssetVo.class)).doesNotContain(
            "storageKey", "extractionCode", "passwordSecretName");
    }

    private static Set<String> fields(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
            .filter(field -> !field.isSynthetic())
            .map(Field::getName)
            .collect(Collectors.toSet());
    }
}
