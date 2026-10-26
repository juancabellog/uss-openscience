package cl.uss.ca.sync;

import cl.uss.ca.exception.ValidationException;
import cl.uss.ca.repository.CrossrefDoiLink;
import cl.uss.ca.service.CrossrefSyncJob;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SyncMode / INCREMENTAL contract tests. Not executed by the agent.
 */
public final class SyncModeTest {
    private SyncModeTest() {
    }

    public static void main(String[] args) {
        persistedFullOnDto();
        persistedIncrementalOnDto();
        unsupportedModeRejected();
        invalidModeRejected();
        sameEntityLockKeyForBothModes();
        incrementalZeroCandidates();
        incrementalSkipsFreshCrossref();
        incrementalSelectsNewNeverSyncedErrorStale();
        sharedDoiOnce();
        consecutiveIncrementalEmpty();
        openAlexModesAndLock();
        openAlexIncrementalZeroCandidates();
        orcidNeverSyncedStaleErrorNew();
        orcidFreshSkipped();
        orcidStaleProfileReconsulted();
        researchOutputsRejectIncremental();
        incrementalNeverInactivates();
        fullCanInactivateWhenComplete();
        OpenAlexAuthorRecoveryTest.main(args);
        SyncModePolicyTest.main(args);
        System.out.println("SyncModeTest OK");
    }

    static void persistedFullOnDto() {
        SyncRunState run = new SyncRunState("CROSSREF", "user-1");
        run.syncId = "10";
        run.syncMode = SyncMode.FULL;
        assert "FULL".equals(run.toDto().syncMode);
        assert run.isFull();
    }

    static void persistedIncrementalOnDto() {
        SyncRunState run = new SyncRunState("ORCID", "user-1");
        run.syncId = "11";
        run.syncMode = SyncMode.INCREMENTAL;
        assert "INCREMENTAL".equals(run.toDto().syncMode);
        assert run.isIncremental();
        assert !"FULL".equals(run.toDto().status);
    }

    static void unsupportedModeRejected() {
        try {
            SyncEntityPolicy.RESEARCH_OUTPUT.resolve("INCREMENTAL");
            throw new AssertionError("expected SYNC_MODE_NOT_SUPPORTED");
        } catch (ValidationException ex) {
            assert "SYNC_MODE_NOT_SUPPORTED".equals(ex.getCode());
        }
        try {
            SyncEntityPolicy.PEOPLE.resolve("INCREMENTAL");
            throw new AssertionError("expected SYNC_MODE_NOT_SUPPORTED");
        } catch (ValidationException ex) {
            assert "SYNC_MODE_NOT_SUPPORTED".equals(ex.getCode());
        }
        try {
            SyncEntityPolicy.JOURNALS.resolve("INCREMENTAL");
            throw new AssertionError("expected SYNC_MODE_NOT_SUPPORTED");
        } catch (ValidationException ex) {
            assert "SYNC_MODE_NOT_SUPPORTED".equals(ex.getCode());
        }
        try {
            SyncEntityPolicy.PROJECTS.resolve("INCREMENTAL");
            throw new AssertionError("expected SYNC_MODE_NOT_SUPPORTED");
        } catch (ValidationException ex) {
            assert "SYNC_MODE_NOT_SUPPORTED".equals(ex.getCode());
        }
        try {
            SyncEntityPolicy.ORGANIZATIONS.resolve("INCREMENTAL");
            throw new AssertionError("expected SYNC_MODE_NOT_SUPPORTED");
        } catch (ValidationException ex) {
            assert "SYNC_MODE_NOT_SUPPORTED".equals(ex.getCode());
        }
        assert SyncMode.FULL == SyncEntityPolicy.RESEARCH_OUTPUT.resolve(null);
        assert SyncMode.FULL == SyncEntityPolicy.CROSSREF.resolve(null);
        assert SyncMode.FULL == SyncEntityPolicy.OPENALEX.resolve(null);
        assert SyncMode.FULL == SyncEntityPolicy.ORCID.resolve("");
        assert SyncMode.FULL == SyncEntityPolicy.CROSSREF.resolve("FULL");
    }

    static void invalidModeRejected() {
        try {
            SyncMode.parse("WEEKLY");
            throw new AssertionError("expected INVALID_SYNC_MODE");
        } catch (ValidationException ex) {
            assert "INVALID_SYNC_MODE".equals(ex.getCode());
        }
    }

    static void sameEntityLockKeyForBothModes() {
        assert "CROSSREF".equals(SyncEntityPolicy.CROSSREF.entity());
        assert SyncEntityPolicy.CROSSREF.supports(SyncMode.FULL);
        assert SyncEntityPolicy.CROSSREF.supports(SyncMode.INCREMENTAL);
    }

    static void incrementalZeroCandidates() {
        OffsetDateTime now = OffsetDateTime.parse("2026-10-05T12:00:00Z");
        Map<String, List<CrossrefDoiLink>> unique = Map.of();
        Map<String, List<CrossrefDoiLink>> selected = CrossrefSyncJob.retainIncremental(
                unique, Map.of(), Set.of(), 30, now, true
        );
        assert selected.isEmpty();
    }

    static void incrementalSkipsFreshCrossref() {
        OffsetDateTime now = OffsetDateTime.parse("2026-10-05T12:00:00Z");
        WorkSyncState fresh = present("FOUND", now.minusDays(2), true);
        Map<String, List<CrossrefDoiLink>> unique = unique("10.1/aaa");
        Map<String, WorkSyncState> stored = Map.of("10.1/aaa", fresh);
        Map<String, List<CrossrefDoiLink>> selected = CrossrefSyncJob.retainIncremental(
                unique, stored, Set.of("10.1/aaa"), 30, now, true
        );
        assert selected.isEmpty();
    }

    static void incrementalSelectsNewNeverSyncedErrorStale() {
        OffsetDateTime now = OffsetDateTime.parse("2026-10-05T12:00:00Z");
        Map<String, List<CrossrefDoiLink>> unique = unique(
                "10.1/new", "10.1/never", "10.1/error", "10.1/stale", "10.1/fresh"
        );
        Map<String, WorkSyncState> stored = new HashMap<>();
        stored.put("10.1/never", present("FOUND", null, true));
        stored.put("10.1/error", present("ERROR", now.minusDays(1), true));
        stored.put("10.1/stale", present("FOUND", now.minusDays(40), true));
        stored.put("10.1/fresh", present("FOUND", now.minusDays(2), true));
        Map<String, List<CrossrefDoiLink>> selected = CrossrefSyncJob.retainIncremental(
                unique,
                stored,
                Set.of("10.1/never", "10.1/error", "10.1/stale", "10.1/fresh"),
                30,
                now,
                true
        );
        assert selected.size() == 4;
        assert selected.containsKey("10.1/new");
        assert selected.containsKey("10.1/never");
        assert selected.containsKey("10.1/error");
        assert selected.containsKey("10.1/stale");
        assert !selected.containsKey("10.1/fresh");
        assert IncrementalReason.NEW == DoiIncrementalClassifier.classifyCrossref(WorkSyncState.missing(), 30, now);
        assert IncrementalReason.NEVER_SYNCED == DoiIncrementalClassifier.classifyCrossref(stored.get("10.1/never"), 30, now);
        WorkSyncState error = stored.get("10.1/error");
        error.mapped = true;
        assert IncrementalReason.REMOTE_ERROR == DoiIncrementalClassifier.classifyCrossref(error, 30, now);
        WorkSyncState stale = stored.get("10.1/stale");
        stale.mapped = true;
        assert IncrementalReason.STALE == DoiIncrementalClassifier.classifyCrossref(stale, 30, now);
    }

    static void sharedDoiOnce() {
        CrossrefDoiLink a = link("https://doi.org/10.1038/nature12373", 1);
        CrossrefDoiLink b = link("DOI:10.1038/NATURE12373", 2);
        CrossrefDoiLink c = link("10.1038/nature12373", 3);
        CrossrefDoiLink d = link("10.1038/nature12373", 4);
        var unique = CrossrefSyncJob.uniqueDois(List.of(a, b, c, d));
        assert unique.size() == 1;
        assert unique.values().iterator().next().size() == 4;
        OffsetDateTime now = OffsetDateTime.parse("2026-10-05T12:00:00Z");
        Map<String, List<CrossrefDoiLink>> selected = CrossrefSyncJob.retainIncremental(
                unique, Map.of(), Set.of(), 30, now, true
        );
        assert selected.size() == 1;
    }

    static void consecutiveIncrementalEmpty() {
        OffsetDateTime now = OffsetDateTime.parse("2026-10-05T12:00:00Z");
        Map<String, List<CrossrefDoiLink>> unique = unique("10.1/a", "10.1/b");
        Map<String, WorkSyncState> stored = Map.of(
                "10.1/a", present("FOUND", now.minusDays(1), true),
                "10.1/b", present("NOT_FOUND", now.minusDays(3), true)
        );
        Map<String, List<CrossrefDoiLink>> first = CrossrefSyncJob.retainIncremental(
                unique, stored, Set.of("10.1/a", "10.1/b"), 30, now, true
        );
        assert first.isEmpty();
        Map<String, List<CrossrefDoiLink>> second = CrossrefSyncJob.retainIncremental(
                unique, stored, Set.of("10.1/a", "10.1/b"), 30, now, true
        );
        assert second.isEmpty();
    }

    static void openAlexModesAndLock() {
        assert "OPENALEX".equals(SyncEntityPolicy.OPENALEX.entity());
        assert SyncEntityPolicy.OPENALEX.supports(SyncMode.FULL);
        assert SyncEntityPolicy.OPENALEX.supports(SyncMode.INCREMENTAL);
        assert SyncMode.FULL == SyncEntityPolicy.OPENALEX.defaultMode();
        assert SyncMode.FULL == SyncEntityPolicy.OPENALEX.resolve("FULL");
    }

    static void openAlexIncrementalZeroCandidates() {
        OffsetDateTime now = OffsetDateTime.parse("2026-10-05T12:00:00Z");
        Map<String, List<cl.uss.ca.repository.OpenAlexDoiLink>> unique = Map.of();
        Map<String, List<cl.uss.ca.repository.OpenAlexDoiLink>> selected = cl.uss.ca.service.OpenAlexSyncJob.retainIncremental(
                unique, Map.of(), Set.of(), 30, now
        );
        assert selected.isEmpty();
    }

    static void orcidNeverSyncedStaleErrorNew() {
        OffsetDateTime now = OffsetDateTime.parse("2026-10-05T12:00:00Z");
        assert IncrementalReason.NEW == OrcidIncrementalClassifier.classify(
                false, null, "0000-0001-2345-6789", null, null, false, 30, now
        );
        assert IncrementalReason.NEVER_SYNCED == OrcidIncrementalClassifier.classify(
                true, "0000-0001-2345-6789", "0000-0001-2345-6789", null, null, false, 30, now
        );
        assert IncrementalReason.REMOTE_ERROR == OrcidIncrementalClassifier.classify(
                true, "0000-0001-2345-6789", "0000-0001-2345-6789", now.minusDays(1), "FAILED", false, 30, now
        );
        assert IncrementalReason.STALE == OrcidIncrementalClassifier.classify(
                true, "0000-0001-2345-6789", "0000-0001-2345-6789", now.minusDays(40), "COMPLETED", false, 30, now
        );
        assert IncrementalReason.SOURCE_CHANGED == OrcidIncrementalClassifier.classify(
                true, "0000-0001-1111-1111", "0000-0001-2345-6789", now.minusDays(1), "COMPLETED", false, 30, now
        );
        assert IncrementalReason.MANUAL_REFRESH == OrcidIncrementalClassifier.classify(
                true, "0000-0001-2345-6789", "0000-0001-2345-6789", now.minusDays(1), "COMPLETED", true, 30, now
        );
        assert IncrementalReason.PARTIAL_DATA == OrcidIncrementalClassifier.classify(
                true, "0000-0001-2345-6789", "0000-0001-2345-6789", now.minusDays(1), "COMPLETED_WITH_WARNINGS", false, 30, now
        );
    }

    static void orcidFreshSkipped() {
        OffsetDateTime now = OffsetDateTime.parse("2026-10-05T12:00:00Z");
        assert OrcidIncrementalClassifier.classify(
                true, "0000-0001-2345-6789", "0000-0001-2345-6789", now.minusDays(2), "COMPLETED", false, 30, now
        ) == null;
    }

    static void orcidStaleProfileReconsulted() {
        OffsetDateTime now = OffsetDateTime.parse("2026-10-05T12:00:00Z");
        assert IncrementalReason.STALE == OrcidIncrementalClassifier.classify(
                true, "0000-0001-2345-6789", "0000-0001-2345-6789", now.minusDays(31), "COMPLETED", false, 30, now
        );
    }

    static void researchOutputsRejectIncremental() {
        assert !SyncEntityPolicy.RESEARCH_OUTPUT.supportsIncremental();
        assert SyncEntityPolicy.RESEARCH_OUTPUT.supportsFull();
        assert SyncMode.FULL == SyncEntityPolicy.RESEARCH_OUTPUT.defaultMode();
    }

    static void incrementalNeverInactivates() {
        SyncJobSupport support = new SyncJobSupport();
        SyncRunState run = new SyncRunState("RESEARCH_OUTPUT", "user-1");
        run.syncMode = SyncMode.INCREMENTAL;
        run.totalExpected = 100;
        run.processed.set(5);
        assert !support.allowsAbsenceReconciliation(run);
        assert !support.canInactivate(run, 100);
    }

    static void fullCanInactivateWhenComplete() {
        SyncJobSupport support = new SyncJobSupport();
        SyncRunState run = new SyncRunState("RESEARCH_OUTPUT", "user-1");
        run.syncMode = SyncMode.FULL;
        run.processed.set(10);
        assert support.allowsAbsenceReconciliation(run);
        assert support.canInactivate(run, 10);
        run.errors.incrementAndGet();
        assert !support.canInactivate(run, 10);
    }

    private static WorkSyncState present(String status, OffsetDateTime lastSyncAt, boolean mapped) {
        WorkSyncState state = new WorkSyncState();
        state.present = true;
        state.status = status;
        state.lastSyncAt = lastSyncAt;
        state.mapped = mapped;
        return state;
    }

    private static Map<String, List<CrossrefDoiLink>> unique(String... dois) {
        Map<String, List<CrossrefDoiLink>> unique = new HashMap<>();
        long id = 1;
        for (String doi : dois) {
            unique.put(doi, List.of(link(doi, id++)));
        }
        return unique;
    }

    private static CrossrefDoiLink link(String doi, long outputId) {
        CrossrefDoiLink link = new CrossrefDoiLink();
        link.doi = doi;
        link.researchOutputId = outputId;
        return link;
    }
}
