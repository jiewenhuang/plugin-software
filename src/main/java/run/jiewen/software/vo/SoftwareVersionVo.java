package run.jiewen.software.vo;

import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import run.jiewen.software.extension.SoftwareVersion;

@Value
@Builder
public class SoftwareVersionVo {

    String name;

    String version;

    String build;

    LocalDate releaseDate;

    Long sizeBytes;

    String minOsVersion;

    String maxOsVersion;

    List<String> architectures;

    String locale;

    String changelog;

    String installGuide;

    List<String> installWarnings;

    String status;

    Integer assetCount;

    Long downloadCount;

    String primaryAssetName;

    public static SoftwareVersionVo from(SoftwareVersion version) {
        var metadata = version.getMetadata();
        var spec = version.getSpec();
        var status = version.getStatus();
        return SoftwareVersionVo.builder()
            .name(metadata == null ? null : metadata.getName())
            .version(spec == null ? null : spec.getVersion())
            .build(spec == null ? null : spec.getBuild())
            .releaseDate(spec == null ? null : spec.getReleaseDate())
            .sizeBytes(spec == null ? null : spec.getSizeBytes())
            .minOsVersion(spec == null ? null : spec.getMinOsVersion())
            .maxOsVersion(spec == null ? null : spec.getMaxOsVersion())
            .architectures(spec == null ? null : spec.getArchitectures())
            .locale(spec == null ? null : spec.getLocale())
            .changelog(spec == null ? null : spec.getChangelog())
            .installGuide(spec == null ? null : spec.getInstallGuide())
            .installWarnings(spec == null ? null : spec.getInstallWarnings())
            .status(spec == null ? null : spec.getStatus())
            .assetCount(status == null ? null : status.getAssetCount())
            .downloadCount(status == null ? null : status.getDownloadCount())
            .primaryAssetName(status == null ? null : status.getPrimaryAssetName())
            .build();
    }
}
