package cl.uss.ca.service;

import cl.uss.ca.domain.OrcidFormat;
import cl.uss.ca.exception.ValidationException;
import cl.uss.ca.repository.OrcidWorkRepository;
import cl.uss.ca.repository.OrcidWorkRepository.PersonCandidate;
import cl.uss.ca.repository.OrcidWorkRepository.ReconcileResult;
import cl.uss.ca.repository.SyncRunRepository;
import cl.uss.ca.sync.SyncJobSupport;
import cl.uss.ca.sync.SyncRunState;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.logging.Logger;

@ApplicationScoped
public class OrcidWorksSyncJob {
    private static final Logger LOG = Logger.getLogger(OrcidWorksSyncJob.class.getName());
    static final String ENTITY = "ORCID";

    @Inject
    OrcidSyncService orcidSyncService;

    @Inject
    OrcidWorkRepository orcidWorkRepository;

    @Inject
    SyncRunRepository syncRunRepository;

    @Inject
    SyncJobSupport jobSupport;

    @Inject
    @ConfigProperty(name = "orcid.sync.delayMs", defaultValue = "250")
    long delayMs;

    @Inject
    @ConfigProperty(name = "orcid.refreshDays", defaultValue = "30")
    int refreshDays;

    public void run(SyncRunState run) throws Exception {
        orcidSyncService.assertTables();
        List<PersonCandidate> people = orcidWorkRepository.listPeopleCandidates(run.syncMode, refreshDays);
        run.totalExpected = people.size();
        persistProgress(run);
        LOG.info("Starting ORCID sync id=" + run.syncId
                + " mode=" + run.syncMode
                + " candidates=" + people.size());
        int index = 0;
        for (PersonCandidate person : people) {
            index++;
            try {
                if (!OrcidFormat.isValid(person.orcid)) {
                    throw new ValidationException("ORCID_INVALID", "ORCID format is invalid");
                }
                ReconcileResult result = orcidSyncService.syncPersonInternal(person, run.syncId);
                run.processed.incrementAndGet();
                run.created.addAndGet(result.groupsCreated);
                run.updated.addAndGet(result.groupsUpdated);
                run.unchanged.addAndGet(result.groupsUnchanged);
                run.markedInactive.addAndGet(result.groupsInactive);
                if (result.detailErrors > 0) {
                    jobSupport.record(
                            run,
                            ENTITY,
                            SyncJobSupport.STAGE_ORCID_DETAIL,
                            person.id,
                            null,
                            person.orcid,
                            new ValidationException(
                                    "DETAIL_FETCH_FAILED",
                                    result.detailErrors + " ORCID work detail fetches failed"
                            )
                    );
                }
            } catch (Exception ex) {
                try {
                    orcidWorkRepository.markPersonFailed(person.id);
                } catch (Exception ignored) {
                    // keep the original person error
                }
                jobSupport.record(
                        run,
                        ENTITY,
                        SyncJobSupport.STAGE_ORCID_FETCH,
                        person.id,
                        null,
                        person.orcid,
                        ex
                );
            }
            if (index % 10 == 0 || index == people.size()) {
                persistProgress(run);
            }
            throttle();
        }
        LOG.info("syncId=" + run.syncId + " ORCID mode=" + run.syncMode
                + " people expected=" + people.size()
                + " processed=" + run.processed.get() + " errors=" + run.errors.get());
    }

    private void persistProgress(SyncRunState run) {
        try {
            syncRunRepository.requireOwnership(run);
        } catch (cl.uss.ca.sync.SyncOwnershipLostException ex) {
            throw ex;
        } catch (Exception ex) {
            LOG.warning("syncId=" + run.syncId + " ORCID heartbeat failed: " + ex.getMessage());
        }
    }

    private void throttle() throws InterruptedException {
        if (delayMs > 0) {
            Thread.sleep(delayMs);
        }
    }
}
