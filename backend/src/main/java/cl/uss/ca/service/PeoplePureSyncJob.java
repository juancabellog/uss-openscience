package cl.uss.ca.service;

import cl.uss.ca.external.pure.PersonMapper;
import cl.uss.ca.external.pure.PureClient;
import cl.uss.ca.external.pure.PurePage;
import cl.uss.ca.external.pure.PurePerson;
import cl.uss.ca.external.pure.json.PersonItem;
import cl.uss.ca.domain.OrcidFormat;
import cl.uss.ca.repository.OrcidWorkRepository;
import cl.uss.ca.repository.PersonSyncRepository;
import cl.uss.ca.repository.SyncRunRepository;
import cl.uss.ca.sync.PresenceRecord;
import cl.uss.ca.sync.SyncChangeRepository;
import cl.uss.ca.sync.SyncJobSupport;
import cl.uss.ca.sync.SyncPresenceRepository;
import cl.uss.ca.sync.SyncRunState;
import cl.uss.ca.sync.diff.AggregateOutcome;
import cl.uss.ca.sync.diff.PersonAggregateComparer;
import cl.uss.ca.sync.diff.PersonSnapshot;
import cl.uss.ca.sync.diff.SyncChange;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@ApplicationScoped
public class PeoplePureSyncJob {
    private static final Logger LOG = Logger.getLogger(PeoplePureSyncJob.class.getName());
    private static final int PAGE_SIZE = 1000;
    private static final String ENTITY = "PEOPLE";

    @Inject
    PureClient pureClient;

    @Inject
    PersonMapper personMapper;

    @Inject
    PersonSyncRepository personSyncRepository;

    @Inject
    OrcidWorkRepository orcidWorkRepository;

    @Inject
    PersonAggregateComparer comparer;

    @Inject
    SyncPresenceRepository presenceRepository;

    @Inject
    SyncChangeRepository syncChangeRepository;

    @Inject
    SyncRunRepository syncRunRepository;

    @Inject
    SyncJobSupport jobSupport;

    public void run(SyncRunState run) throws Exception {
        LOG.info("Starting PEOPLE sync id=" + run.syncId + " mode=" + run.syncMode);
        Integer reported = null;
        int offset = 0;
        boolean identifiers = personSyncRepository.identifiersAvailable();
        try (Connection connection = personSyncRepository.begin()) {
            while (true) {
                PurePage<PersonItem> page = pureClient.fetchPeoplePage(offset, PAGE_SIZE);
                if (reported == null) {
                    reported = page.count;
                    run.totalExpected = reported;
                }
                persistPage(run, connection, page.items, identifiers);
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
        if (presenceRepository.canTrackPeople()) {
            List<PresenceRecord> missing = presenceRepository.markMissingPeople(run.syncId);
            run.markedInactive.addAndGet(missing.size());
            List<SyncChange> changes = new ArrayList<>();
            for (PresenceRecord record : missing) {
                changes.add(SyncChange.inactivated().on(ENTITY, record.id, record.pureId, record.uuid));
            }
            syncChangeRepository.save(run.syncId, changes);
        }
    }

    private void persistPage(
            SyncRunState run,
            Connection connection,
            List<PersonItem> items,
            boolean identifiers
    ) throws Exception {
        List<PurePerson> incoming = new ArrayList<>();
        for (PersonItem item : items) {
            PurePerson person = personMapper.mapearPersona(item);
            if (person == null || person.getUuid() == null) {
                jobSupport.record(
                        run,
                        ENTITY,
                        SyncJobSupport.STAGE_PERSON_MAP,
                        null,
                        item == null ? null : item.getPureId(),
                        item == null ? null : item.getUuid(),
                        new IllegalStateException("Person item could not be mapped")
                );
                continue;
            }
            incoming.add(person);
        }
        Map<String, PersonSnapshot> snapshots = incoming.isEmpty()
                ? Map.of()
                : personSyncRepository.loadSnapshots(connection, incoming.stream().map(PurePerson::getUuid).toList());
        for (PurePerson person : incoming) {
            persistOne(run, connection, person, snapshots.get(person.getUuid()), identifiers);
        }
    }

    private void persistOne(
            SyncRunState run,
            Connection connection,
            PurePerson person,
            PersonSnapshot existing,
            boolean identifiers
    ) {
        try {
            AggregateOutcome outcome = comparer.compare(person, existing, identifiers);
            Long id = personSyncRepository.apply(connection, person, outcome, identifiers);
            outcome.localId = id;
            personSyncRepository.stampOrcidFirstSeen(
                    connection,
                    id,
                    existing == null ? null : existing.orcid,
                    existing == null ? null : existing.orcidFirstSeenAt,
                    person.getOrcid()
            );
            markOrcidPendingIfChanged(connection, existing, person, id);
            presenceRepository.markPersonSeen(connection, person.getUuid(), run.syncId, outcome.touchChangeDetected());
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
                    SyncJobSupport.STAGE_PERSON_UPSERT,
                    existing == null ? null : existing.id,
                    person.getPureId(),
                    person.getUuid(),
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

    private void markOrcidPendingIfChanged(
            Connection connection,
            PersonSnapshot existing,
            PurePerson person,
            Long id
    ) throws Exception {
        if (id == null) {
            return;
        }
        String previous = existing == null ? null : OrcidFormat.normalize(existing.orcid);
        String incoming = OrcidFormat.normalize(person.getOrcid());
        if (java.util.Objects.equals(previous, incoming)) {
            return;
        }
        if (incoming != null) {
            orcidWorkRepository.markOrcidPending(connection, id);
        }
    }

    private void persistProgress(SyncRunState run) {
        try {
            syncRunRepository.requireOwnership(run);
        } catch (cl.uss.ca.sync.SyncOwnershipLostException ex) {
            throw ex;
        } catch (Exception ex) {
            LOG.warning("syncId=" + run.syncId + " people heartbeat failed: " + ex.getMessage());
        }
    }
}
