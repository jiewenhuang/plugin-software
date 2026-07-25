package run.jiewen.software.reconciler;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import run.halo.app.extension.ExtensionClient;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.controller.Reconciler;
import run.jiewen.software.extension.DownloadAsset;
import run.jiewen.software.extension.SoftwareApp;
import run.jiewen.software.extension.SoftwareVersion;

@ExtendWith(MockitoExtension.class)
class SoftwareReconcilersTest {

    @Mock
    ExtensionClient client;

    @Mock
    SoftwareStatusService statusService;

    @Test
    void assetReconcilerUpdatesAssetVersionAndApp() {
        var asset = asset();
        when(client.fetch(DownloadAsset.class, "asset-1")).thenReturn(Optional.of(asset));

        new DownloadAssetReconciler(client, statusService)
            .reconcile(new Reconciler.Request("asset-1"));

        verify(statusService).reconcileAsset("asset-1");
        verify(statusService).reconcileVersion("version-1");
        verify(statusService).reconcileApp("app-1");
        verify(statusService, never()).reconcileAllVersionsAndApps();
    }

    @Test
    void deletedAssetTriggersAggregateRepair() {
        when(client.fetch(DownloadAsset.class, "asset-1")).thenReturn(Optional.empty());

        new DownloadAssetReconciler(client, statusService)
            .reconcile(new Reconciler.Request("asset-1"));

        verify(statusService).reconcileAllVersionsAndApps();
        verify(statusService, never()).reconcileAsset("asset-1");
    }

    @Test
    void movedAssetRepairsBothPreviousAndCurrentParents() {
        var asset = asset();
        asset.getSpec().setAppName("app-2");
        asset.getSpec().setVersionName("version-2");
        var status = new DownloadAsset.Status();
        status.setObservedAppName("app-1");
        status.setObservedVersionName("version-1");
        asset.setStatus(status);
        when(client.fetch(DownloadAsset.class, "asset-1")).thenReturn(Optional.of(asset));

        new DownloadAssetReconciler(client, statusService)
            .reconcile(new Reconciler.Request("asset-1"));

        verify(statusService).reconcileAsset("asset-1");
        verify(statusService).reconcileVersion("version-1");
        verify(statusService).reconcileVersion("version-2");
        verify(statusService).reconcileApp("app-1");
        verify(statusService).reconcileApp("app-2");
    }

    @Test
    void versionReconcilerUpdatesVersionAndOwningApp() {
        var version = version();
        when(client.fetch(SoftwareVersion.class, "version-1")).thenReturn(Optional.of(version));

        new SoftwareVersionReconciler(client, statusService)
            .reconcile(new Reconciler.Request("version-1"));

        verify(statusService).reconcileVersion("version-1");
        verify(statusService).reconcileApp("app-1");
        verify(statusService, never()).reconcileAllApps();
    }

    @Test
    void deletedVersionTriggersAppRepair() {
        when(client.fetch(SoftwareVersion.class, "version-1")).thenReturn(Optional.empty());

        new SoftwareVersionReconciler(client, statusService)
            .reconcile(new Reconciler.Request("version-1"));

        verify(statusService).reconcileAllApps();
        verify(statusService, never()).reconcileVersion("version-1");
    }

    @Test
    void movedVersionRepairsBothPreviousAndCurrentApps() {
        var version = version();
        version.getSpec().setAppName("app-2");
        var status = new SoftwareVersion.Status();
        status.setObservedAppName("app-1");
        version.setStatus(status);
        when(client.fetch(SoftwareVersion.class, "version-1")).thenReturn(Optional.of(version));

        new SoftwareVersionReconciler(client, statusService)
            .reconcile(new Reconciler.Request("version-1"));

        verify(statusService).reconcileVersion("version-1");
        verify(statusService).reconcileApp("app-1");
        verify(statusService).reconcileApp("app-2");
    }

    @Test
    void appReconcilerAlwaysRepairsTaxonomies() {
        when(client.fetch(SoftwareApp.class, "app-1")).thenReturn(Optional.of(app()));

        new SoftwareAppReconciler(client, statusService)
            .reconcile(new Reconciler.Request("app-1"));

        verify(statusService).reconcileApp("app-1");
        verify(statusService).reconcileTaxonomies();
    }

    @Test
    void taxonomyReconcilersDelegateToStatusService() {
        new SoftwareCategoryReconciler(statusService)
            .reconcile(new Reconciler.Request("category-1"));
        new SoftwareTagReconciler(statusService)
            .reconcile(new Reconciler.Request("tag-1"));
        new SoftwareTopicReconciler(statusService)
            .reconcile(new Reconciler.Request("topic-1"));

        verify(statusService).reconcileCategory("category-1");
        verify(statusService).reconcileTag("tag-1");
        verify(statusService).reconcileTopic("topic-1");
    }

    @Test
    void counterReconcilerOnlyHandlesSoftwareCounters() {
        var reconciler = new SoftwareCounterReconciler(statusService);

        reconciler.reconcile(new Reconciler.Request(
            SoftwareStatusService.COUNTER_PREFIX + "app-1"));
        reconciler.reconcile(new Reconciler.Request("posts.content.halo.run/post-1"));

        verify(statusService).reconcileApp("app-1");
        verify(statusService, never()).reconcileApp("post-1");
    }

    private static DownloadAsset asset() {
        var spec = new DownloadAsset.Spec();
        spec.setAppName("app-1");
        spec.setVersionName("version-1");
        var asset = new DownloadAsset();
        asset.setMetadata(metadata("asset-1"));
        asset.setSpec(spec);
        return asset;
    }

    private static SoftwareVersion version() {
        var spec = new SoftwareVersion.Spec();
        spec.setAppName("app-1");
        var version = new SoftwareVersion();
        version.setMetadata(metadata("version-1"));
        version.setSpec(spec);
        return version;
    }

    private static SoftwareApp app() {
        var app = new SoftwareApp();
        app.setMetadata(metadata("app-1"));
        app.setSpec(new SoftwareApp.Spec());
        return app;
    }

    private static Metadata metadata(String name) {
        var metadata = new Metadata();
        metadata.setName(name);
        return metadata;
    }
}
