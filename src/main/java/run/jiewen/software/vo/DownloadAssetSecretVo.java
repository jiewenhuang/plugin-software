package run.jiewen.software.vo;

import lombok.Builder;
import lombok.Value;
import run.jiewen.software.extension.DownloadAsset;

/** Sensitive Console representation guarded by plugin:software:secret. */
@Value
@Builder
public class DownloadAssetSecretVo {

    String name;
    String url;
    String storageKey;
    String extractionCode;
    String passwordSecretName;

    public static DownloadAssetSecretVo from(DownloadAsset asset) {
        var spec = asset.getSpec();
        return DownloadAssetSecretVo.builder()
            .name(asset.getMetadata() == null ? null : asset.getMetadata().getName())
            .url(spec == null ? null : spec.getUrl())
            .storageKey(spec == null ? null : spec.getStorageKey())
            .extractionCode(spec == null ? null : spec.getExtractionCode())
            .passwordSecretName(spec == null ? null : spec.getPasswordSecretName())
            .build();
    }
}
