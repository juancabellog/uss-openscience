package cl.uss.ca.external.pure.metrics;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Idempotent identity for one metric observation.
 * NULL year/source/category become empty sentinels so MySQL UNIQUE cannot
 * admit duplicate rows the way a multi-NULL UNIQUE would.
 */
public final class PureMetricIdentity {
    private static final char SEP = '\0';

    private PureMetricIdentity() {
    }

    public static String key(
            long ownerId,
            String collectionId,
            String metricId,
            Integer year,
            String sourceUri,
            String categoryUri
    ) {
        String raw = ownerId
                + String.valueOf(SEP)
                + sentinel(collectionId)
                + SEP
                + sentinel(metricId)
                + SEP
                + (year == null ? "" : year.toString())
                + SEP
                + sentinel(sourceUri)
                + SEP
                + sentinel(categoryUri);
        return sha256(raw);
    }

    static String sentinel(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? "" : trimmed;
    }

    private static String sha256(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required", ex);
        }
    }
}
