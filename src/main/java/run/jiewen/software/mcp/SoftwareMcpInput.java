package run.jiewen.software.mcp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import run.jiewen.software.SoftwareDomainException;
import tools.jackson.databind.json.JsonMapper;

/** Defense in depth for the fixed schema vocabulary used by this provider. */
final class SoftwareMcpInput {
    private SoftwareMcpInput() {}

    static void validate(Map<String, Object> schema, Map<String, Object> arguments) {
        if (JsonMapper.shared().writeValueAsBytes(arguments).length > SoftwareMcpSchemas.MAX_INPUT_BYTES) {
            throw invalid("Tool input exceeds 256 KiB");
        }
        validateValue(schema, arguments, "arguments");
    }

    static void validateValue(Map<String, Object> schema, Object value, String path) {
        if (value == null) { throw invalid(path + " cannot be null; use clearFields"); }
        switch ((String) schema.get("type")) {
            case "object" -> {
                if (!(value instanceof Map<?, ?> map)) { throw invalid(path + " must be an object"); }
                var properties = SoftwareMcpSchemas.cast(schema.get("properties"));
                var required = (List<?>) schema.getOrDefault("required", List.of());
                if (!map.keySet().containsAll(required)) { throw invalid(path + " is missing required fields"); }
                for (var entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String key) || !properties.containsKey(key)) {
                        // Do not echo untrusted keys: they can themselves contain secret material.
                        throw invalid(path + " contains an unknown field");
                    }
                    validateValue(SoftwareMcpSchemas.cast(properties.get(key)), entry.getValue(), path + "." + key);
                }
            }
            case "array" -> {
                if (!(value instanceof List<?> list)) { throw invalid(path + " must be an array"); }
                if (schema.get("maxItems") instanceof Number max && list.size() > max.intValue()) {
                    throw invalid(path + " has too many items");
                }
                for (var item : list) {
                    validateValue(SoftwareMcpSchemas.cast(schema.get("items")), item, path + "[]");
                }
            }
            case "string" -> {
                if (!(value instanceof String text)) { throw invalid(path + " must be a string"); }
                int length = text.codePointCount(0, text.length());
                if (schema.get("maxLength") instanceof Number max && length > max.intValue()
                    || schema.get("minLength") instanceof Number min && length < min.intValue()) {
                    throw invalid(path + " has an invalid length");
                }
                if (schema.get("pattern") instanceof String regex
                    && !Pattern.compile(regex, Pattern.DOTALL).matcher(text).find()) {
                    throw invalid(path + " has an invalid format");
                }
                if (schema.get("enum") instanceof List<?> allowed && !allowed.contains(text)) {
                    throw invalid(path + " has an unsupported value");
                }
                if ("date".equals(schema.get("format"))) {
                    try { LocalDate.parse(text); } catch (RuntimeException ex) {
                        throw invalid(path + " must be an ISO date");
                    }
                }
            }
            case "boolean" -> {
                if (!(value instanceof Boolean)) { throw invalid(path + " must be a boolean"); }
            }
            case "integer", "number" -> {
                if (!(value instanceof Number number)) { throw invalid(path + " must be a number"); }
                try {
                    var decimal = new BigDecimal(number.toString());
                    if ("integer".equals(schema.get("type"))) { decimal.longValueExact(); }
                    if (schema.get("minimum") instanceof Number min
                        && decimal.compareTo(new BigDecimal(min.toString())) < 0
                        || schema.get("maximum") instanceof Number max
                        && decimal.compareTo(new BigDecimal(max.toString())) > 0) {
                        throw invalid(path + " is out of range");
                    }
                } catch (NumberFormatException | ArithmeticException ex) {
                    throw invalid(path + " must be a finite number of the declared type");
                }
            }
            default -> throw new IllegalStateException("Unsupported provider schema type");
        }
    }

    static long version(Map<String, Object> args) {
        return new BigDecimal(args.get("expectedVersion").toString()).longValueExact();
    }

    private static SoftwareDomainException invalid(String message) {
        return new SoftwareDomainException("INVALID_ARGUMENT", message);
    }
}
