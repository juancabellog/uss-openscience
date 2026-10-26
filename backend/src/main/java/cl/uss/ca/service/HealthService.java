package cl.uss.ca.service;

import cl.uss.ca.api.dto.HealthDto;
import cl.uss.ca.exception.DatabaseException;
import cl.uss.ca.exception.ErrorLog;
import cl.uss.ca.repository.HealthRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.logging.Logger;

@ApplicationScoped
public class HealthService {
    private static final Logger LOG = Logger.getLogger(HealthService.class.getName());

    @Inject
    HealthRepository healthRepository;

    public HealthDto health() {
        HealthDto dto = new HealthDto();
        dto.timestamp = OffsetDateTime.now(ZoneOffset.UTC);
        try {
            boolean up = healthRepository.ping();
            dto.database = up ? "UP" : "DOWN";
            dto.status = up ? "UP" : "DOWN";
        } catch (DatabaseException ex) {
            ErrorLog.unexpected(LOG, "Unable to execute database health check SELECT 1", ex);
            dto.database = "DOWN";
            dto.status = "DOWN";
        }
        return dto;
    }
}
