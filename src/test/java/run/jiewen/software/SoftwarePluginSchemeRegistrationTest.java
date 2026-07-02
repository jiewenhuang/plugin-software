package run.jiewen.software;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import run.halo.app.extension.Extension;
import run.halo.app.extension.Scheme;
import run.halo.app.extension.SchemeManager;
import run.halo.app.extension.index.IndexSpecs;
import run.halo.app.plugin.PluginContext;
import run.jiewen.software.extension.DownloadAsset;
import run.jiewen.software.extension.SoftwareApp;
import run.jiewen.software.extension.SoftwareCategory;
import run.jiewen.software.extension.SoftwareTag;
import run.jiewen.software.extension.SoftwareTopic;
import run.jiewen.software.extension.SoftwareVersion;

class SoftwarePluginSchemeRegistrationTest {

    @Test
    void registersV1SoftwareModelsOnStart() {
        var schemeManager = mock(SchemeManager.class);
        var plugin = new SoftwarePlugin(mock(PluginContext.class), schemeManager);

        plugin.start();

        verifyRegister(schemeManager, SoftwareApp.class);
        verifyRegister(schemeManager, SoftwareVersion.class);
        verifyRegister(schemeManager, DownloadAsset.class);
        verifyRegister(schemeManager, SoftwareCategory.class);
        verifyRegister(schemeManager, SoftwareTag.class);
        verifyRegister(schemeManager, SoftwareTopic.class);
    }

    @Test
    void unregistersV1SoftwareModelsOnStop() {
        var schemeManager = mock(SchemeManager.class);
        var plugin = new SoftwarePlugin(mock(PluginContext.class), schemeManager);

        plugin.stop();

        verify(schemeManager).unregister(Scheme.buildFromType(SoftwareApp.class));
        verify(schemeManager).unregister(Scheme.buildFromType(SoftwareVersion.class));
        verify(schemeManager).unregister(Scheme.buildFromType(DownloadAsset.class));
        verify(schemeManager).unregister(Scheme.buildFromType(SoftwareCategory.class));
        verify(schemeManager).unregister(Scheme.buildFromType(SoftwareTag.class));
        verify(schemeManager).unregister(Scheme.buildFromType(SoftwareTopic.class));
    }

    private static <E extends Extension> void verifyRegister(SchemeManager schemeManager,
        Class<E> type) {
        verify(schemeManager, times(1)).register(eq(type), any(Consumer.class));
    }
}
