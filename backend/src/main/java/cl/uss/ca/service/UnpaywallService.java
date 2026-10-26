package cl.uss.ca.service;

import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.api.dto.SyncRunDto;
import cl.uss.ca.api.dto.UnpaywallFiltersDto;
import cl.uss.ca.api.dto.UnpaywallQuery;
import cl.uss.ca.api.dto.UnpaywallStatsDto;
import cl.uss.ca.api.dto.UnpaywallWorkDetailDto;
import cl.uss.ca.api.dto.UnpaywallWorkSummaryDto;
import cl.uss.ca.exception.NotFoundException;
import cl.uss.ca.external.unpaywall.UnpaywallClient;
import cl.uss.ca.repository.SyncRunRepository;
import cl.uss.ca.repository.UnpaywallRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class UnpaywallService {

    @Inject
    UnpaywallClient unpaywallClient;

    @Inject
    UnpaywallRepository unpaywallRepository;

    @Inject
    SyncRunRepository syncRunRepository;

    public void assertTables() {
        unpaywallRepository.assertAvailable();
    }

    public UnpaywallStatsDto stats() {
        UnpaywallStatsDto dto = new UnpaywallStatsDto();
        dto.configured = unpaywallClient.isConfigured();
        dto.tablesAvailable = unpaywallRepository.available();
        if (dto.tablesAvailable) {
            dto.uniqueDois = unpaywallRepository.countUniqueActiveDois();
            dto.evaluated = unpaywallRepository.countEvaluated();
            dto.oa = unpaywallRepository.countOa(true);
            dto.nonOa = unpaywallRepository.countOa(false);
            dto.repositoryCopy = unpaywallRepository.countRepositoryCopy();
            dto.notFound = unpaywallRepository.countNotFound();
            dto.oaStatusDistribution.addAll(unpaywallRepository.statusDistribution());
        }
        SyncRunDto latest = syncRunRepository.findLatest(PureSyncService.UNPAYWALL);
        if (latest != null) {
            attachExtras(latest);
            dto.lastProcessed = latest.processed;
            dto.lastErrors = latest.errors;
            dto.lastNotFound = latest.notFound;
            dto.lastOa = latest.oa;
            dto.lastNonOa = latest.nonOa;
            dto.lastExecution = latest.finishedAt != null ? latest.finishedAt : latest.startedAt;
            dto.lastStatus = latest.status;
        }
        return dto;
    }

    public PageResponse<UnpaywallWorkSummaryDto> list(UnpaywallQuery query) {
        assertTables();
        return unpaywallRepository.list(query);
    }

    public UnpaywallFiltersDto filters() {
        assertTables();
        return unpaywallRepository.filters();
    }

    public UnpaywallWorkDetailDto detail(long id) {
        assertTables();
        UnpaywallWorkDetailDto dto = unpaywallRepository.findDetail(id);
        if (dto == null) {
            throw new NotFoundException("UNPAYWALL_NOT_FOUND", "Unpaywall work was not found");
        }
        return dto;
    }

    public void attachExtras(SyncRunDto dto) {
        if (dto == null || dto.syncId == null || !PureSyncService.UNPAYWALL.equals(dto.entity)) {
            return;
        }
        if (dto.notFound != null && dto.oa != null) {
            return;
        }
        UnpaywallRepository.RunExtras extras = unpaywallRepository.findRunStats(dto.syncId);
        if (extras == null) {
            return;
        }
        dto.notFound = extras.notFound;
        dto.oa = extras.oa;
        dto.nonOa = extras.nonOa;
    }
}
