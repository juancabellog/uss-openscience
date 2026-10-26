package cl.uss.ca.directory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DirectoryImportPlan {
    public final List<DirectoryPlannedRow> rows = new ArrayList<>();
    public final List<DirectoryIssue> issues = new ArrayList<>();
    public final Set<Long> seenPersonIds = new HashSet<>();
    public int created;
    public int updated;
    public int unchanged;
    public int reactivated;
    public int rejected;
    public int wouldInactivate;
    public boolean censusReliable = true;
    public List<String> unmappedHeaders = List.of();
    public String sheetName;

    public void addIssue(DirectoryIssue issue) {
        issues.add(issue);
        if ("DUPLICATE_RUT_IN_FILE".equals(issue.code) || "DUPLICATE_EMAIL_IN_FILE".equals(issue.code)) {
            censusReliable = false;
        }
    }
}
