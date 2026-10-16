package run.jiewen.software.api;

import java.util.Set;
import org.springframework.beans.BeanWrapperImpl;
import run.halo.app.extension.AbstractExtension;
import run.jiewen.software.extension.*;

/** Closed set of managed resources, shared by transport adapters and domain mutations. */
public enum SoftwareResourceKind {
    APP("app", "apps", "软件", SoftwareApp.class, SoftwareApp.Spec.class,
        "slug displayName subtitle vendor officialWebsite icon cover screenshots summary description "
            + "categoryName tagNames platforms licenseType releaseChannel locale architectures "
            + "editorRating featured recommended seo relatedAppNames relatedPostNames",
        "slug displayName", ""),
    VERSION("version", "versions", "版本", SoftwareVersion.class, SoftwareVersion.Spec.class,
        "appName version build releaseDate sizeBytes minOsVersion maxOsVersion architectures locale "
            + "changelog installGuide installWarnings checksum status",
        "appName version", "appName"),
    DOWNLOAD_ASSET("download_asset", "download_assets", "下载源", DownloadAsset.class,
        DownloadAsset.Spec.class,
        "appName versionName title type provider requiresLogin requiresCaptcha region priority enabled",
        "appName versionName title", "appName versionName"),
    CATEGORY("category", "categories", "分类", SoftwareCategory.class, SoftwareCategory.Spec.class,
        "slug displayName parentName description icon priority visible seo",
        "slug displayName", ""),
    TAG("tag", "tags", "标签", SoftwareTag.class, SoftwareTag.Spec.class,
        "slug displayName description color priority visible seo", "slug displayName", ""),
    TOPIC("topic", "topics", "专题", SoftwareTopic.class, SoftwareTopic.Spec.class,
        "slug title subtitle description cover items featured priority seo", "slug title", "");

    public static final Set<String> SECRET_FIELDS =
        Set.of("url", "storageKey", "extractionCode", "passwordSecretName");
    private final String singular;
    private final String plural;
    private final String title;
    private final Class<? extends AbstractExtension> type;
    private final Class<?> specType;
    private final Set<String> fields;
    private final Set<String> required;
    private final Set<String> immutable;

    SoftwareResourceKind(String singular, String plural, String title,
        Class<? extends AbstractExtension> type, Class<?> specType, String fields,
        String required, String immutable) {
        this.singular = singular;
        this.plural = plural;
        this.title = title;
        this.type = type;
        this.specType = specType;
        this.fields = Set.of(fields.split(" "));
        this.required = Set.of(required.split(" "));
        this.immutable = immutable.isEmpty() ? Set.of() : Set.of(immutable.split(" "));
    }

    public String singular() { return singular; }
    public String plural() { return plural; }
    public String title() { return title; }
    public Class<? extends AbstractExtension> type() { return type; }
    public Class<?> specType() { return specType; }
    public Set<String> fields() { return fields; }
    public Set<String> required() { return required; }
    public Set<String> immutable() { return immutable; }
    public boolean publishable() { return this == APP || this == TOPIC; }

    public Object spec(AbstractExtension resource) {
        return new BeanWrapperImpl(resource).getPropertyValue("spec");
    }

    public void setSpec(AbstractExtension resource, Object spec) {
        new BeanWrapperImpl(resource).setPropertyValue("spec", spec);
    }
}
