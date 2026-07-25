package run.jiewen.software.extension;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = "software.jiewen.run", version = "v1alpha1", kind = "DownloadAsset",
    singular = "downloadasset", plural = "downloadassets")
public class DownloadAsset extends AbstractExtension {

    @Schema(requiredMode = REQUIRED)
    private Spec spec;

    private Status status;

    @Data
    @Schema(name = "DownloadAssetSpec")
    public static class Spec {

        @Schema(requiredMode = REQUIRED, description = "SoftwareApp metadata.name.")
        private String appName;

        @Schema(requiredMode = REQUIRED, description = "SoftwareVersion metadata.name.")
        private String versionName;

        @Schema(requiredMode = REQUIRED)
        private String title;

        private String type;

        private String provider;

        private String url;

        private String storageKey;

        private String extractionCode;

        private String passwordSecretName;

        private Boolean requiresLogin;

        private Boolean requiresCaptcha;

        private String region;

        private Integer priority;

        private Boolean enabled;
    }

    @Data
    @Schema(name = "DownloadAssetStatus")
    public static class Status {

        private String health;

        private Instant lastCheckedAt;

        private Long downloadCount;

        private String observedAppName;

        private String observedVersionName;

        private Long observedVersion;
    }
}
