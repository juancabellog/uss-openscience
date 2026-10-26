package cl.uss.ca.service;

import cl.uss.ca.api.dto.OrcidDiscoveryApplyResultDto;
import cl.uss.ca.api.dto.OrcidDiscoveryExecutionDto;
import cl.uss.ca.api.dto.OrcidDiscoveryPersonDto;
import cl.uss.ca.api.dto.OrcidDiscoverySelectedItemDto;
import cl.uss.ca.api.dto.OrcidDiscoveryStatsDto;
import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.api.dto.PureCorrectionResultDto;
import cl.uss.ca.domain.AuthorNameMatcher;
import cl.uss.ca.domain.OrcidDiscoverySelection;
import cl.uss.ca.exception.ConflictException;
import cl.uss.ca.exception.ErrorLog;
import cl.uss.ca.exception.ValidationException;
import cl.uss.ca.external.orcid.OrcidClient;
import cl.uss.ca.repository.OrcidDiscoveryRepository;
import cl.uss.ca.repository.OrcidDiscoveryRepository.PersonSeed;
import cl.uss.ca.repository.OrcidDiscoveryRepository.SelectedApply;
import cl.uss.ca.security.CurrentUserService;
import jakarta.annotation.Resource;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class OrcidDiscoveryService {
    private static final Logger LOG = Logger.getLogger(OrcidDiscoveryService.class.getName());

    @Resource
    ManagedExecutorService executor;

    @Inject
    OrcidDiscoveryRepository discoveryRepository;

    @Inject
    OrcidDiscoveryJob discoveryJob;

    @Inject
    OrcidClient orcidClient;

    @Inject
    PureCorrectionService correctionService;

    @Inject
    CurrentUserService currentUserService;

    @Inject
    @ConfigProperty(name = "orcid.search.maxCandidates", defaultValue = "20")
    int maxCandidates;

    public OrcidDiscoveryStatsDto stats() {
        OrcidDiscoveryStatsDto dto = discoveryRepository.stats();
        dto.searchMaxCandidates = maxCandidates;
        return dto;
    }

    public OrcidDiscoveryExecutionDto latest() {
        return discoveryRepository.findLatest();
    }

    public OrcidDiscoveryExecutionDto get(long executionId) {
        return discoveryRepository.findById(executionId);
    }

    public List<OrcidDiscoveryExecutionDto> history() {
        return discoveryRepository.listHistory(20);
    }

    public PageResponse<OrcidDiscoveryPersonDto> people(
            long executionId,
            String status,
            String cargo,
            String search,
            String sort,
            String direction,
            int page,
            int size
    ) {
        return discoveryRepository.listPeople(executionId, status, cargo, search, sort, direction, page, size);
    }

    public List<String> cargos(long executionId) {
        return discoveryRepository.listCargos(executionId);
    }

    public OrcidDiscoveryPersonDto person(long executionId, long personId) {
        return discoveryRepository.findPerson(executionId, personId);
    }

    public OrcidDiscoveryExecutionDto start() {
        assertCanDiscover();
        orcidClient.assertConfigured();
        assertTables();
        if (discoveryRepository.hasRunning()) {
            throw new ConflictException("ORCID_DISCOVERY_RUNNING", "An ORCID discovery is already running");
        }
        List<PersonSeed> people = discoveryRepository.listPeopleWithoutOrcid();
        long executionId = discoveryRepository.createExecution(currentUserService.current().id, people.size());
        executor.execute(() -> runSafely(executionId, people));
        return discoveryRepository.findById(executionId);
    }

    /**
     * Same engine as the mass search, limited to one person. Intended for
     * Personas → Buscar ORCID without duplicating matching rules.
     */
    public OrcidDiscoveryExecutionDto startForPerson(long personId) {
        assertCanDiscover();
        orcidClient.assertConfigured();
        assertTables();
        if (discoveryRepository.hasRunning()) {
            throw new ConflictException("ORCID_DISCOVERY_RUNNING", "An ORCID discovery is already running");
        }
        List<PersonSeed> people = discoveryRepository.listPeopleWithoutOrcid().stream()
                .filter(item -> item.personId == personId)
                .toList();
        if (people.isEmpty()) {
            throw new ValidationException("ORCID_DISCOVERY_PERSON_SKIPPED", "The person already has an ORCID or is not active");
        }
        long executionId = discoveryRepository.createExecution(currentUserService.current().id, 1);
        executor.execute(() -> runSafely(executionId, people));
        return discoveryRepository.findById(executionId);
    }

    public void select(long executionId, long personId, Long candidateId, Boolean queued) {
        assertCanDiscover();
        OrcidDiscoveryPersonDto person = discoveryRepository.findPerson(executionId, personId);
        if (OrcidDiscoverySelection.terminal(person.personStatus) && candidateId != null) {
            throw new ValidationException("ORCID_PERSON_NOT_SELECTABLE", "The person cannot be selected");
        }
        if (candidateId == null && Boolean.FALSE.equals(queued) && person.selectedCandidateId != null) {
            discoveryRepository.setApplyQueued(executionId, personId, false);
            return;
        }
        if (candidateId == null) {
            discoveryRepository.selectCandidate(executionId, personId, null, false, null, null);
            return;
        }
        if ("TOO_MANY_CANDIDATES".equals(person.personStatus) || "NO_CANDIDATE".equals(person.personStatus)) {
            throw new ValidationException("ORCID_NO_STORED_CANDIDATES", "No stored ORCID candidates are available");
        }
        var candidate = person.candidates.stream()
                .filter(item -> candidateId.equals(item.id))
                .findFirst()
                .orElseThrow(() -> new ValidationException("ORCID_CANDIDATE_NOT_FOUND", "The candidate was not found"));
        if (!OrcidDiscoverySelection.candidateSelectable(candidate.matchLevel, candidate.status, candidate.alreadyUsedPersonId)) {
            if (candidate.alreadyUsedPersonId != null || "ALREADY_USED".equals(candidate.status)) {
                throw new ValidationException("ORCID_ALREADY_USED", "ORCID ya asociado a otra persona");
            }
            throw new ValidationException("ORCID_CANDIDATE_CONFLICT", "The candidate is in conflict and cannot be selected");
        }
        if (Boolean.TRUE.equals(queued) && candidateId.equals(person.selectedCandidateId)) {
            discoveryRepository.setApplyQueued(executionId, personId, true);
            return;
        }
        String origin = OrcidDiscoverySelection.originForStatus(person.personStatus);
        discoveryRepository.selectCandidate(
                executionId,
                personId,
                candidateId,
                queued,
                origin,
                currentUserService.current().id
        );
    }

    public List<OrcidDiscoverySelectedItemDto> queued(long executionId) {
        List<OrcidDiscoverySelectedItemDto> items = new ArrayList<>();
        for (SelectedApply item : discoveryRepository.listSelected(executionId)) {
            OrcidDiscoverySelectedItemDto dto = new OrcidDiscoverySelectedItemDto();
            dto.personId = item.personId;
            dto.displayName = AuthorNameMatcher.displayName(item.firstName, item.lastName);
            dto.pureId = item.pureId;
            dto.orcid = item.orcid;
            dto.candidateId = item.candidateId;
            dto.personStatus = item.personStatus;
            dto.selectionOrigin = item.selectionOrigin != null
                    ? item.selectionOrigin
                    : OrcidDiscoverySelection.originForStatus(item.personStatus);
            dto.queued = true;
            items.add(dto);
        }
        return items;
    }

    public int selectStrong(long executionId) {
        assertCanDiscover();
        return discoveryRepository.selectStrong(executionId);
    }

    /**
     * Re-classifies stored candidates with the current matcher. Does not call
     * ORCID and does not write to PURE.
     */
    public OrcidDiscoveryExecutionDto recalculate(long executionId) {
        assertCanDiscover();
        assertTables();
        if (discoveryRepository.hasRunning()) {
            throw new ConflictException("ORCID_DISCOVERY_RUNNING", "An ORCID discovery is already running");
        }
        OrcidDiscoveryExecutionDto execution = discoveryRepository.findById(executionId);
        if ("RUNNING".equals(execution.status) || "PENDING".equals(execution.status)) {
            throw new ConflictException("ORCID_DISCOVERY_RUNNING", "An ORCID discovery is already running");
        }
        discoveryJob.recalculate(executionId);
        return discoveryRepository.findById(executionId);
    }

    public OrcidDiscoveryApplyResultDto apply(long executionId, List<Long> requestedPersonIds) {
        List<SelectedApply> authoritative = discoveryRepository.listSelected(executionId);
        Map<Long, SelectedApply> byPersonId = new LinkedHashMap<>();
        for (SelectedApply item : authoritative) {
            byPersonId.put(item.personId, item);
        }
        List<Long> requested = requestedPersonIds == null
                ? new ArrayList<>(byPersonId.keySet())
                : OrcidDiscoverySelection.uniquePersonIds(requestedPersonIds);
        OrcidDiscoveryApplyResultDto result = new OrcidDiscoveryApplyResultDto();
        result.selected = requested.size();
        if (requested.isEmpty()) {
            throw new ValidationException("ORCID_NONE_SELECTED", "No ORCID candidates are selected");
        }
        for (Long personId : requested) {
            SelectedApply item = byPersonId.get(personId);
            if (item == null) {
                result.failed++;
                result.items.add(failedUnknown(personId));
                continue;
            }
            Long usedBy = discoveryRepository.findPersonIdByOrcid(item.orcid, item.personId);
            if (usedBy != null) {
                discoveryRepository.markPersonStatus(executionId, item.personId, "ALREADY_USED");
                result.failed++;
                result.items.add(failedItem(item, "ALREADY_USED", "ORCID ya asociado a otra persona"));
                continue;
            }
            if (!OrcidDiscoverySelection.candidateSelectable(item.matchLevel, item.matchLevel, null)) {
                result.failed++;
                result.items.add(failedItem(item, "CONFLICT", "The candidate is in conflict and cannot be selected"));
                continue;
            }
            try {
                PureCorrectionResultDto applied = correctionService.applyOrcid(
                        item.personId,
                        item.uuid,
                        item.orcid,
                        item.candidateId,
                        item.matchLevel,
                        item.score
                );
                applied.displayName = AuthorNameMatcher.displayName(item.firstName, item.lastName);
                applied.pureId = item.pureId;
                applied.childValue = applied.childValue != null ? applied.childValue : item.orcid;
                applied.localParentId = applied.localParentId != null ? applied.localParentId : item.personId;
                result.items.add(applied);
                if (PureCorrectionService.STATUS_REMOTE_CHANGED.equals(applied.status)) {
                    discoveryRepository.markPersonStatus(executionId, item.personId, "REMOTE_CHANGED");
                    result.failed++;
                } else if (PureCorrectionService.STATUS_LOCAL_FAILED.equals(applied.status)) {
                    discoveryRepository.markPersonStatus(executionId, item.personId, "SYNC_FAILED");
                    result.failed++;
                } else {
                    discoveryRepository.markPersonStatus(executionId, item.personId, "SYNCED");
                    result.succeeded++;
                }
            } catch (ConflictException ex) {
                LOG.warning("ORCID apply conflict personId=" + item.personId);
                discoveryRepository.markPersonStatus(executionId, item.personId, "SYNC_FAILED");
                result.failed++;
                result.items.add(failedItem(item, applyConflictStatus(ex), applyConflictMessage(ex)));
            } catch (RuntimeException ex) {
                LOG.warning("ORCID apply failed personId=" + item.personId);
                discoveryRepository.markPersonStatus(executionId, item.personId, "SYNC_FAILED");
                result.failed++;
                result.items.add(failedItem(item, "FAILED", "The ORCID could not be synchronized with PURE."));
            }
        }
        return result;
    }

    public PureCorrectionResultDto reconcileLocal(long executionId, long personId) {
        PureCorrectionResultDto dto = correctionService.reconcileLocalOrcid(personId);
        if (!PureCorrectionService.STATUS_LOCAL_FAILED.equals(dto.status)) {
            discoveryRepository.markPersonStatus(executionId, personId, "SYNCED");
        }
        return dto;
    }

    public void assertTables() {
        if (!discoveryRepository.available()) {
            throw new ValidationException(
                    "ORCID_DISCOVERY_TABLES_UNAVAILABLE",
                    "orcid_discovery_execution tables are required before searching ORCID candidates"
            );
        }
    }

    private static PureCorrectionResultDto failedUnknown(long personId) {
        PureCorrectionResultDto failed = new PureCorrectionResultDto();
        failed.status = "FAILED";
        failed.entity = "PEOPLE";
        failed.scope = PureCorrectionService.SCOPE_ORCID;
        failed.localParentId = personId;
        failed.message = "The person is not currently queued for PURE synchronization.";
        return failed;
    }

    private static PureCorrectionResultDto failedItem(SelectedApply item, String status, String message) {
        PureCorrectionResultDto failed = new PureCorrectionResultDto();
        failed.status = status;
        failed.entity = "PEOPLE";
        failed.scope = PureCorrectionService.SCOPE_ORCID;
        failed.localParentId = item.personId;
        failed.parentUuid = item.uuid;
        failed.childValue = item.orcid;
        failed.displayName = AuthorNameMatcher.displayName(item.firstName, item.lastName);
        failed.pureId = item.pureId;
        failed.message = message;
        return failed;
    }

    private static String applyConflictStatus(ConflictException ex) {
        if ("PURE_VERIFICATION_FAILED".equals(ex.getCode())) {
            return "PURE_VERIFICATION_FAILED";
        }
        if ("ORCID_CONFLICT".equals(ex.getCode())) {
            return "REMOTE_CHANGED";
        }
        return "FAILED";
    }

    private static String applyConflictMessage(ConflictException ex) {
        if ("PURE_VERIFICATION_FAILED".equals(ex.getCode())) {
            return "The ORCID could not be confirmed in PURE.";
        }
        return "The ORCID could not be synchronized with PURE.";
    }

    private void runSafely(long executionId, List<PersonSeed> people) {
        try {
            discoveryJob.run(executionId, people);
        } catch (Exception ex) {
            ErrorLog.unexpected(LOG, "ORCID discovery execution failed id=" + executionId, ex);
            try {
                discoveryRepository.finish(executionId, "FAILED");
            } catch (RuntimeException persist) {
                ErrorLog.unexpected(LOG, "Unable to mark ORCID discovery FAILED id=" + executionId, persist);
            }
        }
    }

    private void assertCanDiscover() {
        String role = currentUserService.current().role;
        String normalized = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("ADMIN") && !normalized.equals("DATA_STEWARD")) {
            throw new ValidationException("ORCID_DISCOVERY_FORBIDDEN", "ORCID discovery requires ADMIN or DATA_STEWARD");
        }
    }
}
