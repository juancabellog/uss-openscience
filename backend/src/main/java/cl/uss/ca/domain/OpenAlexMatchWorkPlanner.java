package cl.uss.ca.domain;

import cl.uss.ca.domain.OpenAlexPersonMatcher.WorkEvidence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deduplicates match/work rows before persist. One {@code (match, research_output)}
 * relation can carry N authorship evidences.
 */
public final class OpenAlexMatchWorkPlanner {
    private OpenAlexMatchWorkPlanner() {
    }

    public static List<MatchWorkGroup> groupByResearchOutput(List<WorkEvidence> works) {
        Map<Long, MatchWorkGroup> groups = new LinkedHashMap<>();
        if (works == null) {
            return List.of();
        }
        for (WorkEvidence work : works) {
            if (work == null || work.researchOutputId == null) {
                continue;
            }
            MatchWorkGroup group = groups.computeIfAbsent(work.researchOutputId, id -> {
                MatchWorkGroup created = new MatchWorkGroup();
                created.researchOutputId = id;
                return created;
            });
            int order = work.sourceOrder == null ? 0 : work.sourceOrder;
            group.bySourceOrder.putIfAbsent(order, work);
        }
        List<MatchWorkGroup> items = new ArrayList<>();
        for (MatchWorkGroup group : groups.values()) {
            group.evidences.addAll(group.bySourceOrder.values());
            group.primary = primary(group.evidences);
            items.add(group);
        }
        return items;
    }

    public static WorkEvidence primary(List<WorkEvidence> evidences) {
        WorkEvidence best = null;
        for (WorkEvidence item : evidences) {
            if (item == null) {
                continue;
            }
            if (best == null || compare(item, best) < 0) {
                best = item;
            }
        }
        return best;
    }

    private static int compare(WorkEvidence left, WorkEvidence right) {
        int orcid = Boolean.compare(right.orcidMatch, left.orcidMatch);
        if (orcid != 0) {
            return orcid;
        }
        int name = Boolean.compare(
                Boolean.TRUE.equals(right.nameCompatible),
                Boolean.TRUE.equals(left.nameCompatible)
        );
        if (name != 0) {
            return name;
        }
        int leftOrder = left.sourceOrder == null ? Integer.MAX_VALUE : left.sourceOrder;
        int rightOrder = right.sourceOrder == null ? Integer.MAX_VALUE : right.sourceOrder;
        return Integer.compare(leftOrder, rightOrder);
    }

    public static final class MatchWorkGroup {
        public Long researchOutputId;
        public WorkEvidence primary;
        public final List<WorkEvidence> evidences = new ArrayList<>();
        private final Map<Integer, WorkEvidence> bySourceOrder = new LinkedHashMap<>();
    }
}
