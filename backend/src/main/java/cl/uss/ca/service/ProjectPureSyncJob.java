package cl.uss.ca.service;

import cl.uss.ca.external.pure.ProjectMapper;
import cl.uss.ca.external.pure.PureClient;
import cl.uss.ca.external.pure.PurePage;
import cl.uss.ca.external.pure.json.ProjectItem;
import cl.uss.ca.external.pure.model.PureProject;
import cl.uss.ca.repository.ProjectSyncRepository;
import cl.uss.ca.repository.SyncRunRepository;
import cl.uss.ca.sync.PresenceRecord;
import cl.uss.ca.sync.SyncChangeRepository;
import cl.uss.ca.sync.SyncJobSupport;
import cl.uss.ca.sync.SyncPresenceRepository;
import cl.uss.ca.sync.SyncRunState;
import cl.uss.ca.sync.diff.AggregateOutcome;
import cl.uss.ca.sync.diff.ProjectAggregateComparer;
import cl.uss.ca.sync.diff.ProjectSnapshot;
import cl.uss.ca.sync.diff.SyncChange;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@ApplicationScoped
public class ProjectPureSyncJob {
    private static final Logger LOG = Logger.getLogger(ProjectPureSyncJob.class.getName());
    private static final int PAGE_SIZE = 1000;
    private static final int MAX_OFFSET = 500_000;
    private static final String ENTITY = "PROJECTS";

    @Inject
    PureClient pureClient;

    @Inject
    ProjectMapper projectMapper;

    @Inject
    ProjectSyncRepository projectSyncRepository;

    @Inject
    ProjectAggregateComparer comparer;

    @Inject
    SyncPresenceRepository presenceRepository;

    @Inject
    SyncChangeRepository syncChangeRepository;

    @Inject
    SyncRunRepository syncRunRepository;

    @Inject
    SyncJobSupport jobSupport;

    public void run(SyncRunState run) throws Exception {
        LOG.info("Starting PROJECTS sync id=" + run.syncId + " mode=" + run.syncMode);
        Integer reported = null;
        int offset = 0;
        try (Connection connection = projectSyncRepository.begin()) {
            while (true) {
                persistProgress(run);
                PurePage<ProjectItem> page = pureClient.fetchProjectsPage(offset, PAGE_SIZE);
                List<ProjectItem> items = page.items;
                if (reported == null) {
                    reported = page.count;
                    run.totalExpected = reported;
                }
                persistPage(run, connection, items);
                persistProgress(run);
                if (items.isEmpty()) {
                    break;
                }
                if (items.size() < PAGE_SIZE) {
                    break;
                }
                offset += PAGE_SIZE;
                if (reported != null && offset >= reported) {
                    break;
                }
                if (offset > MAX_OFFSET) {
                    throw new IllegalStateException("PURE /projects pagination exceeded max offset");
                }
            }
            persistProgress(run);
            projectSyncRepository.resolvePersonReferences(connection);
            connection.commit();
        }
        persistProgress(run);
        if (!jobSupport.canInactivate(run, reported)) {
            jobSupport.failWithoutInactivation(run, reported);
            return;
        }
        if (presenceRepository.canTrackProjects()) {
            List<PresenceRecord> missing = presenceRepository.markMissingProjects(run.syncId);
            run.markedInactive.addAndGet(missing.size());
            List<SyncChange> changes = new ArrayList<>();
            for (PresenceRecord record : missing) {
                changes.add(SyncChange.inactivated().on(ENTITY, record.id, record.pureId, record.uuid));
            }
            syncChangeRepository.save(run.syncId, changes);
        }
    }

    private void persistPage(SyncRunState run, Connection connection, List<ProjectItem> items) throws Exception {
        List<PureProject> incoming = new ArrayList<>();
        for (ProjectItem item : items) {
            PureProject project = projectMapper.mapear(item);
            if (project == null || project.getUuid() == null) {
                jobSupport.record(
                        run,
                        ENTITY,
                        SyncJobSupport.STAGE_PROJECT_MAP,
                        null,
                        item == null ? null : item.getPureId(),
                        item == null ? null : item.getUuid(),
                        new IllegalStateException("Project item could not be mapped")
                );
                continue;
            }
            incoming.add(project);
        }
        Map<String, ProjectSnapshot> snapshots = incoming.isEmpty()
                ? Map.of()
                : projectSyncRepository.loadSnapshots(connection, incoming.stream().map(PureProject::getUuid).toList());
        for (PureProject project : incoming) {
            persistOne(run, connection, project, snapshots.get(project.getUuid()));
        }
    }

    private void persistOne(
            SyncRunState run,
            Connection connection,
            PureProject project,
            ProjectSnapshot existing
    ) {
        try {
            AggregateOutcome outcome = comparer.compare(project, existing);
            Long id = projectSyncRepository.apply(connection, project, outcome);
            outcome.localId = id;
            presenceRepository.markProjectSeen(connection, project.getUuid(), run.syncId, outcome.touchChangeDetected());
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
                    SyncJobSupport.STAGE_PROJECT_UPSERT,
                    existing == null ? null : existing.id,
                    project.getPureId(),
                    project.getUuid(),
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
            LOG.warning("syncId=" + run.syncId + " projects heartbeat failed: " + ex.getMessage());
        }
    }
}
