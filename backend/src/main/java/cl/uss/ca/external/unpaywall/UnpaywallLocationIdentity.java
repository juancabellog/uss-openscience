package cl.uss.ca.external.unpaywall;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class UnpaywallLocationIdentity {
    private UnpaywallLocationIdentity() {
    }

    public static String key(UnpaywallLocationRecord location) {
        if (location == null) {
            return null;
        }
        String url = firstNonBlank(location.url, location.urlForPdf, location.urlForLandingPage);
        String endpoint = blankToNull(location.endpointId);
        if (url == null && endpoint == null) {
            return null;
        }
        String raw = (endpoint == null ? "" : endpoint.trim().toLowerCase(Locale.ROOT))
                + "|"
                + (url == null ? "" : url.trim());
        return sha1(raw);
    }

    public static boolean same(UnpaywallLocationRecord left, UnpaywallLocationRecord right) {
        return Objects.equals(snapshot(left), snapshot(right));
    }

    public static String snapshot(UnpaywallLocationRecord location) {
        if (location == null) {
            return "";
        }
        return String.join("|",
                nullToEmpty(location.endpointId),
                nullToEmpty(location.hostType),
                nullToEmpty(location.license),
                nullToEmpty(location.version),
                nullToEmpty(location.url),
                nullToEmpty(location.urlForLandingPage),
                nullToEmpty(location.urlForPdf),
                nullToEmpty(location.oaDate),
                Boolean.TRUE.equals(location.isBest) ? "1" : "0",
                nullToEmpty(location.evidence),
                nullToEmpty(location.repositoryInstitution),
                nullToEmpty(location.pmhId)
        );
    }

    public static boolean hasRepositoryCopy(List<UnpaywallLocationRecord> locations) {
        if (locations == null) {
            return false;
        }
        for (UnpaywallLocationRecord location : locations) {
            if (isRepository(location)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isRepository(UnpaywallLocationRecord location) {
        return location != null && "repository".equalsIgnoreCase(blankToNull(location.hostType));
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = blankToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String nullToEmpty(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? "" : normalized;
    }

    private static String sha1(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
