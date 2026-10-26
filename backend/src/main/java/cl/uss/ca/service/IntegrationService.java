package cl.uss.ca.service;

import cl.uss.ca.api.dto.IntegrationEntitySyncDto;
import cl.uss.ca.api.dto.IntegrationStatusDto;
import cl.uss.ca.api.dto.SyncModePolicyDto;
import cl.uss.ca.api.dto.SyncRunDto;
import cl.uss.ca.sync.SyncModeAdvisor;
import cl.uss.ca.external.orcid.OrcidClient;
import cl.uss.ca.external.unpaywall.UnpaywallClient;
import cl.uss.ca.external.crossref.CrossrefClient;
import cl.uss.ca.external.openalex.OpenAlexClient;
import cl.uss.ca.external.dspace.DspaceClient;
import cl.uss.ca.repository.DspaceRepository;
import cl.uss.ca.repository.DashboardRepository;
import cl.uss.ca.repository.OrcidWorkRepository;
import cl.uss.ca.repository.SyncRunRepository;
import cl.uss.ca.repository.UnpaywallRepository;
import cl.uss.ca.repository.CrossrefRepository;
import cl.uss.ca.repository.OpenAlexRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class IntegrationService {

    @Inject
    DashboardRepository dashboardRepository;

    @Inject
    OrcidClient orcidClient;

    @Inject
    OrcidWorkRepository orcidWorkRepository;

    @Inject
    SyncRunRepository syncRunRepository;

    @Inject
    UnpaywallClient unpaywallClient;

    @Inject
    UnpaywallRepository unpaywallRepository;

    @Inject
    CrossrefClient crossrefClient;

    @Inject
    CrossrefRepository crossrefRepository;

    @Inject
    OpenAlexClient openAlexClient;

    @Inject
    OpenAlexRepository openAlexRepository;

    @Inject
    DspaceClient dspaceClient;

    @Inject
    DspaceRepository dspaceRepository;

    @Inject
    SyncModeAdvisor syncModeAdvisor;

    public List<IntegrationStatusDto> list() {
        return List.of(
                pureStatus(),
                orcidStatus(),
                unpaywallStatus(),
                scopusStatus(),
                openAlexStatus(),
                crossrefStatus(),
                dspaceStatus(),
                notConfigured("SCIVAL")
        );
    }

    private IntegrationStatusDto pureStatus() {
        boolean connectorOk = dashboardRepository.hasPurePersonTable();
        Map<String, SyncRunDto> latestByEntity = indexByEntity(
                syncRunRepository.findLatestByEntities(PureSyncService.PURE_ENTITIES)
        );
        boolean anyFailed = false;
        boolean anyWarnings = false;
        List<IntegrationEntitySyncDto> entities = new ArrayList<>();
        for (String entity : PureSyncService.PURE_ENTITIES) {
            IntegrationEntitySyncDto row = new IntegrationEntitySyncDto();
            row.entity = entity;
            row.latestExecution = latestByEntity.get(entity);
            entities.add(row);
            if (row.latestExecution == null) {
                continue;
            }
            if ("FAILED".equals(row.latestExecution.status)) {
                anyFailed = true;
            } else if ("COMPLETED_WITH_WARNINGS".equals(row.latestExecution.status)) {
                anyWarnings = true;
            }
        }
        String status;
        String reason = null;
        if (!connectorOk) {
            status = "NOT_CONFIGURED";
        } else if (anyFailed) {
            status = "WARNING";
            reason = "ENTITY_EXECUTION_FAILED";
        } else if (anyWarnings) {
            status = "WARNING";
            reason = "ENTITY_EXECUTION_WARNINGS";
        } else {
            status = "OPERATIONAL";
        }
        IntegrationStatusDto dto = configured("PURE", status);
        dto.statusReason = reason;
        dto.entities = entities;
        return dto;
    }

    private IntegrationStatusDto orcidStatus() {
        if (!orcidClient.isConfigured() || !orcidWorkRepository.available()) {
            return notConfigured("ORCID");
        }
        SyncRunDto latest = syncRunRepository.findLatest("ORCID");
        IntegrationStatusDto dto = configured("ORCID", lastOrcidHealth(latest));
        dto.statusReason = lastOrcidHealthReason(latest);
        attachExecution(dto, "ORCID", latest);
        return dto;
    }

    /**
     * Integration health is not the last-run badge.
     * OPERATIONAL: Public API configured and tables present.
     * WARNING: only when the last persisted ORCID execution failed.
     */
    private static String lastOrcidHealth(SyncRunDto latest) {
        if (latest != null && "FAILED".equals(latest.status)) {
            return "WARNING";
        }
        return "OPERATIONAL";
    }

    private static String lastOrcidHealthReason(SyncRunDto latest) {
        if (latest != null && "FAILED".equals(latest.status)) {
            return "LAST_EXECUTION_FAILED";
        }
        return null;
    }

    private IntegrationStatusDto unpaywallStatus() {
        if (!unpaywallClient.isConfigured() || !unpaywallRepository.available()) {
            return notConfigured("UNPAYWALL");
        }
        SyncRunDto latest = syncRunRepository.findLatest(PureSyncService.UNPAYWALL);
        IntegrationStatusDto dto = configured("UNPAYWALL", lastOrcidHealth(latest));
        dto.statusReason = lastOrcidHealthReason(latest);
        attachExecution(dto, PureSyncService.UNPAYWALL, latest);
        return dto;
    }

    private IntegrationStatusDto crossrefStatus() {
        if (!crossrefClient.isEnabled() || !crossrefClient.isConfigured() || !crossrefRepository.available()) {
            return notConfigured("CROSSREF");
        }
        SyncRunDto latest = syncRunRepository.findLatest(PureSyncService.CROSSREF);
        IntegrationStatusDto dto = configured("CROSSREF", lastOrcidHealth(latest));
        dto.statusReason = lastOrcidHealthReason(latest);
        attachExecution(dto, PureSyncService.CROSSREF, latest);
        return dto;
    }

    private IntegrationStatusDto openAlexStatus() {
        if (!openAlexClient.isEnabled() || !openAlexClient.isConfigured() || !openAlexRepository.available()) {
            return notConfigured("OPENALEX");
        }
        SyncRunDto latest = syncRunRepository.findLatest(PureSyncService.OPENALEX);
        IntegrationStatusDto dto = configured("OPENALEX", lastOrcidHealth(latest));
        dto.statusReason = lastOrcidHealthReason(latest);
        attachExecution(dto, PureSyncService.OPENALEX, latest);
        return dto;
    }

    private IntegrationStatusDto dspaceStatus() {
        if (!dspaceClient.isEnabled() || !dspaceClient.isConfigured() || !dspaceRepository.available()) {
            return notConfigured("DSPACE");
        }
        SyncRunDto latest = syncRunRepository.findLatest(PureSyncService.DSPACE);
        IntegrationStatusDto dto = configured("DSPACE", lastOrcidHealth(latest));
        dto.statusReason = lastOrcidHealthReason(latest);
        attachExecution(dto, PureSyncService.DSPACE, latest);
        return dto;
    }

    private IntegrationStatusDto scopusStatus() {
        if (!dashboardRepository.hasScopusAuthorTable()) {
            return notConfigured("SCOPUS");
        }
        try {
            dashboardRepository.countScopusAuthors();
            return configured("SCOPUS", "OPERATIONAL");
        } catch (RuntimeException ex) {
            return notConfigured("SCOPUS");
        }
    }

    private static IntegrationStatusDto configured(String code, String status) {
        IntegrationStatusDto dto = new IntegrationStatusDto();
        dto.code = code;
        dto.status = status;
        dto.lastExecution = null;
        dto.lastSuccessfulExecution = null;
        dto.processed = null;
        dto.errors = null;
        return dto;
    }

    private void attachExecution(IntegrationStatusDto dto, String entity, SyncRunDto latest) {
        if (latest != null) {
            dto.lastExecution = latest.finishedAt != null ? latest.finishedAt : latest.startedAt;
            dto.lastSyncMode = latest.syncMode;
            if ("COMPLETED".equals(latest.status) || "COMPLETED_WITH_WARNINGS".equals(latest.status)) {
                dto.lastSuccessfulExecution = latest.finishedAt != null ? latest.finishedAt : latest.startedAt;
            }
            dto.processed = latest.processed;
            dto.errors = latest.errors;
        }
        SyncModePolicyDto policy = syncModeAdvisor.policy(entity);
        dto.lastFullSyncAt = policy.lastFullSyncAt;
        dto.lastSuccessfulFullSyncAt = policy.lastSuccessfulFullSyncAt;
        dto.supportedSyncModes = policy.supportedSyncModes;
        dto.availableSyncModes = policy.availableSyncModes;
        dto.hasValidBaseline = policy.hasValidBaseline;
        dto.recommendedSyncMode = policy.recommendedSyncMode;
    }

    private static IntegrationStatusDto notConfigured(String code) {
        return configured(code, "NOT_CONFIGURED");
    }

    private static Map<String, SyncRunDto> indexByEntity(List<SyncRunDto> runs) {
        Map<String, SyncRunDto> byEntity = new LinkedHashMap<>();
        if (runs == null) {
            return byEntity;
        }
        for (SyncRunDto run : runs) {
            if (run != null && run.entity != null) {
                byEntity.put(run.entity, run);
            }
        }
        return byEntity;
    }
}
