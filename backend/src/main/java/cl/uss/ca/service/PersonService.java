package cl.uss.ca.service;

import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.api.dto.PersonDetailDto;
import cl.uss.ca.api.dto.PersonFiltersDto;
import cl.uss.ca.api.dto.PersonScopusIdentifiersDto;
import cl.uss.ca.api.dto.PersonStatsDto;
import cl.uss.ca.api.dto.PersonSummaryDto;
import cl.uss.ca.exception.NotFoundException;
import cl.uss.ca.exception.ValidationException;
import cl.uss.ca.repository.OrcidWorkRepository;
import cl.uss.ca.repository.PersonRepository;
import cl.uss.ca.repository.PersonRepository.PersonQuery;
import cl.uss.ca.repository.PersonRepository.Population;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@ApplicationScoped
public class PersonService {

    private static final Set<String> SORT_WHITELIST = Set.of(
            "pureId", "name", "position", "orcid", "publicationCount", "createdBy", "modifiedBy"
    );
    private static final Set<String> DIRECTION_WHITELIST = Set.of("asc", "desc");

    @Inject
    PersonRepository personRepository;

    @Inject
    OrcidWorkRepository orcidWorkRepository;

    @Inject
    OpenAlexService openAlexService;

    public PageResponse<PersonSummaryDto> list(
            Integer page,
            Integer size,
            String search,
            String cargo,
            String population,
            String orcidStatus,
            Boolean hasOrcid,
            String scopusStatus,
            Boolean hasScopus,
            String scopusCount,
            String minDistinctScopusCount,
            String pureStatus,
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
        Population parsedPopulation = PersonQuery.parsePopulation(population);
        if (parsedPopulation == null) {
            throw new ValidationException("INVALID_POPULATION", "population must be ALL, RESEARCHERS or WITHOUT_POSITION");
        }
        Boolean resolvedHasOrcid = resolvePresenceStatus(
                orcidStatus,
                hasOrcid,
                "orcidStatus",
                "WITH_ORCID",
                "WITHOUT_ORCID"
        );
        Boolean resolvedHasScopus = resolvePresenceStatus(
                scopusStatus,
                hasScopus,
                "scopusStatus",
                "WITH_SCOPUS",
                "WITHOUT_SCOPUS"
        );
        Integer resolvedScopusCount = parseScopusCount(scopusCount);
        if (resolvedScopusCount != null) {
            resolvedHasScopus = null;
        }
        Integer resolvedMinDistinct = parseScopusCount(minDistinctScopusCount);
        String safeSort = normalizeSort(sort);
        String safeDirection = normalizeDirection(direction);
        PersonQuery query = toQuery(
                search,
                cargo,
                parsedPopulation,
                resolvedHasOrcid,
                resolvedHasScopus,
                resolvedScopusCount,
                resolvedMinDistinct,
                pureStatus,
                safeSort,
                safeDirection,
                safePage,
                safeSize
        );
        long total = personRepository.countPeople(query);
        return PageResponse.of(personRepository.findPeople(query), safePage, safeSize, total);
    }

    public List<PersonSummaryDto> export(String search, String cargo, String population, String orcidStatus,
                                         Boolean hasOrcid, String scopusStatus, Boolean hasScopus,
                                         String scopusCount, String minDistinctScopusCount,
                                         String pureStatus, String sort, String direction) {
        PageResponse<PersonSummaryDto> preview = list(
                0, 1, search, cargo, population, orcidStatus, hasOrcid, scopusStatus, hasScopus,
                scopusCount, minDistinctScopusCount, pureStatus, sort, direction
        );
        if (preview.totalElements == 0) {
            throw new ValidationException("EXPORT_EMPTY", "No records to export with the selected filters");
        }
        Population parsedPopulation = PersonQuery.parsePopulation(population);
        PersonQuery query = toQuery(
                search,
                cargo,
                parsedPopulation,
                resolvePresenceStatus(orcidStatus, hasOrcid, "orcidStatus", "WITH_ORCID", "WITHOUT_ORCID"),
                resolvePresenceStatus(scopusStatus, hasScopus, "scopusStatus", "WITH_SCOPUS", "WITHOUT_SCOPUS"),
                parseScopusCount(scopusCount),
                parseScopusCount(minDistinctScopusCount),
                pureStatus,
                normalizeSort(sort),
                normalizeDirection(direction),
                0,
                -1
        );
        return personRepository.findPeople(query);
    }

    private PersonQuery toQuery(
            String search,
            String cargo,
            Population parsedPopulation,
            Boolean hasOrcid,
            Boolean hasScopus,
            Integer scopusCount,
            Integer minDistinctScopusCount,
            String pureStatus,
            String sort,
            String direction,
            int page,
            int size
    ) {
        if (scopusCount != null) {
            hasScopus = null;
        }
        return new PersonQuery(
                blankToNull(search),
                blankToNull(cargo),
                parsedPopulation,
                hasOrcid,
                hasScopus,
                scopusCount,
                minDistinctScopusCount,
                parsePureStatus(pureStatus),
                sort,
                direction,
                page,
                size
        );
    }

    public PersonDetailDto getById(long id) {
        PersonDetailDto dto = personRepository.findPersonById(id);
        if (dto == null) {
            throw new NotFoundException("PERSON_NOT_FOUND", "Person not found");
        }
        var orcidState = orcidWorkRepository.requirePerson(id);
        if (orcidState != null) {
            dto.orcidSyncPending = orcidState.syncPending;
            dto.lastOrcidSyncAt = orcidState.lastSyncAt;
            dto.lastOrcidSyncStatus = orcidState.lastSyncStatus;
        }
        openAlexService.attachToPerson(dto);
        return dto;
    }

    public PersonScopusIdentifiersDto scopusIdentifiers(long id) {
        PersonScopusIdentifiersDto dto = personRepository.findScopusIdentifiers(id);
        if (dto == null) {
            throw new NotFoundException("PERSON_NOT_FOUND", "Person not found");
        }
        return dto;
    }

    public PersonFiltersDto positions() {
        return personRepository.listPositions();
    }

    public PersonStatsDto stats() {
        return stats(null, null, null, null, null, null, null, null, null, null);
    }

    public PersonStatsDto stats(
            String search,
            String cargo,
            String population,
            String orcidStatus,
            Boolean hasOrcid,
            String scopusStatus,
            Boolean hasScopus,
            String scopusCount,
            String minDistinctScopusCount,
            String pureStatus
    ) {
        Population parsedPopulation = PersonQuery.parsePopulation(population);
        if (parsedPopulation == null) {
            throw new ValidationException("INVALID_POPULATION", "population must be ALL, RESEARCHERS or WITHOUT_POSITION");
        }
        Boolean resolvedHasOrcid = resolvePresenceStatus(
                orcidStatus, hasOrcid, "orcidStatus", "WITH_ORCID", "WITHOUT_ORCID"
        );
        Boolean resolvedHasScopus = resolvePresenceStatus(
                scopusStatus, hasScopus, "scopusStatus", "WITH_SCOPUS", "WITHOUT_SCOPUS"
        );
        Integer resolvedScopusCount = parseScopusCount(scopusCount);
        Integer resolvedMinDistinct = parseScopusCount(minDistinctScopusCount);
        return personRepository.stats(toQuery(
                search,
                cargo,
                parsedPopulation,
                resolvedHasOrcid,
                resolvedHasScopus,
                resolvedScopusCount,
                resolvedMinDistinct,
                pureStatus,
                "name",
                "asc",
                0,
                -1
        ));
    }

    private static String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return "name";
        }
        if (!SORT_WHITELIST.contains(sort)) {
            throw new ValidationException(
                    "INVALID_SORT",
                    "sort must be pureId, name, position, orcid, publicationCount, createdBy or modifiedBy"
            );
        }
        return sort;
    }

    private static String normalizeDirection(String direction) {
        if (direction == null || direction.isBlank()) {
            return "asc";
        }
        String normalized = direction.trim().toLowerCase(Locale.ROOT);
        if (!DIRECTION_WHITELIST.contains(normalized)) {
            throw new ValidationException("INVALID_DIRECTION", "direction must be asc or desc");
        }
        return normalized;
    }

    private static Integer parseScopusCount(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < 0) {
                throw new ValidationException("INVALID_SCOPUS_COUNT", "scopusCount must be >= 0");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new ValidationException("INVALID_SCOPUS_COUNT", "scopusCount must be an integer >= 0");
        }
    }

    private static Boolean resolvePresenceStatus(
            String status,
            Boolean fallback,
            String paramName,
            String withValue,
            String withoutValue
    ) {
        if (status == null || status.isBlank()) {
            return fallback;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if ("ALL".equals(normalized)) {
            return null;
        }
        if (withValue.equals(normalized) || "WITH".equals(normalized)) {
            return Boolean.TRUE;
        }
        if (withoutValue.equals(normalized) || "WITHOUT".equals(normalized)) {
            return Boolean.FALSE;
        }
        throw new ValidationException(
                "INVALID_" + paramName.toUpperCase(Locale.ROOT),
                paramName + " must be ALL, " + withValue + " or " + withoutValue
        );
    }

    static Boolean parsePureStatus(String raw) {
        if (raw == null || raw.isBlank() || "ACTIVE".equalsIgnoreCase(raw.trim())) {
            return Boolean.TRUE;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if ("ALL".equals(normalized)) {
            return null;
        }
        if ("INACTIVE".equals(normalized)) {
            return Boolean.FALSE;
        }
        throw new ValidationException("INVALID_PURE_STATUS", "pureStatus must be ACTIVE, INACTIVE or ALL");
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
