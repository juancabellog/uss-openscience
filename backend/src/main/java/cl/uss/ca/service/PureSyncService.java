package cl.uss.ca.service;

import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.api.dto.SyncChangeGroupDto;
import cl.uss.ca.api.dto.SyncErrorPageDto;
import cl.uss.ca.api.dto.SyncRunDto;
import cl.uss.ca.auth.AppModule;
import cl.uss.ca.auth.AppPermission;
import cl.uss.ca.auth.AuthorizationPolicy;
import cl.uss.ca.exception.ErrorLog;
import cl.uss.ca.exception.ForbiddenException;
import cl.uss.ca.exception.NotFoundException;
import cl.uss.ca.exception.ValidationException;
import cl.uss.ca.external.orcid.OrcidClient;
import cl.uss.ca.external.pure.PureClient;
import cl.uss.ca.repository.SyncRunRepository;
import cl.uss.ca.api.dto.SyncModePolicyDto;
import cl.uss.ca.sync.SyncChangeRepository;
import cl.uss.ca.sync.SyncErrorRepository;
import cl.uss.ca.sync.SyncModeAdvisor;
import cl.uss.ca.security.CurrentUserService;
import cl.uss.ca.api.dto.OpenAlexAuthorRecoveryDto;
import cl.uss.ca.sync.OpenAlexAuthorRecovery;
import cl.uss.ca.sync.OpenAlexSyncPhase;
import cl.uss.ca.sync.SyncCancellation;
import cl.uss.ca.sync.SyncMode;
import cl.uss.ca.sync.SyncOwnershipLostException;
import cl.uss.ca.sync.SyncRunState;
import cl.uss.ca.exception.ConflictException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manual PURE sync.
 *
 * Concurrency authority is MySQL {@code sync_control}, not this JVM.
 * {@link ConcurrentHashMap} here is only a live progress cache for the instance
 * that won the lock. It does not prevent another OpenLiberty instance from starting
 * the same entity; the atomic UPDATE ... AND active_sync_id IS NULL does.
 *
 * {@link ManagedExecutorService} keeps the job independent of the HTTP request and
 * the browser. It does NOT survive an OpenLiberty/JVM restart. heartbeat_at plus
 * stale recovery exist because a RUNNING row can be abandoned after a crash.
 */
@ApplicationScoped
public class PureSyncService {
    private static final Logger LOG = Logger.getLogger(PureSyncService.class.getName());
    public static final String PEOPLE = "PEOPLE";
    public static final String RESEARCH_OUTPUT = "RESEARCH_OUTPUT";
    public static final String JOURNALS = "JOURNALS";
    public static final String PROJECTS = "PROJECTS";
    public static final String ORGANIZATIONS = "ORGANIZATIONS";
    public static final String ORCID = "ORCID";
    public static final String UNPAYWALL = "UNPAYWALL";
    public static final String CROSSREF = "CROSSREF";
    public static final String OPENALEX = "OPENALEX";
    public static final String DSPACE = "DSPACE";
    public static final String JOURNAL_METRICS = "JOURNAL_METRICS";
    public static final String RESEARCH_OUTPUT_METRICS = "RESEARCH_OUTPUT_METRICS";
    public static final String PERSON_METRICS = "PERSON_METRICS";
    public static final List<String> PURE_ENTITIES = List.of(PEOPLE, RESEARCH_OUTPUT, JOURNALS, PROJECTS, ORGANIZATIONS);

    private final Map<String, SyncRunState> runs = new ConcurrentHashMap<>();

    @Resource
    ManagedExecutorService executor;

    @Inject
    CurrentUserService currentUserService;

    @Inject
    PeoplePureSyncJob peopleJob;

    @Inject
    ResearchOutputPureSyncJob researchOutputJob;

    @Inject
    JournalPureSyncJob journalJob;

    @Inject
    ProjectPureSyncJob projectJob;

    @Inject
    OrganizationPureSyncJob organizationJob;

    @Inject
    OrcidWorksSyncJob orcidJob;

    @Inject
    UnpaywallSyncJob unpaywallJob;

    @Inject
    CrossrefSyncJob crossrefJob;

    @Inject
    OpenAlexSyncJob openAlexJob;

    @Inject
    DspaceSyncJob dspaceJob;

    @Inject
    JournalMetricsSyncJob journalMetricsJob;

    @Inject
    ResearchOutputMetricsSyncJob researchOutputMetricsJob;

    @Inject
    PersonMetricsSyncJob personMetricsJob;

    @Inject
    OrcidSyncService orcidSyncService;

    @Inject
    OrcidClient orcidClient;

    @Inject
    cl.uss.ca.external.unpaywall.UnpaywallClient unpaywallClient;

    @Inject
    cl.uss.ca.external.crossref.CrossrefClient crossrefClient;

    @Inject
    cl.uss.ca.external.openalex.OpenAlexClient openAlexClient;

    @Inject
    cl.uss.ca.external.dspace.DspaceClient dspaceClient;

    @Inject
    PureClient pureClient;

    @Inject
    SyncRunRepository syncRunRepository;

    @Inject
    SyncModeAdvisor syncModeAdvisor;

    @Inject
    SyncChangeRepository syncChangeRepository;

    @Inject
    SyncErrorRepository syncErrorRepository;

    @Inject
    DataQualityService dataQualityService;

    @Inject
    UnpaywallService unpaywallService;

    @Inject
    CrossrefService crossrefService;

    @Inject
    OpenAlexService openAlexService;

    @Inject
    DspaceService dspaceService;

    @PostConstruct
    void recoverStaleRuns() {
        try {
            int closed = syncRunRepository.recoverStaleLocks();
            if (closed > 0) {
                LOG.warning("Recovered stale sync locks after startup count=" + closed);
            }
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Unable to recover stale sync locks on startup", ex);
        }
    }

    public SyncRunDto startPeople() {
        return startPeople(null);
    }

    public SyncRunDto startPeople(String mode) {
        return start(PEOPLE, peopleJob::run, mode);
    }

    public SyncRunDto startResearchOutputs() {
        return startResearchOutputs(null);
    }

    public SyncRunDto startResearchOutputs(String mode) {
        return start(RESEARCH_OUTPUT, researchOutputJob::run, mode);
    }

    public SyncRunDto startJournals() {
        return startJournals(null);
    }

    public SyncRunDto startJournals(String mode) {
        return start(JOURNALS, journalJob::run, mode);
    }

    public SyncRunDto startProjects() {
        return startProjects(null);
    }

    public SyncRunDto startProjects(String mode) {
        return start(PROJECTS, projectJob::run, mode);
    }

    public SyncRunDto startOrganizations() {
        return startOrganizations(null);
    }

    public SyncRunDto startOrganizations(String mode) {
        return start(ORGANIZATIONS, organizationJob::run, mode);
    }

    public SyncRunDto startOrcid() {
        return startOrcid(null);
    }

    public SyncRunDto startOrcid(String mode) {
        orcidSyncService.assertTables();
        return startExternal(ORCID, orcidJob::run, orcidClient::assertConfigured, mode);
    }

    public SyncRunDto startUnpaywall() {
        return startUnpaywall(null);
    }

    public SyncRunDto startUnpaywall(String mode) {
        unpaywallService.assertTables();
        return startExternal(UNPAYWALL, unpaywallJob::run, unpaywallClient::assertConfigured, mode);
    }

    public SyncRunDto startCrossref(boolean full) {
        return startCrossref(full ? SyncMode.FULL.name() : SyncMode.INCREMENTAL.name());
    }

    public SyncRunDto startCrossref(String mode) {
        crossrefService.assertTables();
        return startExternal(CROSSREF, crossrefJob::run, crossrefClient::assertConfigured, mode);
    }

    public SyncRunDto startOpenAlex() {
        return startOpenAlex(null);
    }

    public SyncRunDto startOpenAlex(String mode) {
        openAlexService.assertTables();
        return startExternal(OPENALEX, openAlexJob::run, openAlexClient::assertConfigured, mode);
    }

    public OpenAlexAuthorRecoveryDto recoverOpenAlexAuthors(String sourceSyncExecutionId) {
        assertCanSync(OPENALEX);
        openAlexService.assertTables();
        openAlexClient.assertConfigured();
        SyncRunDto source = syncRunRepository.findById(sourceSyncExecutionId);
        if (source == null) {
            throw new NotFoundException("SYNC_NOT_FOUND", "Synchronization was not found");
        }
        openAlexService.attachExtras(source);
        String reject = OpenAlexAuthorRecovery.rejectCode(source);
        if (reject != null) {
            throw new ConflictException(reject, OpenAlexAuthorRecovery.rejectMessage(reject));
        }
        long pending = openAlexService.countPendingAuthors(source);
        if (pending < 1) {
            throw new ConflictException(
                    OpenAlexAuthorRecovery.NO_PENDING,
                    OpenAlexAuthorRecovery.rejectMessage(OpenAlexAuthorRecovery.NO_PENDING)
            );
        }
        SyncRunDto active = syncRunRepository.findActive(OPENALEX);
        if (active != null) {
            throw new ConflictException(
                    "SYNC_ALREADY_RUNNING",
                    "A synchronization is already running for OPENALEX",
                    OPENALEX,
                    active.syncId,
                    active.startedAt,
                    active.startedBy
            );
        }
        SyncRunState run = syncRunRepository.acquire(
                OPENALEX, currentUserService.current().id, SyncMode.RECOVERY
        );
        run.sourceSyncExecutionId = source.syncId;
        run.message = OpenAlexAuthorRecovery.recoveredFromMessage(source.syncId);
        run.phase = OpenAlexSyncPhase.AUTHORS.name();
        run.totalExpected = (int) Math.min(pending, Integer.MAX_VALUE);
        runs.put(run.syncId, run);
        LOG.info("Starting OPENALEX author recovery id=" + run.syncId
                + " sourceSyncId=" + source.syncId
                + " pendingAuthors=" + pending
                + " startedBy=" + run.startedBy
                + " accepted");
        executor.execute(() -> executeJob(run, recoveryRun -> openAlexJob.runRecovery(recoveryRun, source)));
        OpenAlexAuthorRecoveryDto dto = new OpenAlexAuthorRecoveryDto();
        dto.sourceSyncExecutionId = source.syncId;
        dto.recoveryExecutionId = run.syncId;
        dto.status = run.status;
        dto.operation = OpenAlexAuthorRecovery.OPERATION;
        dto.syncMode = SyncMode.RECOVERY.name();
        dto.pendingAuthors = pending;
        return dto;
    }

    public SyncRunDto startDspace() {
        return startDspace(null);
    }

    public SyncRunDto startDspace(String mode) {
        dspaceService.assertTables();
        return startExternal(DSPACE, dspaceJob::run, dspaceClient::assertConfigured, mode);
    }

    /**
     * ORIGINAL bitstream inventory only. Same DSPACE lock as FULL/INCREMENTAL.
     * Uses {@link SyncMode#RECOVERY} so it is not a census and does not start DQ.
     */
    public SyncRunDto startDspaceBitstreamBackfill() {
        assertCanSync(DSPACE);
        dspaceService.assertTables();
        dspaceClient.assertConfigured();
        SyncRunDto active = syncRunRepository.findActive(DSPACE);
        if (active != null) {
            throw new ConflictException(
                    "SYNC_ALREADY_RUNNING",
                    "A synchronization is already running for DSPACE",
                    DSPACE,
                    active.syncId,
                    active.startedAt,
                    active.startedBy
            );
        }
        SyncRunState run = syncRunRepository.acquire(DSPACE, currentUserService.current().id, SyncMode.RECOVERY);
        run.phase = DspaceSyncJob.STAGE_BITSTREAMS;
        run.message = DspaceSyncJob.STAGE_BITSTREAM_BACKFILL;
        runs.put(run.syncId, run);
        LOG.info("Starting DSPACE bitstream backfill id=" + run.syncId
                + " startedBy=" + run.startedBy
                + " accepted");
        executor.execute(() -> executeJob(run, dspaceJob::runBackfill));
        return run.toDto();
    }

    public SyncRunDto startJournalMetrics(String mode) {
        return start(JOURNAL_METRICS, journalMetricsJob::run, mode);
    }

    public SyncRunDto startResearchOutputMetrics(String mode) {
        return start(RESEARCH_OUTPUT_METRICS, researchOutputMetricsJob::run, mode);
    }

    public SyncRunDto startPersonMetrics(String mode) {
        return start(PERSON_METRICS, personMetricsJob::run, mode);
    }

    public SyncRunDto get(String syncId) {
        SyncRunState run = runs.get(syncId);
        if (run != null) {
            assertCanConsult(run.entity);
            SyncRunDto live = run.toDto();
            openAlexService.attachExtras(live);
            return live;
        }
        SyncRunDto persisted = syncRunRepository.findById(syncId);
        if (persisted == null) {
            throw new NotFoundException("SYNC_NOT_FOUND", "Synchronization was not found");
        }
        assertCanConsult(persisted.entity);
        unpaywallService.attachExtras(persisted);
        crossrefService.attachExtras(persisted);
        openAlexService.attachExtras(persisted);
        return persisted;
    }

    public SyncRunDto cancel(String syncId) {
        SyncRunDto persisted = syncRunRepository.findById(syncId);
        if (persisted == null) {
            throw new NotFoundException("SYNC_NOT_FOUND", "Synchronization was not found");
        }
        assertCanSync(persisted.entity);
        if (SyncCancellation.isCancelled(persisted.status) || SyncCancellation.isCancelRequested(persisted.status)) {
            return get(syncId);
        }
        if (!SyncCancellation.isCancellable(persisted.status)) {
            throw new ConflictException(SyncCancellation.NOT_RUNNING, SyncCancellation.notRunningMessage());
        }
        String actor = currentUserService.current().id;
        boolean requested = syncRunRepository.requestCancel(syncId, actor);
        if (!requested) {
            SyncRunDto current = get(syncId);
            if (SyncCancellation.isCancelled(current.status) || SyncCancellation.isCancelRequested(current.status)) {
                return current;
            }
            throw new ConflictException(SyncCancellation.NOT_RUNNING, SyncCancellation.notRunningMessage());
        }
        SyncRunState live = runs.get(syncId);
        if (live != null) {
            live.status = SyncCancellation.REQUESTED;
            live.errorCode = SyncCancellation.REQUEST_CODE;
            live.message = SyncCancellation.requestMessage(actor);
            live.cancelledBy = actor;
        }
        LOG.info("syncId=" + syncId + " entity=" + persisted.entity
                + " cancellation requested by user=" + actor);
        return get(syncId);
    }

    public SyncModePolicyDto policy(String entity) {
        String normalized = normalizeEntity(entity);
        assertCanConsult(normalized);
        SyncModePolicyDto dto = syncModeAdvisor.policy(normalized);
        SyncRunState live = liveForEntity(normalized);
        if (live != null && (dto.lastExecution == null || !live.startedAt.isBefore(dto.lastExecution.startedAt))) {
            dto.lastExecution = live.toDto();
        }
        return dto;
    }

    public SyncRunDto latest(String entity) {
        String normalized = normalizeEntity(entity);
        assertCanConsult(normalized);
        SyncRunDto persisted = syncRunRepository.findLatest(normalized);
        if (persisted != null) {
            SyncRunState live = liveForEntity(normalized);
            if (live != null && !live.startedAt.isBefore(persisted.startedAt)) {
                return live.toDto();
            }
            unpaywallService.attachExtras(persisted);
            crossrefService.attachExtras(persisted);
            openAlexService.attachExtras(persisted);
            return persisted;
        }
        return runs.values().stream()
                .filter(run -> normalized.equals(run.entity))
                .max((a, b) -> a.startedAt.compareTo(b.startedAt))
                .map(SyncRunState::toDto)
                .orElse(null);
    }

    public List<SyncRunDto> history(List<String> entities) {
        if (entities == null || entities.isEmpty()) {
            throw new ValidationException("INVALID_SYNC_ENTITY", "entity is required");
        }
        List<String> normalized = new ArrayList<>();
        for (String entity : entities) {
            String value = normalizeEntity(entity);
            assertCanConsult(value);
            if (!normalized.contains(value)) {
                normalized.add(value);
            }
        }
        return syncRunRepository.findRecent(normalized, 25);
    }

    public List<SyncRunDto> running() {
        Map<String, SyncRunDto> byId = new LinkedHashMap<>();
        for (SyncRunDto persisted : syncRunRepository.findRunning()) {
            byId.put(persisted.syncId, persisted);
        }
        for (SyncRunState live : runs.values()) {
            if (SyncCancellation.isActive(live.status)) {
                byId.put(live.syncId, live.toDto());
            }
        }
        List<SyncRunDto> visible = new ArrayList<>();
        for (SyncRunDto dto : byId.values()) {
            if (canConsult(dto.entity)) {
                openAlexService.attachExtras(dto);
                visible.add(dto);
            }
        }
        return visible;
    }

    public PageResponse<SyncChangeGroupDto> changes(String syncId, String changeType, Integer page, Integer size) {
        get(syncId);
        int safePage = page == null ? 0 : page;
        int safeSize = size == null ? 25 : size;
        if (safePage < 0) {
            throw new ValidationException("INVALID_PAGE", "page must be >= 0");
        }
        if (safeSize < 1 || safeSize > 100) {
            throw new ValidationException("INVALID_SIZE", "size must be between 1 and 100");
        }
        String type = normalizeChangeType(changeType);
        return syncChangeRepository.findGrouped(syncId, type, safePage, safeSize);
    }

    public SyncErrorPageDto errors(String syncId, Integer page, Integer size) {
        get(syncId);
        int safePage = page == null ? 0 : page;
        int safeSize = size == null ? 25 : size;
        if (safePage < 0) {
            throw new ValidationException("INVALID_PAGE", "page must be >= 0");
        }
        if (safeSize < 1 || safeSize > 100) {
            throw new ValidationException("INVALID_SIZE", "size must be between 1 and 100");
        }
        return syncErrorRepository.findPage(syncId, safePage, safeSize);
    }

    private static String normalizeChangeType(String changeType) {
        if (changeType == null || changeType.isBlank() || "ALL".equalsIgnoreCase(changeType.trim())) {
            return "ALL";
        }
        String normalized = changeType.trim().toUpperCase();
        if (!Set.of("CREATED", "UPDATED", "REACTIVATED", "INACTIVATED").contains(normalized)) {
            throw new ValidationException(
                    "INVALID_CHANGE_TYPE",
                    "changeType must be ALL, CREATED, UPDATED, REACTIVATED or INACTIVATED"
            );
        }
        return normalized;
    }

    public void persistProgress(SyncRunState run) {
        try {
            syncRunRepository.requireOwnership(run);
        } catch (SyncOwnershipLostException ex) {
            throw ex;
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "syncId=" + run.syncId + " heartbeat persist failed", ex);
        }
    }

    private SyncRunDto start(String entity, Job job) {
        return start(entity, job, null);
    }

    private SyncRunDto start(String entity, Job job, String mode) {
        return startExternal(entity, job, pureClient::assertConfigured, mode);
    }

    private SyncRunDto startExternal(String entity, Job job, Runnable assertSource) {
        return startExternal(entity, job, assertSource, null);
    }

    private SyncRunDto startExternal(String entity, Job job, Runnable assertSource, String modeRaw) {
        assertCanSync(entity);
        assertSource.run();
        SyncMode mode = syncModeAdvisor.resolveStart(entity, modeRaw);
        SyncRunState run = syncRunRepository.acquire(entity, currentUserService.current().id, mode);
        runs.put(run.syncId, run);
        LOG.info("Starting " + entity + " sync id=" + run.syncId
                + " mode=" + mode
                + " startedBy=" + run.startedBy
                + " accepted");
        executor.execute(() -> executeJob(run, job));
        return run.toDto();
    }

    private void executeJob(SyncRunState run, Job job) {
        try {
            job.run(run);
            if (run.fatal) {
                complete(run, "FAILED", run.errorCode == null ? "SYNC_FAILED" : run.errorCode, run.message);
            } else if (run.errors.get() > 0) {
                complete(run, "COMPLETED_WITH_WARNINGS", run.errorCode, run.message != null
                        ? run.message
                        : "Synchronization finished with item errors.");
            } else {
                complete(run, "COMPLETED", null, null);
            }
        } catch (SyncOwnershipLostException ex) {
            if (stopAfterCancellation(run)) {
                return;
            }
            LOG.warning("syncId=" + run.syncId + " " + run.entity
                    + " worker stopped because execution ownership was lost");
            try {
                syncRunRepository.releaseOwnLock(run.entity, run.syncId);
            } catch (Exception releaseEx) {
                ErrorLog.unexpected(LOG, "syncId=" + run.syncId + " lock release after ownership loss failed", releaseEx);
            }
        } catch (Exception ex) {
            ErrorLog.unexpected(LOG, "syncId=" + run.syncId + " entity=" + run.entity + " failed", ex);
            if (run.isRecovery() || OPENALEX.equals(run.entity)) {
                run.message = "Synchronization failed before completion.";
            } else {
                run.message = "Synchronization failed before completion. Inactive marks were not applied.";
            }
            complete(run, "FAILED", "SYNC_FAILED", run.message);
        }
    }

    private void complete(SyncRunState run, String status, String errorCode, String message) {
        run.status = status;
        run.finishedAt = OffsetDateTime.now();
        run.heartbeatAt = run.finishedAt;
        run.errorCode = errorCode;
        if (message != null) {
            run.message = message;
        }
        boolean finished = false;
        try {
            finished = syncRunRepository.finishAndRelease(run);
        } catch (Exception ex) {
            ErrorLog.unexpected(LOG, "syncId=" + run.syncId + " persist finish failed", ex);
            try {
                syncRunRepository.releaseOwnLock(run.entity, run.syncId);
            } catch (Exception releaseEx) {
                ErrorLog.unexpected(LOG, "syncId=" + run.syncId + " lock release failed", releaseEx);
            }
            return;
        }
        if (!finished) {
            stopAfterCancellation(run);
            return;
        }
        LOG.info("syncId=" + run.syncId
                + " entity=" + run.entity
                + " mode=" + run.syncMode
                + " startedBy=" + run.startedBy
                + " status=" + status
                + " processed=" + run.processed.get()
                + " created=" + run.created.get()
                + " updated=" + run.updated.get()
                + " unchanged=" + run.unchanged.get()
                + " reactivated=" + run.reactivated.get()
                + " markedInactive=" + run.markedInactive.get()
                + " errors=" + run.errors.get());
        if ("COMPLETED".equals(status) && !run.isRecovery()) {
            dataQualityService.startAfterSuccessfulSync(run.entity, run.syncId, run.startedBy);
        }
    }

    private boolean stopAfterCancellation(SyncRunState run) {
        try {
            if (syncRunRepository.finalizeCancellation(run)) {
                LOG.info("syncId=" + run.syncId + " entity=" + run.entity
                        + " worker stopped after cancellation");
                return true;
            }
        } catch (Exception ex) {
            ErrorLog.unexpected(LOG, "syncId=" + run.syncId + " finalize cancellation failed", ex);
        }
        return false;
    }

    private SyncRunState liveForEntity(String entity) {
        return runs.values().stream()
                .filter(run -> entity.equals(run.entity))
                .filter(run -> SyncCancellation.isActive(run.status))
                .findFirst()
                .orElse(null);
    }

    private void assertCanSync(String entity) {
        currentUserService.require(requiredSyncModule(entity), AppPermission.ADMINISTER);
    }

    private void assertCanConsult(String entity) {
        currentUserService.require(requiredSyncModule(entity), AppPermission.CONSULT);
    }

    private boolean canConsult(String entity) {
        AppModule module = AuthorizationPolicy.moduleOfSyncEntity(entity);
        return module != null && currentUserService.allows(module, AppPermission.CONSULT);
    }

    private static AppModule requiredSyncModule(String entity) {
        AppModule module = AuthorizationPolicy.moduleOfSyncEntity(entity);
        if (module == null) {
            throw new ForbiddenException("AUTH_FORBIDDEN", "You are not allowed to perform this action");
        }
        return module;
    }

    public static String normalizeEntity(String entity) {
        if (entity == null || entity.isBlank()) {
            throw new ValidationException("INVALID_SYNC_ENTITY", "entity must be PEOPLE, RESEARCH_OUTPUT, JOURNALS, PROJECTS, ORGANIZATIONS, ORCID, UNPAYWALL, CROSSREF, OPENALEX, DSPACE, JOURNAL_METRICS, RESEARCH_OUTPUT_METRICS or PERSON_METRICS");
        }
        String normalized = entity.trim().toUpperCase();
        if ("RESEARCH_OUTPUTS".equals(normalized)) {
            return RESEARCH_OUTPUT;
        }
        if ("JOURNAL".equals(normalized)) {
            return JOURNALS;
        }
        if ("PROJECT".equals(normalized)) {
            return PROJECTS;
        }
        if ("ORGANIZATION".equals(normalized)) {
            return ORGANIZATIONS;
        }
        if ("ORCID_WORKS".equals(normalized)) {
            return ORCID;
        }
        if ("JOURNAL_METRIC".equals(normalized) || "JOURNALS_METRICS".equals(normalized)) {
            return JOURNAL_METRICS;
        }
        if ("RESEARCH_OUTPUT_METRIC".equals(normalized) || "RESEARCH_OUTPUTS_METRICS".equals(normalized)) {
            return RESEARCH_OUTPUT_METRICS;
        }
        if ("PEOPLE_METRICS".equals(normalized) || "PERSON_METRIC".equals(normalized)) {
            return PERSON_METRICS;
        }
        if (!PEOPLE.equals(normalized) && !RESEARCH_OUTPUT.equals(normalized)
                && !JOURNALS.equals(normalized) && !PROJECTS.equals(normalized)
                && !ORGANIZATIONS.equals(normalized) && !ORCID.equals(normalized)
                && !UNPAYWALL.equals(normalized) && !CROSSREF.equals(normalized)
                && !OPENALEX.equals(normalized) && !DSPACE.equals(normalized)
                && !JOURNAL_METRICS.equals(normalized) && !RESEARCH_OUTPUT_METRICS.equals(normalized)
                && !PERSON_METRICS.equals(normalized)) {
            throw new ValidationException("INVALID_SYNC_ENTITY", "entity must be PEOPLE, RESEARCH_OUTPUT, JOURNALS, PROJECTS, ORGANIZATIONS, ORCID, UNPAYWALL, CROSSREF, OPENALEX, DSPACE, JOURNAL_METRICS, RESEARCH_OUTPUT_METRICS or PERSON_METRICS");
        }
        return normalized;
    }

    @FunctionalInterface
    private interface Job {
        void run(SyncRunState run) throws Exception;
    }
}
