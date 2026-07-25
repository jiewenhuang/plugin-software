package run.jiewen.software.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import run.jiewen.software.extension.SoftwareTag;

/** Console write contract. Metadata and status are deliberately server-owned. */
public record SoftwareTagCommand(
    @Schema(requiredMode = REQUIRED) SoftwareTag.Spec spec
) {

    public SoftwareTag toExtension() {
        var extension = new SoftwareTag();
        extension.setSpec(spec);
        return extension;
    }
}
