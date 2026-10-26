package cl.uss.ca.api;

import cl.uss.ca.api.dto.OrganizationDetailDto;
import cl.uss.ca.api.dto.OrganizationFiltersDto;
import cl.uss.ca.api.dto.OrganizationPersonDto;
import cl.uss.ca.api.dto.OrganizationProjectDto;
import cl.uss.ca.api.dto.OrganizationStatsDto;
import cl.uss.ca.api.dto.OrganizationSummaryDto;
import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.service.ExcelExportService;
import cl.uss.ca.service.OrganizationService;
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

@Path("/organizations")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class OrganizationResource {

    @Inject
    OrganizationService organizationService;

    @Inject
    ExcelExportService excelExportService;

    @GET
    public PageResponse<OrganizationSummaryDto> list(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("25") int size,
            @QueryParam("search") String search,
            @QueryParam("typeUri") String typeUri,
            @QueryParam("presence") String presence,
            @QueryParam("hasParent") String hasParent,
            @QueryParam("sort") String sort,
            @QueryParam("direction") String direction
    ) {
        return organizationService.list(page, size, search, typeUri, presence, hasParent, sort, direction);
    }

    @GET
    @Path("/export.xlsx")
    @Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public Response export(
            @QueryParam("search") String search,
            @QueryParam("typeUri") String typeUri,
            @QueryParam("presence") String presence,
            @QueryParam("hasParent") String hasParent,
            @QueryParam("sort") String sort,
            @QueryParam("direction") String direction
    ) {
        return excelExportService.exportOrganizations(
                organizationService.export(search, typeUri, presence, hasParent, sort, direction)
        );
    }

    @GET
    @Path("/filters")
    public OrganizationFiltersDto filters() {
        return organizationService.filters();
    }

    @GET
    @Path("/stats")
    public OrganizationStatsDto stats(
            @QueryParam("search") String search,
            @QueryParam("typeUri") String typeUri,
            @QueryParam("presence") String presence,
            @QueryParam("hasParent") String hasParent
    ) {
        return organizationService.stats(search, typeUri, presence, hasParent);
    }

    @GET
    @Path("/{id:\\d+}/persons")
    public PageResponse<OrganizationPersonDto> persons(
            @PathParam("id") long id,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("25") int size
    ) {
        return organizationService.relatedPersons(id, page, size);
    }

    @GET
    @Path("/{id:\\d+}/projects")
    public PageResponse<OrganizationProjectDto> projects(
            @PathParam("id") long id,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("25") int size
    ) {
        return organizationService.relatedProjects(id, page, size);
    }

    @GET
    @Path("/{id:\\d+}")
    public OrganizationDetailDto get(@PathParam("id") long id) {
        return organizationService.getById(id);
    }
}
