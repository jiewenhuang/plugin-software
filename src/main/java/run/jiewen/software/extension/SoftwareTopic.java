package run.jiewen.software.extension;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = "software.jiewen.run", version = "v1alpha1", kind = "SoftwareTopic",
    singular = "softwaretopic", plural = "softwaretopics")
public class SoftwareTopic extends AbstractExtension {

    @Schema(requiredMode = REQUIRED)
    private Spec spec;

    private Status status;

    @Data
    public static class Spec {

        @Schema(requiredMode = REQUIRED)
        private String slug;

        @Schema(requiredMode = REQUIRED)
        private String title;

        private String subtitle;

        private String description;

        private String cover;

        private List<Item> items;

        private Boolean featured;

        private Boolean published;

        private Integer priority;

        private SoftwareApp.Seo seo;
    }

    @Data
    public static class Item {

        @Schema(requiredMode = REQUIRED, description = "SoftwareApp metadata.name.")
        private String appName;

        private String reason;

        private Integer priority;
    }

    @Data
    public static class Status {

        private Integer appCount;
    }
}
