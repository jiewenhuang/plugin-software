package run.jiewen.software.operations;

import java.util.LinkedHashMap;
import java.util.Map;
import run.halo.app.extension.AbstractExtension;
import run.jiewen.software.api.SoftwareResourceKind;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Excludes status and secret values; only usable for content/publication, never secret writes. */
public final class SoftwareEditRevision {
    private SoftwareEditRevision() {}

    public static String of(SoftwareResourceKind kind, AbstractExtension resource) {
        var spec = JsonMapper.shared().convertValue(kind.spec(resource),
            new TypeReference<LinkedHashMap<String, Object>>() {});
        if (spec == null) { spec = new LinkedHashMap<>(); }
        if (kind == SoftwareResourceKind.DOWNLOAD_ASSET) {
            SoftwareResourceKind.SECRET_FIELDS.forEach(spec::remove);
        }
        return SoftwareDigests.sha256(Map.of("kind", kind.singular(),
            "name", resource.getMetadata().getName(),
            "createdAt", String.valueOf(resource.getMetadata().getCreationTimestamp()), "spec", spec));
    }
}
