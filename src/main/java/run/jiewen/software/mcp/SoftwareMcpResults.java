package run.jiewen.software.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.BeanWrapperImpl;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.ListResult;
import run.halo.mcpserver.api.McpToolResult;
import run.jiewen.software.SoftwareDomainException;
import run.jiewen.software.api.SoftwareResourceKind;
import run.jiewen.software.extension.DownloadAsset;
import run.jiewen.software.operations.SoftwareCreationService;
import run.jiewen.software.operations.SoftwareEditRevision;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Every response crosses an explicit allowlist, including successful write results. */
final class SoftwareMcpResults {
    private static final JsonMapper JSON = JsonMapper.shared();
    private static final TypeReference<LinkedHashMap<String, Object>> MAP = new TypeReference<>() {};

    private SoftwareMcpResults() {}

    static Map<String, Object> map(Object value) {
        return value == null ? new LinkedHashMap<>() : JSON.convertValue(value, MAP);
    }

    static Map<String, Object> item(SoftwareResourceKind kind, AbstractExtension resource,
        boolean summary, boolean secrets) {
        var result = new LinkedHashMap<String, Object>();
        result.put("name", resource.getMetadata().getName());
        result.put("resourceVersion", resource.getMetadata().getVersion());
        var spec = map(kind.spec(resource));
        var fields = new java.util.HashSet<>(secrets ? SoftwareResourceKind.SECRET_FIELDS : kind.fields());
        if (!secrets) {
            if (kind.publishable()) { fields.add("published"); }
            if (kind == SoftwareResourceKind.APP) {
                fields.addAll(Set.of("publishedAt", "platform"));
                if (spec.get("platforms") == null && spec.get("platform") instanceof String platform) {
                    spec.put("platforms", java.util.Arrays.stream(platform.split("[,，/\\s]+"))
                        .filter(value -> !value.isBlank()).distinct().toList());
                }
            }
        }
        var truncated = new ArrayList<String>();
        for (var field : fields.stream().sorted().toList()) {
            if (summary && (SoftwareMcpSchemas.LONG_TEXT.contains(field)
                || Set.of("items", "screenshots", "installWarnings", "seo", "checksum",
                    "relatedAppNames", "relatedPostNames").contains(field))) {
                continue;
            }
            Object value = spec.get(field);
            if (value == null) { continue; }
            if (summary && value instanceof String text && text.codePointCount(0, text.length()) > 200
                && !field.equals("slug") && !field.endsWith("Name")) {
                value = text.substring(0, text.offsetByCodePoints(0, 200));
                truncated.add(field);
            }
            // Nested fields are typed domain values, not metadata or arbitrary Extension maps.
            result.put(field, withoutNulls(value));
        }
        if (!secrets) {
            result.put("editRevision", SoftwareEditRevision.of(kind, resource));
            if (resource instanceof DownloadAsset asset) {
                var value = asset.getSpec();
                result.put("hasUrl", value != null && hasText(value.getUrl()));
                result.put("hasStorageKey", value != null && hasText(value.getStorageKey()));
                result.put("hasExtractionCode", value != null && hasText(value.getExtractionCode()));
                result.put("hasPasswordSecret", value != null && hasText(value.getPasswordSecretName()));
            }
            var rawStats = map(new BeanWrapperImpl(resource).getPropertyValue("status"));
            var stats = new LinkedHashMap<String, Object>();
            SoftwareMcpSchemas.cast(SoftwareMcpSchemas.stats(kind).get("properties"))
                .keySet().forEach(key -> {
                    if (rawStats.get(key) != null) { stats.put(key, rawStats.get(key)); }
                });
            result.put("stats", stats);
            if (!truncated.isEmpty()) { result.put("truncatedFields", truncated); }
        }
        return result;
    }

    static McpToolResult detail(SoftwareResourceKind kind, AbstractExtension resource,
        boolean secrets, boolean write, List<String> changedFields) {
        var body = Map.<String, Object>of("item", item(kind, resource, false, secrets));
        if (byteSize(body) > SoftwareMcpSchemas.MAX_RESULT_BYTES && write) {
            body = Map.of("item", Map.of("name", resource.getMetadata().getName(),
                "resourceVersion", resource.getMetadata().getVersion()),
                "representationOmitted", true, "changedFields", changedFields);
        }
        return success(body);
    }

    static McpToolResult created(SoftwareResourceKind kind, SoftwareCreationService.Outcome outcome,
        List<String> changedFields) {
        var receipt = outcome.receipt();
        var body = new LinkedHashMap<String, Object>();
        body.put("operation", receipt(receipt));
        if (outcome.resource() != null) { body.put("item", item(kind, outcome.resource(), false, false)); }
        if (outcome.resource() == null || byteSize(body) > SoftwareMcpSchemas.MAX_RESULT_BYTES) {
            body.put("item", Map.of("name", receipt.name(), "resourceVersion", receipt.resourceVersion()));
            body.put("representationOmitted", true);
            body.put("changedFields", changedFields);
        }
        return success(body);
    }

    static Map<String, Object> receipt(SoftwareCreationService.Receipt receipt) {
        var result = map(receipt);
        result.values().removeIf(java.util.Objects::isNull);
        return result;
    }

    static McpToolResult list(SoftwareResourceKind kind, ListResult<AbstractExtension> result) {
        return success(Map.of("items", result.getItems().stream()
                .map(resource -> item(kind, resource, true, false)).toList(),
            "page", result.getPage(), "size", result.getSize(), "total", result.getTotal(),
            "hasNext", (long) result.getPage() * result.getSize() < result.getTotal()));
    }

    static McpToolResult success(Map<String, Object> body) {
        if (byteSize(body) > SoftwareMcpSchemas.MAX_RESULT_BYTES) {
            throw new SoftwareDomainException("RESULT_TOO_LARGE",
                "Result exceeds 512 KiB; use a smaller page or view the full content in Console");
        }
        // Explicit text avoids duplicating large or sensitive structuredContent in a second block.
        return McpToolResult.success(body, "Software operation completed. See structuredContent.");
    }

    private static int byteSize(Object value) { return JSON.writeValueAsBytes(value).length; }

    private static Object withoutNulls(Object value) {
        if (value instanceof Map<?, ?> map) {
            var result = new LinkedHashMap<String, Object>();
            map.forEach((key, item) -> {
                if (item != null) { result.put((String) key, withoutNulls(item)); }
            });
            return result;
        }
        if (value instanceof List<?> list) {
            return list.stream().filter(java.util.Objects::nonNull)
                .map(SoftwareMcpResults::withoutNulls).toList();
        }
        return value;
    }

    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
}
