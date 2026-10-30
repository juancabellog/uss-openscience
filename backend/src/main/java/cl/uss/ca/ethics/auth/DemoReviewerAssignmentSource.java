package cl.uss.ca.ethics.auth;

import java.util.List;

final class DemoReviewerAssignmentSource implements ReviewerAssignmentSource {
    private final List<RoleAssignment> reviewerRows;

    DemoReviewerAssignmentSource(List<RoleAssignment> rows) {
        this.reviewerRows = rows.stream().filter(item -> "REVIEWER".equals(item.roleId())).toList();
    }

    @Override
    public List<ReviewerEngagement> engagementsFor(String userId) {
        return reviewerRows.stream()
                .filter(item -> userId.equals(item.userId()))
                .map(item -> new ReviewerEngagement(item.id(), item.userId(), item.scopeId(), item.status()))
                .toList();
    }
}
