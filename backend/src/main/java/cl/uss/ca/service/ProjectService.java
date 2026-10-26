package cl.uss.ca.service;

import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.api.dto.ProjectDetailDto;
import cl.uss.ca.api.dto.ProjectFiltersDto;
import cl.uss.ca.api.dto.ProjectStatsDto;
import cl.uss.ca.api.dto.ProjectSummaryDto;
import cl.uss.ca.api.dto.SyncRunDto;
import cl.uss.ca.exception.ValidationException;
import cl.uss.ca.repository.ProjectRepository;
import cl.uss.ca.repository.ProjectRepository.ProjectQuery;
import cl.uss.ca.repository.SyncRunRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@ApplicationScoped
public class ProjectService {
    private static final Set<String> SORTS = Set.of(
            "pureId", "title", "acronym", "projectId", "type", "periodStart", "periodEnd",
            "principalInvestigator", "workflow"
    );
    private static final Set<String> PRESENCE = Set.of("ACTIVE", "INACTIVE", "ALL");

    @Inject
    ProjectRepository projectRepository;

    @Inject
    SyncRunRepository syncRunRepository;

    public PageResponse<ProjectSummaryDto> list(
            Integer page,
            Integer size,
            String search,
            String typeUri,
            String workflowStep,
            String visibilityKey,
            String presence,
            Integer startYear,
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
        ProjectQuery query = toQuery(
                search, typeUri, workflowStep, visibilityKey, presence, startYear, sort, direction, safePage, safeSize
        );
        long total = projectRepository.count(query);
        List<ProjectSummaryDto> items = projectRepository.find(query);
        return PageResponse.of(items, safePage, safeSize, total);
    }

    public List<ProjectSummaryDto> export(
            String search,
            String typeUri,
            String workflowStep,
            String visibilityKey,
            String presence,
            Integer startYear,
            String sort,
            String direction
    ) {
        ProjectQuery query = toQuery(
                search, typeUri, workflowStep, visibilityKey, presence, startYear, sort, direction, 0, 0
        );
        List<ProjectSummaryDto> items = projectRepository.find(query);
        if (items.isEmpty()) {
            throw new ValidationException("EXPORT_EMPTY", "No projects to export");
        }
        return items;
    }

    public ProjectDetailDto get(String idOrUuid) {
        if (idOrUuid == null || idOrUuid.isBlank()) {
            throw new ValidationException("INVALID_ID", "id is required");
        }
        return projectRepository.findByIdOrUuid(idOrUuid.trim());
    }

    public ProjectStatsDto stats(
            String search,
            String typeUri,
            String workflowStep,
            String visibilityKey,
            String presence,
            Integer startYear
    ) {
        return projectRepository.stats(
                toQuery(search, typeUri, workflowStep, visibilityKey, presence, startYear, "title", "ASC", 0, 0)
        );
    }

    public ProjectFiltersDto filters() {
        return projectRepository.filters();
    }

    public List<SyncRunDto> history() {
        return syncRunRepository.findRecent(PureSyncService.PROJECTS, 25);
    }

    private ProjectQuery toQuery(
            String search,
            String typeUri,
            String workflowStep,
            String visibilityKey,
            String presence,
            Integer startYear,
            String sort,
            String direction,
            int page,
            int size
    ) {
        return new ProjectQuery(
                search,
                typeUri,
                workflowStep,
                visibilityKey,
                normalizePresence(presence),
                startYear,
                normalizeSort(sort),
                normalizeDirection(direction),
                page,
                size
        );
    }

    private static String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return "title";
        }
        String value = sort.trim();
        if (!SORTS.contains(value)) {
            throw new ValidationException(
                    "INVALID_SORT",
                    "sort must be pureId, title, acronym, projectId, type, periodStart, periodEnd, principalInvestigator or workflow"
            );
        }
        return value;
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

    private static String normalizeDirection(String direction) {
        if (direction == null || direction.isBlank()) {
            return "ASC";
        }
        String value = direction.trim().toUpperCase(Locale.ROOT);
        if (!value.equals("ASC") && !value.equals("DESC")) {
            throw new ValidationException("INVALID_DIRECTION", "direction must be ASC or DESC");
        }
        return value;
    }
}
