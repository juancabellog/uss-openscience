package cl.uss.ca.service;

import cl.uss.ca.domain.DoiNormalizer;
import cl.uss.ca.exception.ValidationException;
import cl.uss.ca.external.unpaywall.UnpaywallClient;
import cl.uss.ca.external.unpaywall.UnpaywallFetchResult;
import cl.uss.ca.repository.UnpaywallDoiLink;
import cl.uss.ca.repository.UnpaywallReconcileResult;
import cl.uss.ca.repository.UnpaywallRepository;
import cl.uss.ca.repository.SyncRunRepository;
import cl.uss.ca.sync.SyncChangeRepository;
import cl.uss.ca.sync.SyncJobSupport;
import cl.uss.ca.sync.SyncRunState;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@ApplicationScoped
public class UnpaywallSyncJob {
    private static final Logger LOG = Logger.getLogger(UnpaywallSyncJob.class.getName());
    static final String ENTITY = "UNPAYWALL";
    private static final long DAILY_BUDGET = 100_000L;

    @Inject
    UnpaywallService unpaywallService;

    @Inject
    UnpaywallClient unpaywallClient;

    @Inject
    UnpaywallRepository unpaywallRepository;

    @Inject
    SyncRunRepository syncRunRepository;

    @Inject
    SyncChangeRepository syncChangeRepository;

    @Inject
    SyncJobSupport jobSupport;

    @Inject
    @ConfigProperty(name = "unpaywall.sync.delayMs", defaultValue = "50")
    long delayMs;

    @Inject
    @ConfigProperty(name = "unpaywall.maxConcurrency", defaultValue = "1")
    int maxConcurrency;

    @Inject
    @ConfigProperty(name = "unpaywall.refreshDays", defaultValue = "0")
    int refreshDays;

    public void run(SyncRunState run) throws Exception {
        unpaywallService.assertTables();
        Map<String, List<UnpaywallDoiLink>> unique = uniqueDois(unpaywallRepository.listActiveDoiLinks());
        if (run.isIncremental()) {
            unique = CrossrefSyncJob.retainIncremental(
                    unique,
                    unpaywallRepository.loadWorkStates(),
                    unpaywallRepository.loadMappedNormalizedDois(),
                    refreshDays,
                    OffsetDateTime.now(),
                    false
            );
        }
        run.totalExpected = unique.size();
        LOG.info("Starting UNPAYWALL sync id=" + run.syncId
                + " mode=" + run.syncMode
                + " candidates=" + unique.size()
                + " concurrency=" + Math.max(1, maxConcurrency));
        if (unique.size() > DAILY_BUDGET) {
            LOG.warning("syncId=" + run.syncId + " Unpaywall unique DOIs=" + unique.size()
                    + " exceed documented daily budget " + DAILY_BUDGET);
        }
        persistProgress(run);
        int index = 0;
        for (Map.Entry<String, List<UnpaywallDoiLink>> entry : unique.entrySet()) {
            index++;
            String normalized = entry.getKey();
            List<UnpaywallDoiLink> links = entry.getValue();
            String displayDoi = links.get(0).doi;
            try {
                processOne(run, normalized, displayDoi, links);
            } catch (Exception ex) {
                jobSupport.record(
                        run,
                        ENTITY,
                        SyncJobSupport.STAGE_UNPAYWALL_FETCH,
                        null,
                        null,
                        normalized,
                        ex
                );
                run.processed.incrementAndGet();
            }
            if (index % 10 == 0 || index == unique.size()) {
                persistProgress(run);
            }
            throttle();
        }
        persistProgress(run);
        LOG.info("syncId=" + run.syncId + " Unpaywall expected=" + unique.size()
                + " processed=" + run.processed.get()
                + " created=" + run.created.get()
                + " updated=" + run.updated.get()
                + " unchanged=" + run.unchanged.get()
                + " notFound=" + run.notFound.get()
                + " errors=" + run.errors.get());
    }

    private void processOne(SyncRunState run, String normalized, String displayDoi, List<UnpaywallDoiLink> links) {
        UnpaywallRepository.StoredWork stored = unpaywallRepository.findByNormalizedDoi(normalized);
        UnpaywallFetchResult fetch = unpaywallClient.fetchByDoi(normalized);
        if (fetch.notFound()) {
            persist(run, normalized, displayDoi, fetch, links);
            run.notFound.incrementAndGet();
            run.processed.incrementAndGet();
            return;
        }
        if (!fetch.found()) {
            jobSupport.record(
                    run,
                    ENTITY,
                    SyncJobSupport.STAGE_UNPAYWALL_FETCH,
                    stored == null ? null : stored.id,
                    null,
                    normalized,
                    new ValidationException(
                            fetch.errorCode == null ? "UNPAYWALL_HTTP_ERROR" : fetch.errorCode,
                            fetch.errorMessage == null ? "Unpaywall request failed" : fetch.errorMessage
                    )
            );
            run.processed.incrementAndGet();
            return;
        }
        persist(run, normalized, displayDoi, fetch, links);
        if (Boolean.TRUE.equals(fetch.work.isOa)) {
            run.oa.incrementAndGet();
        } else if (Boolean.FALSE.equals(fetch.work.isOa)) {
            run.nonOa.incrementAndGet();
        }
        run.processed.incrementAndGet();
    }

    private void persist(
            SyncRunState run,
            String normalized,
            String displayDoi,
            UnpaywallFetchResult fetch,
            List<UnpaywallDoiLink> links
    ) {
        try {
            UnpaywallReconcileResult result = unpaywallRepository.reconcile(
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
            jobSupport.record(
                    run,
                    ENTITY,
                    SyncJobSupport.STAGE_UNPAYWALL_UPSERT,
                    null,
                    null,
                    normalized,
                    ex
            );
        }
    }

    public static Map<String, List<UnpaywallDoiLink>> uniqueDois(List<UnpaywallDoiLink> links) {
        Map<String, List<UnpaywallDoiLink>> unique = new LinkedHashMap<>();
        if (links == null) {
            return unique;
        }
        for (UnpaywallDoiLink link : links) {
            String normalized = DoiNormalizer.normalize(link.doi);
            if (normalized == null) {
                continue;
            }
            unique.computeIfAbsent(normalized, key -> new ArrayList<>()).add(link);
        }
        return unique;
    }

    private void persistProgress(SyncRunState run) {
        try {
            syncRunRepository.requireOwnership(run);
            unpaywallRepository.saveRunStats(run);
        } catch (cl.uss.ca.sync.SyncOwnershipLostException ex) {
            throw ex;
        } catch (Exception ex) {
            LOG.warning("syncId=" + run.syncId + " Unpaywall heartbeat failed: " + ex.getMessage());
        }
    }

    private void throttle() throws InterruptedException {
        if (delayMs > 0) {
            Thread.sleep(delayMs);
        }
    }
}
