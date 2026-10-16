package run.jiewen.software.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import run.jiewen.software.api.SoftwareResourceKind;

/** Explicit JSON contracts; no schema generation from raw Extension/metadata classes. */
final class SoftwareMcpSchemas {
    static final int MAX_INPUT_BYTES = 256 * 1024;
    static final int MAX_RESULT_BYTES = 512 * 1024;
    static final Set<String> LONG_TEXT = Set.of("description", "changelog", "installGuide");
    private static final Set<String> BOOLEANS = Set.of("featured", "recommended", "published",
        "visible", "requiresLogin", "requiresCaptcha", "enabled");
    private static final Set<String> ARRAYS = Set.of("screenshots", "tagNames", "platforms",
        "architectures", "relatedAppNames", "relatedPostNames", "keywords", "installWarnings");

    private SoftwareMcpSchemas() {}

    static Map<String, Object> object(Map<String, Object> properties, List<String> required) {
        return Map.of("type", "object", "properties", properties,
            "required", required, "additionalProperties", false);
    }

    static Map<String, Object> text(int max) {
        return Map.of("type", "string", "maxLength", max);
    }

    static Map<String, Object> nonBlank(int max) {
        return Map.of("type", "string", "maxLength", max, "minLength", 1, "pattern", ".*\\S.*");
    }

    static Map<String, Object> bool() { return Map.of("type", "boolean"); }

    static Map<String, Object> integer(long min, long max) {
        return Map.of("type", "integer", "minimum", min, "maximum", max);
    }

    static Map<String, Object> strings(String... values) {
        return Map.of("type", "string", "enum", List.of(values));
    }

    static Map<String, Object> array(Map<String, Object> items, int max) {
        return Map.of("type", "array", "items", items, "maxItems", max);
    }

    static Map<String, Object> field(String name) {
        if (BOOLEANS.contains(name)) { return bool(); }
        if (ARRAYS.contains(name)) { return array(text(2048), 100); }
        if (LONG_TEXT.contains(name)) { return text(100000); }
        return switch (name) {
            case "slug" -> Map.of("type", "string", "minLength", 1, "maxLength", 253,
                "pattern", "^[a-z0-9]+(?:-[a-z0-9]+)*$");
            case "name", "appName", "versionName", "categoryName", "parentName",
                "passwordSecretName" -> nonBlank(253);
            case "title", "displayName" -> nonBlank(200);
            case "version" -> nonBlank(200);
            case "priority" -> integer(0, Integer.MAX_VALUE);
            case "sizeBytes", "expectedVersion", "resourceVersion" -> integer(0, Long.MAX_VALUE);
            case "editorRating" -> Map.of("type", "number");
            case "releaseDate" -> Map.of("type", "string", "format", "date", "maxLength", 10);
            case "status" -> strings("draft", "released", "beta", "deprecated");
            case "seo" -> object(Map.of("title", text(2048), "description", text(2048),
                "keywords", array(text(2048), 100)), List.of());
            case "checksum" -> object(Map.of(
                "sha256", Map.of("type", "string", "pattern", "^[a-fA-F0-9]{64}$"),
                "md5", Map.of("type", "string", "pattern", "^[a-fA-F0-9]{32}$")), List.of());
            case "items" -> array(object(Map.of("appName", nonBlank(253), "reason", text(2048),
                "priority", integer(0, Integer.MAX_VALUE)), List.of("appName")), 200);
            case "color" -> Map.of("type", "string", "pattern", "^#(?:[a-fA-F0-9]{3}|[a-fA-F0-9]{6})$");
            default -> text(2048);
        };
    }

    static Map<String, Object> spec(SoftwareResourceKind kind, boolean create) {
        var properties = new LinkedHashMap<String, Object>();
        kind.fields().stream().sorted().filter(name -> create || !kind.immutable().contains(name))
            .forEach(name -> properties.put(name, field(name)));
        return object(properties, create ? kind.required().stream().sorted().toList() : List.of());
    }

    static Map<String, Object> named(boolean version) {
        var props = new LinkedHashMap<String, Object>();
        props.put("name", field("name"));
        if (version) { props.put("expectedVersion", field("expectedVersion")); }
        return object(props, new ArrayList<>(props.keySet()));
    }

    static Map<String, Object> create(SoftwareResourceKind kind) {
        return object(Map.of("spec", spec(kind, true), "requestId", requestId()), List.of("spec"));
    }

    static Map<String, Object> update(SoftwareResourceKind kind, boolean secrets) {
        var patch = secrets ? object(secretProperties(), List.of()) : spec(kind, false);
        var properties = new LinkedHashMap<String, Object>();
        properties.put("name", field("name"));
        properties.put("expectedVersion", field("expectedVersion"));
        if (!secrets) { properties.put("expectedEditRevision", editRevision()); }
        properties.put("patch", patch);
        properties.put("clearFields", array(nonBlank(253), 100));
        return object(properties, List.of("name", "expectedVersion"));
    }

    static Map<String, Object> publication() {
        return object(Map.of("name", field("name"), "expectedVersion", field("expectedVersion"),
            "published", bool(), "expectedEditRevision", editRevision()), List.of("name", "expectedVersion", "published"));
    }

    static Map<String, Object> secretProperties() {
        var properties = new LinkedHashMap<String, Object>();
        SoftwareResourceKind.SECRET_FIELDS.stream().sorted()
            .forEach(key -> properties.put(key, field(key)));
        return properties;
    }

    static List<String> filters(SoftwareResourceKind kind) {
        return switch (kind) {
            case APP -> List.of("keyword", "slug", "published", "categoryName", "tagName",
                "platform", "featured", "recommended");
            case VERSION -> List.of("keyword", "appName", "version", "status");
            case DOWNLOAD_ASSET -> List.of("keyword", "appName", "versionName", "type", "provider",
                "enabled", "protection");
            case CATEGORY -> List.of("keyword", "slug", "parentName", "visible");
            case TAG -> List.of("keyword", "slug", "visible", "usage");
            case TOPIC -> List.of("keyword", "slug", "published", "featured");
        };
    }

    static Map<String, String> sortFields(SoftwareResourceKind kind) {
        var fields = new LinkedHashMap<String, String>();
        fields.put("createdAt", "metadata.creationTimestamp");
        fields.put("name", "metadata.name");
        switch (kind) {
            case APP -> {
                fields.put("publishedAt", "spec.publishedAt");
                fields.put("downloads", "status.downloadCount");
            }
            case VERSION -> fields.put("releaseDate", "spec.releaseDate");
            case DOWNLOAD_ASSET -> {
                fields.put("priority", "spec.priority");
                fields.put("downloads", "status.downloadCount");
            }
            default -> {
                fields.put("priority", "spec.priority");
                fields.put("appCount", "status.appCount");
            }
        }
        return fields;
    }

    static Map<String, Object> list(SoftwareResourceKind kind) {
        var properties = new LinkedHashMap<String, Object>();
        for (var key : filters(kind)) {
            properties.put(key, switch (key) {
                case "keyword" -> text(200);
                case "usage" -> strings("used", "unused");
                case "protection" -> strings("protected");
                default -> field(key);
            });
        }
        properties.put("page", integer(1, Integer.MAX_VALUE));
        properties.put("size", integer(1, 100));
        properties.put("sortBy", strings(sortFields(kind).keySet().toArray(String[]::new)));
        properties.put("order", strings("asc", "desc"));
        return object(properties, List.of());
    }

    static Map<String, Object> item(SoftwareResourceKind kind, boolean secrets) {
        var props = new LinkedHashMap<String, Object>();
        if (secrets) {
            props.putAll(secretProperties());
        } else {
            kind.fields().stream().sorted().forEach(key -> props.put(key, field(key)));
            if (kind.publishable()) { props.put("published", bool()); }
            if (kind == SoftwareResourceKind.APP) {
                props.put("platform", text(2048));
                props.put("publishedAt", text(2048));
            }
            if (kind == SoftwareResourceKind.DOWNLOAD_ASSET) {
                for (var key : List.of("hasUrl", "hasStorageKey", "hasExtractionCode", "hasPasswordSecret")) {
                    props.put(key, bool());
                }
            }
            props.put("stats", stats(kind));
            props.put("truncatedFields", array(text(253), 100));
        }
        // Output represents existing content too: do not impose new input limits on legacy data.
        props.replaceAll((key, value) -> outputType(cast(value)));
        props.put("name", Map.of("type", "string"));
        props.put("resourceVersion", Map.of("type", "integer"));
        if (!secrets) { props.put("editRevision", editRevision()); }
        return object(props, List.of("name", "resourceVersion"));
    }

    static Map<String, Object> editRevision() {
        return Map.of("type", "string", "pattern", "^[a-f0-9]{64}$");
    }

    static Map<String, Object> requestId() {
        return Map.of("type", "string", "pattern",
            "^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$");
    }

    static Map<String, Object> operationQuery() {
        return object(Map.of("resourceType", strings(java.util.Arrays.stream(SoftwareResourceKind.values())
            .map(SoftwareResourceKind::singular).toArray(String[]::new)), "requestId", requestId()),
            List.of("resourceType", "requestId"));
    }

    static Map<String, Object> receipt() {
        return object(Map.of("requestId", requestId(), "resourceType", text(32), "name", text(253),
            "state", strings("pending", "writing", "succeeded", "failed", "unknown"),
            "resourceVersion", field("resourceVersion"), "errorCode", text(100), "replayed", bool()),
            List.of("requestId", "resourceType", "name", "state", "replayed"));
    }

    static Map<String, Object> preflightResult() {
        var issue = object(Map.of("code", text(100), "severity", strings("info", "warning", "error"),
            "message", text(2048)), List.of("code", "severity", "message"));
        var dependency = object(Map.of("relation", text(100), "resourceType", text(32),
            "count", integer(0, Long.MAX_VALUE), "names", array(text(253), 20),
            "truncated", bool(), "blocking", bool()),
            List.of("relation", "resourceType", "count", "names", "truncated", "blocking"));
        var properties = new LinkedHashMap<String, Object>();
        properties.put("name", text(253)); properties.put("resourceType", text(32));
        properties.put("resourceVersion", field("resourceVersion")); properties.put("editRevision", editRevision());
        properties.put("action", strings("publish", "delete")); properties.put("allowed", bool());
        properties.put("ready", bool()); properties.put("checkedAt", text(100));
        properties.put("issues", array(issue, 100)); properties.put("dependencies", array(dependency, 10));
        return object(properties, new ArrayList<>(properties.keySet()));
    }

    static Map<String, Object> stats(SoftwareResourceKind kind) {
        var props = new LinkedHashMap<String, Object>();
        String fields = switch (kind) {
            case APP -> "latestVersionName latestVersion latestReleaseDate minOsVersion sizeBytes downloadCount likeCount commentCount health";
            case VERSION -> "assetCount downloadCount primaryAssetName";
            case DOWNLOAD_ASSET -> "health lastCheckedAt downloadCount";
            default -> "appCount";
        };
        for (var name : fields.split(" ")) {
            props.put(name, Map.of("type", name.endsWith("Count") || name.equals("sizeBytes") ? "integer" : "string"));
        }
        return object(props, List.of());
    }

    static Map<String, Object> detailResult(SoftwareResourceKind kind, boolean secrets) {
        return object(Map.of("item", item(kind, secrets),
            "representationOmitted", bool(), "changedFields", array(text(253), 100),
            "operation", receipt()), List.of("item"));
    }

    static Map<String, Object> listResult(SoftwareResourceKind kind) {
        return object(Map.of("items", array(item(kind, false), 100),
            "page", integer(1, Integer.MAX_VALUE), "size", integer(1, 100),
            "total", integer(0, Long.MAX_VALUE), "hasNext", bool()),
            List.of("items", "page", "size", "total", "hasNext"));
    }

    static Map<String, Object> deletionResult() {
        return object(Map.of("name", text(253), "state", strings("deletion_requested")),
            List.of("name", "state"));
    }

    static Map<String, Object> optionsResult() {
        var props = new LinkedHashMap<String, Object>();
        for (var key : List.of("platforms", "architectures", "versionStatuses", "downloadTypes",
            "downloadProviders", "releaseChannels", "locales")) {
            props.put(key, Map.of("type", "array", "items", object(Map.of(
                "label", Map.of("type", "string"), "value", Map.of("type", "string")),
                List.of("label", "value"))));
        }
        return object(props, new ArrayList<>(props.keySet()));
    }

    static Map<String, Object> statsResult() {
        var apps = new LinkedHashMap<String, Object>();
        for (var key : List.of("total", "published", "drafts", "downloads")) {
            apps.put(key, Map.of("type", "integer"));
        }
        apps.put("platforms", Map.of("type", "array", "items", Map.of("type", "string")));
        var assets = new LinkedHashMap<String, Object>();
        for (var key : List.of("total", "enabled", "disabled", "protectedCount", "downloads")) {
            assets.put(key, Map.of("type", "integer"));
        }
        return object(Map.of("apps", object(apps, new ArrayList<>(apps.keySet())),
            "downloadAssets", object(assets, new ArrayList<>(assets.keySet()))),
            List.of("apps", "downloadAssets"));
    }

    private static Map<String, Object> outputType(Map<String, Object> input) {
        var result = new LinkedHashMap<String, Object>();
        result.put("type", input.get("type"));
        if (input.containsKey("properties")) {
            var props = new LinkedHashMap<String, Object>();
            cast(input.get("properties")).forEach((k, v) -> props.put(k, outputType(cast(v))));
            result.put("properties", props);
            result.put("additionalProperties", false);
        }
        if (input.containsKey("items")) { result.put("items", outputType(cast(input.get("items")))); }
        return result;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> cast(Object value) { return (Map<String, Object>) value; }
}
