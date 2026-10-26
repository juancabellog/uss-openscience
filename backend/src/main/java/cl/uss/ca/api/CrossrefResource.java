package cl.uss.ca.api;

import cl.uss.ca.api.dto.CrossrefFiltersDto;
import cl.uss.ca.api.dto.CrossrefQuery;
import cl.uss.ca.api.dto.CrossrefStatsDto;
import cl.uss.ca.api.dto.CrossrefWorkDetailDto;
import cl.uss.ca.api.dto.CrossrefWorkSummaryDto;
import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.repository.CrossrefRepository;
import cl.uss.ca.service.CrossrefService;
import cl.uss.ca.service.ExcelExportService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/crossref")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class CrossrefResource {

    @Inject
    CrossrefService crossrefService;

    @Inject
    CrossrefRepository crossrefRepository;

    @Inject
    ExcelExportService excelExportService;

    @GET
    @Path("/stats")
    public CrossrefStatsDto stats() {
        return crossrefService.stats();
    }

    @GET
    @Path("/filters")
    public CrossrefFiltersDto filters() {
        return crossrefService.filters();
    }

    @GET
    @Path("/works")
    public PageResponse<CrossrefWorkSummaryDto> works(
            @QueryParam("search") String search,
            @QueryParam("status") String status,
            @QueryParam("workType") String workType,
            @QueryParam("hasOrcid") Boolean hasOrcid,
            @QueryParam("hasAffiliation") Boolean hasAffiliation,
            @QueryParam("hasRor") Boolean hasRor,
            @QueryParam("hasFunding") Boolean hasFunding,
            @QueryParam("hasLicense") Boolean hasLicense,
            @QueryParam("hasUpdate") Boolean hasUpdate,
            @QueryParam("comparison") String comparison,
            @QueryParam("sort") @DefaultValue("syncedAt") String sort,
            @QueryParam("direction") @DefaultValue("desc") String direction,
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("size") @DefaultValue("25") int size
    ) {
        return crossrefService.list(query(
                search, status, workType, hasOrcid, hasAffiliation, hasRor, hasFunding, hasLicense, hasUpdate,
                comparison, sort, direction, page, size
        ));
    }

    @GET
    @Path("/works/export.xlsx")
    @Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public Response export(
            @QueryParam("search") String search,
            @QueryParam("status") String status,
            @QueryParam("workType") String workType,
            @QueryParam("hasOrcid") Boolean hasOrcid,
            @QueryParam("hasAffiliation") Boolean hasAffiliation,
            @QueryParam("hasRor") Boolean hasRor,
            @QueryParam("hasFunding") Boolean hasFunding,
            @QueryParam("hasLicense") Boolean hasLicense,
            @QueryParam("hasUpdate") Boolean hasUpdate,
            @QueryParam("comparison") String comparison,
            @QueryParam("sort") @DefaultValue("syncedAt") String sort,
            @QueryParam("direction") @DefaultValue("desc") String direction
    ) {
        CrossrefQuery query = query(
                search, status, workType, hasOrcid, hasAffiliation, hasRor, hasFunding, hasLicense, hasUpdate,
                comparison, sort, direction, 1, 20000
        );
        return excelExportService.exportCrossref(crossrefRepository.listForExport(query));
    }

    @GET
    @Path("/works/{id}")
    public CrossrefWorkDetailDto detail(@PathParam("id") long id) {
        return crossrefService.detail(id);
    }

    @GET
    @Path("/outputs/{researchOutputId}")
    public List<CrossrefWorkSummaryDto> byOutput(@PathParam("researchOutputId") long researchOutputId) {
        return crossrefService.byResearchOutput(researchOutputId);
    }

    private static CrossrefQuery query(
            String search,
            String status,
            String workType,
            Boolean hasOrcid,
            Boolean hasAffiliation,
            Boolean hasRor,
            Boolean hasFunding,
            Boolean hasLicense,
            Boolean hasUpdate,
            String comparison,
            String sort,
            String direction,
            int page,
            int size
    ) {
        CrossrefQuery query = new CrossrefQuery();
        query.search = search;
        query.status = status;
        query.workType = workType;
        query.hasOrcid = hasOrcid;
        query.hasAffiliation = hasAffiliation;
        query.hasRor = hasRor;
        query.hasFunding = hasFunding;
        query.hasLicense = hasLicense;
        query.hasUpdate = hasUpdate;
        query.comparison = comparison;
        query.sort = sort;
        query.direction = direction;
        query.page = page;
        query.pageSize = size;
        return query;
    }
}
