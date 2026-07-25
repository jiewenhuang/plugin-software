package run.jiewen.software.reconciler;

import java.util.LinkedHashSet;
import org.springframework.stereotype.Component;
import run.halo.app.extension.ExtensionClient;
import run.halo.app.extension.controller.Controller;
import run.halo.app.extension.controller.ControllerBuilder;
import run.halo.app.extension.controller.Reconciler;
import run.jiewen.software.extension.SoftwareVersion;

@Component
public class SoftwareVersionReconciler implements Reconciler<Reconciler.Request> {

    private final ExtensionClient client;
    private final SoftwareStatusService statusService;

    public SoftwareVersionReconciler(ExtensionClient client, SoftwareStatusService statusService) {
        this.client = client;
        this.statusService = statusService;
    }

    @Override
    public Result reconcile(Request request) {
        var version = client.fetch(SoftwareVersion.class, request.name());
        if (version.isPresent()) {
            var status = version.get().getStatus();
            var previousAppName = status == null ? null : status.getObservedAppName();
            var spec = version.get().getSpec();
            var currentAppName = spec == null ? null : spec.getAppName();
            statusService.reconcileVersion(request.name());
            distinctNames(previousAppName, currentAppName)
                .forEach(statusService::reconcileApp);
        } else {
            statusService.reconcileAllApps();
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
        return builder.extension(new SoftwareVersion()).build();
    }
}
