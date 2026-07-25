package run.jiewen.software.reconciler;

import org.springframework.stereotype.Component;
import run.halo.app.extension.ExtensionClient;
import run.halo.app.extension.controller.Controller;
import run.halo.app.extension.controller.ControllerBuilder;
import run.halo.app.extension.controller.Reconciler;
import run.jiewen.software.extension.SoftwareApp;

@Component
public class SoftwareAppReconciler implements Reconciler<Reconciler.Request> {

    private final ExtensionClient client;
    private final SoftwareStatusService statusService;

    public SoftwareAppReconciler(ExtensionClient client, SoftwareStatusService statusService) {
        this.client = client;
        this.statusService = statusService;
    }

    @Override
    public Result reconcile(Request request) {
        if (client.fetch(SoftwareApp.class, request.name()).isPresent()) {
            statusService.reconcileApp(request.name());
        }
        statusService.reconcileTaxonomies();
        return Result.doNotRetry();
    }

    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder.extension(new SoftwareApp()).build();
    }
}
