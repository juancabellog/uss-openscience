package cl.uss.ca.ethics.auth;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Resuelve a qué comité, proyecto y proceso pertenece un contexto.
 * Los identificadores son los del prototipo ethics-uss, no un catálogo nuevo.
 */
public final class EthicsResourceIndex {
    private final Map<String, CaseRef> byCode;
    private final Map<String, CaseRef> byId;
    private final Map<String, String> projectIdByCode;
    private final Set<String> committees;
    private final Set<String> projectIds;

    public EthicsResourceIndex(
            Map<String, CaseRef> byCode,
            Map<String, CaseRef> byId,
            Map<String, String> projectIdByCode,
            Set<String> committees,
            Set<String> projectIds
    ) {
        this.byCode = Map.copyOf(byCode);
        this.byId = Map.copyOf(byId);
        this.projectIdByCode = Map.copyOf(projectIdByCode);
        this.committees = Set.copyOf(committees);
        this.projectIds = Set.copyOf(projectIds);
    }

    public static EthicsResourceIndex prototype() {
        Map<String, String> projects = new LinkedHashMap<>();
        projects.put("ETI-2026-001", "prj-1");
        projects.put("ETI-2026-002", "prj-2");
        projects.put("ETI-2026-003", "prj-3");
        projects.put("ETI-2025-018", "prj-4");
        projects.put("ETI-2026-004", "prj-5");
        projects.put("ETI-2026-005", "prj-6");
        Map<String, CaseRef> byCode = new LinkedHashMap<>();
        Map<String, CaseRef> byId = new LinkedHashMap<>();
        add(byCode, byId, "case-1-cec", "CEC-2026-001", "prj-1", "CEC");
        add(byCode, byId, "case-1-bio", "BIO-2026-001", "prj-1", "BIOSEGURIDAD");
        add(byCode, byId, "case-2", "CECUA-2026-004", "prj-2", "CECUA");
        add(byCode, byId, "case-adm", "CECUA-2026-014", "prj-2", "CECUA");
        add(byCode, byId, "case-pres", "CECUA-2026-021", "prj-1", "CECUA");
        add(byCode, byId, "case-4", "CEC-2025-018", "prj-4", "CEC");
        add(byCode, byId, "case-5", "CEC-2026-011", "prj-5", "CEC");
        return new EthicsResourceIndex(
                byCode,
                byId,
                projects,
                Set.of("CEC", "CECUA", "BIOSEGURIDAD"),
                Set.copyOf(projects.values())
        );
    }

    public boolean knownCommittee(String committee) {
        return committee != null && committees.contains(committee);
    }

    public String canonicalProjectId(String projectIdOrCode) {
        if (projectIdOrCode == null) {
            return null;
        }
        if (projectIds.contains(projectIdOrCode)) {
            return projectIdOrCode;
        }
        return projectIdByCode.get(projectIdOrCode);
    }

    public boolean knownProcess(String processId) {
        return caseOfProcess(processId) != null;
    }

    public Resolved resolve(AuthorizationContext context) {
        AuthorizationContext safe = context == null ? AuthorizationContext.empty() : context;
        String processId = blankToNull(safe.processId());
        String caseId = blankToNull(safe.committeeCaseId());
        String requestedProject = blankToNull(safe.projectId());
        String projectId = canonicalProjectId(requestedProject);
        String committee = blankToNull(safe.committeeType());
        boolean mismatch = false;
        CaseRef fromProcess = caseOfProcess(processId);
        if (processId != null && fromProcess == null) {
            mismatch = true;
        }
        if (fromProcess != null) {
            mismatch = mismatch || conflicts(committee, fromProcess.committee) || conflicts(projectId, fromProcess.projectId) || conflicts(caseId, fromProcess.id);
            committee = fromProcess.committee;
            projectId = fromProcess.projectId;
            caseId = fromProcess.id;
        }
        CaseRef fromCase = caseId == null ? null : byId.get(caseId);
        if (caseId != null && fromCase == null) {
            mismatch = true;
        }
        if (fromCase != null) {
            mismatch = mismatch || conflicts(committee, fromCase.committee) || conflicts(projectId, fromCase.projectId);
            committee = fromCase.committee;
            projectId = fromCase.projectId;
        }
        if (requestedProject != null && projectId == null) {
            mismatch = true;
        }
        if (committee != null && !knownCommittee(committee)) {
            mismatch = true;
        }
        return new Resolved(committee, projectId, caseId, processId, mismatch);
    }

    private CaseRef caseOfProcess(String processId) {
        if (processId == null || processId.isBlank()) {
            return null;
        }
        String code = processId;
        int round = processId.indexOf(':');
        if (round >= 0) {
            code = processId.substring(0, round);
            String suffix = processId.substring(round + 1);
            if (!suffix.matches("R[1-9][0-9]*")) {
                return null;
            }
        }
        return byCode.get(code);
    }

    private static boolean conflicts(String requested, String actual) {
        return requested != null && actual != null && !requested.equals(actual);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void add(
            Map<String, CaseRef> byCode,
            Map<String, CaseRef> byId,
            String id,
            String code,
            String projectId,
            String committee
    ) {
        CaseRef ref = new CaseRef(id, code, projectId, committee);
        byCode.put(code, ref);
        byId.put(id, ref);
    }

    public record CaseRef(String id, String code, String projectId, String committee) {
    }

    public record Resolved(String committee, String projectId, String caseId, String processId, boolean mismatch) {
    }
}
