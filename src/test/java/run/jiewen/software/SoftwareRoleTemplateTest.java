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
    }
}
