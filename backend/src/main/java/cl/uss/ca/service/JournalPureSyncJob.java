package cl.uss.ca.service;

import cl.uss.ca.external.pure.JournalMapper;
import cl.uss.ca.external.pure.PureClient;
import cl.uss.ca.external.pure.PurePage;
import cl.uss.ca.external.pure.json.JournalItem;
import cl.uss.ca.external.pure.model.PureJournal;
import cl.uss.ca.repository.JournalSyncRepository;
import cl.uss.ca.repository.SyncRunRepository;
import cl.uss.ca.sync.PresenceRecord;
import cl.uss.ca.sync.SyncChangeRepository;
import cl.uss.ca.sync.SyncJobSupport;
import cl.uss.ca.sync.SyncPresenceRepository;
import cl.uss.ca.sync.SyncRunState;
import cl.uss.ca.sync.diff.AggregateOutcome;
import cl.uss.ca.sync.diff.JournalAggregateComparer;
import cl.uss.ca.sync.diff.JournalSnapshot;
import cl.uss.ca.sync.diff.SyncChange;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@ApplicationScoped
public class JournalPureSyncJob {
    private static final Logger LOG = Logger.getLogger(JournalPureSyncJob.class.getName());
    private static final int PAGE_SIZE = 1000;
    private static final String ENTITY = "JOURNALS";

    @Inject
    PureClient pureClient;

    @Inject
    JournalMapper journalMapper;

    @Inject
    JournalSyncRepository journalSyncRepository;

    @Inject
    JournalAggregateComparer comparer;

    @Inject
    SyncPresenceRepository presenceRepository;

    @Inject
    SyncChangeRepository syncChangeRepository;

    @Inject
    SyncRunRepository syncRunRepository;

    @Inject
    SyncJobSupport jobSupport;

    public void run(SyncRunState run) throws Exception {
        LOG.info("Starting JOURNALS sync id=" + run.syncId + " mode=" + run.syncMode);
        Integer reported = null;
        int offset = 0;
        try (Connection connection = journalSyncRepository.begin()) {
            while (true) {
                PurePage<JournalItem> page = pureClient.fetchJournalsPage(offset, PAGE_SIZE);
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
            journalSyncRepository.resolveResearchOutputReferences(connection);
            connection.commit();
        }
        if (!jobSupport.canInactivate(run, reported)) {
            jobSupport.failWithoutInactivation(run, reported);
            return;
        }
        if (presenceRepository.canTrackJournals()) {
            List<PresenceRecord> missing = presenceRepository.markMissingJournals(run.syncId);
            run.markedInactive.addAndGet(missing.size());
            List<SyncChange> changes = new ArrayList<>();
            for (PresenceRecord record : missing) {
                changes.add(SyncChange.inactivated().on(ENTITY, record.id, record.pureId, record.uuid));
            }
            syncChangeRepository.save(run.syncId, changes);
        }
    }

    private void persistPage(SyncRunState run, Connection connection, List<JournalItem> items) throws Exception {
        List<PureJournal> incoming = new ArrayList<>();
        for (JournalItem item : items) {
            PureJournal journal = journalMapper.mapear(item);
            if (journal == null || journal.getUuid() == null) {
                jobSupport.record(
                        run,
                        ENTITY,
                        SyncJobSupport.STAGE_JOURNAL_MAP,
                        null,
                        item == null ? null : item.getPureId(),
                        item == null ? null : item.getUuid(),
                        new IllegalStateException("Journal item could not be mapped")
                );
                continue;
            }
            incoming.add(journal);
        }
        Map<String, JournalSnapshot> snapshots = incoming.isEmpty()
                ? Map.of()
                : journalSyncRepository.loadSnapshots(connection, incoming.stream().map(PureJournal::getUuid).toList());
        for (PureJournal journal : incoming) {
            persistOne(run, connection, journal, snapshots.get(journal.getUuid()));
        }
    }

    private void persistOne(
            SyncRunState run,
            Connection connection,
            PureJournal journal,
            JournalSnapshot existing
    ) {
        try {
            AggregateOutcome outcome = comparer.compare(journal, existing);
            Long id = journalSyncRepository.apply(connection, journal, outcome);
            outcome.localId = id;
            presenceRepository.markJournalSeen(connection, journal.getUuid(), run.syncId, outcome.touchChangeDetected());
            for (SyncChange change : outcome.changes) {
                change.on(ENTITY, outcome.localId, outcome.pureId, outcome.uuid);
            }
            syncChangeRepository.save(connection, run.syncId, outcome.changes);
            connection.commit();
            run.processed.incrementAndGet();
            classify(run, outcome.kind);
        } catch (Exception ex) {
            try {
                connection.rollback();
            } catch (Exception ignored) {
                // keep the original persist error
            }
            jobSupport.record(
                    run,
                    ENTITY,
                    SyncJobSupport.STAGE_JOURNAL_UPSERT,
                    existing == null ? null : existing.id,
                    journal.getPureId(),
                    journal.getUuid(),
                    ex
            );
        }
    }

    private static void classify(SyncRunState run, AggregateOutcome.Kind kind) {
        switch (kind) {
            case CREATED -> run.created.incrementAndGet();
            case UPDATED -> run.updated.incrementAndGet();
            case REACTIVATED -> run.reactivated.incrementAndGet();
            case UNCHANGED -> run.unchanged.incrementAndGet();
        }
    }

    private void persistProgress(SyncRunState run) {
        try {
            syncRunRepository.requireOwnership(run);
        } catch (cl.uss.ca.sync.SyncOwnershipLostException ex) {
            throw ex;
        } catch (Exception ex) {
            LOG.warning("syncId=" + run.syncId + " journals heartbeat failed: " + ex.getMessage());
        }
    }
}
