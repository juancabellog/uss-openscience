package cl.uss.ca.api;

import cl.uss.ca.api.dto.AdminUserDeleteResultDto;
import cl.uss.ca.api.dto.AdminUserSummaryDto;
import cl.uss.ca.api.dto.AdminUserWriteDto;
import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.service.UserAdminService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/admin/users")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminUserResource {

    @Inject
    UserAdminService userAdminService;

    @GET
    public PageResponse<AdminUserSummaryDto> list(
            @QueryParam("search") String search,
            @QueryParam("status") String status,
            @QueryParam("roleId") Long roleId,
            @QueryParam("sort") @DefaultValue("createdAt") String sort,
            @QueryParam("direction") @DefaultValue("desc") String direction,
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("size") @DefaultValue("25") int size
    ) {
        return userAdminService.list(search, status, roleId, sort, direction, page, size);
    }

    @POST
    public AdminUserSummaryDto create(AdminUserWriteDto body) {
        return userAdminService.create(body);
    }

    @GET
    @Path("/{id}")
    public AdminUserSummaryDto get(@PathParam("id") long id) {
        return userAdminService.get(id);
    }

    @PUT
    @Path("/{id}")
    public AdminUserSummaryDto update(@PathParam("id") long id, AdminUserWriteDto body) {
        return userAdminService.update(id, body);
    }

    @POST
    @Path("/{id}/disable")
    public AdminUserSummaryDto disable(@PathParam("id") long id) {
        return userAdminService.disable(id);
    }

    @POST
    @Path("/{id}/enable")
    public AdminUserSummaryDto enable(@PathParam("id") long id) {
        return userAdminService.enable(id);
    }

    @POST
    @Path("/{id}/resend-activation")
    public AdminUserSummaryDto resend(@PathParam("id") long id) {
        return userAdminService.resend(id);
    }

    @DELETE
    @Path("/{id}")
    public AdminUserDeleteResultDto delete(@PathParam("id") long id) {
        return userAdminService.delete(id);
    }
}
