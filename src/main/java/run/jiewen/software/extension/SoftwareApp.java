package run.jiewen.software.extension;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = "software.jiewen.run", version = "v1alpha1", kind = "SoftwareApp",
    singular = "softwareapp", plural = "softwareapps")
public class SoftwareApp extends AbstractExtension {

    @Schema(requiredMode = REQUIRED)
    private Spec spec;

    private Status status;

    @Data
    public static class Spec {

        @Schema(requiredMode = REQUIRED, description = "Public URL slug, independent from metadata.name.")
        private String slug;

        @Schema(requiredMode = REQUIRED, description = "Display name shown to visitors.")
        private String displayName;

        private String subtitle;

        private String vendor;

        private String officialWebsite;

        private String icon;

        private String cover;

        private List<String> screenshots;

        private String summary;

        private String description;

        private String categoryName;

        private List<String> tagNames;

        private String platform;

        private String licenseType;

        private String releaseChannel;

        private String locale;

        private List<String> architectures;

        private Double editorRating;

        private Boolean featured;

        private Boolean recommended;

        private Boolean published;

        private Instant publishedAt;

        private Seo seo;

        private List<String> relatedAppNames;

        private List<String> relatedPostNames;
    }

    @Data
    public static class Seo {

        private String title;

        private String description;

        private List<String> keywords;
    }

    @Data
    public static class Status {

        private String latestVersionName;

        private String latestVersion;

        private LocalDate latestReleaseDate;

        private String minOsVersion;

        private Long sizeBytes;

        private Long downloadCount;

        private Long likeCount;

        private Long commentCount;

        private String health;
    }
}
