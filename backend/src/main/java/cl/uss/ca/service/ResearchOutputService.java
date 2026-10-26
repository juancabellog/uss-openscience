package cl.uss.ca.service;

import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.api.dto.ResearchOutputDetailDto;
import cl.uss.ca.api.dto.ResearchOutputDoiVersionsDto;
import cl.uss.ca.api.dto.ResearchOutputFiltersDto;
import cl.uss.ca.api.dto.ResearchOutputStatsDto;
import cl.uss.ca.api.dto.ResearchOutputSummaryDto;
import cl.uss.ca.exception.NotFoundException;
import cl.uss.ca.exception.ValidationException;
import cl.uss.ca.repository.ResearchOutputRepository;
import cl.uss.ca.repository.ResearchOutputRepository.ResearchOutputQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@ApplicationScoped
public class ResearchOutputService {

    private static final Set<String> SORT_WHITELIST = Set.of(
            "pureId", "title", "year", "type", "workflow", "doi", "doiVersionCount", "journal",
            "contributorsCount", "fwci", "createdBy", "modifiedBy"
    );
    private static final Set<String> DIRECTION_WHITELIST = Set.of("asc", "desc");

    @Inject
    ResearchOutputRepository researchOutputRepository;

    public PageResponse<ResearchOutputSummaryDto> list(
            Integer page,
            Integer size,
            String search,
            String type,
            Integer year,
            Boolean hasDoi,
            String doiStatus,
            String workflowStep,
            String pureStatus,
            java.math.BigDecimal fwciFrom,
            java.math.BigDecimal fwciTo,
            String sort,
            String direction
    ) {
        int safePage = page == null ? 0 : page;
        int safeSize = size == null ? 25 : size;
        if (safePage < 0) {
            throw new ValidationException("INVALID_PAGE", "page must be >= 0");
        }
        if (safeSize < 1 || safeSize > 100) {
            throw new ValidationException("INVALID_SIZE", "size must be between 1 and 100");
        }
        ResearchOutputQuery query = toQuery(
                search, type, year, hasDoi, doiStatus, workflowStep, pureStatus, fwciFrom, fwciTo, sort, direction, safePage, safeSize
        );
        long total = researchOutputRepository.count(query);
        return PageResponse.of(researchOutputRepository.find(query), safePage, safeSize, total);
    }

    public List<ResearchOutputSummaryDto> export(
            String search,
            String type,
            Integer year,
            Boolean hasDoi,
            String doiStatus,
            String workflowStep,
            String pureStatus,
            java.math.BigDecimal fwciFrom,
            java.math.BigDecimal fwciTo,
            String sort,
            String direction
    ) {
        PageResponse<ResearchOutputSummaryDto> preview =
                list(0, 1, search, type, year, hasDoi, doiStatus, workflowStep, pureStatus, fwciFrom, fwciTo, sort, direction);
        if (preview.totalElements == 0) {
            throw new ValidationException("EXPORT_EMPTY", "No records to export with the selected filters");
        }
        return researchOutputRepository.find(
                toQuery(search, type, year, hasDoi, doiStatus, workflowStep, pureStatus, fwciFrom, fwciTo, sort, direction, 0, -1)
        );
    }

    public ResearchOutputStatsDto stats(String pureStatus) {
        return stats(null, null, null, null, null, null, pureStatus, null, null);
    }

    public ResearchOutputStatsDto stats(
            String search,
            String type,
            Integer year,
            Boolean hasDoi,
            String doiStatus,
            String workflowStep,
            String pureStatus,
            java.math.BigDecimal fwciFrom,
            java.math.BigDecimal fwciTo
    ) {
        return researchOutputRepository.stats(
                toQuery(search, type, year, hasDoi, doiStatus, workflowStep, pureStatus, fwciFrom, fwciTo, "year", "desc", 0, -1)
        );
    }

    public ResearchOutputDetailDto getById(long id) {
        ResearchOutputDetailDto dto = researchOutputRepository.findById(id);
        if (dto == null) {
            throw new NotFoundException("RESEARCH_OUTPUT_NOT_FOUND", "Research output not found");
        }
        return dto;
    }

    public ResearchOutputDoiVersionsDto doiVersions(long id) {
        ResearchOutputDoiVersionsDto dto = researchOutputRepository.findDoiVersions(id);
        if (dto == null) {
            throw new NotFoundException("RESEARCH_OUTPUT_NOT_FOUND", "Research output not found");
        }
        return dto;
    }

    public ResearchOutputFiltersDto filters() {
        return researchOutputRepository.filters();
    }

    private ResearchOutputQuery toQuery(
            String search,
            String type,
            Integer year,
            Boolean hasDoi,
            String doiStatus,
            String workflowStep,
            String pureStatus,
            java.math.BigDecimal fwciFrom,
            java.math.BigDecimal fwciTo,
            String sort,
            String direction,
            int page,
            int size
    ) {
        return new ResearchOutputQuery(
                blankToNull(search),
                blankToNull(type),
                year,
                hasDoi,
                parseDoiStatus(doiStatus),
                blankToNull(workflowStep),
                PersonService.parsePureStatus(pureStatus),
                fwciFrom,
                fwciTo,
                normalizeSort(sort),
                normalizeDirection(direction),
                page,
                size
        );
    }

    static String parseDoiStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ALL" -> "ALL";
            case "WITH_DOI", "WITH" -> "WITH_DOI";
            case "WITHOUT_DOI", "WITHOUT" -> "WITHOUT_DOI";
            case "MULTIPLE_DOI_VERSIONS" -> "MULTIPLE_DOI_VERSIONS";
            case "SHARED_DOI" -> "SHARED_DOI";
            default -> throw new ValidationException(
                    "INVALID_DOI_STATUS",
                    "doiStatus must be ALL, WITH_DOI, WITHOUT_DOI, MULTIPLE_DOI_VERSIONS or SHARED_DOI"
            );
        };
    }

    private static String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return null;
        }
        if (!SORT_WHITELIST.contains(sort)) {
            throw new ValidationException(
                    "INVALID_SORT",
                    "sort must be pureId, title, year, type, workflow, doi, doiVersionCount, journal, contributorsCount, createdBy or modifiedBy"
            );
        }
        return sort;
    }

    private static String normalizeDirection(String direction) {
        if (direction == null || direction.isBlank()) {
            return "desc";
        }
        String normalized = direction.trim().toLowerCase(Locale.ROOT);
        if (!DIRECTION_WHITELIST.contains(normalized)) {
            throw new ValidationException("INVALID_DIRECTION", "direction must be asc or desc");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
