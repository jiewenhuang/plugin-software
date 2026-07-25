package run.jiewen.software.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import run.jiewen.software.extension.SoftwareCategory;

/** Console write contract. Metadata and status are deliberately server-owned. */
public record SoftwareCategoryCommand(
    @Schema(requiredMode = REQUIRED) SoftwareCategory.Spec spec
) {

    public SoftwareCategory toExtension() {
        var extension = new SoftwareCategory();
        extension.setSpec(spec);
        return extension;
    }
}
