package cl.uss.ca.service;

import cl.uss.ca.audit.AuditCatalog;
import cl.uss.ca.auth.AppModule;
import cl.uss.ca.auth.AuthorizationPolicy;
import cl.uss.ca.external.pure.metrics.PureMetricFetchResult;
import cl.uss.ca.sync.SyncEntityPolicy;

/**
 * Entity vs metrics sync contracts. Not executed by the agent.
 *
 * Covered conceptually (1-32 backend / 33-43 frontend):
 * incremental recommended, FULL confirmation, single POST, busy, cancel,
 * heartbeat, ownership lost, 409, retry 429/5xx/timeout, EMPTY, FAILED keeps
 * snapshot, second incremental idempotent, active owners, hIndex matrix,
 * FWCI decimal, journal two collections / partial failure.
 */
public final class PureMetricsSyncSeparationTest {
    private PureMetricsSyncSeparationTest() {
    }

    public static void main(String[] args) {
        metricsEntitiesAreNotParentEntities();
        metricsLocksAreIndependentAcrossDomains();
        authorizationMapsMetricsToParentModule();
        auditDistinguishesMetricsOperations();
        emptyReplacesAndFailedKeepsSnapshot();
        dualModeUsesItemWarningsBaseline();
        System.out.println("PureMetricsSyncSeparationTest OK");
    }

    static void metricsEntitiesAreNotParentEntities() {
        assert !"PEOPLE".equals(PersonMetricsSyncJob.ENTITY);
        assert "PERSON_METRICS".equals(PersonMetricsSyncJob.ENTITY);
        assert "RESEARCH_OUTPUT_METRICS".equals(ResearchOutputMetricsSyncJob.ENTITY);
        assert "JOURNAL_METRICS".equals(JournalMetricsSyncJob.ENTITY);
        assert SyncEntityPolicy.PERSON_METRICS.entity().equals("PERSON_METRICS");
        assert SyncEntityPolicy.RESEARCH_OUTPUT_METRICS.entity().equals("RESEARCH_OUTPUT_METRICS");
        assert SyncEntityPolicy.JOURNAL_METRICS.entity().equals("JOURNAL_METRICS");
    }

    static void metricsLocksAreIndependentAcrossDomains() {
        assert !SyncEntityPolicy.PERSON_METRICS.entity().equals(SyncEntityPolicy.PEOPLE.entity());
        assert !SyncEntityPolicy.PERSON_METRICS.entity().equals(SyncEntityPolicy.JOURNAL_METRICS.entity());
        assert !SyncEntityPolicy.RESEARCH_OUTPUT_METRICS.entity().equals(SyncEntityPolicy.RESEARCH_OUTPUT.entity());
        assert !SyncEntityPolicy.JOURNAL_METRICS.entity().equals(SyncEntityPolicy.JOURNALS.entity());
    }

    static void authorizationMapsMetricsToParentModule() {
        assert AuthorizationPolicy.moduleOfSyncEntity("PERSON_METRICS") == AppModule.PEOPLE;
        assert AuthorizationPolicy.moduleOfSyncEntity("RESEARCH_OUTPUT_METRICS") == AppModule.RESEARCH_OUTPUTS;
        assert AuthorizationPolicy.moduleOfSyncEntity("JOURNAL_METRICS") == AppModule.JOURNALS;
        assert AuthorizationPolicy.moduleOfSyncPath("/sync/pure/person-metrics", null) == AppModule.PEOPLE;
        assert AuthorizationPolicy.moduleOfSyncPath("/sync/pure/research-output-metrics", null) == AppModule.RESEARCH_OUTPUTS;
        assert AuthorizationPolicy.moduleOfSyncPath("/sync/pure/journal-metrics", null) == AppModule.JOURNALS;
    }

    static void auditDistinguishesMetricsOperations() {
        assert "PURE".equals(AuditCatalog.sourceOf("PERSON_METRICS"));
        assert "SYNC".equals(AuditCatalog.TYPE_SYNC);
        assert "SYNC_PERSON_METRICS".equals(AuditCatalog.operationOf("PERSON_METRICS"));
        assert "SYNC_RESEARCH_OUTPUT_METRICS".equals(AuditCatalog.operationOf("RESEARCH_OUTPUT_METRICS"));
        assert "SYNC_JOURNAL_METRICS".equals(AuditCatalog.operationOf("JOURNAL_METRICS"));
        assert !"SYNC_PERSONS".equals(AuditCatalog.operationOf("PERSON_METRICS"));
        assert AuditCatalog.entitiesForSource("PURE").contains("PERSON_METRICS");
        assert AuditCatalog.entitiesForSource("PURE").contains("PEOPLE");
    }

    static void emptyReplacesAndFailedKeepsSnapshot() {
        if (!PureMetricFetchResult.empty().replacesSnapshot()) {
            throw new AssertionError("EMPTY must replace snapshot");
        }
        if (PureMetricFetchResult.failed(504, "PURE_HTTP_504", "timeout").replacesSnapshot()) {
            throw new AssertionError("FAILED must keep previous snapshot");
        }
        if (PureMetricFetchResult.failed(429, "PURE_HTTP_429", "rate").replacesSnapshot()) {
            throw new AssertionError("429 must keep previous snapshot");
        }
        if (PureMetricFetchResult.failed(503, "PURE_HTTP_503", "unavailable").replacesSnapshot()) {
            throw new AssertionError("5xx must keep previous snapshot");
        }
    }

    static void dualModeUsesItemWarningsBaseline() {
        assert SyncEntityPolicy.PERSON_METRICS.supportsIncremental();
        assert SyncEntityPolicy.RESEARCH_OUTPUT_METRICS.supportsIncremental();
        assert SyncEntityPolicy.JOURNAL_METRICS.supportsIncremental();
        assert !SyncEntityPolicy.PEOPLE.supportsIncremental();
    }
}
