package cl.uss.ca.api;

import cl.uss.ca.api.dto.DashboardDto;
import cl.uss.ca.service.DashboardService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;

import java.util.logging.Logger;

@Path("/dashboard")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class DashboardResource {

    private static final Logger LOG = Logger.getLogger(DashboardResource.class.getName());

    @Inject
    DashboardService dashboardService;

    @GET
    public DashboardDto dashboard(@Context UriInfo uriInfo, @Context HttpHeaders headers) {
        LOG.info(() -> "Dashboard GET " + uriInfo.getRequestUri()
                + " origin=" + header(headers, "Origin")
                + " host=" + header(headers, "Host"));
        DashboardDto dto = dashboardService.dashboard();
        LOG.info("Dashboard OK metrics=" + (dto.metrics != null));
        return dto;
    }

    private static String header(HttpHeaders headers, String name) {
        String value = headers.getHeaderString(name);
        return value == null || value.isBlank() ? "-" : value;
    }
}
