package cl.uss.ca.external.unpaywall;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class UnpaywallWorkSnapshot {
    private UnpaywallWorkSnapshot() {
    }

    public static String parent(UnpaywallWorkRecord work, String outcome) {
        if (work == null) {
            return outcome == null ? "" : outcome;
        }
        return String.join("|",
                empty(outcome),
                flag(work.isOa),
                empty(work.oaStatus),
                empty(work.genre),
                empty(work.title),
                work.year == null ? "" : String.valueOf(work.year),
                empty(work.publishedDate),
                empty(work.publisher),
                empty(work.journalName),
                empty(work.journalIssns),
                empty(work.journalIssnL),
                flag(work.journalIsOa),
                flag(work.journalIsInDoaj),
                flag(work.hasRepositoryCopy),
                empty(work.doiUrl),
                UnpaywallLocationIdentity.snapshot(work.bestOaLocation)
        );
    }

    public static String locations(List<UnpaywallLocationRecord> locations) {
        if (locations == null || locations.isEmpty()) {
            return "";
        }
        List<String> keys = new ArrayList<>();
        for (UnpaywallLocationRecord location : locations) {
            keys.add(UnpaywallLocationIdentity.snapshot(location));
        }
        keys.sort(Comparator.naturalOrder());
        return keys.stream().collect(Collectors.joining("||"));
    }

    public static boolean sameParent(String left, String right) {
        return Objects.equals(empty(left), empty(right));
    }

    public static boolean sameLocations(String left, String right) {
        return Objects.equals(empty(left), empty(right));
    }

    private static String empty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String flag(Boolean value) {
        if (value == null) {
            return "";
        }
        return value ? "1" : "0";
    }
}
