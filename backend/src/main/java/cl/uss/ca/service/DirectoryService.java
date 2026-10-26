package cl.uss.ca.service;

import cl.uss.ca.api.dto.DirectoryImportSummaryDto;
import cl.uss.ca.api.dto.DirectoryIssueDto;
import cl.uss.ca.api.dto.DirectoryPersonDetailDto;
import cl.uss.ca.api.dto.DirectoryPersonSummaryDto;
import cl.uss.ca.api.dto.DirectoryStatsDto;
import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.exception.ValidationException;
import cl.uss.ca.repository.DirectoryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Set;

@ApplicationScoped
public class DirectoryService {
    private static final Set<String> PRESENCE = Set.of("ACTIVE", "INACTIVE", "ALL");

    @Inject
    DirectoryRepository directoryRepository;

    @Inject
    DirectoryImportService directoryImportService;

    public PageResponse<DirectoryPersonSummaryDto> list(Integer page, Integer size, String search, String presence) {
        int safePage = page == null ? 0 : page;
        int safeSize = size == null ? 25 : size;
        if (safePage < 0) {
            throw new ValidationException("INVALID_PAGE", "page must be >= 0");
        }
        if (safeSize < 1 || safeSize > 100) {
            throw new ValidationException("INVALID_SIZE", "size must be between 1 and 100");
        }
        String safePresence = normalizePresence(presence);
        long total = directoryRepository.countPeople(search, safePresence);
        List<DirectoryPersonSummaryDto> items = directoryRepository.findPeople(search, safePresence, safePage, safeSize);
        return PageResponse.of(items, safePage, safeSize, total);
    }

    public DirectoryStatsDto stats(String search, String presence) {
        return directoryRepository.stats(search, normalizePresence(presence));
    }

    public DirectoryPersonDetailDto get(long id) {
        return directoryRepository.findPerson(id);
    }

    public List<DirectoryImportSummaryDto> imports() {
        return directoryImportService.decorate(directoryRepository.findImports());
    }

    public DirectoryImportSummaryDto importById(long id) {
        return directoryImportService.decorate(directoryRepository.findImport(id));
    }

    public List<DirectoryIssueDto> issues(long importId) {
        directoryRepository.findImport(importId);
        return directoryRepository.findIssues(importId);
    }

    private static String normalizePresence(String presence) {
        if (presence == null || presence.isBlank()) {
            return "ACTIVE";
        }
        String value = presence.trim().toUpperCase();
        if (!PRESENCE.contains(value)) {
            throw new ValidationException("INVALID_PRESENCE", "presence must be ACTIVE, INACTIVE or ALL");
        }
        return value;
    }
}
