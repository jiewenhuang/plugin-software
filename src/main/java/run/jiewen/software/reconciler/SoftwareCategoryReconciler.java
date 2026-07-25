package run.jiewen.software.reconciler;

import org.springframework.stereotype.Component;
import run.halo.app.extension.controller.Controller;
import run.halo.app.extension.controller.ControllerBuilder;
import run.halo.app.extension.controller.Reconciler;
import run.jiewen.software.extension.SoftwareCategory;

@Component
public class SoftwareCategoryReconciler implements Reconciler<Reconciler.Request> {

    private final SoftwareStatusService statusService;

    public SoftwareCategoryReconciler(SoftwareStatusService statusService) {
        this.statusService = statusService;
    }

    @Override
    public Result reconcile(Request request) {
        statusService.reconcileCategory(request.name());
        return Result.doNotRetry();
    }

    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder.extension(new SoftwareCategory()).build();
    }
}
