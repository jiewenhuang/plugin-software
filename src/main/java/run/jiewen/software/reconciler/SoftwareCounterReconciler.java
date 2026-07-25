package run.jiewen.software.reconciler;

import static run.halo.app.extension.index.query.Queries.startsWith;

import org.springframework.stereotype.Component;
import run.halo.app.core.extension.Counter;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.controller.Controller;
import run.halo.app.extension.controller.ControllerBuilder;
import run.halo.app.extension.controller.Reconciler;

@Component
public class SoftwareCounterReconciler implements Reconciler<Reconciler.Request> {

    private final SoftwareStatusService statusService;

    public SoftwareCounterReconciler(SoftwareStatusService statusService) {
        this.statusService = statusService;
    }

    @Override
    public Result reconcile(Request request) {
        if (request.name().startsWith(SoftwareStatusService.COUNTER_PREFIX)) {
            statusService.reconcileApp(
                request.name().substring(SoftwareStatusService.COUNTER_PREFIX.length()));
        }
        return Result.doNotRetry();
    }

    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder.extension(new Counter())
            .syncAllListOptions(ListOptions.builder()
                .andQuery(startsWith("metadata.name", SoftwareStatusService.COUNTER_PREFIX))
                .build())
            .build();
    }
}
