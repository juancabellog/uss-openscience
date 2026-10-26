package cl.uss.ca.api;

import cl.uss.ca.api.dto.SystemInfoDto;
import cl.uss.ca.service.SystemInfoService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/system/info")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class SystemInfoResource {

    @Inject
    SystemInfoService systemInfoService;

    @GET
    public SystemInfoDto info() {
        return systemInfoService.info();
    }
}
