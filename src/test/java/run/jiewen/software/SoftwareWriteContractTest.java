package run.jiewen.software;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import io.swagger.v3.oas.annotations.media.Schema;
import org.junit.jupiter.api.Test;
import run.jiewen.software.api.DownloadAssetCommand;
import run.jiewen.software.api.DownloadAssetSecretCommand;
import run.jiewen.software.api.SoftwareAppCommand;
import run.jiewen.software.api.SoftwareCategoryCommand;
import run.jiewen.software.api.SoftwareTagCommand;
import run.jiewen.software.api.SoftwareTopicCommand;
import run.jiewen.software.api.SoftwareVersionCommand;
import run.jiewen.software.vo.DownloadAssetConsoleVo;
import run.jiewen.software.vo.DownloadAssetSecretVo;

class SoftwareWriteContractTest {

    @Test
    void ordinaryCommandsCannotWriteMetadataOrStatus() {
        assertThat(recordFields(SoftwareAppCommand.class)).containsExactly("spec");
        assertThat(recordFields(SoftwareVersionCommand.class)).containsExactly("spec");
        assertThat(recordFields(SoftwareCategoryCommand.class)).containsExactly("spec");
        assertThat(recordFields(SoftwareTagCommand.class)).containsExactly("spec");
        assertThat(recordFields(SoftwareTopicCommand.class)).containsExactly("spec");
        assertThat(recordFields(DownloadAssetCommand.class)).containsExactly("spec");
    }

    @Test
    void ordinaryDownloadContractIsSecretFree() {
        assertThat(recordFields(DownloadAssetCommand.DownloadAssetSpec.class))
            .containsExactlyInAnyOrder(
                "appName", "versionName", "title", "type", "provider", "requiresLogin",
                "requiresCaptcha", "region", "priority", "enabled")
            .doesNotContain("url", "storageKey", "extractionCode", "passwordSecretName");
        assertThat(fields(DownloadAssetConsoleVo.Spec.class))
            .doesNotContain("url", "storageKey", "extractionCode", "passwordSecretName")
            .contains("hasUrl", "hasStorageKey", "hasExtractionCode", "hasPasswordSecret");
    }

    @Test
    void sensitiveDownloadContractRemainsExplicitAndIsolated() {
        assertThat(recordFields(DownloadAssetSecretCommand.class))
            .containsExactly("url", "storageKey", "extractionCode", "passwordSecretName");
        assertThat(fields(DownloadAssetSecretVo.class))
            .containsExactlyInAnyOrder(
                "name", "url", "storageKey", "extractionCode", "passwordSecretName");
    }

    @Test
    void downloadCommandUsesAUniqueOpenApiSpecName() {
        var schema = DownloadAssetCommand.DownloadAssetSpec.class.getAnnotation(Schema.class);

        assertThat(schema).isNotNull();
        assertThat(schema.name()).isEqualTo("DownloadAssetCommandSpec");
    }

    private static Set<String> fields(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
            .filter(field -> !field.isSynthetic())
            .map(field -> field.getName())
            .collect(Collectors.toSet());
    }

    private static Set<String> recordFields(Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
            .map(RecordComponent::getName)
            .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}
