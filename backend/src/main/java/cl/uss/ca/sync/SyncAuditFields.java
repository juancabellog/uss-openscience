package cl.uss.ca.sync;

/**
 * sync_change/sync_error.record_uuid is the PURE uuid (VARCHAR 128 in production).
 * Lookup keys (DOI, URL) belong in record_key.
 */
public final class SyncAuditFields {
    public static final int RECORD_UUID_MAX = 128;
    public static final int RECORD_KEY_MAX = 1000;

    private SyncAuditFields() {
    }

    public static String uuid(String value) {
        return clip(value, RECORD_UUID_MAX);
    }

    public static String key(String value) {
        return clip(value, RECORD_KEY_MAX);
    }

    static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
