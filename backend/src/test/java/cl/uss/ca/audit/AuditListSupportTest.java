package cl.uss.ca.audit;

import java.time.OffsetDateTime;

/**
 * Audit list filters and order. Not executed by the agent.
 */
public final class AuditListSupportTest {
    private AuditListSupportTest() {
    }

    public static void main(String[] args) {
        statusAndMode();
        sourceAndEntity();
        dateBounds();
        defaultOrderDesc();
        unsupportedEventType();
        auditUserJoinDoesNotMixCollations();
        System.out.println("AuditListSupportTest OK");
    }

    static void statusAndMode() {
        assert "FAILED".equals(AuditListSupport.statusFilter("failed"));
        assert "COMPLETED_WITH_WARNINGS".equals(AuditListSupport.statusFilter("COMPLETED_WITH_WARNINGS"));
        assert "RUNNING".equals(AuditListSupport.statusFilter("RUNNING"));
        assert "CANCELLED".equals(AuditListSupport.statusFilter("cancelled"));
        assert "CANCEL_REQUESTED".equals(AuditListSupport.statusFilter("CANCEL_REQUESTED"));
        assert AuditListSupport.statusFilter("all") == null;
        assert "FULL".equals(AuditListSupport.syncModeFilter("FULL"));
        assert "INCREMENTAL".equals(AuditListSupport.syncModeFilter("incremental"));
        assert "RECOVERY".equals(AuditListSupport.syncModeFilter("RECOVERY"));
        assert AuditListSupport.syncModeFilter("all") == null;
    }

    static void sourceAndEntity() {
        assert AuditListSupport.sourceEntities("PURE").contains("PEOPLE");
        assert AuditListSupport.sourceEntities("OPENALEX").equals(java.util.List.of("OPENALEX"));
        assert AuditListSupport.sourceEntities("SCOPUS").isEmpty();
        assert AuditListSupport.sourceEntities(null) == null;
        assert "RESEARCH_OUTPUT".equals(AuditListSupport.entityFilter("RESEARCH_OUTPUTS"));
        assert "SYNC:44".equals(AuditCatalog.eventId("44"));
        assert "44".equals(AuditCatalog.executionIdOf("SYNC:44"));
        assert "44".equals(AuditCatalog.executionIdOf("44"));
    }

    static void dateBounds() {
        OffsetDateTime from = AuditListSupport.fromTimestamp("2026-09-01");
        OffsetDateTime to = AuditListSupport.toTimestamp("2026-09-30");
        assert from != null && from.getDayOfMonth() == 1;
        assert to != null && to.getMonthValue() == 9;
        assert to.isAfter(from);
    }

    static void defaultOrderDesc() {
        assert AuditListSupport.orderBy(null, null).startsWith("e.started_at DESC");
        assert AuditListSupport.orderBy("startedAt", "asc").startsWith("e.started_at ASC");
    }

    static void unsupportedEventType() {
        assert AuditCatalog.isSupportedEventType("SYNC");
        assert AuditCatalog.isSupportedEventType(null);
        assert !AuditCatalog.isSupportedEventType("CORRECTION");
        assert !AuditCatalog.isSupportedEventType("DATA_QUALITY");
    }

    static void auditUserJoinDoesNotMixCollations() {
        String join = cl.uss.ca.repository.SyncRunRepository.STARTED_BY_USER_ON;
        assert join.contains("COLLATE utf8mb4_bin");
        assert join.contains("CAST(u.id AS CHAR CHARACTER SET utf8mb4)");
        assert !join.contains("CAST(u.id AS CHAR) = e.started_by");
    }
}
