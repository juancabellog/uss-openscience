package cl.uss.ca.service;

import cl.uss.ca.exception.ValidationException;
import cl.uss.ca.external.pure.PureClientException;
import cl.uss.ca.external.pure.metrics.PureMetricFetchOutcome;
import cl.uss.ca.external.pure.metrics.PureMetricFetchResult;
import cl.uss.ca.external.pure.metrics.PureMetricOwner;
import cl.uss.ca.external.pure.metrics.PureMetricsParser;
import cl.uss.ca.repository.PureMetricRepository;
import cl.uss.ca.repository.PureMetricTables;
import cl.uss.ca.repository.SyncRunRepository;
import cl.uss.ca.sync.SyncJobSupport;
import cl.uss.ca.sync.SyncRunState;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;
import java.util.logging.Logger;

@ApplicationScoped
public class PureMetricsSyncSupport {
    private static final Logger LOG = Logger.getLogger(PureMetricsSyncSupport.class.getName());

    @Inject
    PureMetricRepository metricRepository;

    @Inject
    SyncJobSupport jobSupport;

    @Inject
    SyncRunRepository syncRunRepository;

    @Inject
    @ConfigProperty(name = "metrics.sync.delayMs", defaultValue = "50")
    long delayMs;

    @Inject
    @ConfigProperty(name = "metrics.maxConcurrency", defaultValue = "2")
    int maxConcurrency;

    @Inject
    @ConfigProperty(name = "metrics.refreshAfterHours", defaultValue = "168")
    int refreshAfterHours;

    @Inject
    @ConfigProperty(name = "metrics.person.refreshHours", defaultValue = "168")
    int personRefreshHours;

    @Inject
    @ConfigProperty(name = "metrics.researchOutput.refreshHours", defaultValue = "168")
    int researchOutputRefreshHours;

    @Inject
    @ConfigProperty(name = "metrics.journal.refreshHours", defaultValue = "168")
    int journalRefreshHours;

    public int refreshAfterHours() {
        return Math.max(1, refreshAfterHours);
    }

    public int refreshAfterHours(PureMetricTables tables) {
        if (tables == PureMetricTables.PERSON) {
            return Math.max(1, personRefreshHours);
        }
        if (tables == PureMetricTables.RESEARCH_OUTPUT) {
            return Math.max(1, researchOutputRefreshHours);
        }
        if (tables == PureMetricTables.JOURNAL) {
            return Math.max(1, journalRefreshHours);
        }
        return refreshAfterHours();
    }

    public int maxConcurrency() {
        return Math.max(1, maxConcurrency);
    }

    public void processOwners(
            SyncRunState run,
            String entity,
            String stage,
            PureMetricTables tables,
            List<PureMetricOwner> owners,
            List<String> collections,
            BiFunction<String, String, JsonNode> fetcher
    ) throws Exception {
        List<PureMetricOwner> list = owners == null ? List.of() : owners;
        run.totalExpected = list.size();
        logStart(run, entity, list.size(), refreshAfterHours(tables));
        heartbeat(run);
        if (list.isEmpty()) {
            heartbeat(run);
            return;
        }
        int concurrency = Math.min(maxConcurrency(), list.size());
        if (concurrency <= 1) {
            int index = 0;
            for (PureMetricOwner owner : list) {
                index++;
                classify(run, processOwner(run, entity, stage, tables, owner, collections, fetcher));
                run.processed.incrementAndGet();
                if (index % 10 == 0 || index == list.size()) {
                    heartbeat(run);
                }
                throttle();
            }
            heartbeat(run);
            return;
        }
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        ExecutorCompletionService<Boolean> completion = new ExecutorCompletionService<>(pool);
        try {
            int next = 0;
            int inFlight = 0;
            int done = 0;
            while (done < list.size()) {
                heartbeat(run);
                while (inFlight < concurrency && next < list.size()) {
                    PureMetricOwner owner = list.get(next++);
                    completion.submit(() -> {
                        classify(run, processOwner(run, entity, stage, tables, owner, collections, fetcher));
                        run.processed.incrementAndGet();
                        throttle();
                        return Boolean.TRUE;
                    });
                    inFlight++;
                }
                try {
                    completion.take().get();
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    if (cause instanceof Error error) {
                        throw error;
                    }
                    if (cause instanceof RuntimeException runtime) {
                        throw runtime;
                    }
                    if (cause instanceof Exception checked) {
                        throw checked;
                    }
                    throw ex;
                }
                inFlight--;
                done++;
            }
        } finally {
            pool.shutdownNow();
        }
        heartbeat(run);
    }

    public OwnerOutcome processOwner(
            SyncRunState run,
            String entity,
            String stage,
            PureMetricTables tables,
            PureMetricOwner owner,
            List<String> collections,
            BiFunction<String, String, JsonNode> fetcher
    ) {
        OwnerOutcome outcome = new OwnerOutcome();
        boolean anyFailed = false;
        boolean anyInvalid = false;
        boolean anySuccess = false;
        boolean allEmpty = true;
        int observations = 0;
        for (String collection : collections) {
            CollectionOutcome collectionOutcome = processCollection(
                    run, entity, stage, tables, owner, collection, fetcher
            );
            observations += collectionOutcome.observations;
            switch (collectionOutcome.result.outcome) {
                case SUCCESS -> {
                    anySuccess = true;
                    allEmpty = false;
                }
                case EMPTY -> {
                }
                case FAILED -> anyFailed = true;
                case INVALID -> {
                    anyInvalid = true;
                    allEmpty = false;
                }
            }
        }
        outcome.observations = observations;
        if (anyFailed || anyInvalid) {
            outcome.failed = true;
        } else if (anySuccess) {
            outcome.success = true;
        } else if (allEmpty) {
            outcome.empty = true;
        }
        return outcome;
    }

    public CollectionOutcome processCollection(
            SyncRunState run,
            String entity,
            String stage,
            PureMetricTables tables,
            PureMetricOwner owner,
            String collection,
            BiFunction<String, String, JsonNode> fetcher
    ) {
        PureMetricFetchResult result;
        heartbeat(run);
        try {
            JsonNode body = fetcher.apply(owner.uuid(), collection);
            result = PureMetricsParser.parse(body, owner.uuid(), collection);
        } catch (PureClientException ex) {
            result = PureMetricFetchResult.failed(
                    ex.getStatusCode(),
                    "PURE_HTTP_" + ex.getStatusCode(),
                    clip(ex.getMessage(), 500)
            );
        } catch (Exception ex) {
            result = PureMetricFetchResult.failed(0, "PURE_METRICS_FETCH", clip(ex.getMessage(), 500));
        }
        if (result.replacesSnapshot()) {
            metricRepository.replaceSnapshot(tables, owner.id(), collection, result.observations, run.syncId);
        }
        metricRepository.saveFetch(tables, owner.id(), collection, result, run.syncId);
        if (result.outcome == PureMetricFetchOutcome.FAILED || result.outcome == PureMetricFetchOutcome.INVALID) {
            jobSupport.record(
                    run,
                    entity,
                    stage,
                    owner.id(),
                    owner.pureId(),
                    owner.uuid(),
                    owner.uuid() + ":" + collection,
                    new ValidationException(
                            result.errorCode == null ? result.outcome.name() : result.errorCode,
                            collection + " " + (result.errorMessage == null ? result.outcome.name() : result.errorMessage)
                    )
            );
        }
        return new CollectionOutcome(result, result.observations.size());
    }

    public void heartbeat(SyncRunState run) {
        try {
            syncRunRepository.requireOwnership(run);
        } catch (cl.uss.ca.sync.SyncOwnershipLostException ex) {
            throw ex;
        } catch (Exception ex) {
            LOG.warning("syncId=" + run.syncId + " metrics heartbeat failed: " + ex.getMessage());
        }
    }

    public void throttle() {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Metrics sync interrupted", ex);
        }
    }

    public void logStart(SyncRunState run, String label, int owners) {
        logStart(run, label, owners, refreshAfterHours());
    }

    public void logStart(SyncRunState run, String label, int owners, int refreshHours) {
        LOG.info("Starting " + label + " sync id=" + run.syncId
                + " mode=" + run.syncMode
                + " owners=" + owners
                + " concurrency=" + maxConcurrency()
                + " delayMs=" + delayMs
                + " refreshAfterHours=" + refreshHours);
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static void classify(SyncRunState run, OwnerOutcome outcome) {
        if (outcome.failed) {
            return;
        }
        if (outcome.success) {
            run.created.incrementAndGet();
        } else if (outcome.empty) {
            run.unchanged.incrementAndGet();
        }
    }

    public static final class OwnerOutcome {
        public boolean success;
        public boolean empty;
        public boolean failed;
        public int observations;
    }

    public record CollectionOutcome(PureMetricFetchResult result, int observations) {
    }
}
