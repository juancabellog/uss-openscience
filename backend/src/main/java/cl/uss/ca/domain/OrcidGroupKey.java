package cl.uss.ca.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * Technical local key for an ORCID work group. Not an official ORCID identifier.
 *
 * Preference: SHA-256 of the ORCID plus the sorted identity external IDs of the
 * group (doi/eid/wosuid with relationship self). ISSN part-of is excluded.
 *
 * Fallback when no identity IDs exist: SHA-256 of the ORCID plus the sorted
 * put-codes present in that group. Never uses an empty/null key.
 */
public final class OrcidGroupKey {
    public static final String KIND_EXTERNAL = "EXT";
    public static final String KIND_PUT_CODES = "PUT";

    private OrcidGroupKey() {
    }

    public static Result fromIdentityIds(String orcid, List<String> typeValuePairs) {
        TreeSet<String> sorted = new TreeSet<>();
        if (typeValuePairs != null) {
            for (String pair : typeValuePairs) {
                if (pair != null && !pair.isBlank()) {
                    sorted.add(pair.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        if (sorted.isEmpty()) {
            return null;
        }
        return new Result(KIND_EXTERNAL, sha256(orcid + "\n" + String.join("\n", sorted)));
    }

    public static Result fromPutCodes(String orcid, List<Long> putCodes) {
        TreeSet<Long> sorted = new TreeSet<>();
        if (putCodes != null) {
            for (Long putCode : putCodes) {
                if (putCode != null) {
                    sorted.add(putCode);
                }
            }
        }
        if (sorted.isEmpty()) {
            throw new IllegalStateException("ORCID group without identity IDs or put-codes");
        }
        List<String> parts = new ArrayList<>();
        for (Long putCode : sorted) {
            parts.add(String.valueOf(putCode));
        }
        return new Result(KIND_PUT_CODES, sha256(orcid + "\nput:" + String.join(",", parts)));
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required", ex);
        }
    }

    public static final class Result {
        public final String kind;
        public final String key;

        Result(String kind, String key) {
            this.kind = kind;
            this.key = key;
        }
    }
}
