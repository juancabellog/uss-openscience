package cl.uss.ca.external.crossref;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

public final class CrossrefWorkSnapshot {
    private CrossrefWorkSnapshot() {
    }

    public static String parent(CrossrefWorkRecord work, String status) {
        if (work == null) {
            return empty(status);
        }
        return String.join("|",
                empty(status),
                empty(work.canonicalDoi),
                empty(work.type),
                empty(work.title),
                empty(work.subtitle),
                empty(work.containerTitle),
                empty(work.publisher),
                empty(work.volume),
                empty(work.issue),
                empty(work.page),
                empty(work.articleNumber),
                empty(work.language),
                number(work.referenceCount),
                number(work.isReferencedByCount),
                empty(work.publishedDate),
                empty(work.publishedPrintDate),
                empty(work.publishedOnlineDate),
                empty(work.issuedDate),
                empty(work.registrationAgencyId),
                empty(work.registrationAgency)
        );
    }

    public static String children(CrossrefWorkRecord work) {
        if (work == null) {
            return "";
        }
        return String.join("||",
                authors(work.authors),
                issns(work.issns),
                funders(work.funders),
                licenses(work.licenses),
                relations(work.relations)
        );
    }

    public static boolean same(String left, String right) {
        return Objects.equals(empty(left), empty(right));
    }

    private static String authors(List<CrossrefAuthorRecord> authors) {
        List<String> keys = new ArrayList<>();
        if (authors != null) {
            for (CrossrefAuthorRecord author : authors) {
                keys.add(String.join("~",
                        String.valueOf(author.sequenceNo),
                        empty(author.fullName),
                        empty(author.orcid),
                        affiliations(author.affiliations)
                ));
            }
        }
        return keys.stream().collect(Collectors.joining("/"));
    }

    private static String affiliations(List<CrossrefAffiliationRecord> affiliations) {
        List<String> keys = new ArrayList<>();
        if (affiliations != null) {
            for (CrossrefAffiliationRecord affiliation : affiliations) {
                keys.add(empty(affiliation.name) + "~" + empty(affiliation.ror));
            }
        }
        keys.sort(Comparator.naturalOrder());
        return String.join(",", keys);
    }

    private static String issns(List<CrossrefIssnRecord> issns) {
        List<String> keys = new ArrayList<>();
        if (issns != null) {
            for (CrossrefIssnRecord issn : issns) {
                keys.add(empty(issn.type) + ":" + empty(issn.issn).toLowerCase(Locale.ROOT));
            }
        }
        keys.sort(Comparator.naturalOrder());
        return String.join(",", keys);
    }

    private static String funders(List<CrossrefFunderRecord> funders) {
        List<String> keys = new ArrayList<>();
        if (funders != null) {
            for (CrossrefFunderRecord funder : funders) {
                keys.add(empty(funder.funderDoi) + "~" + empty(funder.name) + "~" + empty(funder.award));
            }
        }
        keys.sort(Comparator.naturalOrder());
        return String.join(",", keys);
    }

    private static String licenses(List<CrossrefLicenseRecord> licenses) {
        List<String> keys = new ArrayList<>();
        if (licenses != null) {
            for (CrossrefLicenseRecord license : licenses) {
                keys.add(empty(license.url) + "~" + empty(license.startDate) + "~" + empty(license.contentVersion));
            }
        }
        keys.sort(Comparator.naturalOrder());
        return String.join(",", keys);
    }

    private static String relations(List<CrossrefRelationRecord> relations) {
        List<String> keys = new ArrayList<>();
        if (relations != null) {
            for (CrossrefRelationRecord relation : relations) {
                keys.add(empty(relation.type).toLowerCase(Locale.ROOT) + "~" + empty(relation.doi));
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
