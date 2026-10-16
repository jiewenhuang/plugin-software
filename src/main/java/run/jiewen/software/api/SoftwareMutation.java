package run.jiewen.software.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.BeanWrapperImpl;
import run.jiewen.software.SoftwareDomainException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Explicit patch semantics, independent of MCP and the existing HTTP merge-patch contract. */
public record SoftwareMutation(long expectedVersion, Map<String, Object> patch,
    List<String> clearFields, Mode mode, String expectedEditRevision) {
    public enum Mode { CONTENT, PUBLICATION, SECRETS }

    private static final JsonMapper JSON = JsonMapper.shared();
    private static final TypeReference<LinkedHashMap<String, Object>> MAP = new TypeReference<>() {};

    public SoftwareMutation(long expectedVersion, Map<String, Object> patch,
        List<String> clearFields, Mode mode) {
        this(expectedVersion, patch, clearFields, mode, null);
    }

    public SoftwareMutation {
        if (expectedVersion < 0 || patch == null || clearFields == null || mode == null) {
            throw invalid("A version, patch and clearFields are required");
        }
        if (expectedEditRevision != null && (!expectedEditRevision.matches("[a-f0-9]{64}")
            || mode == Mode.SECRETS)) {
            throw invalid("Edit revision is only supported for content and publication writes");
        }
        patch = JSON.convertValue(patch, MAP);
        clearFields = List.copyOf(clearFields);
    }

    public static Object createSpec(SoftwareResourceKind kind, Map<String, Object> values) {
        checkFields(values, kind.fields());
        return JSON.convertValue(values, kind.specType());
    }

    public Object apply(SoftwareResourceKind kind, Object current) {
        if (current == null) {
            throw invalid("Stored resource spec is missing");
        }
        Set<String> allowed = switch (mode) {
            case CONTENT -> {
                var fields = new java.util.HashSet<>(kind.fields());
                fields.removeAll(kind.immutable());
                yield fields;
            }
            case PUBLICATION -> {
                if (!kind.publishable()) {
                    throw invalid("Resource does not support publication");
                }
                yield Set.of("published");
            }
            case SECRETS -> {
                if (kind != SoftwareResourceKind.DOWNLOAD_ASSET) {
                    throw invalid("Resource does not support download secrets");
                }
                yield SoftwareResourceKind.SECRET_FIELDS;
            }
        };
        checkFields(patch, allowed);
        if (patch.isEmpty() && clearFields.isEmpty()) {
            throw invalid("Provide at least one patch field or clearFields entry");
        }
        var target = JSON.convertValue(current, MAP);
        var writtenPaths = new ArrayList<String>();
        paths(patch, "", writtenPaths);
        var cleared = new java.util.HashSet<String>();
        for (var path : clearFields) {
            String[] parts = path.split("\\.", -1);
            if (parts.length > 2 || !allowed.contains(parts[0])
                || kind.required().contains(parts[0]) || parts[0].equals("published")) {
                throw invalid("clearFields contains a field that cannot be cleared");
            }
            if (!cleared.add(path) || writtenPaths.stream().anyMatch(p -> overlaps(p, path))
                || cleared.stream().anyMatch(p -> !p.equals(path) && overlaps(p, path))) {
                throw invalid("Conflicting patch and clearFields paths");
            }
            var descriptor = new BeanWrapperImpl(kind.specType());
            Class<?> fieldType = descriptor.getPropertyType(parts[0]);
            if (parts.length == 2) {
                var nested = nestedFields(parts[0]);
                if (!nested.contains(parts[1])) {
                    throw invalid("Unknown nested clearFields path");
                }
                fieldType = new BeanWrapperImpl(fieldType).getPropertyType(parts[1]);
            }
            if (fieldType == null || fieldType.isPrimitive() || fieldType == Boolean.class
                || Number.class.isAssignableFrom(fieldType)) {
                throw invalid("clearFields contains a field that cannot be cleared");
            }
            Object clearedValue = List.class.isAssignableFrom(fieldType) ? List.of() : null;
            if (parts.length == 1) {
                target.put(parts[0], clearedValue);
            } else if (target.get(parts[0]) instanceof Map<?, ?> nested) {
                var copy = new LinkedHashMap<String, Object>();
                nested.forEach((k, v) -> copy.put((String) k, v));
                copy.put(parts[1], clearedValue);
                target.put(parts[0], copy);
            }
        }
        merge(target, patch);
        return JSON.convertValue(target, kind.specType());
    }

    private static void checkFields(Map<String, Object> values, Set<String> allowed) {
        if (values == null) {
            throw invalid("A spec or patch object is required");
        }
        for (var entry : values.entrySet()) {
            if (!allowed.contains(entry.getKey())) {
                throw invalid("Input contains a field that is not writable");
            }
            if (entry.getValue() == null) {
                throw invalid("Use clearFields instead of null");
            }
            if (entry.getValue() instanceof Map<?, ?> object) {
                var nested = new LinkedHashMap<String, Object>();
                object.forEach((k, v) -> nested.put(String.valueOf(k), v));
                checkFields(nested, nestedFields(entry.getKey()));
            }
        }
    }

    private static Set<String> nestedFields(String field) {
        return switch (field) {
            case "seo" -> Set.of("title", "description", "keywords");
            case "checksum" -> Set.of("sha256", "md5");
            default -> Set.of();
        };
    }

    private static boolean overlaps(String left, String right) {
        return left.equals(right) || left.startsWith(right + ".") || right.startsWith(left + ".");
    }

    private static void paths(Map<String, Object> map, String prefix, List<String> result) {
        map.forEach((key, value) -> {
            String path = prefix + key;
            if (value instanceof Map<?, ?> nested && !nested.isEmpty()) {
                paths(JSON.convertValue(nested, MAP), path + ".", result);
            } else {
                result.add(path);
            }
        });
    }

    private static void merge(Map<String, Object> target, Map<String, Object> patch) {
        patch.forEach((key, value) -> {
            if (value instanceof Map<?, ?> nested) {
                var child = target.get(key) instanceof Map<?, ?> existing
                    ? JSON.convertValue(existing, MAP) : new LinkedHashMap<String, Object>();
                merge(child, JSON.convertValue(nested, MAP));
                target.put(key, child);
            } else {
                target.put(key, value);
            }
        });
    }

    private static SoftwareDomainException invalid(String message) {
        return new SoftwareDomainException("INVALID_ARGUMENT", message);
    }
}
