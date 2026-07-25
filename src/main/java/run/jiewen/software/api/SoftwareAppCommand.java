package run.jiewen.software.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import run.jiewen.software.extension.SoftwareApp;

/** Console write contract. Metadata and status are deliberately server-owned. */
public record SoftwareAppCommand(
    @Schema(requiredMode = REQUIRED) SoftwareApp.Spec spec
) {

    public SoftwareApp toExtension() {
        var extension = new SoftwareApp();
        extension.setSpec(spec);
        return extension;
    }
}
