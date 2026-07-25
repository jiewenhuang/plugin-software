package run.jiewen.software.download;

import java.net.URI;

/** Shared allow-list for every URL-like value that can leave the download authorization boundary. */
public final class DownloadTargetPolicy {

    private DownloadTargetPolicy() {
    }

    public static String firstUsable(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (var candidate : candidates) {
            var usable = usable(candidate);
            if (usable != null) {
                return usable;
            }
        }
        return null;
    }

    public static boolean isUsable(String value) {
        return usable(value) != null;
    }

    public static String usable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var normalized = value.trim();
        try {
            var uri = URI.create(normalized);
            if (normalized.startsWith("/")) {
                return normalized.startsWith("//") || normalized.contains("\\")
                    || uri.isAbsolute() || uri.getRawAuthority() != null
                    ? null : normalized;
            }
            var scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                && uri.getRawAuthority() != null && !uri.getRawAuthority().isBlank()
                ? normalized : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
