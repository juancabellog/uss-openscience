package cl.uss.ca.service;

import cl.uss.ca.api.dto.AuditEventDto;
import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.api.dto.SyncChangeDto;
import cl.uss.ca.api.dto.SyncErrorDto;
import cl.uss.ca.api.dto.SyncRunDto;
import cl.uss.ca.audit.AuditCatalog;
import cl.uss.ca.audit.AuditEvents;
import cl.uss.ca.audit.AuditListSupport;
import cl.uss.ca.exception.NotFoundException;
import cl.uss.ca.repository.AccessRepository;
import cl.uss.ca.repository.AccessUserRecord;
import cl.uss.ca.repository.SyncRunRepository;
import cl.uss.ca.sync.SyncChangeRepository;
import cl.uss.ca.sync.SyncErrorRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class AuditService {

    @Inject
    SyncRunRepository syncRunRepository;

    @Inject
    SyncChangeRepository syncChangeRepository;

    @Inject
    SyncErrorRepository syncErrorRepository;

    @Inject
    AccessRepository accessRepository;

    @Inject
    OpenAlexService openAlexService;

    public PageResponse<AuditEventDto> list(
            String search,
            String from,
            String to,
            String eventType,
            String source,
            String entity,
            String status,
            String syncMode,
            String actor,
            String sort,
            String direction,
            int page,
            int size
    ) {
        int safePage = AuditListSupport.safePage(page);
        int safeSize = AuditListSupport.safeSize(size);
        if (!AuditCatalog.isSupportedEventType(eventType)) {
            return PageResponse.of(List.of(), safePage - 1, safeSize, 0);
        }
        PageResponse<SyncRunDto> runs = syncRunRepository.findPage(
                search,
                AuditListSupport.fromTimestamp(from),
                AuditListSupport.toTimestamp(to),
                AuditListSupport.entityFilter(entity),
                AuditListSupport.sourceEntities(source),
                AuditListSupport.statusFilter(status),
                AuditListSupport.syncModeFilter(syncMode),
                actor,
                sort,
                direction,
                safePage,
                safeSize
        );
        Map<String, String> actors = resolveActors(runs.items);
        List<AuditEventDto> items = new ArrayList<>();
        if (runs.items != null) {
            for (SyncRunDto run : runs.items) {
                items.add(AuditEvents.fromSync(run, actors.get(run.startedBy)));
            }
        }
        return PageResponse.of(items, runs.page, runs.size, runs.totalElements);
    }

    public AuditEventDto getById(String eventId) {
        String syncId = AuditCatalog.executionIdOf(eventId);
        SyncRunDto run = syncRunRepository.findById(syncId);
        if (run == null) {
            throw new NotFoundException("AUDIT_EVENT_NOT_FOUND", "Audit event was not found");
        }
        Map<String, String> actors = resolveActors(List.of(run));
        openAlexService.attachExtras(run);
        return AuditEvents.fromSync(run, actors.get(run.startedBy));
    }

    public PageResponse<SyncChangeDto> changes(String eventId, int page, int size) {
        String syncId = requireExecution(eventId);
        PageResponse<SyncChangeDto> result = syncChangeRepository.findPage(syncId, page, size);
        if (result.items != null) {
            for (SyncChangeDto change : result.items) {
                AuditEvents.sanitize(change);
            }
        }
        return result;
    }

    public PageResponse<SyncErrorDto> errors(String eventId, int page, int size) {
        String syncId = requireExecution(eventId);
        PageResponse<SyncErrorDto> result = syncErrorRepository.findItemsPage(syncId, page, size);
        if (result.items != null) {
            for (SyncErrorDto error : result.items) {
                AuditEvents.sanitize(error);
            }
        }
        return result;
    }

    private String requireExecution(String eventId) {
        String syncId = AuditCatalog.executionIdOf(eventId);
        if (syncRunRepository.findById(syncId) == null) {
            throw new NotFoundException("AUDIT_EVENT_NOT_FOUND", "Audit event was not found");
        }
        return syncId;
    }

    private Map<String, String> resolveActors(List<SyncRunDto> runs) {
        Map<String, String> labels = new HashMap<>();
        if (runs == null || runs.isEmpty()) {
            return labels;
        }
        List<Long> ids = new ArrayList<>();
        for (SyncRunDto run : runs) {
            Long id = parseUserId(run.startedBy);
            if (id != null) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return labels;
        }
        Map<Long, AccessUserRecord> users = accessRepository.findUsersByIds(ids);
        for (AccessUserRecord user : users.values()) {
            labels.put(String.valueOf(user.id), AuditCatalog.displayName(
                    user.firstName, user.lastName, user.email, user.id
            ));
        }
        return labels;
    }

    private static Long parseUserId(String startedBy) {
        if (startedBy == null || !startedBy.trim().matches("\\d{1,18}")) {
            return null;
        }
        try {
            return Long.parseLong(startedBy.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
