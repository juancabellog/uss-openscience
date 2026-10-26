package cl.uss.ca.service;

import cl.uss.ca.api.dto.DspaceBitstreamDto;
import cl.uss.ca.api.dto.DspaceCollectionListDto;
import cl.uss.ca.api.dto.DspaceCommunityListDto;
import cl.uss.ca.api.dto.DspaceItemDetailDto;
import cl.uss.ca.api.dto.DspaceItemListDto;
import cl.uss.ca.api.dto.DspaceItemQuery;
import cl.uss.ca.api.dto.DspaceItemSummaryDto;
import cl.uss.ca.api.dto.DspaceStatsDto;
import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.api.dto.SyncErrorDto;
import cl.uss.ca.api.dto.SyncRunDto;
import cl.uss.ca.domain.DspaceAccessComparison;
import cl.uss.ca.domain.DspaceListSupport;
import cl.uss.ca.exception.ForbiddenException;
import cl.uss.ca.exception.NotFoundException;
import cl.uss.ca.exception.ValidationException;
import cl.uss.ca.external.dspace.DspaceBitstreamContent;
import cl.uss.ca.external.dspace.DspaceClient;
import cl.uss.ca.repository.DspaceRepository;
import cl.uss.ca.repository.SyncRunRepository;
import cl.uss.ca.sync.SyncErrorRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class DspaceService {

    @Inject
    DspaceClient dspaceClient;

    @Inject
    DspaceRepository dspaceRepository;

    @Inject
    ExcelExportService excelExportService;

    @Inject
    SyncRunRepository syncRunRepository;

    @Inject
    SyncErrorRepository syncErrorRepository;

    public void assertTables() {
        dspaceRepository.assertAvailable();
    }

    public DspaceStatsDto stats() {
        return decorateStats(dspaceRepository.counts());
    }

    public DspaceStatsDto stats(DspaceItemQuery query) {
        return decorateStats(dspaceRepository.counts(query == null ? new DspaceItemQuery() : query));
    }

    private DspaceStatsDto decorateStats(DspaceStatsDto dto) {
        dto.configured = dspaceClient.isConfigured();
        dto.enabled = dspaceClient.isEnabled();
        dto.tablesAvailable = dspaceRepository.available();
        if (dto.dspaceVersion == null) {
            dto.dspaceVersion = dspaceRepository.findVersion();
        }
        SyncRunDto latest = syncRunRepository.findLatest(PureSyncService.DSPACE);
        if (latest != null) {
            dto.lastProcessed = latest.processed;
            dto.lastErrors = latest.errors;
            dto.lastExecution = latest.finishedAt != null ? latest.finishedAt : latest.startedAt;
            dto.lastStatus = latest.status;
        }
        return dto;
    }

    public List<DspaceItemSummaryDto> byResearchOutput(long researchOutputId) {
        if (!dspaceRepository.available()) {
            return List.of();
        }
        return dspaceRepository.byResearchOutput(researchOutputId);
    }

    public PageResponse<DspaceItemListDto> listItems(DspaceItemQuery query) {
        assertTables();
        return dspaceRepository.listItems(query == null ? new DspaceItemQuery() : query);
    }

    public Response exportItems(DspaceItemQuery query) {
        assertTables();
        DspaceItemQuery safe = query == null ? new DspaceItemQuery() : query;
        List<DspaceItemListDto> rows = dspaceRepository.exportItems(safe);
        if (rows.isEmpty()) {
            throw new ValidationException("EXPORT_EMPTY", "There are no DSpace items to export.");
        }
        return excelExportService.exportDspace(rows, exportFilterRows(safe, rows.size()));
    }

    public DspaceItemDetailDto itemDetail(String uuid) {
        assertTables();
        DspaceItemDetailDto dto = dspaceRepository.findItemDetail(uuid);
        if (dto == null) {
            throw new NotFoundException("DSPACE_ITEM_NOT_FOUND", "DSpace item was not found");
        }
        return dto;
    }

    public Response downloadBitstream(String itemUuid, String bitstreamUuid) {
        assertTables();
        DspaceBitstreamDto bitstream = dspaceRepository.findBitstream(itemUuid, bitstreamUuid);
        if (bitstream == null) {
            throw new NotFoundException("DSPACE_BITSTREAM_NOT_FOUND", "DSpace bitstream was not found");
        }
        if (!DspaceAccessComparison.isPublicDownload(bitstream.publicDownload, bitstream.accessStatus)) {
            throw new ForbiddenException(
                    "DSPACE_BITSTREAM_RESTRICTED",
                    "This DSpace bitstream is not publicly downloadable"
            );
        }
        dspaceClient.assertConfigured();
        DspaceBitstreamContent content = dspaceClient.fetchBitstreamContent(bitstream.uuid);
        StreamingOutput body = output -> {
            try (content) {
                if (content.stream != null) {
                    content.stream.transferTo(output);
                }
                output.flush();
            } catch (ForbiddenException | ValidationException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new ValidationException("DSPACE_TEMPORARY_ERROR", "DSpace content request failed", ex);
            }
        };
        Response.ResponseBuilder builder = Response.ok(body)
                .type(contentType(content.contentType))
                .header("Content-Disposition", "attachment; filename=\"" + attachmentFilename(bitstream.name) + "\"")
                .header("Access-Control-Expose-Headers", "Content-Disposition");
        if (content.contentLength != null && content.contentLength >= 0) {
            builder.header("Content-Length", content.contentLength);
        }
        return builder.build();
    }

    private static String contentType(String value) {
        return value == null || value.isBlank() ? "application/octet-stream" : value;
    }

    private static List<String[]> exportFilterRows(DspaceItemQuery query, int exported) {
        List<String[]> rows = new ArrayList<>();
        boolean selection = query.uuids != null && !query.uuids.isEmpty();
        rows.add(new String[] {"Modo exportación", selection ? "Selección manual" : "Resultado filtrado"});
        rows.add(new String[] {"Búsqueda", blank(query.search)});
        rows.add(new String[] {"Estado", blank(query.outcome)});
        rows.add(new String[] {"Acceso DSpace", blank(query.accessStatus)});
        rows.add(new String[] {"Vinculación PURE", blank(query.matchStatus)});
        rows.add(new String[] {"Método de match", blank(query.matchMethod)});
        rows.add(new String[] {"Colección", blank(query.collectionUuid)});
        rows.add(new String[] {"Comunidad", blank(query.communityUuid)});
        rows.add(new String[] {"DOI", blank(query.doi)});
        rows.add(new String[] {"DOI compartido", Boolean.TRUE.equals(query.sharedDoi) ? "Sí" : "Todos"});
        String hasFile = DspaceListSupport.hasFileFilter(query.hasFile);
        rows.add(new String[] {"Archivo", hasFile == null ? "Todos" : ("YES".equals(hasFile) ? "Con archivo" : "Sin archivo")});
        String comparison = DspaceListSupport.accessComparisonFilter(query.accessComparison);
        rows.add(new String[] {"Comparación PURE", comparison == null ? "Todos" : comparison});
        rows.add(new String[] {"Orden", (query.sort == null ? "syncedAt" : query.sort) + " "
                + (query.direction == null ? "desc" : query.direction)});
        rows.add(new String[] {"Registros exportados", String.valueOf(exported)});
        rows.add(new String[] {
                "Fecha exportación",
                ZonedDateTime.now(ZoneId.of("America/Santiago"))
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        });
        return rows;
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? "Todos" : value.trim();
    }

    private static String attachmentFilename(String name) {
        if (name == null || name.isBlank()) {
            return "bitstream";
        }
        return name.replaceAll("[\\r\\n\"\\\\]", "_");
    }

    public PageResponse<DspaceCollectionListDto> listCollections(
            int page,
            int size,
            String search,
            String sort,
            String direction
    ) {
        assertTables();
        return dspaceRepository.listCollections(page, size, search, sort, direction);
    }

    public PageResponse<DspaceCommunityListDto> listCommunities(
            int page,
            int size,
            String search,
            String sort,
            String direction
    ) {
        assertTables();
        return dspaceRepository.listCommunities(page, size, search, sort, direction);
    }

    public PageResponse<SyncErrorDto> incidents(String stage, int page, int size) {
        return syncErrorRepository.findByEntity(PureSyncService.DSPACE, stage, page, size);
    }

    public List<SyncRunDto> history() {
        return syncRunRepository.findRecent(PureSyncService.DSPACE, 25);
    }
}
