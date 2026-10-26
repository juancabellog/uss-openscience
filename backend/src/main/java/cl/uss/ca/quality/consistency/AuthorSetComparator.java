package cl.uss.ca.quality.consistency;

import cl.uss.ca.domain.AuthorNameMatcher;
import cl.uss.ca.quality.DataQualityIdentifiers;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class AuthorSetComparator {
    private static final AuthorNameMatcher NAMES = new AuthorNameMatcher();

    private AuthorSetComparator() {
    }

    public static ComparisonResult compare(List<AuthorEvidence> pure, List<AuthorEvidence> external, Boolean complete) {
        if (pure == null || pure.isEmpty() || external == null || external.isEmpty()) {
            return ComparisonResult.NOT_COMPARABLE;
        }
        match(pure, external);
        int matched = count(external, "MATCHED");
        int externalOnly = count(external, "EXTERNAL_ONLY");
        int pureOnly = count(pure, "PURE_ONLY");
        if (Boolean.FALSE.equals(complete) && externalOnly == 0 && matched > 0) {
            return ComparisonResult.CONSISTENT;
        }
        if (matched == 0) {
            return ComparisonResult.DIFFERENCE_DETECTED;
        }
        if (pureOnly == 0 && externalOnly == 0) {
            return ComparisonResult.CONSISTENT;
        }
        if (Boolean.FALSE.equals(complete) && pureOnly == 0) {
            return ComparisonResult.CONSISTENT;
        }
        return ComparisonResult.DIFFERENCE_DETECTED;
    }

    public static void match(List<AuthorEvidence> pure, List<AuthorEvidence> external) {
        boolean[] used = new boolean[external.size()];
        for (AuthorEvidence local : pure) {
            int index = findMatch(local, external, used);
            if (index >= 0) {
                used[index] = true;
                local.matchStatus = "MATCHED";
                external.get(index).matchStatus = "MATCHED";
            } else {
                local.matchStatus = "PURE_ONLY";
            }
        }
        for (int i = 0; i < external.size(); i++) {
            if (!used[i] && external.get(i).matchStatus == null) {
                external.get(i).matchStatus = "EXTERNAL_ONLY";
            }
        }
    }

    public static String summary(List<AuthorEvidence> pure, List<AuthorEvidence> external) {
        return "PURE " + size(pure)
                + " · external " + size(external)
                + " · MATCHED " + count(external, "MATCHED")
                + " · PURE_ONLY " + count(pure, "PURE_ONLY")
                + " · EXTERNAL_ONLY " + count(external, "EXTERNAL_ONLY");
    }

    public static String fingerprint(List<AuthorEvidence> authors) {
        Set<String> keys = new LinkedHashSet<>();
        if (authors != null) {
            for (AuthorEvidence author : authors) {
                if (author.orcid != null) {
                    keys.add("orcid:" + author.orcid);
                } else if (author.scopusAuthorId != null) {
                    keys.add("scopus:" + author.scopusAuthorId);
                } else if (author.openAlexAuthorId != null) {
                    keys.add("openalex:" + author.openAlexAuthorId);
                } else if (author.fullName != null) {
                    keys.add("name:" + BibliographicComparators.normalizeExact(author.fullName));
                }
            }
        }
        return String.join(",", keys);
    }

    private static int findMatch(AuthorEvidence local, List<AuthorEvidence> external, boolean[] used) {
        for (int i = 0; i < external.size(); i++) {
            if (used[i]) {
                continue;
            }
            AuthorEvidence remote = external.get(i);
            if (idMatch(local.orcid, remote.orcid, true)
                    || idMatch(local.scopusAuthorId, remote.scopusAuthorId, false)
                    || idMatch(local.openAlexAuthorId, remote.openAlexAuthorId, false)) {
                return i;
            }
        }
        for (int i = 0; i < external.size(); i++) {
            if (used[i]) {
                continue;
            }
            if (NAMES.coinciden(local.fullName, external.get(i).fullName)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean idMatch(String left, String right, boolean orcid) {
        if (left == null || right == null) {
            return false;
        }
        if (orcid) {
            String a = DataQualityIdentifiers.normalizeOrcid(left);
            String b = DataQualityIdentifiers.normalizeOrcid(right);
            return a != null && a.equalsIgnoreCase(b);
        }
        return left.trim().equalsIgnoreCase(right.trim());
    }

    private static int count(List<AuthorEvidence> authors, String status) {
        int total = 0;
        if (authors != null) {
            for (AuthorEvidence author : authors) {
                if (status.equals(author.matchStatus)) {
                    total++;
                }
            }
        }
        return total;
    }

    private static int size(List<AuthorEvidence> authors) {
        return authors == null ? 0 : authors.size();
    }

    public static List<AuthorEvidence> copy(List<AuthorEvidence> authors) {
        List<AuthorEvidence> copy = new ArrayList<>();
        if (authors == null) {
            return copy;
        }
        for (AuthorEvidence author : authors) {
            AuthorEvidence item = new AuthorEvidence();
            item.fullName = author.fullName;
            item.orcid = author.orcid;
            item.scopusAuthorId = author.scopusAuthorId;
            item.openAlexAuthorId = author.openAlexAuthorId;
            item.position = author.position;
            copy.add(item);
        }
        return copy;
    }
}
