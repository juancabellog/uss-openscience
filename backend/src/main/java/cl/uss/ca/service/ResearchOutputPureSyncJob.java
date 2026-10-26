package cl.uss.ca.service;

import cl.uss.ca.external.pure.PureClient;
import cl.uss.ca.external.pure.PurePage;
import cl.uss.ca.external.pure.ResearchOutputMapper;
import cl.uss.ca.external.pure.json.ResearchOutputItem;
import cl.uss.ca.external.pure.model.ResearchOutput;
import cl.uss.ca.repository.ResearchOutputSyncRepository;
import cl.uss.ca.repository.SyncRunRepository;
import cl.uss.ca.sync.PresenceRecord;
import cl.uss.ca.sync.SyncChangeRepository;
import cl.uss.ca.sync.SyncJobSupport;
import cl.uss.ca.sync.SyncPresenceRepository;
import cl.uss.ca.sync.SyncRunState;
import cl.uss.ca.sync.diff.AggregateOutcome;
import cl.uss.ca.sync.diff.ResearchOutputAggregateComparer;
import cl.uss.ca.sync.diff.ResearchOutputSnapshot;
import cl.uss.ca.sync.diff.SyncChange;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@ApplicationScoped
public class ResearchOutputPureSyncJob {
    private static final Logger LOG = Logger.getLogger(ResearchOutputPureSyncJob.class.getName());
    private static final int PAGE_SIZE = 1000;
    private static final String ENTITY = "RESEARCH_OUTPUT";

    @Inject
    PureClient pureClient;

    @Inject
    ResearchOutputMapper researchOutputMapper;

    @Inject
    ResearchOutputSyncRepository researchOutputSyncRepository;

    @Inject
    ResearchOutputAggregateComparer comparer;

    @Inject
    SyncPresenceRepository presenceRepository;

    @Inject
    SyncChangeRepository syncChangeRepository;

    @Inject
    SyncRunRepository syncRunRepository;

    @Inject
    SyncJobSupport jobSupport;

    public void run(SyncRunState run) throws Exception {
        LOG.info("Starting RESEARCH_OUTPUT sync id=" + run.syncId + " mode=" + run.syncMode);
        Integer reported = null;
        int offset = 0;
        try (Connection connection = researchOutputSyncRepository.begin()) {
            while (true) {
                PurePage<ResearchOutputItem> page = pureClient.fetchResearchOutputsPage(offset, PAGE_SIZE);
                if (reported == null) {
                    reported = page.count;
                    run.totalExpected = reported;
                }
                persistPage(run, connection, page.items);
                persistProgress(run);
                if (page.items.isEmpty()) {
                    break;
                }
                offset += PAGE_SIZE;
                if (reported != null && offset >= reported) {
                    break;
                }
            }
        }
        if (!jobSupport.canInactivate(run, reported)) {
            jobSupport.failWithoutInactivation(run, reported);
            return;
        }
        if (presenceRepository.canTrackResearchOutputs()) {
            List<PresenceRecord> missing = presenceRepository.markMissingResearchOutputs(run.syncId);
            run.markedInactive.addAndGet(missing.size());
            List<SyncChange> changes = new ArrayList<>();
            for (PresenceRecord record : missing) {
                changes.add(SyncChange.inactivated().on(ENTITY, record.id, record.pureId, record.uuid));
            }
            syncChangeRepository.save(run.syncId, changes);
        }
    }

    private void persistPage(SyncRunState run, Connection connection, List<ResearchOutputItem> items)
            throws Exception {
        List<ResearchOutput> incoming = new ArrayList<>();
        for (ResearchOutputItem item : items) {
            ResearchOutput output = researchOutputMapper.mapear(item);
            if (output == null || output.getUuid() == null) {
                jobSupport.record(
                        run,
                        ENTITY,
                        SyncJobSupport.STAGE_OUTPUT_MAP,
                        null,
                        item == null ? null : item.getPureId(),
                        item == null ? null : item.getUuid(),
                        new IllegalStateException("Research output item could not be mapped")
                );
                continue;
            }
            incoming.add(output);
        }
        Map<String, ResearchOutputSnapshot> snapshots = incoming.isEmpty()
                ? Map.of()
                : researchOutputSyncRepository.loadSnapshots(
                        connection,
                        incoming.stream().map(ResearchOutput::getUuid).toList()
                );
        for (ResearchOutput output : incoming) {
            persistOne(run, connection, output, snapshots.get(output.getUuid()));
        }
    }

    private void persistOne(
            SyncRunState run,
            Connection connection,
            ResearchOutput output,
            ResearchOutputSnapshot existing
    ) {
        try {
            AggregateOutcome outcome = comparer.compare(output, existing);
            Long id = researchOutputSyncRepository.apply(connection, output, outcome);
            outcome.localId = id;
            presenceRepository.markResearchOutputSeen(
                    connection,
                    output.getUuid(),
                    run.syncId,
                    outcome.touchChangeDetected()
            );
            for (SyncChange change : outcome.changes) {
                change.on(ENTITY, outcome.localId, outcome.pureId, outcome.uuid);
            }
            syncChangeRepository.save(connection, run.syncId, outcome.changes);
            connection.commit();
            run.processed.incrementAndGet();
            switch (outcome.kind) {
                case CREATED -> run.created.incrementAndGet();
                case UPDATED -> run.updated.incrementAndGet();
                case REACTIVATED -> run.reactivated.incrementAndGet();
                case UNCHANGED -> run.unchanged.incrementAndGet();
            }
        } catch (Exception ex) {
            try {
                connection.rollback();
            } catch (Exception ignored) {
                // keep the original persist error
            }
            jobSupport.record(
                    run,
                    ENTITY,
                    SyncJobSupport.STAGE_OUTPUT_UPSERT,
                    existing == null ? null : existing.id,
                    output.getPureId(),
                    output.getUuid(),
                    ex
            );
        }
    }

    private void persistProgress(SyncRunState run) {
        try {
            syncRunRepository.requireOwnership(run);
        } catch (cl.uss.ca.sync.SyncOwnershipLostException ex) {
            throw ex;
        } catch (Exception ex) {
            LOG.warning("syncId=" + run.syncId + " research-output heartbeat failed: " + ex.getMessage());
        }
    }
}
