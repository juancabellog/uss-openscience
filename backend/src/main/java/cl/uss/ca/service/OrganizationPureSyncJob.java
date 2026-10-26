package cl.uss.ca.service;

import cl.uss.ca.external.pure.OrganizationMapper;
import cl.uss.ca.external.pure.PureClient;
import cl.uss.ca.external.pure.PurePage;
import cl.uss.ca.external.pure.json.OrganizationItem;
import cl.uss.ca.external.pure.model.PureOrganization;
import cl.uss.ca.repository.OrganizationSyncRepository;
import cl.uss.ca.repository.SyncRunRepository;
import cl.uss.ca.sync.PresenceRecord;
import cl.uss.ca.sync.SyncChangeRepository;
import cl.uss.ca.sync.SyncJobSupport;
import cl.uss.ca.sync.SyncPresenceRepository;
import cl.uss.ca.sync.SyncRunState;
import cl.uss.ca.sync.diff.AggregateOutcome;
import cl.uss.ca.sync.diff.OrganizationAggregateComparer;
import cl.uss.ca.sync.diff.OrganizationSnapshot;
import cl.uss.ca.sync.diff.SyncChange;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@ApplicationScoped
public class OrganizationPureSyncJob {
    private static final Logger LOG = Logger.getLogger(OrganizationPureSyncJob.class.getName());
    private static final int PAGE_SIZE = 1000;
    private static final String ENTITY = "ORGANIZATIONS";

    @Inject
    PureClient pureClient;

    @Inject
    OrganizationMapper organizationMapper;

    @Inject
    OrganizationSyncRepository organizationSyncRepository;

    @Inject
    OrganizationAggregateComparer comparer;

    @Inject
    SyncPresenceRepository presenceRepository;

    @Inject
    SyncChangeRepository syncChangeRepository;

    @Inject
    SyncRunRepository syncRunRepository;

    @Inject
    SyncJobSupport jobSupport;

    public void run(SyncRunState run) throws Exception {
        LOG.info("Starting ORGANIZATIONS sync id=" + run.syncId + " mode=" + run.syncMode);
        Integer reported = null;
        int offset = 0;
        try (Connection connection = organizationSyncRepository.begin()) {
            while (true) {
                PurePage<OrganizationItem> page = pureClient.fetchOrganizationsPage(offset, PAGE_SIZE);
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
            connection.commit();
        }
        if (!jobSupport.canInactivate(run, reported)) {
            jobSupport.failWithoutInactivation(run, reported);
            return;
        }
        if (presenceRepository.canTrackOrganizations()) {
            List<PresenceRecord> missing = presenceRepository.markMissingOrganizations(run.syncId);
            run.markedInactive.addAndGet(missing.size());
            List<SyncChange> changes = new ArrayList<>();
            for (PresenceRecord record : missing) {
                changes.add(SyncChange.inactivated().on(ENTITY, record.id, record.pureId, record.uuid));
            }
            syncChangeRepository.save(run.syncId, changes);
        }
    }

    private void persistPage(SyncRunState run, Connection connection, List<OrganizationItem> items) throws Exception {
        List<PureOrganization> incoming = new ArrayList<>();
        for (OrganizationItem item : items) {
            PureOrganization organization = organizationMapper.mapear(item);
            if (organization == null || organization.getUuid() == null) {
                jobSupport.record(
                        run,
                        ENTITY,
                        SyncJobSupport.STAGE_ORGANIZATION_MAP,
                        null,
                        item == null ? null : item.getPureId(),
                        item == null ? null : item.getUuid(),
                        new IllegalStateException("Organization item could not be mapped")
                );
                continue;
            }
            incoming.add(organization);
        }
        Map<String, OrganizationSnapshot> snapshots = incoming.isEmpty()
                ? Map.of()
                : organizationSyncRepository.loadSnapshots(
                        connection, incoming.stream().map(PureOrganization::getUuid).toList());
        for (PureOrganization organization : incoming) {
            persistOne(run, connection, organization, snapshots.get(organization.getUuid()));
        }
    }

    private void persistOne(
            SyncRunState run,
            Connection connection,
            PureOrganization organization,
            OrganizationSnapshot existing
    ) {
        try {
            AggregateOutcome outcome = comparer.compare(organization, existing);
            Long id = organizationSyncRepository.apply(connection, organization, outcome);
            outcome.localId = id;
            presenceRepository.markOrganizationSeen(
                    connection, organization.getUuid(), run.syncId, outcome.touchChangeDetected());
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
                    SyncJobSupport.STAGE_ORGANIZATION_UPSERT,
                    existing == null ? null : existing.id,
                    organization.getPureId(),
                    organization.getUuid(),
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
            LOG.warning("syncId=" + run.syncId + " organizations heartbeat failed: " + ex.getMessage());
        }
    }
}
