package cl.uss.ca.sync;

import cl.uss.ca.exception.ValidationException;

public enum SyncMode {
    FULL,
    INCREMENTAL,
    /**
     * Explicit continuation of a previous execution (OpenAlex AUTHORS).
     * Not a population census. Never treated as FULL or INCREMENTAL.
     */
    RECOVERY;

    public boolean isFull() {
        return this == FULL;
    }

    public boolean isIncremental() {
        return this == INCREMENTAL;
    }

    public boolean isRecovery() {
        return this == RECOVERY;
    }

    public static SyncMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toUpperCase();
        if ("COMPLETE".equals(value) || "COMPLETA".equals(value)) {
            return FULL;
        }
        if ("PARTIAL".equals(value)) {
            return INCREMENTAL;
        }
        try {
            return SyncMode.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("INVALID_SYNC_MODE", "sync mode must be FULL or INCREMENTAL");
        }
    }
}
