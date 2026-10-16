package run.jiewen.software.finders;

import java.util.Map;
import reactor.core.publisher.Mono;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.ListResult;
import run.jiewen.software.SoftwareConsoleQuery;
import run.jiewen.software.api.DownloadAssetSecretCommand;
import run.jiewen.software.api.SoftwareMutation;
import run.jiewen.software.api.SoftwareResourceKind;
import run.jiewen.software.extension.DownloadAsset;
import run.jiewen.software.extension.SoftwareApp;
import run.jiewen.software.extension.SoftwareCategory;
import run.jiewen.software.extension.SoftwareTag;
import run.jiewen.software.extension.SoftwareTopic;
import run.jiewen.software.extension.SoftwareVersion;
import run.jiewen.software.query.SoftwareListCriteria;
import run.jiewen.software.vo.SoftwareConsoleStatsVo;
import run.jiewen.software.vo.DownloadAssetSecretVo;
import run.jiewen.software.vo.DownloadAssetStatsVo;

public interface SoftwareConsoleService {

    Mono<ListResult<AbstractExtension>> listResources(SoftwareResourceKind kind,
        SoftwareListCriteria criteria);

    Mono<AbstractExtension> getResource(SoftwareResourceKind kind, String name);

    Mono<AbstractExtension> createResource(SoftwareResourceKind kind, Map<String, Object> spec);

    Mono<AbstractExtension> prepareCreateResource(SoftwareResourceKind kind,
        Map<String, Object> spec, String name);

    Mono<Void> validateResourceState(AbstractExtension resource);

    Mono<AbstractExtension> patchResource(SoftwareResourceKind kind, String name,
        SoftwareMutation mutation);

    Mono<AbstractExtension> deleteResource(SoftwareResourceKind kind, String name,
        Long expectedVersion);

    Mono<ListResult<SoftwareApp>> listApps(SoftwareConsoleQuery query);

    Mono<SoftwareConsoleStatsVo> getAppStats();

    Mono<SoftwareApp> getApp(String name);

    Mono<SoftwareApp> createApp(SoftwareApp app);

    Mono<SoftwareApp> replaceApp(String name, SoftwareApp app);

    Mono<SoftwareApp> updateApp(String name, SoftwareApp app);

    Mono<SoftwareApp> deleteApp(String name);

    Mono<ListResult<SoftwareVersion>> listVersions(SoftwareConsoleQuery query);

    Mono<SoftwareVersion> createVersion(SoftwareVersion version);

    Mono<SoftwareVersion> replaceVersion(String name, SoftwareVersion version);

    Mono<SoftwareVersion> updateVersion(String name, SoftwareVersion version);

    Mono<SoftwareVersion> deleteVersion(String name);

    Mono<ListResult<DownloadAsset>> listDownloadAssets(SoftwareConsoleQuery query);

    Mono<DownloadAssetStatsVo> getDownloadAssetStats();

    Mono<DownloadAsset> createDownloadAsset(DownloadAsset asset);

    Mono<DownloadAsset> replaceDownloadAsset(String name, DownloadAsset asset);

    Mono<DownloadAsset> updateDownloadAsset(String name, DownloadAsset asset);

    Mono<DownloadAssetSecretVo> getDownloadAssetSecret(String name);

    Mono<DownloadAssetSecretVo> updateDownloadAssetSecret(String name,
        DownloadAssetSecretCommand command);

    Mono<DownloadAsset> deleteDownloadAsset(String name);

    Mono<ListResult<SoftwareCategory>> listCategories(SoftwareConsoleQuery query);

    Mono<SoftwareCategory> createCategory(SoftwareCategory category);

    Mono<SoftwareCategory> replaceCategory(String name, SoftwareCategory category);

    Mono<SoftwareCategory> updateCategory(String name, SoftwareCategory category);

    Mono<SoftwareCategory> deleteCategory(String name);

    Mono<ListResult<SoftwareTag>> listTags(SoftwareConsoleQuery query);

    Mono<SoftwareTag> getTag(String name);

    Mono<SoftwareTag> createTag(SoftwareTag tag);

    Mono<SoftwareTag> replaceTag(String name, SoftwareTag tag);

    Mono<SoftwareTag> updateTag(String name, SoftwareTag tag);

    Mono<SoftwareTag> deleteTag(String name);

    Mono<ListResult<SoftwareTopic>> listTopics(SoftwareConsoleQuery query);

    Mono<SoftwareTopic> createTopic(SoftwareTopic topic);

    Mono<SoftwareTopic> replaceTopic(String name, SoftwareTopic topic);

    Mono<SoftwareTopic> updateTopic(String name, SoftwareTopic topic);

    Mono<SoftwareTopic> deleteTopic(String name);
}
