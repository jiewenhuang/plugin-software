package run.jiewen.software.operations;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import tools.jackson.databind.json.JsonMapper;

/** Stable digests for request identity and non-sensitive business content. */
public final class SoftwareDigests {
    private SoftwareDigests() {}

    public static String sha256(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(JsonMapper.shared().writeValueAsBytes(canonical(value))));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static Object canonical(Object value) {
        if (value instanceof Map<?, ?> map) {
            var sorted = new TreeMap<String, Object>();
            map.forEach((key, item) -> sorted.put((String) key, canonical(item)));
            return sorted;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(SoftwareDigests::canonical).toList();
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString()).stripTrailingZeros();
        }
        return value;
    }
}
