package cl.uss.ca.service;

import cl.uss.ca.api.dto.SystemInfoDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class SystemInfoService {

    @Inject
    @ConfigProperty(name = "openscience.application", defaultValue = "OPENSCIENCE-USS")
    String application;

    @Inject
    @ConfigProperty(name = "openscience.version", defaultValue = "0.1.0")
    String version;

    @Inject
    @ConfigProperty(name = "openscience.environment", defaultValue = "DEV")
    String environment;

    @Inject
    @ConfigProperty(name = "openscience.dataMode", defaultValue = "REAL")
    String dataMode;

    @Inject
    @ConfigProperty(name = "openscience.api.version", defaultValue = "v1")
    String apiVersion;

    public SystemInfoDto info() {
        SystemInfoDto dto = new SystemInfoDto();
        dto.application = application;
        dto.version = version;
        dto.environment = environment;
        dto.dataMode = dataMode;
        dto.apiVersion = apiVersion;
        return dto;
    }
}
