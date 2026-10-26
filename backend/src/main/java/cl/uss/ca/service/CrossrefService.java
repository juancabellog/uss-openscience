package cl.uss.ca.service;

import cl.uss.ca.api.dto.CrossrefFiltersDto;
import cl.uss.ca.api.dto.CrossrefQuery;
import cl.uss.ca.api.dto.CrossrefStatsDto;
import cl.uss.ca.api.dto.CrossrefWorkDetailDto;
import cl.uss.ca.api.dto.CrossrefWorkSummaryDto;
import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.api.dto.SyncRunDto;
import cl.uss.ca.exception.NotFoundException;
import cl.uss.ca.external.crossref.CrossrefClient;
import cl.uss.ca.external.crossref.CrossrefStatus;
import cl.uss.ca.repository.CrossrefRepository;
import cl.uss.ca.repository.SyncRunRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class CrossrefService {

    @Inject
    CrossrefClient crossrefClient;

    @Inject
    CrossrefRepository crossrefRepository;

    @Inject
    SyncRunRepository syncRunRepository;

    public void assertTables() {
        crossrefRepository.assertAvailable();
    }

    public CrossrefStatsDto stats() {
        CrossrefStatsDto dto = new CrossrefStatsDto();
        dto.enabled = crossrefClient.isEnabled();
        dto.configured = crossrefClient.isConfigured();
        dto.tablesAvailable = crossrefRepository.available();
        if (dto.tablesAvailable) {
            dto.uniqueDois = crossrefRepository.countUniqueActiveDois();
            dto.evaluated = crossrefRepository.countStatus(CrossrefStatus.FOUND)
                    + crossrefRepository.countStatus(CrossrefStatus.OTHER_AGENCY)
                    + crossrefRepository.countStatus(CrossrefStatus.NOT_FOUND)
                    + crossrefRepository.countStatus(CrossrefStatus.INVALID_DOI);
            dto.found = crossrefRepository.countStatus(CrossrefStatus.FOUND);
            dto.otherAgency = crossrefRepository.countStatus(CrossrefStatus.OTHER_AGENCY);
            dto.notFound = crossrefRepository.countStatus(CrossrefStatus.NOT_FOUND);
            dto.invalid = crossrefRepository.countStatus(CrossrefStatus.INVALID_DOI);
            dto.withDifferences = crossrefRepository.countDifferences();
            dto.withOrcid = crossrefRepository.countFlag("has_orcid");
            dto.withFunding = crossrefRepository.countFlag("has_funding");
            dto.withRor = crossrefRepository.countFlag("has_ror");
            dto.withUpdate = crossrefRepository.countFlag("has_update")
                    + crossrefRepository.countFlag("has_retraction");
        }
        SyncRunDto latest = syncRunRepository.findLatest(PureSyncService.CROSSREF);
        if (latest != null) {
            attachExtras(latest);
            dto.lastProcessed = latest.processed;
            dto.lastErrors = latest.errors;
            dto.lastExecution = latest.finishedAt != null ? latest.finishedAt : latest.startedAt;
            dto.lastStatus = latest.status;
        }
        return dto;
    }

    public PageResponse<CrossrefWorkSummaryDto> list(CrossrefQuery query) {
        assertTables();
        return crossrefRepository.list(query);
    }

    public CrossrefFiltersDto filters() {
        assertTables();
        return crossrefRepository.filters();
    }

    public CrossrefWorkDetailDto detail(long id) {
        assertTables();
        CrossrefWorkDetailDto dto = crossrefRepository.findDetail(id);
        if (dto == null) {
            throw new NotFoundException("CROSSREF_NOT_FOUND", "Crossref work was not found");
        }
        return dto;
    }

    public List<CrossrefWorkSummaryDto> byResearchOutput(long researchOutputId) {
        if (!crossrefRepository.available()) {
            return List.of();
        }
        return crossrefRepository.findByResearchOutput(researchOutputId);
    }

    public void attachExtras(SyncRunDto dto) {
        if (dto == null || dto.syncId == null || !PureSyncService.CROSSREF.equals(dto.entity)) {
            return;
        }
        CrossrefRepository.RunExtras extras = crossrefRepository.findRunStats(dto.syncId);
        if (extras == null) {
            return;
        }
        dto.oa = extras.found;
        dto.nonOa = extras.otherAgency;
        dto.notFound = extras.notFound;
        dto.markedInactive = extras.invalid;
    }
}
