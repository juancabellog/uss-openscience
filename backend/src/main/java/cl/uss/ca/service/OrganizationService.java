package cl.uss.ca.service;

import cl.uss.ca.api.dto.OrganizationDetailDto;
import cl.uss.ca.api.dto.OrganizationFiltersDto;
import cl.uss.ca.api.dto.OrganizationPersonDto;
import cl.uss.ca.api.dto.OrganizationProjectDto;
import cl.uss.ca.api.dto.OrganizationStatsDto;
import cl.uss.ca.api.dto.OrganizationSummaryDto;
import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.exception.ValidationException;
import cl.uss.ca.repository.OrganizationRepository;
import cl.uss.ca.repository.OrganizationRepository.OrganizationQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@ApplicationScoped
public class OrganizationService {
    private static final Set<String> SORTS = Set.of(
            "pureId", "name", "type", "parent", "modifiedDatePure", "activeInPure"
    );
    private static final Set<String> PRESENCE = Set.of("ACTIVE", "INACTIVE", "ALL");
    private static final Set<String> PARENT = Set.of("WITH", "WITHOUT");

    @Inject
    OrganizationRepository organizationRepository;

    public PageResponse<OrganizationSummaryDto> list(
            Integer page,
            Integer size,
            String search,
            String typeUri,
            String presence,
            String hasParent,
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
        OrganizationQuery query = toQuery(search, typeUri, presence, hasParent, sort, direction, safePage, safeSize);
        long total = organizationRepository.count(query);
        List<OrganizationSummaryDto> items = organizationRepository.find(query);
        return PageResponse.of(items, safePage, safeSize, total);
    }

    public List<OrganizationSummaryDto> export(
            String search,
            String typeUri,
            String presence,
            String hasParent,
            String sort,
            String direction
    ) {
        OrganizationQuery query = toQuery(search, typeUri, presence, hasParent, sort, direction, 0, 0);
        List<OrganizationSummaryDto> items = organizationRepository.find(query);
        if (items.isEmpty()) {
            throw new ValidationException("EXPORT_EMPTY", "No organizations to export");
        }
        return items;
    }

    public OrganizationDetailDto getById(long id) {
        return organizationRepository.findById(id);
    }

    public OrganizationStatsDto stats(String search, String typeUri, String presence, String hasParent) {
        return organizationRepository.stats(toQuery(search, typeUri, presence, hasParent, "name", "ASC", 0, 0));
    }

    public OrganizationFiltersDto filters() {
        return organizationRepository.filters();
    }

    public PageResponse<OrganizationPersonDto> relatedPersons(long id, Integer page, Integer size) {
        int safePage = page == null ? 0 : page;
        int safeSize = size == null ? 25 : size;
        if (safePage < 0) {
            throw new ValidationException("INVALID_PAGE", "page must be >= 0");
        }
        if (safeSize < 1 || safeSize > 100) {
            throw new ValidationException("INVALID_SIZE", "size must be between 1 and 100");
        }
        long total = organizationRepository.countRelatedPersons(id);
        List<OrganizationPersonDto> items = organizationRepository.findRelatedPersons(id, safePage, safeSize);
        return PageResponse.of(items, safePage, safeSize, total);
    }

    public PageResponse<OrganizationProjectDto> relatedProjects(long id, Integer page, Integer size) {
        int safePage = page == null ? 0 : page;
        int safeSize = size == null ? 25 : size;
        if (safePage < 0) {
            throw new ValidationException("INVALID_PAGE", "page must be >= 0");
        }
        if (safeSize < 1 || safeSize > 100) {
            throw new ValidationException("INVALID_SIZE", "size must be between 1 and 100");
        }
        long total = organizationRepository.countRelatedProjects(id);
        List<OrganizationProjectDto> items = organizationRepository.findRelatedProjects(id, safePage, safeSize);
        return PageResponse.of(items, safePage, safeSize, total);
    }

    private OrganizationQuery toQuery(
            String search,
            String typeUri,
            String presence,
            String hasParent,
            String sort,
            String direction,
            int page,
            int size
    ) {
        return new OrganizationQuery(
                search,
                typeUri,
                normalizePresence(presence),
                normalizeParent(hasParent),
                normalizeSort(sort),
                normalizeDirection(direction),
                page,
                size
        );
    }

    private static String normalizePresence(String presence) {
        if (presence == null || presence.isBlank()) {
            return "ACTIVE";
        }
        String value = presence.trim().toUpperCase(Locale.ROOT);
        if (!PRESENCE.contains(value)) {
            throw new ValidationException("INVALID_PRESENCE", "presence must be ACTIVE, INACTIVE or ALL");
        }
        return value;
    }

    private static String normalizeParent(String hasParent) {
        if (hasParent == null || hasParent.isBlank()) {
            return null;
        }
        String value = hasParent.trim().toUpperCase(Locale.ROOT);
        if (!PARENT.contains(value)) {
            throw new ValidationException("INVALID_HAS_PARENT", "hasParent must be WITH or WITHOUT");
        }
        return value;
    }

    private static String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return "name";
        }
        String value = sort.trim();
        for (String allowed : SORTS) {
            if (allowed.equalsIgnoreCase(value)) {
                return allowed;
            }
        }
        throw new ValidationException("INVALID_SORT", "Unsupported sort field");
    }

    private static String normalizeDirection(String direction) {
        if (direction == null || direction.isBlank()) {
            return "ASC";
        }
        String value = direction.trim().toUpperCase(Locale.ROOT);
        if (!"ASC".equals(value) && !"DESC".equals(value)) {
            throw new ValidationException("INVALID_DIRECTION", "direction must be ASC or DESC");
        }
        return value;
    }
}
