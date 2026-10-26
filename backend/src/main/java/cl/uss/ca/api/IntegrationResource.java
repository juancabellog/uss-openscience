package cl.uss.ca.api;

import cl.uss.ca.api.dto.IntegrationStatusDto;
import cl.uss.ca.service.IntegrationService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/integrations")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class IntegrationResource {

    @Inject
    IntegrationService integrationService;

    @GET
    public List<IntegrationStatusDto> list() {
        return integrationService.list();
    }
}
