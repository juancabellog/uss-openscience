package cl.uss.ca.sync;

import cl.uss.ca.api.dto.SyncModePolicyDto;
import cl.uss.ca.api.dto.SyncRunDto;
import cl.uss.ca.exception.ValidationException;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Baseline / recommended-mode contract tests. Not executed by the agent.
 */
public final class SyncModePolicyTest {
    private SyncModePolicyTest() {
    }

    public static void main(String[] args) {
        neverSyncedRecommendsFullAndRejectsIncremental();
        successfulFullAllowsIncremental();
        fullThenIncrementalStillRecommendsIncremental();
        failedIncrementalKeepsBaselineAndRecommendsIncremental();
        incrementalWarningsRecommendIncremental();
        laterFailedFullKeepsHistoricalBaselineAndRecommendsFull();
        fullWarningsWithoutBaselineRecommendFull();
        fullWarningsWithBaselineRecommendIncremental();
        fullOnlyRejectsIncremental();
        manualFullRemainsAvailableAfterBaseline();
        dspaceSupportsIncrementalLikeOpenAlex();
        nullSyncModeIsFullForLegacyItemIntegrations();
        metricsEntitiesAreDualModeAndIndependentFromParent();
        System.out.println("SyncModePolicyTest OK");
    }

    static void neverSyncedRecommendsFullAndRejectsIncremental() {
        SyncModePolicyDto policy = SyncModePolicy.evaluate(
                SyncEntityPolicy.ORCID, null, List.of(), null
        );
        assert !policy.hasValidBaseline;
        assert "FULL".equals(policy.recommendedSyncMode);
        assert policy.availableSyncModes.equals(List.of("FULL"));
        assert SyncMode.FULL == SyncModePolicy.resolveStart(SyncEntityPolicy.ORCID, null, policy);
        try {
            SyncModePolicy.resolveStart(SyncEntityPolicy.ORCID, "INCREMENTAL", policy);
            throw new AssertionError("expected SYNC_BASELINE_REQUIRED");
        } catch (ValidationException ex) {
            assert "SYNC_BASELINE_REQUIRED".equals(ex.getCode());
        }
    }

    static void successfulFullAllowsIncremental() {
        SyncRunDto full = run("ORCID", "FULL", "COMPLETED", "2026-09-28T20:50:00Z");
        SyncModePolicyDto policy = SyncModePolicy.evaluate(
                SyncEntityPolicy.ORCID, full, List.of(full), full
        );
        assert policy.hasValidBaseline;
        assert "INCREMENTAL".equals(policy.recommendedSyncMode);
        assert policy.availableSyncModes.contains("INCREMENTAL");
        assert SyncMode.INCREMENTAL == SyncModePolicy.resolveStart(SyncEntityPolicy.ORCID, null, policy);
        assert SyncMode.INCREMENTAL == SyncModePolicy.resolveStart(SyncEntityPolicy.ORCID, "INCREMENTAL", policy);
    }

    static void fullThenIncrementalStillRecommendsIncremental() {
        SyncRunDto full = run("UNPAYWALL", "FULL", "COMPLETED", "2026-10-01T10:00:00Z");
        SyncRunDto incremental = run("UNPAYWALL", "INCREMENTAL", "COMPLETED", "2026-10-07T10:00:00Z");
        SyncModePolicyDto policy = SyncModePolicy.evaluate(
                SyncEntityPolicy.UNPAYWALL, incremental, List.of(full), full
        );
        assert policy.hasValidBaseline;
        assert "INCREMENTAL".equals(policy.recommendedSyncMode);
        assert SyncMode.INCREMENTAL == SyncModePolicy.resolveStart(SyncEntityPolicy.UNPAYWALL, null, policy);
    }

    static void failedIncrementalKeepsBaselineAndRecommendsIncremental() {
        SyncRunDto full = run("UNPAYWALL", "FULL", "COMPLETED", "2026-09-01T10:00:00Z");
        SyncRunDto failedInc = run("UNPAYWALL", "INCREMENTAL", "FAILED", "2026-10-06T08:00:00Z");
        SyncModePolicyDto policy = SyncModePolicy.evaluate(
                SyncEntityPolicy.UNPAYWALL, failedInc, List.of(full), full
        );
        assert policy.hasValidBaseline;
        assert "INCREMENTAL".equals(policy.recommendedSyncMode);
    }

    static void incrementalWarningsRecommendIncremental() {
        SyncRunDto full = run("CROSSREF", "FULL", "COMPLETED", "2026-09-01T10:00:00Z");
        SyncRunDto warnings = run("CROSSREF", "INCREMENTAL", "COMPLETED_WITH_WARNINGS", "2026-10-06T08:00:00Z");
        SyncModePolicyDto policy = SyncModePolicy.evaluate(
                SyncEntityPolicy.CROSSREF, warnings, List.of(full), full
        );
        assert policy.hasValidBaseline;
        assert "INCREMENTAL".equals(policy.recommendedSyncMode);
    }

    static void laterFailedFullKeepsHistoricalBaselineAndRecommendsFull() {
        SyncRunDto historic = run("OPENALEX", "FULL", "COMPLETED", "2026-08-01T10:00:00Z");
        SyncRunDto failedFull = run("OPENALEX", "FULL", "FAILED", "2026-10-06T08:00:00Z");
        SyncModePolicyDto policy = SyncModePolicy.evaluate(
                SyncEntityPolicy.OPENALEX, failedFull, List.of(historic), failedFull
        );
        assert policy.hasValidBaseline;
        assert "FULL".equals(policy.recommendedSyncMode);
        assert policy.availableSyncModes.contains("INCREMENTAL");
        assert SyncMode.FULL == SyncModePolicy.resolveStart(SyncEntityPolicy.OPENALEX, null, policy);
        assert SyncMode.INCREMENTAL == SyncModePolicy.resolveStart(SyncEntityPolicy.OPENALEX, "INCREMENTAL", policy);
    }

    static void fullWarningsWithoutBaselineRecommendFull() {
        SyncRunDto warnings = run("PEOPLE", "FULL", "COMPLETED_WITH_WARNINGS", "2026-10-06T08:00:00Z");
        SyncModePolicyDto policy = SyncModePolicy.evaluate(
                SyncEntityPolicy.PEOPLE, warnings, List.of(warnings), warnings
        );
        assert !policy.hasValidBaseline;
        assert "FULL".equals(policy.recommendedSyncMode);
        assert !policy.availableSyncModes.contains("INCREMENTAL");
    }

    static void fullWarningsWithBaselineRecommendIncremental() {
        SyncRunDto warnings = run("ORCID", "FULL", "COMPLETED_WITH_WARNINGS", "2026-10-06T08:00:00Z");
        SyncModePolicyDto policy = SyncModePolicy.evaluate(
                SyncEntityPolicy.ORCID, warnings, List.of(warnings), warnings
        );
        assert policy.hasValidBaseline;
        assert "INCREMENTAL".equals(policy.recommendedSyncMode);
        assert SyncBaselineKind.ITEM_WARNINGS_OK.establishesBaseline(warnings);
        assert !SyncBaselineKind.COMPLETED_FULL_ONLY.establishesBaseline(warnings);
    }

    static void fullOnlyRejectsIncremental() {
        SyncRunDto full = run("RESEARCH_OUTPUT", "FULL", "COMPLETED", "2026-09-01T10:00:00Z");
        SyncModePolicyDto policy = SyncModePolicy.evaluate(
                SyncEntityPolicy.RESEARCH_OUTPUT, full, List.of(full), full
        );
        assert !SyncEntityPolicy.RESEARCH_OUTPUT.supportsIncremental();
        assert policy.availableSyncModes.equals(List.of("FULL"));
        try {
            SyncModePolicy.resolveStart(SyncEntityPolicy.RESEARCH_OUTPUT, "INCREMENTAL", policy);
            throw new AssertionError("expected SYNC_MODE_NOT_SUPPORTED");
        } catch (ValidationException ex) {
            assert "SYNC_MODE_NOT_SUPPORTED".equals(ex.getCode());
        }
    }

    static void dspaceSupportsIncrementalLikeOpenAlex() {
        assert SyncEntityPolicy.DSPACE.supportsIncremental();
        SyncRunDto full = run("DSPACE", "FULL", "COMPLETED_WITH_WARNINGS", "2026-10-06T08:00:00Z");
        SyncModePolicyDto policy = SyncModePolicy.evaluate(
                SyncEntityPolicy.DSPACE, full, List.of(full), full
        );
        assert policy.hasValidBaseline;
        assert policy.availableSyncModes.contains("INCREMENTAL");
        assert "INCREMENTAL".equals(policy.recommendedSyncMode);
    }

    static void nullSyncModeIsFullForLegacyItemIntegrations() {
        SyncRunDto orcid = run("ORCID", null, "COMPLETED", "2026-09-01T10:00:00Z");
        SyncRunDto unpaywall = run("UNPAYWALL", null, "COMPLETED_WITH_WARNINGS", "2026-09-02T10:00:00Z");
        SyncRunDto crossref = run("CROSSREF", null, "COMPLETED", "2026-09-03T10:00:00Z");
        SyncRunDto dspace = run("DSPACE", null, "COMPLETED_WITH_WARNINGS", "2026-10-06T08:00:00Z");
        SyncRunDto openalex = run("OPENALEX", null, "COMPLETED", "2026-09-04T10:00:00Z");
        assert SyncBaselineKind.isFull(orcid);
        assert SyncBaselineKind.ITEM_WARNINGS_OK.establishesBaseline(orcid);
        assert SyncBaselineKind.ITEM_WARNINGS_OK.establishesBaseline(unpaywall);
        assert SyncBaselineKind.isFull(crossref);
        assert SyncBaselineKind.ITEM_WARNINGS_OK.establishesBaseline(crossref);
        assert SyncBaselineKind.isFull(dspace);
        assert SyncBaselineKind.ITEM_WARNINGS_OK.establishesBaseline(dspace);
        assert !SyncBaselineKind.isFull(openalex);
        SyncModePolicyDto orcidPolicy = SyncModePolicy.evaluate(
                SyncEntityPolicy.ORCID, orcid, List.of(orcid), orcid
        );
        assert orcidPolicy.hasValidBaseline;
        assert "INCREMENTAL".equals(orcidPolicy.recommendedSyncMode);
        SyncModePolicyDto unpaywallPolicy = SyncModePolicy.evaluate(
                SyncEntityPolicy.UNPAYWALL, unpaywall, List.of(unpaywall), unpaywall
        );
        assert unpaywallPolicy.hasValidBaseline;
        assert "INCREMENTAL".equals(unpaywallPolicy.recommendedSyncMode);
        SyncModePolicyDto crossrefPolicy = SyncModePolicy.evaluate(
                SyncEntityPolicy.CROSSREF, crossref, List.of(crossref), crossref
        );
        assert crossrefPolicy.hasValidBaseline;
        assert "INCREMENTAL".equals(crossrefPolicy.recommendedSyncMode);
        assert crossrefPolicy.availableSyncModes.contains("INCREMENTAL");
        SyncModePolicyDto dspacePolicy = SyncModePolicy.evaluate(
                SyncEntityPolicy.DSPACE, dspace, List.of(dspace), dspace
        );
        assert dspacePolicy.hasValidBaseline;
        assert "INCREMENTAL".equals(dspacePolicy.recommendedSyncMode);
        SyncModePolicyDto openalexPolicy = SyncModePolicy.evaluate(
                SyncEntityPolicy.OPENALEX, openalex, List.of(), openalex
        );
        assert !openalexPolicy.hasValidBaseline;
        assert "FULL".equals(openalexPolicy.recommendedSyncMode);
    }

    static void metricsEntitiesAreDualModeAndIndependentFromParent() {
        assert SyncEntityPolicy.PERSON_METRICS.supportsIncremental();
        assert SyncEntityPolicy.RESEARCH_OUTPUT_METRICS.supportsIncremental();
        assert SyncEntityPolicy.JOURNAL_METRICS.supportsIncremental();
        assert !SyncEntityPolicy.PEOPLE.supportsIncremental();
        assert !SyncEntityPolicy.RESEARCH_OUTPUT.supportsIncremental();
        assert !SyncEntityPolicy.JOURNALS.supportsIncremental();
        assert !SyncEntityPolicy.ORGANIZATIONS.supportsIncremental();
        SyncRunDto full = run("PERSON_METRICS", "FULL", "COMPLETED_WITH_WARNINGS", "2026-10-19T13:40:00Z");
        SyncModePolicyDto policy = SyncModePolicy.evaluate(
                SyncEntityPolicy.PERSON_METRICS, full, List.of(full), full
        );
        assert policy.hasValidBaseline;
        assert "INCREMENTAL".equals(policy.recommendedSyncMode);
        assert policy.availableSyncModes.contains("FULL");
        assert policy.availableSyncModes.contains("INCREMENTAL");
    }

    static void manualFullRemainsAvailableAfterBaseline() {
        SyncRunDto full = run("ORCID", "FULL", "COMPLETED", "2026-09-28T20:50:00Z");
        SyncModePolicyDto policy = SyncModePolicy.evaluate(
                SyncEntityPolicy.ORCID, full, List.of(full), full
        );
        assert policy.availableSyncModes.contains("FULL");
        assert SyncMode.FULL == SyncModePolicy.resolveStart(SyncEntityPolicy.ORCID, "FULL", policy);
    }

    private static SyncRunDto run(String entity, String mode, String status, String finishedAt) {
        SyncRunDto dto = new SyncRunDto();
        dto.entity = entity;
        dto.syncMode = mode;
        dto.status = status;
        dto.finishedAt = OffsetDateTime.parse(finishedAt);
        dto.startedAt = dto.finishedAt;
        return dto;
    }
}
