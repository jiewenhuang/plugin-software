package run.jiewen.software.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import run.jiewen.software.extension.DownloadAsset;

/**
 * Non-secret download source contract. Secret material is ignored here and must be managed through
 * the dedicated secret endpoint.
 */
public record DownloadAssetCommand(
    @Schema(requiredMode = REQUIRED) DownloadAssetSpec spec
) {

    public DownloadAsset toExtension() {
        var extension = new DownloadAsset();
        extension.setSpec(spec == null ? null : spec.toSpec());
        return extension;
    }

    @Schema(name = "DownloadAssetCommandSpec")
    public record DownloadAssetSpec(
        String appName,
        String versionName,
        String title,
        String type,
        String provider,
        Boolean requiresLogin,
        Boolean requiresCaptcha,
        String region,
        Integer priority,
        Boolean enabled
    ) {

        DownloadAsset.Spec toSpec() {
            var target = new DownloadAsset.Spec();
            target.setAppName(appName);
            target.setVersionName(versionName);
            target.setTitle(title);
            target.setType(type);
            target.setProvider(provider);
            target.setRequiresLogin(requiresLogin);
            target.setRequiresCaptcha(requiresCaptcha);
            target.setRegion(region);
            target.setPriority(priority);
            target.setEnabled(enabled);
            return target;
        }
    }
}
