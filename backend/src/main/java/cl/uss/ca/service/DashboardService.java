package cl.uss.ca.service;

import cl.uss.ca.api.dto.DashboardDto;
import cl.uss.ca.api.dto.DashboardMetricsDto;
import cl.uss.ca.api.dto.DataQualitySummaryDto;
import cl.uss.ca.api.dto.MetricDto;
import cl.uss.ca.auth.AppModule;
import cl.uss.ca.auth.AppPermission;
import cl.uss.ca.exception.DatabaseException;
import cl.uss.ca.exception.ErrorLog;
import cl.uss.ca.repository.DashboardRepository;
import cl.uss.ca.repository.DataQualityRepository;
import cl.uss.ca.security.CurrentUserService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.function.LongSupplier;
import java.util.logging.Logger;

@ApplicationScoped
public class DashboardService {
    private static final Logger LOG = Logger.getLogger(DashboardService.class.getName());
    private static final String SOURCE_PURE_REPLICA = "PURE_LOCAL_REPLICA";
    private static final String SOURCE_OPENSCIENCE = "OPENSCIENCE";

    @Inject
    DashboardRepository dashboardRepository;

    @Inject
    IntegrationService integrationService;

    @Inject
    DataQualityRepository dataQualityRepository;

    @Inject
    CurrentUserService currentUserService;

    public DashboardDto dashboard() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        DashboardDto dto = new DashboardDto();
        dto.lastUpdated = now;
        dto.metrics = new DashboardMetricsDto();
        dto.integrations = integrationService.list();
        dto.dataQuality = qualitySummary();

        MetricDto registeredPersons = countMetric(
                "REGISTERED_PERSONS",
                SOURCE_PURE_REPLICA,
                now,
                dashboardRepository::countPersons
        );
        MetricDto personsWithOrcid = countMetric(
                "PERSONS_WITH_ORCID",
                SOURCE_PURE_REPLICA,
                now,
                dashboardRepository::countPersonsWithOrcid
        );

        MetricDto researchers = countMetric(
                "RESEARCHERS",
                SOURCE_PURE_REPLICA,
                now,
                dashboardRepository::countResearchers
        );
        MetricDto researchersWithOrcid = countMetric(
                "RESEARCHERS_WITH_ORCID",
                SOURCE_PURE_REPLICA,
                now,
                dashboardRepository::countResearchersWithOrcid
        );

        dto.metrics.registeredPersons = registeredPersons;
        dto.metrics.personsWithOrcid = personsWithOrcid;
        dto.metrics.orcidCoveragePercent = coverageFrom(
                "ORCID_COVERAGE",
                registeredPersons,
                personsWithOrcid,
                now
        );
        dto.metrics.orcidAuthenticated = countMetric(
                "ORCID_AUTHENTICATED",
                SOURCE_PURE_REPLICA,
                now,
                dashboardRepository::countOrcidAuthenticated
        );
        dto.metrics.personsWithScopusId = countMetric(
                "PERSONS_WITH_SCOPUS_ID",
                SOURCE_PURE_REPLICA,
                now,
                dashboardRepository::countPersonsWithScopusId
        );
        dto.metrics.researchers = researchers;
        dto.metrics.researchersWithOrcid = researchersWithOrcid;
        dto.metrics.researchersWithOrcidPercent = coverageFrom(
                "RESEARCHERS_WITH_ORCID_COVERAGE",
                researchers,
                researchersWithOrcid,
                now
        );
        dto.metrics.pendingDataQualityIssues = qualityMetric(now);
        redact(dto, now);
        return dto;
    }

    private void redact(DashboardDto dto, OffsetDateTime at) {
        if (!currentUserService.allows(AppModule.PEOPLE, AppPermission.CONSULT)) {
            dto.metrics.registeredPersons = unavailable("REGISTERED_PERSONS", SOURCE_PURE_REPLICA, at, "AUTH_FORBIDDEN");
            dto.metrics.personsWithOrcid = unavailable("PERSONS_WITH_ORCID", SOURCE_PURE_REPLICA, at, "AUTH_FORBIDDEN");
            dto.metrics.orcidCoveragePercent = unavailable("ORCID_COVERAGE", SOURCE_PURE_REPLICA, at, "AUTH_FORBIDDEN");
            dto.metrics.orcidAuthenticated = unavailable("ORCID_AUTHENTICATED", SOURCE_PURE_REPLICA, at, "AUTH_FORBIDDEN");
            dto.metrics.researchers = unavailable("RESEARCHERS", SOURCE_PURE_REPLICA, at, "AUTH_FORBIDDEN");
            dto.metrics.researchersWithOrcid = unavailable("RESEARCHERS_WITH_ORCID", SOURCE_PURE_REPLICA, at, "AUTH_FORBIDDEN");
            dto.metrics.researchersWithOrcidPercent = unavailable("RESEARCHERS_WITH_ORCID_COVERAGE", SOURCE_PURE_REPLICA, at, "AUTH_FORBIDDEN");
            dto.metrics.personsWithScopusId = unavailable("PERSONS_WITH_SCOPUS_ID", SOURCE_PURE_REPLICA, at, "AUTH_FORBIDDEN");
        }
        if (!currentUserService.allows(AppModule.DATA_QUALITY, AppPermission.CONSULT)) {
            dto.metrics.pendingDataQualityIssues = unavailable("PENDING_DATA_QUALITY_ISSUES", SOURCE_OPENSCIENCE, at, "AUTH_FORBIDDEN");
            DataQualitySummaryDto hidden = new DataQualitySummaryDto();
            hidden.available = false;
            dto.dataQuality = hidden;
        }
        if (!currentUserService.allows(AppModule.INTEGRATIONS, AppPermission.CONSULT)) {
            dto.integrations = java.util.List.of();
        }
    }

    private MetricDto countMetric(String code, String source, OffsetDateTime at, LongSupplier query) {
        try {
            long value = query.getAsLong();
            LOG.info(() -> "Dashboard metric " + code + " = " + value);
            return count(code, value, source, at);
        } catch (DatabaseException ex) {
            ErrorLog.unexpected(LOG, "ERROR calculating dashboard metric " + code, ex);
            return unavailable(code, source, at, "QUERY_FAILED");
        }
    }

    private MetricDto coverageFrom(String code, MetricDto denominator, MetricDto numerator, OffsetDateTime at) {
        if (!denominator.available || !numerator.available
                || denominator.value == null || numerator.value == null) {
            return unavailable(code, SOURCE_PURE_REPLICA, at, "DEPENDENCY_UNAVAILABLE");
        }
        return percent(
                code,
                numerator.value.longValue(),
                denominator.value.longValue(),
                SOURCE_PURE_REPLICA,
                at
        );
    }

    private MetricDto qualityMetric(OffsetDateTime at) {
        if (!dataQualityRepository.available()) {
            return unavailable("PENDING_DATA_QUALITY_ISSUES", SOURCE_OPENSCIENCE, at, "TABLE_UNAVAILABLE");
        }
        return countMetric("PENDING_DATA_QUALITY_ISSUES", SOURCE_OPENSCIENCE, at, dataQualityRepository::countOpenFindings);
    }

    private DataQualitySummaryDto qualitySummary() {
        DataQualitySummaryDto dto = new DataQualitySummaryDto();
        if (!dataQualityRepository.available()) {
            dto.available = false;
            return dto;
        }
        try {
            long open = dataQualityRepository.countOpenFindings();
            dto.available = true;
            dto.open = open;
            dto.inReview = 0L;
            dto.validated = 0L;
            dto.corrected = 0L;
            dto.dismissed = 0L;
            return dto;
        } catch (DatabaseException ex) {
            ErrorLog.unexpected(LOG, "ERROR calculating data-quality summary", ex);
            DataQualitySummaryDto fallback = new DataQualitySummaryDto();
            fallback.available = false;
            return fallback;
        }
    }

    private static MetricDto count(String code, long value, String source, OffsetDateTime at) {
        MetricDto dto = new MetricDto();
        dto.code = code;
        dto.value = BigDecimal.valueOf(value);
        dto.unit = "COUNT";
        dto.available = true;
        dto.source = source;
        dto.calculatedAt = at;
        return dto;
    }

    private static MetricDto percent(String code, long numerator, long denominator, String source, OffsetDateTime at) {
        MetricDto dto = new MetricDto();
        dto.code = code;
        dto.unit = "PERCENT";
        dto.numerator = numerator;
        dto.denominator = denominator;
        dto.source = source;
        dto.calculatedAt = at;
        if (denominator <= 0) {
            dto.available = false;
            dto.unavailableReason = "DENOMINATOR_ZERO";
            LOG.info(() -> "Dashboard metric " + code + " unavailable: DENOMINATOR_ZERO");
            return dto;
        }
        dto.available = true;
        dto.value = BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP);
        LOG.info(() -> "Dashboard metric " + code + " = " + dto.value);
        return dto;
    }

    private static MetricDto unavailable(String code, String source, OffsetDateTime at, String reason) {
        MetricDto dto = new MetricDto();
        dto.code = code;
        dto.available = false;
        dto.source = source;
        dto.calculatedAt = at;
        dto.unavailableReason = reason;
        LOG.info(() -> "Dashboard metric " + code + " unavailable: " + reason);
        return dto;
    }
}
