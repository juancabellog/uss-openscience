package cl.uss.ca.api;

import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.api.dto.ProjectDetailDto;
import cl.uss.ca.api.dto.ProjectFiltersDto;
import cl.uss.ca.api.dto.ProjectStatsDto;
import cl.uss.ca.api.dto.ProjectSummaryDto;
import cl.uss.ca.api.dto.SyncRunDto;
import cl.uss.ca.service.ExcelExportService;
import cl.uss.ca.service.ProjectService;
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

@Path("/projects")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class ProjectResource {

    @Inject
    ProjectService projectService;

    @Inject
    ExcelExportService excelExportService;

    @GET
    public PageResponse<ProjectSummaryDto> list(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("25") int size,
            @QueryParam("search") String search,
            @QueryParam("typeUri") String typeUri,
            @QueryParam("workflowStep") String workflowStep,
            @QueryParam("visibilityKey") String visibilityKey,
            @QueryParam("presence") String presence,
            @QueryParam("startYear") Integer startYear,
            @QueryParam("sort") String sort,
            @QueryParam("direction") String direction
    ) {
        return projectService.list(
                page, size, search, typeUri, workflowStep, visibilityKey, presence, startYear, sort, direction
        );
    }

    @GET
    @Path("/export.xlsx")
    @Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public Response export(
            @QueryParam("search") String search,
            @QueryParam("typeUri") String typeUri,
            @QueryParam("workflowStep") String workflowStep,
            @QueryParam("visibilityKey") String visibilityKey,
            @QueryParam("presence") String presence,
            @QueryParam("startYear") Integer startYear,
            @QueryParam("sort") String sort,
            @QueryParam("direction") String direction
    ) {
        return excelExportService.exportProjects(
                projectService.export(search, typeUri, workflowStep, visibilityKey, presence, startYear, sort, direction)
        );
    }

    @GET
    @Path("/filters")
    public ProjectFiltersDto filters() {
        return projectService.filters();
    }

    @GET
    @Path("/stats")
    public ProjectStatsDto stats(
            @QueryParam("search") String search,
            @QueryParam("typeUri") String typeUri,
            @QueryParam("workflowStep") String workflowStep,
            @QueryParam("visibilityKey") String visibilityKey,
            @QueryParam("presence") String presence,
            @QueryParam("startYear") Integer startYear
    ) {
        return projectService.stats(search, typeUri, workflowStep, visibilityKey, presence, startYear);
    }

    @GET
    @Path("/summary")
    public ProjectStatsDto summary(
            @QueryParam("search") String search,
            @QueryParam("typeUri") String typeUri,
            @QueryParam("workflowStep") String workflowStep,
            @QueryParam("visibilityKey") String visibilityKey,
            @QueryParam("presence") String presence,
            @QueryParam("startYear") Integer startYear
    ) {
        return stats(search, typeUri, workflowStep, visibilityKey, presence, startYear);
    }

    @GET
    @Path("/history")
    public List<SyncRunDto> history() {
        return projectService.history();
    }

    @GET
    @Path("/{id}")
    public ProjectDetailDto get(@PathParam("id") String id) {
        return projectService.get(id);
    }
}
