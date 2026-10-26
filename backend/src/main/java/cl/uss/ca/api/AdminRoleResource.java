package cl.uss.ca.api;

import cl.uss.ca.api.dto.AdminRoleDto;
import cl.uss.ca.api.dto.AdminRoleWriteDto;
import cl.uss.ca.service.RoleAdminService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/admin/roles")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminRoleResource {

    @Inject
    RoleAdminService roleAdminService;

    @GET
    public List<AdminRoleDto> list(@QueryParam("includeInactive") @DefaultValue("true") boolean includeInactive) {
        return roleAdminService.list(includeInactive);
    }

    @POST
    public AdminRoleDto create(AdminRoleWriteDto body) {
        return roleAdminService.create(body);
    }

    @GET
    @Path("/{id}")
    public AdminRoleDto get(@PathParam("id") long id) {
        return roleAdminService.get(id);
    }

    @PUT
    @Path("/{id}")
    public AdminRoleDto update(@PathParam("id") long id, AdminRoleWriteDto body) {
        return roleAdminService.update(id, body);
    }

    @POST
    @Path("/{id}/duplicate")
    public AdminRoleDto duplicate(@PathParam("id") long id) {
        return roleAdminService.duplicate(id);
    }

    @POST
    @Path("/{id}/disable")
    public AdminRoleDto disable(@PathParam("id") long id) {
        return roleAdminService.disable(id);
    }
}
