package cl.uss.ca.service;

import cl.uss.ca.api.dto.OpenAlexAuthorQuery;
import cl.uss.ca.api.dto.OpenAlexAuthorSummaryDto;
import cl.uss.ca.api.dto.OpenAlexFiltersDto;
import cl.uss.ca.api.dto.OpenAlexMatchDetailDto;
import cl.uss.ca.api.dto.OpenAlexMatchQuery;
import cl.uss.ca.api.dto.OpenAlexMatchSummaryDto;
import cl.uss.ca.api.dto.OpenAlexPersonIdentityDto;
import cl.uss.ca.api.dto.OpenAlexStatsDto;
import cl.uss.ca.api.dto.OpenAlexWorkDetailDto;
import cl.uss.ca.api.dto.OpenAlexWorkQuery;
import cl.uss.ca.api.dto.OpenAlexWorkSummaryDto;
import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.api.dto.PersonDetailDto;
import cl.uss.ca.api.dto.PureCorrectionResultDto;
import cl.uss.ca.api.dto.SyncRunDto;
import cl.uss.ca.auth.AppModule;
import cl.uss.ca.auth.AppPermission;
import cl.uss.ca.domain.OpenAlexOrcidSyncPolicy;
import cl.uss.ca.domain.OpenAlexPersonMatcher;
import cl.uss.ca.exception.ConflictException;
import cl.uss.ca.exception.NotFoundException;
import cl.uss.ca.exception.ValidationException;
import cl.uss.ca.security.CurrentUserService;
import cl.uss.ca.sync.OpenAlexAuthorRecovery;
import cl.uss.ca.external.openalex.OpenAlexClient;
import cl.uss.ca.external.openalex.OpenAlexStatus;
import cl.uss.ca.repository.OpenAlexRepository;
import cl.uss.ca.repository.SyncRunRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class OpenAlexService {

    @Inject
    OpenAlexClient openAlexClient;

    @Inject
    OpenAlexRepository openAlexRepository;

    @Inject
    SyncRunRepository syncRunRepository;

    @Inject
    PureCorrectionService pureCorrectionService;

    @Inject
    CurrentUserService currentUserService;

    public void assertTables() {
        openAlexRepository.assertAvailable();
    }

    public OpenAlexStatsDto stats() {
        OpenAlexStatsDto dto = new OpenAlexStatsDto();
        dto.enabled = openAlexClient.isEnabled();
        dto.configured = openAlexClient.isConfigured();
        dto.tablesAvailable = openAlexRepository.available();
        if (dto.tablesAvailable) {
            dto.uniqueDois = openAlexRepository.countUniqueActiveDois();
            dto.found = openAlexRepository.countOutcome(OpenAlexStatus.FOUND);
            dto.notFound = openAlexRepository.countOutcome(OpenAlexStatus.NOT_FOUND);
            dto.invalid = openAlexRepository.countOutcome(OpenAlexStatus.INVALID_DOI);
            dto.evaluated = dto.found + dto.notFound + dto.invalid;
            dto.authors = openAlexRepository.countAuthors();
            dto.personsLinked = openAlexRepository.countLinkedPersons();
            dto.pendingMatches = openAlexRepository.countStatusMatches("STRONG")
                    + openAlexRepository.countStatusMatches("MANUAL_REVIEW")
                    + openAlexRepository.countStatusMatches("CONFLICT");
            dto.confirmedMatches = openAlexRepository.countStatusMatches("CONFIRMED");
            dto.conflicts = openAlexRepository.countStatusMatches("CONFLICT");
            dto.coverageNumerator = openAlexRepository.countConfirmedPersons();
            dto.coverageDenominator = openAlexRepository.countEvaluablePersons();
        }
        SyncRunDto latest = syncRunRepository.findLatest(PureSyncService.OPENALEX);
        if (latest != null) {
            attachExtras(latest);
            dto.lastProcessed = latest.processed;
            dto.lastErrors = latest.errors;
            dto.lastExecution = latest.finishedAt != null ? latest.finishedAt : latest.startedAt;
            dto.lastStatus = latest.status;
        }
        dto.pendingRecoveries = pendingRecoveries();
        return dto;
    }

    public List<SyncRunDto> history() {
        List<SyncRunDto> items = syncRunRepository.findRecent(PureSyncService.OPENALEX, 25);
        for (SyncRunDto item : items) {
            attachExtras(item);
        }
        return items;
    }

    public List<SyncRunDto> pendingRecoveries() {
        List<SyncRunDto> pending = new ArrayList<>();
        if (!openAlexRepository.available()) {
            return pending;
        }
        for (SyncRunDto item : syncRunRepository.findRecent(PureSyncService.OPENALEX, 50)) {
            attachExtras(item);
            if (item.pendingAuthors != null && item.pendingAuthors > 0
                    && OpenAlexAuthorRecovery.TYPE.equals(item.recoveryType)) {
                pending.add(item);
            }
        }
        return pending;
    }

    public PageResponse<OpenAlexWorkSummaryDto> listWorks(OpenAlexWorkQuery query) {
        assertTables();
        return openAlexRepository.listWorks(query);
    }

    public PageResponse<OpenAlexAuthorSummaryDto> listAuthors(OpenAlexAuthorQuery query) {
        assertTables();
        return openAlexRepository.listAuthors(query);
    }

    public PageResponse<OpenAlexMatchSummaryDto> listMatches(OpenAlexMatchQuery query) {
        assertTables();
        PageResponse<OpenAlexMatchSummaryDto> page = openAlexRepository.listMatches(query);
        if (page != null && page.items != null) {
            for (OpenAlexMatchSummaryDto item : page.items) {
                enrich(item);
            }
        }
        return page;
    }

    public OpenAlexFiltersDto filters() {
        assertTables();
        return openAlexRepository.filters();
    }

    public OpenAlexWorkDetailDto workDetail(long id) {
        assertTables();
        OpenAlexWorkDetailDto dto = openAlexRepository.findWorkDetail(id);
        if (dto == null) {
            throw new NotFoundException("OPENALEX_NOT_FOUND", "OpenAlex work was not found");
        }
        return dto;
    }

    public OpenAlexMatchDetailDto matchDetail(long id) {
        return matchDetail(id, 1, 25, null);
    }

    public OpenAlexMatchDetailDto matchDetail(long id, Integer workPage, Integer workSize, String workSearch) {
        assertTables();
        OpenAlexMatchDetailDto dto = openAlexRepository.findMatchDetail(id, workPage, workSize, workSearch);
        if (dto == null) {
            throw new NotFoundException("OPENALEX_MATCH_NOT_FOUND", "OpenAlex match was not found");
        }
        enrich(dto);
        return dto;
    }

    public OpenAlexMatchDetailDto confirm(long id, String user) {
        assertTables();
        OpenAlexMatchDetailDto current = openAlexRepository.findMatchDetail(id);
        if (current == null) {
            throw new NotFoundException("OPENALEX_MATCH_NOT_FOUND", "OpenAlex match was not found");
        }
        if (OpenAlexPersonMatcher.STATUS_REJECTED.equals(current.status)) {
            throw new ValidationException("OPENALEX_MATCH_REJECTED", "A rejected OpenAlex candidate cannot be confirmed");
        }
        Long otherPerson = openAlexRepository.findConfirmedPersonIdForAuthor(current.openalexAuthorId, id);
        if (OpenAlexOrcidSyncPolicy.authorConfirmedForOtherPerson(current.personId, otherPerson)) {
            throw new ConflictException(
                    "OPENALEX_AUTHOR_ALREADY_CONFIRMED",
                    "This OpenAlex Author is already confirmed for another PURE person"
            );
        }
        OpenAlexMatchDetailDto dto = openAlexRepository.confirm(id, user);
        if (dto == null) {
            throw new NotFoundException("OPENALEX_MATCH_NOT_FOUND", "OpenAlex match was not found");
        }
        enrich(dto);
        return dto;
    }

    public OpenAlexMatchDetailDto reject(long id, String user) {
        assertTables();
        OpenAlexMatchDetailDto dto = openAlexRepository.reject(id, user);
        if (dto == null) {
            throw new NotFoundException("OPENALEX_MATCH_NOT_FOUND", "OpenAlex match was not found");
        }
        enrich(dto);
        return dto;
    }

    public PureCorrectionResultDto syncOrcid(long matchId) {
        assertTables();
        currentUserService.require(AppModule.PEOPLE, AppPermission.ADMINISTER);
        OpenAlexMatchDetailDto match = matchDetail(matchId);
        OpenAlexOrcidSyncPolicy.Decision decision = OpenAlexOrcidSyncPolicy.evaluate(
                match.status,
                match.personOrcid,
                match.openalexOrcid
        );
        if (!decision.syncAvailable) {
            throw new ValidationException(
                    decision.blockedReason == null ? "ORCID_SYNC_BLOCKED" : decision.blockedReason,
                    "This OpenAlex ORCID cannot be written to PURE"
            );
        }
        if (match.personId == null) {
            throw new ValidationException("OPENALEX_MATCH_PERSON_MISSING", "The OpenAlex match has no PURE person");
        }
        return pureCorrectionService.applyOrcid(
                match.personId,
                match.personUuid,
                match.openalexOrcid,
                match.id,
                match.status,
                match.matchingWorkCount,
                PureCorrectionService.EVIDENCE_OPENALEX,
                match.openalexAuthorId
        );
    }

    static void enrich(OpenAlexMatchSummaryDto dto) {
        if (dto == null) {
            return;
        }
        OpenAlexOrcidSyncPolicy.Decision decision = OpenAlexOrcidSyncPolicy.evaluate(
                dto.status,
                dto.personOrcid,
                dto.openalexOrcid
        );
        dto.identityConfirmed = decision.identityConfirmed;
        dto.orcidComparison = decision.comparison;
        dto.orcidSyncAvailable = decision.syncAvailable;
        dto.orcidSyncBlockedReason = decision.blockedReason;
    }

    public void attachToPerson(PersonDetailDto dto) {
        if (dto == null || dto.id == null || !openAlexRepository.available()) {
            return;
        }
        OpenAlexPersonIdentityDto identity = openAlexRepository.identityForPerson(dto.id);
        if (identity == null) {
            return;
        }
        dto.openalexAuthorId = identity.openalexAuthorId;
        dto.openalexDisplayName = identity.displayName;
        dto.openalexMatchStatus = identity.status;
        dto.openalexUrl = identity.openalexUrl;
        dto.openalexMatchingWorkCount = identity.matchingWorkCount;
        dto.openalexConfirmed = identity.confirmed;
    }

    public void attachExtras(SyncRunDto dto) {
        if (dto == null || dto.syncId == null || !PureSyncService.OPENALEX.equals(dto.entity)) {
            return;
        }
        OpenAlexRepository.RunExtras extras = openAlexRepository.findRunStats(dto.syncId);
        dto.markedInactive = 0;
        if (extras != null) {
            dto.oa = extras.found;
            dto.notFound = extras.notFound;
            dto.invalidCount = extras.invalid;
            if (extras.phase != null) {
                dto.phase = extras.phase;
            }
            if (extras.authorsTotal != null) {
                dto.authorsTotal = extras.authorsTotal;
            }
            if (extras.authorsProcessed != null) {
                dto.authorsProcessed = extras.authorsProcessed;
            }
            if (extras.authorsCreated != null) {
                dto.authorsCreated = extras.authorsCreated;
            }
            if (extras.authorsUpdated != null) {
                dto.authorsUpdated = extras.authorsUpdated;
            }
            if (extras.sourceSyncExecutionId != null) {
                dto.sourceSyncExecutionId = extras.sourceSyncExecutionId;
            }
        }
        attachRecovery(dto);
    }

    /** Global count of openalex_author.needs_refresh = 1. Not scoped to the source execution. */
    public long countPendingAuthors(SyncRunDto source) {
        if (source == null || source.syncId == null) {
            return 0;
        }
        return openAlexRepository.countPendingAuthors();
    }

    private void attachRecovery(SyncRunDto dto) {
        dto.recoveryBlockedReason = null;
        if (OpenAlexAuthorRecovery.rejectCode(dto) != null) {
            dto.recoveryAvailable = false;
            return;
        }
        long pending = countPendingAuthors(dto);
        dto.pendingAuthors = pending;
        if (pending < 1) {
            dto.recoveryAvailable = false;
            dto.recoveryType = null;
            return;
        }
        dto.recoveryType = OpenAlexAuthorRecovery.TYPE;
        if (syncRunRepository.findActive(PureSyncService.OPENALEX) != null) {
            dto.recoveryAvailable = false;
            dto.recoveryBlockedReason = OpenAlexAuthorRecovery.RUNNING_BLOCKED;
            return;
        }
        dto.recoveryAvailable = true;
    }
}
