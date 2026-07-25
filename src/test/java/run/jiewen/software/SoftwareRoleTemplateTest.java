package run.jiewen.software;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SoftwareRoleTemplateTest {

    @Test
    void anonymousRoleAllowsPublicDownloadSubresource() throws IOException {
        var roleTemplate = Files.readString(
            Path.of("src/main/resources/extensions/roleTemplate.yaml"));

        assertThat(roleTemplate).contains("rbac.authorization.halo.run/aggregate-to-anonymous");
        assertThat(roleTemplate).contains("apps/download");
        assertThat(roleTemplate).contains("download-options");
        assertThat(roleTemplate).contains("resources: [ \"download-grants\" ]");
        assertThat(roleTemplate).contains("verbs: [ \"create\" ]");
    }

    @Test
    void sensitiveDownloadConfigurationHasAnIndependentRole() throws IOException {
        var roleTemplate = Files.readString(
            Path.of("src/main/resources/extensions/roleTemplate.yaml"));
        var secretRole = java.util.Arrays.stream(roleTemplate.split("---"))
            .filter(document -> document.contains("name: role-template-software-secret"))
            .findFirst()
            .orElseThrow();
        var ordinaryViewRole = java.util.Arrays.stream(roleTemplate.split("---"))
            .filter(document -> document.contains("name: role-template-software-view"))
            .findFirst()
            .orElseThrow();

        assertThat(secretRole).contains("plugin:software:secret", "download-assets/secrets");
        assertThat(ordinaryViewRole).doesNotContain("download-assets/secrets");
    }

    @Test
    void builtInRolesDoNotGrantRawExtensionApiAccess() throws IOException {
        var roleTemplate = Files.readString(
            Path.of("src/main/resources/extensions/roleTemplate.yaml"));

        assertThat(roleTemplate).doesNotContain("apiGroups: [ \"software.jiewen.run\" ]");
    }
}
