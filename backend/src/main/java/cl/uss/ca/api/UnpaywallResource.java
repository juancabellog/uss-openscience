package cl.uss.ca.api;

import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.api.dto.UnpaywallFiltersDto;
import cl.uss.ca.api.dto.UnpaywallQuery;
import cl.uss.ca.api.dto.UnpaywallStatsDto;
import cl.uss.ca.api.dto.UnpaywallWorkDetailDto;
import cl.uss.ca.api.dto.UnpaywallWorkSummaryDto;
import cl.uss.ca.repository.UnpaywallRepository;
import cl.uss.ca.service.ExcelExportService;
import cl.uss.ca.service.UnpaywallService;
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

@Path("/unpaywall")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class UnpaywallResource {

    @Inject
    UnpaywallService unpaywallService;

    @Inject
    UnpaywallRepository unpaywallRepository;

    @Inject
    ExcelExportService excelExportService;

    @GET
    @Path("/stats")
    public UnpaywallStatsDto stats() {
        return unpaywallService.stats();
    }

    @GET
    @Path("/filters")
    public UnpaywallFiltersDto filters() {
        return unpaywallService.filters();
    }

    @GET
    @Path("/works")
    public PageResponse<UnpaywallWorkSummaryDto> works(
            @QueryParam("search") String search,
            @QueryParam("oaStatus") String oaStatus,
            @QueryParam("hostType") String hostType,
            @QueryParam("license") String license,
            @QueryParam("version") String version,
            @QueryParam("isOa") Boolean isOa,
            @QueryParam("repositoryCopy") Boolean repositoryCopy,
            @QueryParam("outcome") String outcome,
            @QueryParam("sort") @DefaultValue("retrievedAt") String sort,
            @QueryParam("direction") @DefaultValue("desc") String direction,
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("size") @DefaultValue("25") int size
    ) {
        return unpaywallService.list(query(
                search, oaStatus, hostType, license, version, isOa, repositoryCopy, outcome, sort, direction, page, size
        ));
    }

    @GET
    @Path("/works/export.xlsx")
    @Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public Response export(
            @QueryParam("search") String search,
            @QueryParam("oaStatus") String oaStatus,
            @QueryParam("hostType") String hostType,
            @QueryParam("license") String license,
            @QueryParam("version") String version,
            @QueryParam("isOa") Boolean isOa,
            @QueryParam("repositoryCopy") Boolean repositoryCopy,
            @QueryParam("outcome") String outcome,
            @QueryParam("sort") @DefaultValue("retrievedAt") String sort,
            @QueryParam("direction") @DefaultValue("desc") String direction
    ) {
        UnpaywallQuery query = query(
                search, oaStatus, hostType, license, version, isOa, repositoryCopy, outcome, sort, direction, 1, 20000
        );
        return excelExportService.exportUnpaywall(unpaywallRepository.listForExport(query));
    }

    @GET
    @Path("/works/{id}")
    public UnpaywallWorkDetailDto detail(@PathParam("id") long id) {
        return unpaywallService.detail(id);
    }

    private static UnpaywallQuery query(
            String search,
            String oaStatus,
            String hostType,
            String license,
            String version,
            Boolean isOa,
            Boolean repositoryCopy,
            String outcome,
            String sort,
            String direction,
            int page,
            int size
    ) {
        UnpaywallQuery query = new UnpaywallQuery();
        query.search = search;
        query.oaStatus = oaStatus;
        query.hostType = hostType;
        query.license = license;
        query.version = version;
        query.isOa = isOa;
        query.repositoryCopy = repositoryCopy;
        query.outcome = outcome;
        query.sort = sort;
        query.direction = direction;
        query.page = page;
        query.pageSize = size;
        return query;
    }
}
