package cl.uss.ca.api;

import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.api.dto.PersonDetailDto;
import cl.uss.ca.api.dto.PersonFiltersDto;
import cl.uss.ca.api.dto.PersonScopusIdentifiersDto;
import cl.uss.ca.api.dto.PersonOrcidSyncResultDto;
import cl.uss.ca.api.dto.PersonOrcidWorksDto;
import cl.uss.ca.api.dto.PersonStatsDto;
import cl.uss.ca.api.dto.PersonSummaryDto;
import cl.uss.ca.api.dto.PureCorrectionResultDto;
import cl.uss.ca.service.OrcidSyncService;
import cl.uss.ca.service.PersonService;
import cl.uss.ca.service.PureCorrectionService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import cl.uss.ca.service.ExcelExportService;
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

@Path("/people")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class PersonResource {

    @Inject
    PersonService personService;

    @Inject
    ExcelExportService excelExportService;

    @Inject
    PureCorrectionService pureCorrectionService;

    @Inject
    OrcidSyncService orcidSyncService;

    @Inject
    cl.uss.ca.service.PureMetricsService metricsService;

    @GET
    public PageResponse<PersonSummaryDto> list(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("25") int size,
            @QueryParam("search") String search,
            @QueryParam("cargo") String cargo,
            @QueryParam("population") String population,
            @QueryParam("orcidStatus") String orcidStatus,
            @QueryParam("hasOrcid") Boolean hasOrcid,
            @QueryParam("scopusStatus") String scopusStatus,
            @QueryParam("hasScopus") Boolean hasScopus,
            @QueryParam("scopusCount") String scopusCount,
            @QueryParam("minDistinctScopusCount") String minDistinctScopusCount,
            @QueryParam("pureStatus") String pureStatus,
            @QueryParam("sort") String sort,
            @QueryParam("direction") String direction
    ) {
        return personService.list(
                page,
                size,
                search,
                cargo,
                population,
                orcidStatus,
                hasOrcid,
                scopusStatus,
                hasScopus,
                scopusCount,
                minDistinctScopusCount,
                pureStatus,
                sort,
                direction
        );
    }

    @GET
    @Path("/export.xlsx")
    @Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public Response export(
            @QueryParam("search") String search,
            @QueryParam("cargo") String cargo,
            @QueryParam("population") String population,
            @QueryParam("orcidStatus") String orcidStatus,
            @QueryParam("hasOrcid") Boolean hasOrcid,
            @QueryParam("scopusStatus") String scopusStatus,
            @QueryParam("hasScopus") Boolean hasScopus,
            @QueryParam("scopusCount") String scopusCount,
            @QueryParam("minDistinctScopusCount") String minDistinctScopusCount,
            @QueryParam("pureStatus") String pureStatus,
            @QueryParam("sort") String sort,
            @QueryParam("direction") String direction
    ) {
        return excelExportService.exportPeople(
                personService.export(
                        search, cargo, population, orcidStatus, hasOrcid, scopusStatus, hasScopus,
                        scopusCount, minDistinctScopusCount, pureStatus, sort, direction
                )
        );
    }

    @GET
    @Path("/filters")
    public PersonFiltersDto filters() {
        return personService.positions();
    }

    @GET
    @Path("/positions")
    public PersonFiltersDto positions() {
        return personService.positions();
    }

    @GET
    @Path("/stats")
    public PersonStatsDto stats(
            @QueryParam("search") String search,
            @QueryParam("cargo") String cargo,
            @QueryParam("population") String population,
            @QueryParam("orcidStatus") String orcidStatus,
            @QueryParam("hasOrcid") Boolean hasOrcid,
            @QueryParam("scopusStatus") String scopusStatus,
            @QueryParam("hasScopus") Boolean hasScopus,
            @QueryParam("scopusCount") String scopusCount,
            @QueryParam("minDistinctScopusCount") String minDistinctScopusCount,
            @QueryParam("pureStatus") String pureStatus
    ) {
        return personService.stats(
                search,
                cargo,
                population,
                orcidStatus,
                hasOrcid,
                scopusStatus,
                hasScopus,
                scopusCount,
                minDistinctScopusCount,
                pureStatus
        );
    }

    @GET
    @Path("/{id:\\d+}/scopus-identifiers")
    public PersonScopusIdentifiersDto scopusIdentifiers(@PathParam("id") long id) {
        return personService.scopusIdentifiers(id);
    }

    @DELETE
    @Path("/{id:\\d+}/scopus-identifiers/{identifierPureId}")
    public PureCorrectionResultDto removeScopusIdentifier(
            @PathParam("id") long id,
            @PathParam("identifierPureId") long identifierPureId,
            @QueryParam("identifierValue") String identifierValue
    ) {
        return pureCorrectionService.removeScopusIdentifier(id, identifierPureId, identifierValue);
    }

    @POST
    @Path("/{id:\\d+}/scopus-identifiers/{identifierPureId}/reconcile-local")
    public PureCorrectionResultDto reconcileLocalScopusIdentifier(
            @PathParam("id") long id,
            @PathParam("identifierPureId") long identifierPureId
    ) {
        return pureCorrectionService.reconcileLocalScopusIdentifier(id, identifierPureId);
    }

    @GET
    @Path("/{id:\\d+}/metrics")
    public cl.uss.ca.api.dto.PersonMetricsDto metrics(@PathParam("id") long id) {
        return metricsService.personMetrics(id);
    }

    @GET
    @Path("/{id:\\d+}")
    public PersonDetailDto get(@PathParam("id") long id) {
        return personService.getById(id);
    }

    @GET
    @Path("/{id:\\d+}/orcid-works")
    public PersonOrcidWorksDto orcidWorks(@PathParam("id") long id) {
        return orcidSyncService.works(id);
    }

    @POST
    @Path("/{id:\\d+}/orcid/sync")
    public PersonOrcidSyncResultDto syncOrcid(@PathParam("id") long id) {
        return orcidSyncService.syncPerson(id);
    }
}
