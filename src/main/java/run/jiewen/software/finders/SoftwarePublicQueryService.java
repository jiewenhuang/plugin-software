package run.jiewen.software.finders;

import java.util.List;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.PageRequest;
import run.jiewen.software.vo.DownloadAssetVo;
import run.jiewen.software.api.DownloadGrantRequest;
import run.jiewen.software.vo.DownloadGrantVo;
import run.jiewen.software.vo.DownloadSecurityVo;
import run.jiewen.software.vo.SoftwareAppDetailVo;
import run.jiewen.software.vo.SoftwareAppVo;
import run.jiewen.software.vo.SoftwareCategoryVo;
import run.jiewen.software.vo.SoftwareHomeVo;
import run.jiewen.software.vo.SoftwareTagVo;
import run.jiewen.software.vo.SoftwareTopicDetailVo;
import run.jiewen.software.vo.SoftwareTopicVo;

public interface SoftwarePublicQueryService {

    Mono<ListResult<SoftwareAppVo>> listApps(ListOptions options, PageRequest page);

    Mono<SoftwareAppVo> getAppBySlug(String slug);

    Mono<SoftwareAppDetailVo> getAppDetailBySlug(String slug);

    /**
     * Returns the default home-page aggregation. App sections are ordered by latest release,
     * downloads, recommendation/latest release, and comments respectively. Topics and categories
     * retain their configured priority order.
     */
    Mono<SoftwareHomeVo> getHome();

    /**
     * Returns the home-page aggregation with at most {@code size} apps per app section.
     */
    Mono<SoftwareHomeVo> getHome(int size);

    /**
     * Returns published recommended apps, ordered by latest release with creation time as a stable
     * tie-breaker.
     */
    Mono<ListResult<SoftwareAppVo>> recommendedApps(int size);

    /** Returns all published topics in configured priority order, capped by the public limit. */
    Mono<List<SoftwareTopicVo>> publishedTopics();

    /**
     * Loads topic summaries and their published apps in batch. Requested slugs keep caller order;
     * an empty list means all published topics. Apps retain topic item priority order.
     */
    Mono<List<SoftwareTopicDetailVo>> topicDetails(List<String> slugs);

    Mono<DownloadAssetVo> getPrimaryDownloadAsset(String slug);

    Mono<DownloadAssetVo> getDownloadAsset(String slug, String assetName);

    Mono<DownloadGrantVo> grantDownload(DownloadGrantRequest request, String remoteAddress);

    Mono<DownloadSecurityVo> getDownloadSecurityOptions();

    Mono<ListResult<SoftwareCategoryVo>> listCategories(ListOptions options, PageRequest page);

    Mono<SoftwareCategoryVo> getCategoryBySlug(String slug);

    Mono<ListResult<SoftwareTagVo>> listTags(ListOptions options, PageRequest page);

    Mono<SoftwareTagVo> getTagBySlug(String slug);

    Mono<ListResult<SoftwareTopicVo>> listTopics(ListOptions options, PageRequest page);

    Mono<SoftwareTopicDetailVo> getTopicDetailBySlug(String slug);
}
