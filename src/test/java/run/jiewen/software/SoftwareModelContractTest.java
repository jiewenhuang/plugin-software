package run.jiewen.software;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.jupiter.api.Test;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

class SoftwareModelContractTest {

    private static final String MODEL_PACKAGE = "run.jiewen.software.extension";
    private static final String GROUP = "software.jiewen.run";
    private static final String VERSION = "v1alpha1";

    @Test
    void v1ModelsDeclareExpectedGvk() throws Exception {
        var contracts = List.of(
            new ModelContract("SoftwareApp", "SoftwareApp", "softwareapp", "softwareapps"),
            new ModelContract("SoftwareVersion", "SoftwareVersion", "softwareversion",
                "softwareversions"),
            new ModelContract("DownloadAsset", "DownloadAsset", "downloadasset",
                "downloadassets"),
            new ModelContract("SoftwareCategory", "SoftwareCategory", "softwarecategory",
                "softwarecategories"),
            new ModelContract("SoftwareTag", "SoftwareTag", "softwaretag", "softwaretags"),
            new ModelContract("SoftwareTopic", "SoftwareTopic", "softwaretopic",
                "softwaretopics")
        );

        for (var contract : contracts) {
            var type = Class.forName(MODEL_PACKAGE + "." + contract.className());

            assertThat(AbstractExtension.class).isAssignableFrom(type);
            assertThat(Modifier.isAbstract(type.getModifiers())).isFalse();

            var gvk = type.getAnnotation(GVK.class);
            assertThat(gvk).isNotNull();
            assertThat(gvk.group()).isEqualTo(GROUP);
            assertThat(gvk.version()).isEqualTo(VERSION);
            assertThat(gvk.kind()).isEqualTo(contract.kind());
            assertThat(gvk.singular()).isEqualTo(contract.singular());
            assertThat(gvk.plural()).isEqualTo(contract.plural());
        }
    }

    @Test
    void softwareAppSeparatesHaloResourceNameFromPublicSlug() throws Exception {
        var appType = Class.forName(MODEL_PACKAGE + ".SoftwareApp");
        var specType = Class.forName(MODEL_PACKAGE + ".SoftwareApp$Spec");
        var statusType = Class.forName(MODEL_PACKAGE + ".SoftwareApp$Status");

        assertThat(appType.getDeclaredField("spec").getType()).isEqualTo(specType);
        assertThat(appType.getDeclaredField("status").getType()).isEqualTo(statusType);

        assertThat(specType.getDeclaredField("slug").getType()).isEqualTo(String.class);
        assertThat(specType.getDeclaredField("displayName").getType()).isEqualTo(String.class);
        assertThat(specType.getDeclaredField("categoryName").getType()).isEqualTo(String.class);
        assertThat(specType.getDeclaredField("tagNames").getType()).isEqualTo(List.class);

        assertThat(statusType.getDeclaredField("latestVersion").getType()).isEqualTo(String.class);
        assertThat(statusType.getDeclaredField("downloadCount").getType()).isEqualTo(Long.class);
    }

    record ModelContract(String className, String kind, String singular, String plural) {
    }
}
