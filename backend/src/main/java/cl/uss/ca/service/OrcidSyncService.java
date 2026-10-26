package cl.uss.ca.service;

import cl.uss.ca.api.dto.OrcidStatsDto;
import cl.uss.ca.api.dto.OrcidWorkGroupDto;
import cl.uss.ca.api.dto.PersonOrcidSyncResultDto;
import cl.uss.ca.api.dto.PersonOrcidWorksDto;
import cl.uss.ca.api.dto.SyncRunDto;
import cl.uss.ca.domain.OrcidFormat;
import cl.uss.ca.exception.ConflictException;
import cl.uss.ca.exception.NotFoundException;
import cl.uss.ca.exception.ValidationException;
import cl.uss.ca.external.orcid.OrcidClient;
import cl.uss.ca.external.orcid.OrcidEmploymentRecord;
import cl.uss.ca.external.orcid.OrcidWorkGroupRecord;
import cl.uss.ca.repository.DashboardRepository;
import cl.uss.ca.repository.OrcidEmploymentRepository;
import cl.uss.ca.repository.OrcidWorkRepository;
import cl.uss.ca.repository.OrcidWorkRepository.DetailError;
import cl.uss.ca.repository.OrcidWorkRepository.PersonCandidate;
import cl.uss.ca.repository.OrcidWorkRepository.ReconcileResult;
import cl.uss.ca.repository.PersonRepository;
import cl.uss.ca.repository.SyncRunRepository;
import cl.uss.ca.sync.SyncChangeRepository;
import cl.uss.ca.sync.SyncErrorRepository;
import cl.uss.ca.sync.SyncItemError;
import cl.uss.ca.sync.SyncJobSupport;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class OrcidSyncService {

    @Inject
    OrcidClient orcidClient;

    @Inject
    OrcidWorkRepository orcidWorkRepository;

    @Inject
    OrcidEmploymentRepository orcidEmploymentRepository;

    @Inject
    OrcidMatchService orcidMatchService;

    @Inject
    PersonRepository personRepository;

    @Inject
    DashboardRepository dashboardRepository;

    @Inject
    SyncRunRepository syncRunRepository;

    @Inject
    SyncChangeRepository syncChangeRepository;

    @Inject
    SyncErrorRepository syncErrorRepository;

    public PersonOrcidWorksDto works(long personId) {
        PersonCandidate person = requirePerson(personId);
        PersonOrcidWorksDto dto = new PersonOrcidWorksDto();
        dto.personId = person.id;
        dto.orcid = person.orcid;
        dto.orcidValid = OrcidFormat.isValid(person.orcid);
        dto.orcidSyncPending = person.syncPending;
        dto.lastOrcidSyncAt = person.lastSyncAt;
        dto.lastOrcidSyncStatus = person.lastSyncStatus;
        dto.groups = orcidWorkRepository.listGroupsByPerson(personId);
        dto.workGroupCount = dto.groups.size();
        for (OrcidWorkGroupDto group : dto.groups) {
            dto.works.addAll(group.assertions);
        }
        dto.workAssertionCount = dto.works.size();
        var personDetail = personRepository.findPersonById(personId);
        orcidMatchService.apply(dto, personDetail == null ? null : personDetail.uuid);
        return dto;
    }

    public PersonOrcidSyncResultDto syncPerson(long personId) {
        orcidClient.assertConfigured();
        assertTables();
        if (orcidGlobalRunning()) {
            throw new ConflictException("SYNC_ALREADY_RUNNING", "An ORCID synchronization is already running");
        }
        PersonCandidate person = requirePerson(personId);
        ReconcileResult result = syncPersonInternal(person, null);
        return toResult(person.id, person.orcid, result);
    }

    public ReconcileResult syncPersonInternal(PersonCandidate person, String syncId) {
        String orcid = OrcidFormat.normalizeValidOrNull(person.orcid);
        if (orcid == null) {
            throw new ValidationException("ORCID_INVALID", "Person does not have a valid ORCID");
        }
        List<OrcidWorkGroupRecord> incoming = orcidClient.fetchWorks(orcid);
        ReconcileResult result = orcidWorkRepository.reconcilePerson(
                person.id,
                orcid,
                incoming,
                orcidClient::fetchWorkDetail,
                syncId
        );
        persistEmployments(person, orcid, syncId, result);
        if (syncId != null) {
            syncChangeRepository.save(syncId, result.changes);
        }
        persistDetailErrors(syncId, person.id, orcid, result.detailFailures);
        return result;
    }

    private void persistEmployments(
            PersonCandidate person,
            String orcid,
            String syncId,
            ReconcileResult result
    ) {
        if (!orcidEmploymentRepository.available()) {
            return;
        }
        try {
            List<OrcidEmploymentRecord> employments = orcidClient.fetchEmployments(orcid);
            orcidEmploymentRepository.replaceForPerson(person.id, orcid, employments);
        } catch (Exception ex) {
            persistEmploymentError(syncId, person.id, orcid, ex);
            result.detailErrors++;
        }
    }

    private void persistEmploymentError(String syncId, long personId, String orcid, Exception ex) {
        if (syncId == null) {
            return;
        }
        SyncItemError item = new SyncItemError();
        item.syncId = syncId;
        item.entity = "ORCID";
        item.localRecordId = personId;
        item.recordUuid = orcid;
        item.stage = SyncJobSupport.STAGE_ORCID_EMPLOYMENT;
        item.errorCode = ex instanceof ValidationException validation ? validation.getCode() : "EMPLOYMENT_FETCH_FAILED";
        item.errorMessage = ex.getMessage();
        syncErrorRepository.save(item);
    }

    public OrcidStatsDto stats() {
        OrcidStatsDto dto = new OrcidStatsDto();
        dto.configured = orcidClient.isConfigured();
        dto.tablesAvailable = orcidWorkRepository.available();
        dto.peopleWithOrcid = dashboardRepository.countPersonsWithOrcid();
        if (dto.tablesAvailable) {
            dto.peopleSynced = orcidWorkRepository.countPeopleSynced();
            dto.workGroupCount = orcidWorkRepository.countWorkGroups();
            dto.workAssertionCount = orcidWorkRepository.countAssertions();
            dto.works = dto.workGroupCount;
        }
        SyncRunDto latest = syncRunRepository.findLatest("ORCID");
        if (latest != null) {
            dto.lastProcessed = latest.processed;
            dto.lastErrors = latest.errors;
            dto.lastExecution = latest.finishedAt != null ? latest.finishedAt : latest.startedAt;
            dto.lastStatus = latest.status;
        }
        return dto;
    }

    public void assertTables() {
        if (!orcidWorkRepository.available()) {
            throw new ValidationException(
                    "ORCID_TABLES_UNAVAILABLE",
                    "orcid_work_group tables are required before synchronizing ORCID"
            );
        }
    }

    private void persistDetailErrors(String syncId, long personId, String orcid, List<DetailError> errors) {
        if (syncId == null || errors == null || errors.isEmpty()) {
            return;
        }
        for (DetailError error : errors) {
            SyncItemError item = new SyncItemError();
            item.syncId = syncId;
            item.entity = "ORCID";
            item.localRecordId = personId;
            item.pureId = error.putCode;
            item.recordUuid = orcid;
            item.stage = SyncJobSupport.STAGE_ORCID_DETAIL;
            item.errorCode = error.code == null ? "DETAIL_FETCH_FAILED" : error.code;
            item.errorMessage = error.message;
            syncErrorRepository.save(item);
        }
    }

    private boolean orcidGlobalRunning() {
        return syncRunRepository.findRunning().stream().anyMatch(run -> "ORCID".equals(run.entity));
    }

    private PersonCandidate requirePerson(long personId) {
        PersonCandidate person = orcidWorkRepository.requirePerson(personId);
        if (person == null) {
            throw new NotFoundException("PERSON_NOT_FOUND", "Person not found");
        }
        return person;
    }

    private static PersonOrcidSyncResultDto toResult(long personId, String orcid, ReconcileResult result) {
        PersonOrcidSyncResultDto dto = new PersonOrcidSyncResultDto();
        dto.personId = personId;
        dto.orcid = orcid;
        dto.status = result.detailErrors > 0 ? "COMPLETED_WITH_WARNINGS" : "COMPLETED";
        dto.groupsReceived = result.groupsReceived;
        dto.groupsCreated = result.groupsCreated;
        dto.groupsUpdated = result.groupsUpdated;
        dto.groupsUnchanged = result.groupsUnchanged;
        dto.groupsInactive = result.groupsInactive;
        dto.assertionsReceived = result.assertionsReceived;
        dto.assertionsCreated = result.assertionsCreated;
        dto.assertionsUpdated = result.assertionsUpdated;
        dto.assertionsUnchanged = result.assertionsUnchanged;
        dto.assertionsInactive = result.assertionsInactive;
        dto.detailsFetched = result.detailsFetched;
        dto.detailErrors = result.detailErrors;
        dto.received = result.groupsReceived;
        dto.created = result.groupsCreated;
        dto.updated = result.groupsUpdated;
        dto.unchanged = result.groupsUnchanged;
        dto.reactivated = 0;
        dto.markedInactive = result.groupsInactive;
        return dto;
    }
}
