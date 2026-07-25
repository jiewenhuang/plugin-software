package run.jiewen.software.extension;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = "software.jiewen.run", version = "v1alpha1", kind = "SoftwareCategory",
    singular = "softwarecategory", plural = "softwarecategories")
public class SoftwareCategory extends AbstractExtension {

    @Schema(requiredMode = REQUIRED)
    private Spec spec;

    private Status status;

    @Data
    @Schema(name = "SoftwareCategorySpec")
    public static class Spec {

        @Schema(requiredMode = REQUIRED)
        private String slug;

        @Schema(requiredMode = REQUIRED)
        private String displayName;

        private String parentName;

        private String description;

        private String icon;

        private Integer priority;

        private Boolean visible;

        private SoftwareApp.Seo seo;
    }

    @Data
    @Schema(name = "SoftwareCategoryStatus")
    public static class Status {

        private Integer appCount;

        private Long observedVersion;
    }
}
