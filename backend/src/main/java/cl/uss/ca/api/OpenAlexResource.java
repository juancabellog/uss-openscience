package cl.uss.ca.api;

import cl.uss.ca.api.dto.OpenAlexAuthorQuery;
import cl.uss.ca.api.dto.OpenAlexAuthorSummaryDto;
import cl.uss.ca.api.dto.OpenAlexFiltersDto;
import cl.uss.ca.api.dto.OpenAlexMatchDetailDto;
import cl.uss.ca.api.dto.OpenAlexMatchQuery;
import cl.uss.ca.api.dto.OpenAlexMatchSummaryDto;
import cl.uss.ca.api.dto.OpenAlexStatsDto;
import cl.uss.ca.api.dto.OpenAlexWorkDetailDto;
import cl.uss.ca.api.dto.OpenAlexWorkQuery;
import cl.uss.ca.api.dto.OpenAlexWorkSummaryDto;
import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.api.dto.PureCorrectionResultDto;
import cl.uss.ca.api.dto.SyncRunDto;
import cl.uss.ca.repository.OpenAlexRepository;
import cl.uss.ca.security.CurrentUserService;
import cl.uss.ca.service.ExcelExportService;
import cl.uss.ca.service.OpenAlexService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
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

@Path("/openalex")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class OpenAlexResource {

    @Inject
    OpenAlexService openAlexService;

    @Inject
    OpenAlexRepository openAlexRepository;

    @Inject
    ExcelExportService excelExportService;

    @Inject
    CurrentUserService currentUserService;

    @GET
    @Path("/stats")
    public OpenAlexStatsDto stats() {
        return openAlexService.stats();
    }

    @GET
    @Path("/history")
    public List<SyncRunDto> history() {
        return openAlexService.history();
    }

    @GET
    @Path("/filters")
    public OpenAlexFiltersDto filters() {
        return openAlexService.filters();
    }

    @GET
    @Path("/works")
    public PageResponse<OpenAlexWorkSummaryDto> works(
            @QueryParam("search") String search,
            @QueryParam("status") String status,
            @QueryParam("workType") String workType,
            @QueryParam("sort") @DefaultValue("syncedAt") String sort,
            @QueryParam("direction") @DefaultValue("desc") String direction,
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("size") @DefaultValue("25") int size
    ) {
        OpenAlexWorkQuery query = new OpenAlexWorkQuery();
        query.search = search;
        query.status = status;
        query.workType = workType;
        query.sort = sort;
        query.direction = direction;
        query.page = page;
        query.pageSize = size;
        return openAlexService.listWorks(query);
    }

    @GET
    @Path("/works/{id}")
    public OpenAlexWorkDetailDto work(@PathParam("id") long id) {
        return openAlexService.workDetail(id);
    }

    @GET
    @Path("/authors")
    public PageResponse<OpenAlexAuthorSummaryDto> authors(
            @QueryParam("search") String search,
            @QueryParam("sort") @DefaultValue("syncedAt") String sort,
            @QueryParam("direction") @DefaultValue("desc") String direction,
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("size") @DefaultValue("25") int size
    ) {
        OpenAlexAuthorQuery query = new OpenAlexAuthorQuery();
        query.search = search;
        query.sort = sort;
        query.direction = direction;
        query.page = page;
        query.pageSize = size;
        return openAlexService.listAuthors(query);
    }

    @GET
    @Path("/matches")
    public PageResponse<OpenAlexMatchSummaryDto> matches(
            @QueryParam("search") String search,
            @QueryParam("status") String status,
            @QueryParam("position") String position,
            @QueryParam("hasOrcid") Boolean hasOrcid,
            @QueryParam("ussAffiliation") Boolean ussAffiliation,
            @QueryParam("minWorks") Integer minWorks,
            @QueryParam("sort") @DefaultValue("works") String sort,
            @QueryParam("direction") @DefaultValue("desc") String direction,
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("size") @DefaultValue("25") int size
    ) {
        return openAlexService.listMatches(matchQuery(
                search, status, position, hasOrcid, ussAffiliation, minWorks, sort, direction, page, size
        ));
    }

    @GET
    @Path("/matches/export.xlsx")
    @Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public Response export(
            @QueryParam("search") String search,
            @QueryParam("status") String status,
            @QueryParam("position") String position,
            @QueryParam("hasOrcid") Boolean hasOrcid,
            @QueryParam("ussAffiliation") Boolean ussAffiliation,
            @QueryParam("minWorks") Integer minWorks,
            @QueryParam("sort") @DefaultValue("works") String sort,
            @QueryParam("direction") @DefaultValue("desc") String direction
    ) {
        OpenAlexMatchQuery query = matchQuery(
                search, status, position, hasOrcid, ussAffiliation, minWorks, sort, direction, 1, 20000
        );
        return excelExportService.exportOpenAlex(openAlexRepository.listMatchesForExport(query));
    }

    @GET
    @Path("/matches/{id}")
    public OpenAlexMatchDetailDto match(
            @PathParam("id") long id,
            @QueryParam("workPage") @DefaultValue("1") int workPage,
            @QueryParam("workSize") @DefaultValue("25") int workSize,
            @QueryParam("workSearch") String workSearch
    ) {
        return openAlexService.matchDetail(id, workPage, workSize, workSearch);
    }

    @POST
    @Path("/matches/{id}/confirm")
    public OpenAlexMatchDetailDto confirm(@PathParam("id") long id) {
        return openAlexService.confirm(id, currentUserService.current().id);
    }

    @POST
    @Path("/matches/{id}/reject")
    public OpenAlexMatchDetailDto reject(@PathParam("id") long id) {
        return openAlexService.reject(id, currentUserService.current().id);
    }

    @POST
    @Path("/matches/{id}/sync-orcid")
    public PureCorrectionResultDto syncOrcid(@PathParam("id") long id) {
        return openAlexService.syncOrcid(id);
    }

    private static OpenAlexMatchQuery matchQuery(
            String search,
            String status,
            String position,
            Boolean hasOrcid,
            Boolean ussAffiliation,
            Integer minWorks,
            String sort,
            String direction,
            int page,
            int size
    ) {
        OpenAlexMatchQuery query = new OpenAlexMatchQuery();
        query.search = search;
        query.status = status;
        query.position = position;
        query.hasOrcid = hasOrcid;
        query.ussAffiliation = ussAffiliation;
        query.minWorks = minWorks;
        query.sort = sort;
        query.direction = direction;
        query.page = page;
        query.pageSize = size;
        return query;
    }
}
