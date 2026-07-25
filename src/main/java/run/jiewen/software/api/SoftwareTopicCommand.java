package run.jiewen.software.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import run.jiewen.software.extension.SoftwareTopic;

/** Console write contract. Metadata and status are deliberately server-owned. */
public record SoftwareTopicCommand(
    @Schema(requiredMode = REQUIRED) SoftwareTopic.Spec spec
) {

    public SoftwareTopic toExtension() {
        var extension = new SoftwareTopic();
        extension.setSpec(spec);
        return extension;
    }
}
