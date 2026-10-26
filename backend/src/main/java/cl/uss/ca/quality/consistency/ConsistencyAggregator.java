package cl.uss.ca.quality.consistency;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ConsistencyAggregator {
    private ConsistencyAggregator() {
    }

    public static ComparisonResult aggregate(List<ExternalEvidence> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return ComparisonResult.NO_EXTERNAL_DATA;
        }
        int comparable = 0;
        int differences = 0;
        int consistent = 0;
        Set<String> externalNormalized = new LinkedHashSet<>();
        for (ExternalEvidence item : evidence) {
            if (item.result == ComparisonResult.NOT_COMPARABLE || item.result == ComparisonResult.NO_EXTERNAL_DATA) {
                continue;
            }
            comparable++;
            if (item.normalizedValue != null && !item.normalizedValue.isBlank()) {
                externalNormalized.add(item.normalizedValue);
            }
            if (item.result == ComparisonResult.DIFFERENCE_DETECTED) {
                differences++;
            } else if (item.result == ComparisonResult.CONSISTENT) {
                consistent++;
            }
        }
        if (comparable == 0) {
            return ComparisonResult.NOT_COMPARABLE;
        }
        if (externalNormalized.size() > 1) {
            return ComparisonResult.EXTERNAL_SOURCE_CONFLICT;
        }
        if (differences > 0) {
            return ComparisonResult.DIFFERENCE_DETECTED;
        }
        if (consistent == comparable) {
            return ComparisonResult.CONSISTENT;
        }
        return ComparisonResult.DIFFERENCE_DETECTED;
    }

    public static boolean shouldPersistFinding(ComparisonResult aggregate, ConsistencyField field) {
        if (aggregate == ComparisonResult.DIFFERENCE_DETECTED
                || aggregate == ComparisonResult.EXTERNAL_SOURCE_CONFLICT) {
            return true;
        }
        return field == ConsistencyField.PAGES && aggregate == ComparisonResult.NOT_COMPARABLE;
    }
}
