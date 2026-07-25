package run.jiewen.software.reconciler;

import java.util.LinkedHashSet;
import org.springframework.stereotype.Component;
import run.halo.app.extension.ExtensionClient;
import run.halo.app.extension.controller.Controller;
import run.halo.app.extension.controller.ControllerBuilder;
import run.halo.app.extension.controller.Reconciler;
import run.jiewen.software.extension.DownloadAsset;

@Component
public class DownloadAssetReconciler implements Reconciler<Reconciler.Request> {

    private final ExtensionClient client;
    private final SoftwareStatusService statusService;

    public DownloadAssetReconciler(ExtensionClient client, SoftwareStatusService statusService) {
        this.client = client;
        this.statusService = statusService;
    }

    @Override
    public Result reconcile(Request request) {
        var asset = client.fetch(DownloadAsset.class, request.name());
        if (asset.isPresent()) {
            var status = asset.get().getStatus();
            var previousVersionName = status == null ? null : status.getObservedVersionName();
            var previousAppName = status == null ? null : status.getObservedAppName();
            var spec = asset.get().getSpec();
            var currentVersionName = spec == null ? null : spec.getVersionName();
            var currentAppName = spec == null ? null : spec.getAppName();
            statusService.reconcileAsset(request.name());
            distinctNames(previousVersionName, currentVersionName)
                .forEach(statusService::reconcileVersion);
            distinctNames(previousAppName, currentAppName)
                .forEach(statusService::reconcileApp);
        } else {
            statusService.reconcileAllVersionsAndApps();
        }
        return Result.doNotRetry();
    }

    private static LinkedHashSet<String> distinctNames(String previous, String current) {
        var names = new LinkedHashSet<String>();
        if (previous != null && !previous.isBlank()) {
            names.add(previous);
        }
        if (current != null && !current.isBlank()) {
            names.add(current);
        }
        return names;
    }

    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder.extension(new DownloadAsset()).build();
    }
}
