package cl.uss.ca.external.openalex;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

public final class OpenAlexWorkSnapshot {
    private OpenAlexWorkSnapshot() {
    }

    public static String parent(OpenAlexWorkRecord work, String status) {
        if (work == null) {
            return empty(status);
        }
        return String.join("|",
                empty(status),
                empty(work.openalexWorkId),
                empty(work.doi),
                empty(work.displayName),
                number(work.publicationYear),
                empty(work.publicationDate),
                empty(work.workType),
                number(work.citedByCount),
                number(work.referencedWorksCount),
                empty(work.oaStatus),
                empty(work.language),
                empty(work.primarySourceId),
                empty(work.updatedDate)
        );
    }

    public static String authorships(OpenAlexWorkRecord work) {
        if (work == null) {
            return "";
        }
        List<String> keys = new ArrayList<>();
        for (OpenAlexAuthorshipRecord authorship : work.authorships) {
            keys.add(String.join("~",
                    String.valueOf(authorship.sourceOrder),
                    empty(authorship.authorPosition),
                    empty(authorship.openalexAuthorId),
                    empty(authorship.rawAuthorName),
                    empty(authorship.authorOrcid),
                    empty(authorship.observedOrcid),
                    institutions(authorship.institutions)
            ));
        }
        return keys.stream().collect(Collectors.joining("/"));
    }

    public static String author(OpenAlexAuthorRecord author) {
        if (author == null) {
            return "";
        }
        return String.join("|",
                empty(author.openalexAuthorId),
                empty(author.displayName),
                empty(author.orcid),
                number(author.worksCount),
                number(author.citedByCount),
                empty(author.updatedDate)
        );
    }

    public static boolean same(String left, String right) {
        return Objects.equals(empty(left), empty(right));
    }

    private static String institutions(List<OpenAlexInstitutionRecord> institutions) {
        List<String> keys = new ArrayList<>();
        if (institutions != null) {
            for (OpenAlexInstitutionRecord institution : institutions) {
                keys.add(empty(institution.openalexInstitutionId)
                        + "~" + empty(institution.ror).toLowerCase(Locale.ROOT)
                        + "~" + empty(institution.displayName));
            }
        }
        keys.sort(Comparator.naturalOrder());
        return String.join(",", keys);
    }

    private static String empty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String number(Integer value) {
        return value == null ? "" : String.valueOf(value);
    }
}
