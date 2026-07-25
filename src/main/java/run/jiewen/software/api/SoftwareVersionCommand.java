package run.jiewen.software.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import run.jiewen.software.extension.SoftwareVersion;

/** Console write contract. Metadata and status are deliberately server-owned. */
public record SoftwareVersionCommand(
    @Schema(requiredMode = REQUIRED) SoftwareVersion.Spec spec
) {

    public SoftwareVersion toExtension() {
        var extension = new SoftwareVersion();
        extension.setSpec(spec);
        return extension;
    }
}
