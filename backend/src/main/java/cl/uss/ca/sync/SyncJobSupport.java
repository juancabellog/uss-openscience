package cl.uss.ca.sync;

import cl.uss.ca.exception.ErrorLog;
import cl.uss.ca.exception.ExceptionDetail;
import cl.uss.ca.exception.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shared completion and per-item error handling for PEOPLE, RESEARCH_OUTPUT, JOURNALS, PROJECTS and ORGANIZATIONS.
 */
@ApplicationScoped
public class SyncJobSupport {
    private static final Logger LOG = Logger.getLogger(SyncJobSupport.class.getName());
    public static final String PURE_INCOMPLETE = "PURE_INCOMPLETE";
    public static final String SYNC_PROCESSING_FAILED = "SYNC_PROCESSING_FAILED";
    public static final String STAGE_PERSON_MAP = "PERSON_MAP";
    public static final String STAGE_PERSON_UPSERT = "PERSON_UPSERT";
    public static final String STAGE_OUTPUT_MAP = "RESEARCH_OUTPUT_MAP";
    public static final String STAGE_OUTPUT_UPSERT = "RESEARCH_OUTPUT_UPSERT";
    public static final String STAGE_JOURNAL_MAP = "JOURNAL_MAP";
    public static final String STAGE_JOURNAL_UPSERT = "JOURNAL_UPSERT";
    public static final String STAGE_PROJECT_MAP = "PROJECT_MAP";
    public static final String STAGE_PROJECT_UPSERT = "PROJECT_UPSERT";
    public static final String STAGE_ORGANIZATION_MAP = "ORGANIZATION_MAP";
    public static final String STAGE_ORGANIZATION_UPSERT = "ORGANIZATION_UPSERT";
    public static final String STAGE_ORCID_FETCH = "ORCID_FETCH";
    public static final String STAGE_ORCID_UPSERT = "ORCID_UPSERT";
    public static final String STAGE_ORCID_DETAIL = "ORCID_DETAIL";
    public static final String STAGE_ORCID_EMPLOYMENT = "ORCID_EMPLOYMENT";
    public static final String STAGE_UNPAYWALL_FETCH = "UNPAYWALL_FETCH";
    public static final String STAGE_UNPAYWALL_UPSERT = "UNPAYWALL_UPSERT";
    public static final String STAGE_CROSSREF_FETCH = "CROSSREF_FETCH";
    public static final String STAGE_CROSSREF_UPSERT = "CROSSREF_UPSERT";
    public static final String STAGE_OPENALEX_FETCH = "OPENALEX_FETCH";
    public static final String STAGE_OPENALEX_UPSERT = "OPENALEX_UPSERT";
    public static final String STAGE_OPENALEX_AUTHOR = "OPENALEX_AUTHOR";
    public static final String STAGE_OPENALEX_MATCH = "OPENALEX_MATCH";
    public static final String STAGE_DSPACE_DISCOVER = "DSPACE_DISCOVER";
    public static final String STAGE_DSPACE_UPSERT = "DSPACE_UPSERT";
    public static final String STAGE_DSPACE_MATCH = "DSPACE_MATCH";
    public static final String STAGE_DSPACE_BITSTREAMS = "BITSTREAM_BACKFILL";
    public static final String STAGE_JOURNAL_METRICS = "JOURNAL_METRICS";
    public static final String STAGE_RESEARCH_OUTPUT_METRICS = "RESEARCH_OUTPUT_METRICS";
    public static final String STAGE_PERSON_METRICS = "PERSON_METRICS";

    private final Set<String> loggedSignatures = ConcurrentHashMap.newKeySet();

    @Inject
    SyncErrorRepository syncErrorRepository;

    public boolean canInactivate(SyncRunState run, Integer reported) {
        return allowsAbsenceReconciliation(run) && classify(run, reported) == null;
    }

    /**
     * INCREMENTAL never infers absence. Missing from the incremental set is not a deletion.
     */
    public boolean allowsAbsenceReconciliation(SyncRunState run) {
        return run == null || (!run.isIncremental() && !run.isRecovery());
    }

    public String classify(SyncRunState run, Integer reported) {
        long fetched = fetched(run);
        if (reported == null || fetched != reported.longValue()) {
            return PURE_INCOMPLETE;
        }
        if (run.errors.get() > 0) {
            return SYNC_PROCESSING_FAILED;
        }
        return null;
    }

    public void failWithoutInactivation(SyncRunState run, Integer reported) {
        String code = classify(run, reported);
        if (code == null) {
            return;
        }
        run.fatal = true;
        run.errorCode = code;
        if (PURE_INCOMPLETE.equals(code)) {
            run.message = "PURE reported " + reported
                    + " records but only " + fetched(run)
                    + " were received. Inactive marks were not applied.";
            LOG.warning("syncId=" + run.syncId + " " + code
                    + " fetched=" + fetched(run) + " reported=" + reported
                    + " succeeded=" + run.processed.get()
                    + " failed=" + run.errors.get());
            return;
        }
        run.message = fetched(run) + " records were received from PURE, "
                + run.processed.get() + " processed successfully, "
                + run.errors.get() + " failed. Inactive marks were not applied.";
        LOG.warning("syncId=" + run.syncId + " " + code
                + " fetched=" + fetched(run)
                + " succeeded=" + run.processed.get()
                + " failed=" + run.errors.get());
    }

    public static long fetched(SyncRunState run) {
        return run.processed.get() + run.errors.get();
    }

    public void record(
            SyncRunState run,
            String entity,
            String stage,
            Long localRecordId,
            Long pureId,
            String uuid,
            Exception ex
    ) {
        record(run, entity, stage, localRecordId, pureId, uuid, uuid, ex);
    }

    public void record(
            SyncRunState run,
            String entity,
            String stage,
            Long localRecordId,
            Long pureId,
            String uuid,
            String recordKey,
            Exception ex
    ) {
        run.errors.incrementAndGet();
        String message = clip(ExceptionDetail.describe(ex), 2000);
        String code = errorCode(ex);
        String signature = run.syncId + "|" + stage + "|" + code + "|" + message;
        if (loggedSignatures.add(signature)) {
            ErrorLog.unexpected(LOG, "syncId=" + run.syncId + " stage=" + stage
                    + " uuid=" + uuid + " key=" + recordKey + " failed", ex);
        } else {
            LOG.warning("syncId=" + run.syncId + " stage=" + stage
                    + " uuid=" + uuid + " key=" + recordKey + " failed: " + message);
        }
        SyncItemError error = new SyncItemError();
        error.syncId = run.syncId;
        error.entity = entity;
        error.localRecordId = localRecordId;
        error.pureId = pureId;
        error.recordUuid = uuid;
        error.recordKey = recordKey;
        error.stage = stage;
        error.errorCode = code;
        error.errorMessage = message;
        try {
            syncErrorRepository.save(error);
        } catch (Exception persistEx) {
            LOG.log(Level.WARNING, "syncId=" + run.syncId
                    + " unable to persist sync_error original=" + code + " " + message, persistEx);
        }
    }

    private static String errorCode(Throwable ex) {
        Throwable root = root(ex);
        if (root instanceof ValidationException validation && validation.getCode() != null && !validation.getCode().isBlank()) {
            return validation.getCode();
        }
        if (root instanceof SQLException sql && sql.getSQLState() != null && !sql.getSQLState().isBlank()) {
            return "SQLSTATE_" + sql.getSQLState();
        }
        return root.getClass().getSimpleName();
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static Throwable root(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
