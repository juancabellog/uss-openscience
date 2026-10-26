package cl.uss.ca.service;

import cl.uss.ca.external.pure.PureClient;
import cl.uss.ca.external.pure.metrics.PureMetricCatalog;
import cl.uss.ca.external.pure.metrics.PureMetricOwner;
import cl.uss.ca.repository.PureMetricRepository;
import cl.uss.ca.repository.PureMetricTables;
import cl.uss.ca.sync.SyncJobSupport;
import cl.uss.ca.sync.SyncRunState;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.logging.Logger;

@ApplicationScoped
public class PersonMetricsSyncJob {
    private static final Logger LOG = Logger.getLogger(PersonMetricsSyncJob.class.getName());
    static final String ENTITY = "PERSON_METRICS";
    private static final List<String> COLLECTIONS = List.of(PureMetricCatalog.COLLECTION_HINDEX);

    @Inject
    PureClient pureClient;

    @Inject
    PureMetricRepository metricRepository;

    @Inject
    PureMetricsSyncSupport support;

    public void run(SyncRunState run) throws Exception {
        run.phase = "METRICS";
        List<PureMetricOwner> owners = run.isIncremental()
                ? metricRepository.listIncrementalOwners(
                        PureMetricTables.PERSON, COLLECTIONS, support.refreshAfterHours(PureMetricTables.PERSON))
                : metricRepository.listActiveOwners(PureMetricTables.PERSON);
        support.processOwners(
                run,
                ENTITY,
                SyncJobSupport.STAGE_PERSON_METRICS,
                PureMetricTables.PERSON,
                owners,
                COLLECTIONS,
                pureClient::getPersonMetrics
        );
        LOG.info("syncId=" + run.syncId + " " + ENTITY
                + " processed=" + run.processed.get()
                + " success=" + run.created.get()
                + " empty=" + run.unchanged.get()
                + " failed=" + run.errors.get());
    }
}
