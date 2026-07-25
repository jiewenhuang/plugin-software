package run.jiewen.software.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;

/** Request for a one-time download decision. */
public record DownloadGrantRequest(
    @Schema(requiredMode = REQUIRED) String slug,
    @Schema(description = "Optional DownloadAsset metadata.name; latest released primary asset is used when absent.")
    String assetName,
    @Schema(description = "Captcha provider response token when the asset requires verification.")
    String captchaToken
) {
}
