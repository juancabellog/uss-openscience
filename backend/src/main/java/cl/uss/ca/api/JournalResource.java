package cl.uss.ca.api;

import cl.uss.ca.api.dto.JournalDetailDto;
import cl.uss.ca.api.dto.JournalFiltersDto;
import cl.uss.ca.api.dto.JournalIssnsDto;
import cl.uss.ca.api.dto.JournalStatsDto;
import cl.uss.ca.api.dto.JournalSummaryDto;
import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.api.dto.JournalMetricsDto;
import cl.uss.ca.service.ExcelExportService;
import cl.uss.ca.service.JournalService;
import cl.uss.ca.service.PureMetricsService;
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

@Path("/journals")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class JournalResource {

    @Inject
    JournalService journalService;

    @Inject
    ExcelExportService excelExportService;

    @Inject
    PureMetricsService metricsService;

    @GET
    public PageResponse<JournalSummaryDto> list(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("25") int size,
            @QueryParam("search") String search,
            @QueryParam("typeUri") String typeUri,
            @QueryParam("workflowStep") String workflowStep,
            @QueryParam("issnCount") String issnCount,
            @QueryParam("publications") String publications,
            @QueryParam("citescore") String citescore,
            @QueryParam("quartile") String quartile,
            @QueryParam("sort") String sort,
            @QueryParam("direction") String direction
    ) {
        return journalService.list(
                page, size, search, typeUri, workflowStep, issnCount, publications, citescore, quartile, sort, direction
        );
    }

    @GET
    @Path("/export.xlsx")
    @Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public Response export(
            @QueryParam("search") String search,
            @QueryParam("typeUri") String typeUri,
            @QueryParam("workflowStep") String workflowStep,
            @QueryParam("issnCount") String issnCount,
            @QueryParam("publications") String publications,
            @QueryParam("citescore") String citescore,
            @QueryParam("quartile") String quartile,
            @QueryParam("sort") String sort,
            @QueryParam("direction") String direction
    ) {
        return excelExportService.exportJournals(
                journalService.export(search, typeUri, workflowStep, issnCount, publications, citescore, quartile, sort, direction)
        );
    }

    @GET
    @Path("/filters")
    public JournalFiltersDto filters() {
        return journalService.filters();
    }

    @GET
    @Path("/stats")
    public JournalStatsDto stats(
            @QueryParam("search") String search,
            @QueryParam("typeUri") String typeUri,
            @QueryParam("workflowStep") String workflowStep,
            @QueryParam("issnCount") String issnCount,
            @QueryParam("publications") String publications,
            @QueryParam("citescore") String citescore,
            @QueryParam("quartile") String quartile
    ) {
        return journalService.stats(search, typeUri, workflowStep, issnCount, publications, citescore, quartile);
    }

    @GET
    @Path("/{id:\\d+}/issns")
    public JournalIssnsDto issns(@PathParam("id") long id) {
        return journalService.issns(id);
    }

    @GET
    @Path("/{id:\\d+}/metrics")
    public JournalMetricsDto metrics(
            @PathParam("id") long id,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("50") int size
    ) {
        return metricsService.journalMetrics(id, page, size);
    }

    @GET
    @Path("/{id:\\d+}")
    public JournalDetailDto get(@PathParam("id") long id) {
        return journalService.getById(id);
    }
}
