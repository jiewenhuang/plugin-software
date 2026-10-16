package run.jiewen.software.extension;

import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

/** Internal durable creation receipt. No request payload, secret value or access token is stored. */
@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = "operations.software.jiewen.run", version = "v1alpha1", kind = "SoftwareCreateOperation",
    singular = "softwarecreateoperation", plural = "softwarecreateoperations")
public class SoftwareCreateOperation extends AbstractExtension {
    private Spec spec;

    @Data
    public static class Spec {
        private String resourceType;
        private String requestDigest;
        private String targetName;
        private String state;
        private Instant startedAt;
        private Long resourceVersion;
        private String errorCode;
    }
}
