package run.jiewen.software.extension;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = "software.jiewen.run", version = "v1alpha1", kind = "SoftwareVersion",
    singular = "softwareversion", plural = "softwareversions")
public class SoftwareVersion extends AbstractExtension {

    @Schema(requiredMode = REQUIRED)
    private Spec spec;

    private Status status;

    @Data
    @Schema(name = "SoftwareVersionSpec")
    public static class Spec {

        @Schema(requiredMode = REQUIRED, description = "SoftwareApp metadata.name.")
        private String appName;

        @Schema(requiredMode = REQUIRED)
        private String version;

        private String build;

        private LocalDate releaseDate;

        private Long sizeBytes;

        private String minOsVersion;

        private String maxOsVersion;

        private List<String> architectures;

        private String locale;

        private String changelog;

        private String installGuide;

        private List<String> installWarnings;

        private Checksum checksum;

        private String status;
    }

    @Data
    @Schema(name = "SoftwareVersionChecksum")
    public static class Checksum {

        private String sha256;

        private String md5;
    }

    @Data
    @Schema(name = "SoftwareVersionStatus")
    public static class Status {

        private Integer assetCount;

        private Long downloadCount;

        private String primaryAssetName;

        private String observedAppName;

        private Long observedVersion;
    }
}
