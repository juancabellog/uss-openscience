package cl.uss.ca.service;

import cl.uss.ca.api.dto.JournalDetailDto;
import cl.uss.ca.api.dto.JournalFiltersDto;
import cl.uss.ca.api.dto.JournalIssnsDto;
import cl.uss.ca.api.dto.JournalStatsDto;
import cl.uss.ca.api.dto.JournalSummaryDto;
import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.exception.ValidationException;
import cl.uss.ca.repository.JournalRepository;
import cl.uss.ca.repository.JournalRepository.JournalQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@ApplicationScoped
public class JournalService {
    private static final Set<String> SORTS = Set.of(
            "pureId", "title", "publicationCount", "issn", "scopusId", "type", "workflow",
            "citeScore", "quartile", "createdBy", "modifiedBy"
    );

    @Inject
    JournalRepository journalRepository;

    public PageResponse<JournalSummaryDto> list(
            Integer page,
            Integer size,
            String search,
            String typeUri,
            String workflowStep,
            String issnCount,
            String publications,
            String citescore,
            String quartile,
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
        JournalQuery query = toQuery(
                search, typeUri, workflowStep, issnCount, publications, citescore, quartile, sort, direction, safePage, safeSize
        );
        long total = journalRepository.count(query);
        List<JournalSummaryDto> items = journalRepository.find(query);
        return PageResponse.of(items, safePage, safeSize, total);
    }

    public List<JournalSummaryDto> export(
            String search,
            String typeUri,
            String workflowStep,
            String issnCount,
            String publications,
            String citescore,
            String quartile,
            String sort,
            String direction
    ) {
        JournalQuery query = toQuery(
                search, typeUri, workflowStep, issnCount, publications, citescore, quartile, sort, direction, 0, 0
        );
        List<JournalSummaryDto> items = journalRepository.find(query);
        if (items.isEmpty()) {
            throw new ValidationException("EXPORT_EMPTY", "No journals to export");
        }
        return items;
    }

    public JournalDetailDto getById(long id) {
        return journalRepository.findById(id);
    }

    public JournalIssnsDto issns(long id) {
        return journalRepository.issns(id);
    }

    public JournalStatsDto stats() {
        return stats(null, null, null, null, null, null, null);
    }

    public JournalStatsDto stats(
            String search,
            String typeUri,
            String workflowStep,
            String issnCount,
            String publications,
            String citescore,
            String quartile
    ) {
        return journalRepository.stats(
                toQuery(search, typeUri, workflowStep, issnCount, publications, citescore, quartile, "title", "ASC", 0, 0)
        );
    }

    public JournalFiltersDto filters() {
        return journalRepository.filters();
    }

    private JournalQuery toQuery(
            String search,
            String typeUri,
            String workflowStep,
            String issnCount,
            String publications,
            String citescore,
            String quartile,
            String sort,
            String direction,
            int page,
            int size
    ) {
        return new JournalQuery(
                search,
                typeUri,
                workflowStep,
                issnCount,
                normalizePublications(publications),
                normalizeCiteScore(citescore),
                normalizeQuartile(quartile),
                true,
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
                    "sort must be pureId, title, publicationCount, issn, scopusId, type, workflow, citeScore, quartile, createdBy or modifiedBy"
            );
        }
        return value;
    }

    private static String normalizePublications(String publications) {
        if (publications == null || publications.isBlank() || "ALL".equalsIgnoreCase(publications.trim())) {
            return null;
        }
        String value = publications.trim().toUpperCase(Locale.ROOT);
        if (!value.equals("WITH_PUBLICATIONS") && !value.equals("WITHOUT_PUBLICATIONS")) {
            throw new ValidationException(
                    "INVALID_PUBLICATIONS",
                    "publications must be ALL, WITH_PUBLICATIONS or WITHOUT_PUBLICATIONS"
            );
        }
        return value;
    }

    private static String normalizeCiteScore(String citescore) {
        if (citescore == null || citescore.isBlank() || "ALL".equalsIgnoreCase(citescore.trim())) {
            return null;
        }
        String value = citescore.trim().toUpperCase(Locale.ROOT);
        if (value.equals("WITH") || value.equals("WITH_CITESCORE")) {
            return "WITH";
        }
        if (value.equals("WITHOUT") || value.equals("WITHOUT_CITESCORE")) {
            return "WITHOUT";
        }
        throw new ValidationException("INVALID_CITESCORE", "citescore must be ALL, WITH or WITHOUT");
    }

    private static String normalizeQuartile(String quartile) {
        if (quartile == null || quartile.isBlank() || "ALL".equalsIgnoreCase(quartile.trim())) {
            return null;
        }
        String value = quartile.trim().toUpperCase(Locale.ROOT).replace("Q", "");
        if (!Set.of("1", "2", "3", "4").contains(value)) {
            throw new ValidationException("INVALID_QUARTILE", "quartile must be Q1, Q2, Q3 or Q4");
        }
        return "Q" + value;
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
