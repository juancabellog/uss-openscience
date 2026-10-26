package cl.uss.ca.directory;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class DirectoryImportPlanner {

    public DirectoryImportPlan plan(
            List<DirectoryIncomingRow> incoming,
            DirectoryCatalog catalog,
            List<DirectoryIssue> preIssues
    ) {
        DirectoryImportPlan plan = new DirectoryImportPlan();
        if (preIssues != null) {
            for (DirectoryIssue issue : preIssues) {
                plan.addIssue(issue);
            }
        }
        Map<String, List<Integer>> rutRows = new HashMap<>();
        Map<String, List<Integer>> emailRows = new HashMap<>();
        for (DirectoryIncomingRow row : incoming) {
            if (row.rutHmac != null) {
                rutRows.computeIfAbsent(row.rutHmac, key -> new ArrayList<>()).add(row.sourceRowNumber);
            }
            if (row.emailNormalized != null) {
                emailRows.computeIfAbsent(row.emailNormalized, key -> new ArrayList<>()).add(row.sourceRowNumber);
            }
        }
        Set<Integer> duplicateRows = new HashSet<>();
        addFileDuplicates(plan, rutRows, "DUPLICATE_RUT_IN_FILE", duplicateRows);
        addFileDuplicates(plan, emailRows, "DUPLICATE_EMAIL_IN_FILE", duplicateRows);
        Set<Integer> preIssueRows = new HashSet<>();
        for (DirectoryIssue issue : plan.issues) {
            if ("INVALID_RUT".equals(issue.code) || "INVALID_EMAIL".equals(issue.code)) {
                preIssueRows.add(issue.sourceRowNumber);
            }
        }
        for (DirectoryIncomingRow row : incoming) {
            if (duplicateRows.contains(row.sourceRowNumber) || preIssueRows.contains(row.sourceRowNumber)) {
                plan.rows.add(new DirectoryPlannedRow(row, DirectoryReconcileDecision.ISSUE, issueCode(plan, row.sourceRowNumber), null));
                plan.rejected++;
                continue;
            }
            DirectoryPersonRef byRut = row.rutHmac == null ? null : catalog.byRut.get(row.rutHmac);
            DirectoryPersonRef byEmail = row.emailNormalized == null ? null : catalog.byEmail.get(row.emailNormalized);
            DirectoryReconcileResult result = DirectoryReconciler.decide(row, byRut, byEmail);
            plan.rows.add(new DirectoryPlannedRow(row, result.decision, result.issueCode, result.existing));
            if (result.decision == DirectoryReconcileDecision.ISSUE) {
                plan.addIssue(new DirectoryIssue(
                        row.sourceRowNumber,
                        result.issueCode,
                        result.existing == null ? null : result.existing.id,
                        byRut != null && byEmail != null && byRut.id != byEmail.id ? byEmail.id : null
                ));
                plan.rejected++;
                continue;
            }
            classify(plan, result.decision);
            if (result.existing != null) {
                plan.seenPersonIds.add(result.existing.id);
            }
        }
        if (plan.rejected > 0) {
            plan.censusReliable = false;
        }
        for (Long id : catalog.activeIds) {
            if (!plan.seenPersonIds.contains(id)) {
                plan.wouldInactivate++;
            }
        }
        return plan;
    }

    private static void addFileDuplicates(
            DirectoryImportPlan plan,
            Map<String, List<Integer>> grouped,
            String code,
            Set<Integer> duplicateRows
    ) {
        for (List<Integer> rows : grouped.values()) {
            if (rows.size() < 2) {
                continue;
            }
            for (Integer row : rows) {
                duplicateRows.add(row);
                plan.addIssue(new DirectoryIssue(row, code, null, null));
            }
        }
    }

    private static String issueCode(DirectoryImportPlan plan, int rowNumber) {
        for (int i = plan.issues.size() - 1; i >= 0; i--) {
            DirectoryIssue issue = plan.issues.get(i);
            if (issue.sourceRowNumber == rowNumber) {
                return issue.code;
            }
        }
        return "ISSUE";
    }

    private static void classify(DirectoryImportPlan plan, DirectoryReconcileDecision decision) {
        switch (decision) {
            case CREATE -> plan.created++;
            case UPDATE -> plan.updated++;
            case UNCHANGED -> plan.unchanged++;
            case REACTIVATE -> plan.reactivated++;
            case ISSUE -> plan.rejected++;
        }
    }
}
