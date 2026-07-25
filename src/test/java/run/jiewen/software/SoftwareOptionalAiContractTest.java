package run.jiewen.software;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.jiewen.software.ai.SoftwareAiConsoleEndpoint;
import run.jiewen.software.ai.SoftwareAiService;
import run.jiewen.software.finders.SoftwareConsoleService;

class SoftwareOptionalAiContractTest {

    @Test
    void manifestDeclaresAiFoundationAsVersionedOptionalDependency() throws Exception {
        var manifest = Files.readString(Path.of("src/main/resources/plugin.yaml"));

        assertThat(manifest)
            .contains("ai-foundation?: \">=1.0.0-SNAPSHOT & <2.0.0\"")
            .doesNotContain("ai-foundation: \"*\"");
    }

    @Test
    void coreConsoleEndpointCanBeConstructedWithoutAiTypes() {
        var constructor = SoftwareConsoleEndpoint.class.getDeclaredConstructors()[0];

        assertThat(Arrays.asList(constructor.getParameterTypes()))
            .containsExactly(SoftwareConsoleService.class, ReactiveSettingFetcher.class);
        assertThat(Arrays.stream(SoftwareConsoleEndpoint.class.getDeclaredFields())
            .map(field -> field.getType().getName()))
            .noneMatch(name -> name.contains("SoftwareAi"));
    }

    @Test
    void aiBeansAreConditionalOnAiFoundationApi() {
        assertAiFoundationCondition(SoftwareAiService.class);
        assertAiFoundationCondition(SoftwareAiConsoleEndpoint.class);
    }

    @Test
    void settingsDoNotRequireAiFoundationFormComponents() throws Exception {
        var settings = Files.readString(Path.of("src/main/resources/extensions/settings.yaml"));

        assertThat(settings)
            .doesNotContain("$formkit: aiModelSelector")
            .contains("name: languageModelName");
    }

    private static void assertAiFoundationCondition(Class<?> type) {
        var condition = type.getAnnotation(ConditionalOnClass.class);
        assertThat(condition).isNotNull();
        assertThat(condition.name()).containsExactly("run.halo.aifoundation.AiModelService");
    }
}
