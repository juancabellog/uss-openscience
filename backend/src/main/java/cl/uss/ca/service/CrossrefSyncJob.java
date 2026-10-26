package cl.uss.ca.service;

import cl.uss.ca.domain.DoiNormalizer;
import cl.uss.ca.exception.ValidationException;
import cl.uss.ca.external.crossref.CrossrefClient;
import cl.uss.ca.external.crossref.CrossrefFetchResult;
import cl.uss.ca.external.crossref.CrossrefStatus;
import cl.uss.ca.quality.DataQualityIdentifiers;
import cl.uss.ca.repository.CrossrefDoiLink;
import cl.uss.ca.repository.CrossrefReconcileResult;
import cl.uss.ca.repository.CrossrefRepository;
import cl.uss.ca.repository.SyncRunRepository;
import cl.uss.ca.sync.DoiIncrementalClassifier;
import cl.uss.ca.sync.IncrementalReason;
import cl.uss.ca.sync.SyncChangeRepository;
import cl.uss.ca.sync.SyncJobSupport;
import cl.uss.ca.sync.SyncRunState;
import cl.uss.ca.sync.WorkSyncState;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

@ApplicationScoped
public class CrossrefSyncJob {
    private static final Logger LOG = Logger.getLogger(CrossrefSyncJob.class.getName());
    static final String ENTITY = "CROSSREF";

    @Inject
    CrossrefService crossrefService;

    @Inject
    CrossrefClient crossrefClient;

    @Inject
    CrossrefRepository crossrefRepository;

    @Inject
    SyncRunRepository syncRunRepository;

    @Inject
    SyncChangeRepository syncChangeRepository;

    @Inject
    SyncJobSupport jobSupport;

    @Inject
    @ConfigProperty(name = "crossref.sync.delayMs", defaultValue = "80")
    long delayMs;

    @Inject
    @ConfigProperty(name = "crossref.maxConcurrency", defaultValue = "1")
    int maxConcurrency;

    @Inject
    @ConfigProperty(name = "crossref.refreshDays", defaultValue = "30")
    int refreshDays;

    private volatile boolean fullMode;

    public void runFull(SyncRunState run) throws Exception {
        run.syncMode = cl.uss.ca.sync.SyncMode.FULL;
        run(run);
    }

    public void runIncremental(SyncRunState run) throws Exception {
        run.syncMode = cl.uss.ca.sync.SyncMode.INCREMENTAL;
        run(run);
    }

    public void run(SyncRunState run) throws Exception {
        fullMode = run.isFull();
        crossrefService.assertTables();
        Map<String, List<CrossrefDoiLink>> unique = uniqueDois(crossrefRepository.listDoiLinks(run.isIncremental()));
        if (run.isIncremental()) {
            unique = retainIncremental(
                    unique,
                    crossrefRepository.loadWorkStates(),
                    crossrefRepository.loadMappedNormalizedDois(),
                    refreshDays,
                    OffsetDateTime.now(),
                    true
            );
        }
        run.totalExpected = unique.size();
        LOG.info("Starting CROSSREF sync id=" + run.syncId
                + " mode=" + run.syncMode
                + " candidates=" + unique.size()
                + " concurrency=" + Math.max(1, maxConcurrency));
        persistProgress(run);
        int index = 0;
        for (Map.Entry<String, List<CrossrefDoiLink>> entry : unique.entrySet()) {
            index++;
            String normalized = entry.getKey();
            List<CrossrefDoiLink> links = entry.getValue();
            String displayDoi = links.get(0).doi;
            try {
                processOne(run, normalized, displayDoi, links);
            } catch (Exception ex) {
                jobSupport.record(run, ENTITY, SyncJobSupport.STAGE_CROSSREF_FETCH, null, null, normalized, ex);
                run.processed.incrementAndGet();
            }
            if (index % 10 == 0 || index == unique.size()) {
                persistProgress(run);
            }
            throttle();
        }
        persistProgress(run);
        LOG.info("syncId=" + run.syncId
                + " Crossref processed=" + run.processed.get()
                + " created=" + run.created.get()
                + " updated=" + run.updated.get()
                + " unchanged=" + run.unchanged.get()
                + " errors=" + run.errors.get());
    }

    private void processOne(SyncRunState run, String normalized, String displayDoi, List<CrossrefDoiLink> links) {
        if (!DataQualityIdentifiers.isValidDoi(normalized)) {
            persist(run, normalized, displayDoi, CrossrefFetchResult.invalid("DOI format is invalid"), links);
            run.markedInactive.incrementAndGet();
            run.processed.incrementAndGet();
            return;
        }
        CrossrefRepository.StoredWork stored = crossrefRepository.findByNormalizedDoi(normalized);
        CrossrefFetchResult fetch = crossrefClient.fetchWork(normalized);
        if (CrossrefStatus.FOUND.equals(fetch.status)) {
            run.oa.incrementAndGet();
        } else if (CrossrefStatus.OTHER_AGENCY.equals(fetch.status)) {
            run.nonOa.incrementAndGet();
        } else if (CrossrefStatus.NOT_FOUND.equals(fetch.status)) {
            run.notFound.incrementAndGet();
        } else if (CrossrefStatus.INVALID_DOI.equals(fetch.status)) {
            run.markedInactive.incrementAndGet();
        } else if (fetch.retryable()) {
            jobSupport.record(
                    run,
                    ENTITY,
                    SyncJobSupport.STAGE_CROSSREF_FETCH,
                    stored == null ? null : stored.id,
                    null,
                    normalized,
                    new ValidationException(
                            fetch.errorCode == null ? "CROSSREF_HTTP_ERROR" : fetch.errorCode,
                            fetch.errorMessage == null ? "Crossref request failed" : fetch.errorMessage
                    )
            );
            run.processed.incrementAndGet();
            return;
        }
        persist(run, normalized, displayDoi, fetch, links);
        run.processed.incrementAndGet();
    }

    private void persist(
            SyncRunState run,
            String normalized,
            String displayDoi,
            CrossrefFetchResult fetch,
            List<CrossrefDoiLink> links
    ) {
        try {
            CrossrefReconcileResult result = crossrefRepository.reconcile(
                    normalized, displayDoi, fetch, links, run.syncId
            );
            if ("CREATED".equals(result.kind)) {
                run.created.incrementAndGet();
            } else if ("UPDATED".equals(result.kind)) {
                run.updated.incrementAndGet();
            } else {
                run.unchanged.incrementAndGet();
            }
            syncChangeRepository.save(run.syncId, result.changes);
        } catch (Exception ex) {
            jobSupport.record(run, ENTITY, SyncJobSupport.STAGE_CROSSREF_UPSERT, null, null, normalized, ex);
        }
    }

    public static Map<String, List<CrossrefDoiLink>> uniqueDois(List<CrossrefDoiLink> links) {
        Map<String, List<CrossrefDoiLink>> unique = new LinkedHashMap<>();
        if (links == null) {
            return unique;
        }
        for (CrossrefDoiLink link : links) {
            String normalized = DoiNormalizer.normalize(link.doi);
            if (normalized == null) {
                continue;
            }
            unique.computeIfAbsent(normalized.toLowerCase(Locale.ROOT), key -> new ArrayList<>()).add(link);
        }
        return unique;
    }

    public static <V> Map<String, V> retainIncremental(
            Map<String, V> unique,
            Map<String, WorkSyncState> storedByDoi,
            Set<String> mappedDois,
            int refreshDays,
            OffsetDateTime now,
            boolean crossref
    ) {
        Map<String, V> selected = new LinkedHashMap<>();
        if (unique == null) {
            return selected;
        }
        for (Map.Entry<String, V> entry : unique.entrySet()) {
            WorkSyncState state = storedByDoi == null ? null : storedByDoi.get(entry.getKey());
            if (state == null) {
                state = WorkSyncState.missing();
            }
            state.mapped = mappedDois != null && mappedDois.contains(entry.getKey());
            IncrementalReason reason = crossref
                    ? DoiIncrementalClassifier.classifyCrossref(state, refreshDays, now)
                    : DoiIncrementalClassifier.classifyUnpaywall(state, refreshDays, now);
            if (reason != null) {
                selected.put(entry.getKey(), entry.getValue());
            }
        }
        return selected;
    }

    private void persistProgress(SyncRunState run) {
        try {
            syncRunRepository.requireOwnership(run);
            crossrefRepository.saveRunStats(run);
        } catch (cl.uss.ca.sync.SyncOwnershipLostException ex) {
            throw ex;
        } catch (Exception ex) {
            LOG.warning("syncId=" + run.syncId + " Crossref heartbeat failed: " + ex.getMessage());
        }
    }

    private void throttle() {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ValidationException("CROSSREF_INTERRUPTED", "Crossref sync was interrupted");
        }
    }
}
