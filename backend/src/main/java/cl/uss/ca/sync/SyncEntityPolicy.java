package cl.uss.ca.sync;

import cl.uss.ca.exception.ValidationException;
import cl.uss.ca.service.PureSyncService;

import java.util.List;

/**
 * Declares which {@link SyncMode} values each sync entity accepts.
 * Avoids a global if/else on integration names inside jobs.
 */
public enum SyncEntityPolicy {
    PEOPLE("PEOPLE", true, false, SyncBaselineKind.COMPLETED_FULL_ONLY),
    RESEARCH_OUTPUT("RESEARCH_OUTPUT", true, false, SyncBaselineKind.COMPLETED_FULL_ONLY),
    JOURNALS("JOURNALS", true, false, SyncBaselineKind.COMPLETED_FULL_ONLY),
    PROJECTS("PROJECTS", true, false, SyncBaselineKind.COMPLETED_FULL_ONLY),
    ORGANIZATIONS("ORGANIZATIONS", true, false, SyncBaselineKind.COMPLETED_FULL_ONLY),
    ORCID("ORCID", true, true, SyncBaselineKind.ITEM_WARNINGS_OK),
    UNPAYWALL("UNPAYWALL", true, true, SyncBaselineKind.ITEM_WARNINGS_OK),
    CROSSREF("CROSSREF", true, true, SyncBaselineKind.ITEM_WARNINGS_OK),
    OPENALEX("OPENALEX", true, true, SyncBaselineKind.ITEM_WARNINGS_OK),
    DSPACE("DSPACE", true, true, SyncBaselineKind.ITEM_WARNINGS_OK),
    JOURNAL_METRICS("JOURNAL_METRICS", true, true, SyncBaselineKind.ITEM_WARNINGS_OK),
    RESEARCH_OUTPUT_METRICS("RESEARCH_OUTPUT_METRICS", true, true, SyncBaselineKind.ITEM_WARNINGS_OK),
    PERSON_METRICS("PERSON_METRICS", true, true, SyncBaselineKind.ITEM_WARNINGS_OK);

    private final String entity;
    private final boolean supportsFull;
    private final boolean supportsIncremental;
    private final SyncBaselineKind baselineKind;

    SyncEntityPolicy(
            String entity,
            boolean supportsFull,
            boolean supportsIncremental,
            SyncBaselineKind baselineKind
    ) {
        this.entity = entity;
        this.supportsFull = supportsFull;
        this.supportsIncremental = supportsIncremental;
        this.baselineKind = baselineKind;
    }

    public String entity() {
        return entity;
    }

    public boolean supportsFull() {
        return supportsFull;
    }

    public boolean supportsIncremental() {
        return supportsIncremental;
    }

    public SyncBaselineKind baselineKind() {
        return baselineKind;
    }

    /**
     * Pre-SyncMode executions of these entities walked the integration population.
     * {@code sync_mode NULL} can be treated as FULL so a historic COMPLETED run
     * establishes baseline. OPENALEX was added with the column present.
     * CROSSREF skip-fresh predates {@code sync_mode}; INCREMENTAL still picks up
     * NEVER_SYNCED / STALE, so treating NULL as FULL is safe and required for the UI.
     * DSPACE without a stored mode always crawled discover (no date filter).
     */
    public boolean treatsNullSyncModeAsFull() {
        return this == PEOPLE || this == RESEARCH_OUTPUT || this == JOURNALS || this == PROJECTS
                || this == ORGANIZATIONS
                || this == ORCID || this == UNPAYWALL
                || this == CROSSREF || this == DSPACE
                || this == JOURNAL_METRICS || this == RESEARCH_OUTPUT_METRICS || this == PERSON_METRICS;
    }

    public static boolean treatsNullSyncModeAsFull(String entity) {
        try {
            return ofEntity(entity).treatsNullSyncModeAsFull();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * Fail-safe without baseline context. Start paths must use {@link SyncModeAdvisor}.
     */
    public SyncMode defaultMode() {
        return SyncMode.FULL;
    }

    public boolean supports(SyncMode mode) {
        if (mode == null) {
            return false;
        }
        if (mode.isFull()) {
            return supportsFull;
        }
        if (mode.isIncremental()) {
            return supportsIncremental;
        }
        return false;
    }

    public List<SyncMode> supportedModes() {
        if (supportsFull && supportsIncremental) {
            return List.of(SyncMode.FULL, SyncMode.INCREMENTAL);
        }
        if (supportsFull) {
            return List.of(SyncMode.FULL);
        }
        return List.of(SyncMode.INCREMENTAL);
    }

    /**
     * Blank/null uses the entity default. Unknown tokens fail with INVALID_SYNC_MODE.
     * A known mode that this entity does not support fails with SYNC_MODE_NOT_SUPPORTED
     * (no silent fallback to FULL).
     */
    public SyncMode resolve(String raw) {
        SyncMode parsed = SyncMode.parse(raw);
        SyncMode mode = parsed == null ? defaultMode() : parsed;
        assertSupported(mode);
        return mode;
    }

    public void assertSupported(SyncMode mode) {
        if (supports(mode)) {
            return;
        }
        throw new ValidationException(
                "SYNC_MODE_NOT_SUPPORTED",
                entity + " does not support sync mode " + (mode == null ? "null" : mode.name())
        );
    }

    public static SyncEntityPolicy ofEntity(String entity) {
        String normalized = PureSyncService.normalizeEntity(entity);
        for (SyncEntityPolicy policy : values()) {
            if (policy.entity.equals(normalized)) {
                return policy;
            }
        }
        throw new ValidationException("INVALID_SYNC_ENTITY", "Unknown sync entity: " + entity);
    }
}
