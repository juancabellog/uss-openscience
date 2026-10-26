package cl.uss.ca.api;

import cl.uss.ca.api.dto.CrossrefWorkSummaryDto;
import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.api.dto.ResearchOutputDetailDto;
import cl.uss.ca.api.dto.ResearchOutputDoiVersionsDto;
import cl.uss.ca.api.dto.ResearchOutputFiltersDto;
import cl.uss.ca.api.dto.ResearchOutputStatsDto;
import cl.uss.ca.api.dto.ResearchOutputSummaryDto;
import cl.uss.ca.api.dto.ResearchOutputTypeDto;
import cl.uss.ca.api.dto.PureCorrectionResultDto;
import cl.uss.ca.service.ExcelExportService;
import cl.uss.ca.service.PureCorrectionService;
import cl.uss.ca.service.ResearchOutputService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/research-output")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class ResearchOutputResource {

    @Inject
    ResearchOutputService researchOutputService;

    @Inject
    ExcelExportService excelExportService;

    @Inject
    PureCorrectionService pureCorrectionService;

    @Inject
    cl.uss.ca.service.CrossrefService crossrefService;

    @Inject
    cl.uss.ca.service.DspaceService dspaceService;

    @Inject
    cl.uss.ca.service.PureMetricsService metricsService;

    @GET
    public PageResponse<ResearchOutputSummaryDto> list(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("25") int size,
            @QueryParam("search") String search,
            @QueryParam("type") String type,
            @QueryParam("year") Integer year,
            @QueryParam("hasDoi") Boolean hasDoi,
            @QueryParam("doiStatus") String doiStatus,
            @QueryParam("workflow") String workflow,
            @QueryParam("pureStatus") String pureStatus,
            @QueryParam("fwciFrom") java.math.BigDecimal fwciFrom,
            @QueryParam("fwciTo") java.math.BigDecimal fwciTo,
            @QueryParam("sort") String sort,
            @QueryParam("direction") String direction
    ) {
        return researchOutputService.list(
                page, size, search, type, year, hasDoi, doiStatus, workflow, pureStatus, fwciFrom, fwciTo, sort, direction
        );
    }

    @GET
    @Path("/export.xlsx")
    @Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public Response exportXlsx(
            @QueryParam("search") String search,
            @QueryParam("type") String type,
            @QueryParam("year") Integer year,
            @QueryParam("hasDoi") Boolean hasDoi,
            @QueryParam("doiStatus") String doiStatus,
            @QueryParam("workflow") String workflow,
            @QueryParam("pureStatus") String pureStatus,
            @QueryParam("fwciFrom") java.math.BigDecimal fwciFrom,
            @QueryParam("fwciTo") java.math.BigDecimal fwciTo,
            @QueryParam("sort") String sort,
            @QueryParam("direction") String direction
    ) {
        return excelExportService.exportResearchOutputs(
                researchOutputService.export(search, type, year, hasDoi, doiStatus, workflow, pureStatus, fwciFrom, fwciTo, sort, direction)
        );
    }

    @GET
    @Path("/filters")
    public ResearchOutputFiltersDto filters() {
        return researchOutputService.filters();
    }

    @GET
    @Path("/stats")
    public ResearchOutputStatsDto stats(
            @QueryParam("search") String search,
            @QueryParam("type") String type,
            @QueryParam("year") Integer year,
            @QueryParam("hasDoi") Boolean hasDoi,
            @QueryParam("doiStatus") String doiStatus,
            @QueryParam("workflow") String workflow,
            @QueryParam("pureStatus") String pureStatus,
            @QueryParam("fwciFrom") java.math.BigDecimal fwciFrom,
            @QueryParam("fwciTo") java.math.BigDecimal fwciTo
    ) {
        return researchOutputService.stats(search, type, year, hasDoi, doiStatus, workflow, pureStatus, fwciFrom, fwciTo);
    }

    @GET
    @Path("/types")
    public List<ResearchOutputTypeDto> types() {
        return researchOutputService.filters().types;
    }

    @GET
    @Path("/{id:\\d+}/crossref")
    public List<CrossrefWorkSummaryDto> crossref(@PathParam("id") long id) {
        return crossrefService.byResearchOutput(id);
    }

    @GET
    @Path("/{id:\\d+}/dspace")
    public List<cl.uss.ca.api.dto.DspaceItemSummaryDto> dspace(@PathParam("id") long id) {
        return dspaceService.byResearchOutput(id);
    }

    @GET
    @Path("/{id:\\d+}/doi-versions")
    public ResearchOutputDoiVersionsDto doiVersions(@PathParam("id") long id) {
        return researchOutputService.doiVersions(id);
    }

    @DELETE
    @Path("/{id:\\d+}/doi-versions/{versionPureId}")
    public PureCorrectionResultDto removeDoiVersion(
            @PathParam("id") long id,
            @PathParam("versionPureId") long versionPureId,
            @QueryParam("doi") String doi
    ) {
        return pureCorrectionService.removeDoiVersion(id, versionPureId, doi);
    }

    @POST
    @Path("/{id:\\d+}/doi-versions/{versionPureId}/reconcile-local")
    public PureCorrectionResultDto reconcileLocalDoiVersion(
            @PathParam("id") long id,
            @PathParam("versionPureId") long versionPureId
    ) {
        return pureCorrectionService.reconcileLocalDoiVersion(id, versionPureId);
    }

    @GET
    @Path("/{id:\\d+}/metrics")
    public cl.uss.ca.api.dto.ResearchOutputMetricsDto metrics(@PathParam("id") long id) {
        return metricsService.researchOutputMetrics(id);
    }

    @GET
    @Path("/{id:\\d+}")
    public ResearchOutputDetailDto get(@PathParam("id") long id) {
        return researchOutputService.getById(id);
    }
}
