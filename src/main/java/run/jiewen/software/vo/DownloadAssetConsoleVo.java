package run.jiewen.software.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;
import run.halo.app.extension.MetadataOperator;
import run.jiewen.software.extension.DownloadAsset;

/** Secret-free Console representation suitable for the ordinary view role. */
@Value
@Builder
public class DownloadAssetConsoleVo {

    MetadataOperator metadata;

    Spec spec;

    DownloadAsset.Status status;

    @Value
    @Builder
    @Schema(name = "DownloadAssetConsoleSpec")
    public static class Spec {
        String appName;
        String versionName;
        String title;
        String type;
        String provider;
        Boolean requiresLogin;
        Boolean requiresCaptcha;
        String region;
        Integer priority;
        Boolean enabled;
        boolean hasUrl;
        boolean hasStorageKey;
        boolean hasExtractionCode;
        boolean hasPasswordSecret;
    }

    public static DownloadAssetConsoleVo from(DownloadAsset asset) {
        var spec = asset.getSpec();
        return DownloadAssetConsoleVo.builder()
            .metadata(asset.getMetadata())
            .status(asset.getStatus())
            .spec(Spec.builder()
                .appName(spec == null ? null : spec.getAppName())
                .versionName(spec == null ? null : spec.getVersionName())
                .title(spec == null ? null : spec.getTitle())
                .type(spec == null ? null : spec.getType())
                .provider(spec == null ? null : spec.getProvider())
                .requiresLogin(spec == null ? null : spec.getRequiresLogin())
                .requiresCaptcha(spec == null ? null : spec.getRequiresCaptcha())
                .region(spec == null ? null : spec.getRegion())
                .priority(spec == null ? null : spec.getPriority())
                .enabled(spec == null ? null : spec.getEnabled())
                .hasUrl(spec != null && hasText(spec.getUrl()))
                .hasStorageKey(spec != null && hasText(spec.getStorageKey()))
                .hasExtractionCode(spec != null && hasText(spec.getExtractionCode()))
                .hasPasswordSecret(spec != null && hasText(spec.getPasswordSecretName()))
                .build())
            .build();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
