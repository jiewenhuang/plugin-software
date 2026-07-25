package run.jiewen.software.api;

import io.swagger.v3.oas.annotations.media.Schema;

/** Sensitive download configuration accepted only by the secret-management endpoint. */
public record DownloadAssetSecretCommand(
    @Schema(description = "Final download URL.") String url,
    @Schema(description = "Provider-specific storage key.") String storageKey,
    @Schema(description = "Extraction code shown only after a download grant is issued.")
    String extractionCode,
    @Schema(description = "Halo Secret metadata.name containing password/url overrides.")
    String passwordSecretName
) {
}
