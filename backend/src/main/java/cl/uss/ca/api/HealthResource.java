package cl.uss.ca.api;

import cl.uss.ca.api.dto.HealthDto;
import cl.uss.ca.service.HealthService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/health")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class HealthResource {

    @Inject
    HealthService healthService;

    @GET
    public Response health() {
        HealthDto dto = healthService.health();
        int status = "UP".equals(dto.status) ? 200 : 503;
        return Response.status(status).entity(dto).build();
    }
}
