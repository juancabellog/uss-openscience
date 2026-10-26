package cl.uss.ca.api;

import cl.uss.ca.api.dto.DirectoryImportResultDto;
import cl.uss.ca.api.dto.DirectoryImportSummaryDto;
import cl.uss.ca.api.dto.DirectoryIssueDto;
import cl.uss.ca.api.dto.DirectoryPersonDetailDto;
import cl.uss.ca.api.dto.DirectoryPersonSummaryDto;
import cl.uss.ca.api.dto.DirectoryStatsDto;
import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.exception.ValidationException;
import cl.uss.ca.security.CurrentUserService;
import cl.uss.ca.service.DirectoryImportService;
import cl.uss.ca.service.DirectoryService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/directory")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class DirectoryResource {

    @Inject
    DirectoryService directoryService;

    @Inject
    DirectoryImportService directoryImportService;

    @Inject
    CurrentUserService currentUserService;

    @GET
    public PageResponse<DirectoryPersonSummaryDto> list(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("25") int size,
            @QueryParam("search") String search,
            @QueryParam("presence") String presence
    ) {
        return directoryService.list(page, size, search, presence);
    }

    @GET
    @Path("/stats")
    public DirectoryStatsDto stats(
            @QueryParam("search") String search,
            @QueryParam("presence") String presence
    ) {
        return directoryService.stats(search, presence);
    }

    @GET
    @Path("/imports")
    public List<DirectoryImportSummaryDto> imports() {
        return directoryService.imports();
    }

    @GET
    @Path("/imports/{id:\\d+}")
    public DirectoryImportSummaryDto importById(@PathParam("id") long id) {
        return directoryService.importById(id);
    }

    @POST
    @Path("/imports/{id:\\d+}/recover")
    public DirectoryImportSummaryDto recover(@PathParam("id") long id) {
        return directoryImportService.recoverImport(id);
    }

    @GET
    @Path("/imports/{id:\\d+}/issues")
    public List<DirectoryIssueDto> issues(@PathParam("id") long id) {
        return directoryService.issues(id);
    }

    @POST
    @Path("/imports/dry-run")
    @Consumes({
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
            MediaType.APPLICATION_OCTET_STREAM
    })
    public DirectoryImportResultDto dryRun(byte[] body, @HeaderParam("X-Filename") String filename) {
        requireFile(body);
        return directoryImportService.dryRun(body, filename);
    }

    @POST
    @Path("/imports")
    @Consumes({
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
            MediaType.APPLICATION_OCTET_STREAM
    })
    public DirectoryImportResultDto importFile(byte[] body, @HeaderParam("X-Filename") String filename) {
        requireFile(body);
        String actor = currentUserService.current().email;
        if (actor == null || actor.isBlank()) {
            actor = currentUserService.current().id;
        }
        return directoryImportService.importFile(body, filename, actor);
    }

    @GET
    @Path("/{id:\\d+}")
    public DirectoryPersonDetailDto get(@PathParam("id") long id) {
        return directoryService.get(id);
    }

    private static void requireFile(byte[] body) {
        if (body == null || body.length == 0) {
            throw new ValidationException("DIRECTORY_FILE_EMPTY", "The directory file is empty");
        }
    }
}
