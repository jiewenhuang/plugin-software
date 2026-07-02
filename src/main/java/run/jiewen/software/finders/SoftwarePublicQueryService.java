package run.jiewen.software.finders;

import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.PageRequest;
import run.jiewen.software.vo.DownloadAssetVo;
import run.jiewen.software.vo.SoftwareAppDetailVo;
import run.jiewen.software.vo.SoftwareAppVo;
import run.jiewen.software.vo.SoftwareCategoryVo;
import run.jiewen.software.vo.SoftwareTagVo;
import run.jiewen.software.vo.SoftwareTopicDetailVo;
import run.jiewen.software.vo.SoftwareTopicVo;

public interface SoftwarePublicQueryService {

    Mono<ListResult<SoftwareAppVo>> listApps(ListOptions options, PageRequest page);

    Mono<SoftwareAppVo> getAppBySlug(String slug);

    Mono<SoftwareAppDetailVo> getAppDetailBySlug(String slug);

    Mono<DownloadAssetVo> getPrimaryDownloadAsset(String slug);

    Mono<DownloadAssetVo> getDownloadAsset(String slug, String assetName);

    Mono<ListResult<SoftwareCategoryVo>> listCategories(ListOptions options, PageRequest page);

    Mono<ListResult<SoftwareTagVo>> listTags(ListOptions options, PageRequest page);

    Mono<ListResult<SoftwareTopicVo>> listTopics(ListOptions options, PageRequest page);

    Mono<SoftwareTopicDetailVo> getTopicDetailBySlug(String slug);
}
