package run.jiewen.software.vo;

import lombok.Builder;
import lombok.Value;
import run.jiewen.software.extension.DownloadAsset;

@Value
@Builder
public class DownloadAssetVo {

    String name;

    String versionName;

    String title;

    String type;

    String provider;

    String url;

    Boolean requiresLogin;

    Boolean requiresCaptcha;

    String region;

    Integer priority;

    Long downloadCount;

    public static DownloadAssetVo fromPublicList(DownloadAsset asset) {
        return from(asset, false);
    }

    public static DownloadAssetVo fromDownloadGate(DownloadAsset asset) {
        return from(asset, true);
    }

    private static DownloadAssetVo from(DownloadAsset asset, boolean includeUrl) {
        var metadata = asset.getMetadata();
        var spec = asset.getSpec();
        var status = asset.getStatus();
        return DownloadAssetVo.builder()
            .name(metadata == null ? null : metadata.getName())
            .versionName(spec == null ? null : spec.getVersionName())
            .title(spec == null ? null : spec.getTitle())
            .type(spec == null ? null : spec.getType())
            .provider(spec == null ? null : spec.getProvider())
            .url(includeUrl && spec != null ? spec.getUrl() : null)
            .requiresLogin(spec != null && Boolean.TRUE.equals(spec.getRequiresLogin()))
            .requiresCaptcha(spec != null && Boolean.TRUE.equals(spec.getRequiresCaptcha()))
            .region(spec == null ? null : spec.getRegion())
            .priority(spec == null ? null : spec.getPriority())
            .downloadCount(status == null ? null : status.getDownloadCount())
            .build();
    }
}
