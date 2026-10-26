package cl.uss.ca.external.dspace;

import cl.uss.ca.domain.AuthorNameMatcher;
import cl.uss.ca.domain.DoiNormalizer;
import cl.uss.ca.quality.CrossrefComparison;
import cl.uss.ca.quality.consistency.PublishedYearExtractor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Relates DSpace items to PURE research outputs. Exact DOI/handle/UUID are confirmed.
 * Title+year+authors are candidates only and stay unconfirmed when ambiguous.
 */
public final class DspaceMatcher {
    public static final String DOI = "DOI";
    public static final String HANDLE = "HANDLE";
    public static final String UUID = "UUID";
    public static final String TITLE_AUTHOR_YEAR = "TITLE_AUTHOR_YEAR";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String CANDIDATE = "CANDIDATE";
    public static final String SECONDARY = "SECONDARY";

    private final AuthorNameMatcher names = new AuthorNameMatcher();

    public List<DspaceMatchDecision> match(
            List<DspaceItemRecord> items,
            List<DspacePureCandidate> outputs
    ) {
        List<DspaceMatchDecision> decisions = new ArrayList<>();
        if (items == null || outputs == null) {
            return decisions;
        }
        Map<String, List<DspacePureCandidate>> byDoi = indexDoi(outputs);
        Map<String, List<DspacePureCandidate>> byHandle = indexHandle(outputs);
        Map<String, List<DspacePureCandidate>> byUuid = indexUuid(outputs);
        Map<String, List<DspaceItemRecord>> itemsByDoi = new LinkedHashMap<>();
        for (DspaceItemRecord item : items) {
            if (item == null || item.uuid == null) {
                continue;
            }
            String doi = DoiNormalizer.normalize(item.normalizedDoi != null ? item.normalizedDoi : item.doiRaw);
            if (doi != null) {
                itemsByDoi.computeIfAbsent(doi, key -> new ArrayList<>()).add(item);
            }
            List<DspacePureCandidate> uuidHits = byUuid.get(item.uuid.toLowerCase(Locale.ROOT));
            if (uuidHits != null && uuidHits.size() == 1) {
                decisions.add(DspaceMatchDecision.of(item.uuid, uuidHits.get(0).researchOutputId, UUID, CONFIRMED, true, 1.0));
                continue;
            }
            String handle = normalizeHandle(item.handle);
            List<DspacePureCandidate> handleHits = handle == null ? null : byHandle.get(handle);
            if (handleHits != null && handleHits.size() == 1) {
                decisions.add(DspaceMatchDecision.of(item.uuid, handleHits.get(0).researchOutputId, HANDLE, CONFIRMED, true, 1.0));
                continue;
            }
            List<DspacePureCandidate> doiHits = doi == null ? null : byDoi.get(doi);
            if (doiHits != null && doiHits.size() == 1) {
                boolean canonical = isPreferredForDoi(item, itemsByDoi.get(doi));
                decisions.add(DspaceMatchDecision.of(
                        item.uuid,
                        doiHits.get(0).researchOutputId,
                        DOI,
                        canonical ? CONFIRMED : SECONDARY,
                        canonical,
                        1.0
                ));
                continue;
            }
            if (doiHits != null && doiHits.size() > 1) {
                continue;
            }
            if (doi != null) {
                continue;
            }
            List<DspacePureCandidate> titleHits = titleYearAuthorHits(item, outputs);
            if (titleHits.size() == 1) {
                DspaceMatchDecision decision = DspaceMatchDecision.of(
                        item.uuid,
                        titleHits.get(0).researchOutputId,
                        TITLE_AUTHOR_YEAR,
                        CANDIDATE,
                        false,
                        CrossrefComparison.TITLE_JACCARD_THRESHOLD
                );
                decisions.add(decision);
            }
        }
        return decisions;
    }

    public static String normalizeHandle(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        int handleIdx = value.toLowerCase(Locale.ROOT).indexOf("/handle/");
        if (handleIdx >= 0) {
            value = value.substring(handleIdx + "/handle/".length());
        }
        if (value.startsWith("https://") || value.startsWith("http://")) {
            int slash = value.indexOf('/', value.indexOf("://") + 3);
            value = slash < 0 ? value : value.substring(slash + 1);
        }
        value = value.replaceFirst("^/+", "");
        return value.isBlank() ? null : value;
    }

    private List<DspacePureCandidate> titleYearAuthorHits(DspaceItemRecord item, List<DspacePureCandidate> outputs) {
        List<DspacePureCandidate> hits = new ArrayList<>();
        String title = item.name;
        Integer year = PublishedYearExtractor.from(
                DspaceHalParser.firstMetadata(item.metadata, "dc.date.issued"),
                DspaceHalParser.firstMetadata(item.metadata, "dc.date")
        );
        List<String> authors = DspaceHalParser.allMetadata(item.metadata, "dc.contributor.author");
        for (DspacePureCandidate output : outputs) {
            if (output == null || !output.inResearchScope || output.normalizedDoi != null) {
                continue;
            }
            if (year == null || output.year == null || !year.equals(output.year)) {
                continue;
            }
            if (CrossrefComparison.titlesDiffer(output.title, title)) {
                continue;
            }
            if (!authorsOverlap(authors, output.authors)) {
                continue;
            }
            hits.add(output);
        }
        return hits;
    }

    private boolean authorsOverlap(List<String> left, List<String> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return false;
        }
        for (String a : left) {
            for (String b : right) {
                if (names.coinciden(a, b)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isPreferredForDoi(DspaceItemRecord item, List<DspaceItemRecord> siblings) {
        if (siblings == null || siblings.size() <= 1) {
            return true;
        }
        DspaceItemRecord best = siblings.get(0);
        for (DspaceItemRecord candidate : siblings) {
            if (rank(candidate) > rank(best)) {
                best = candidate;
            }
        }
        return item.uuid.equals(best.uuid);
    }

    private static int rank(DspaceItemRecord item) {
        String access = item.access == null ? null : DspaceAccessClassifier.normalizeStatus(item.access.status);
        if (DspaceAccessClassifier.OPEN_ACCESS.equals(access)) {
            return 3;
        }
        if (DspaceAccessClassifier.EMBARGO.equals(access)) {
            return 2;
        }
        if (DspaceAccessClassifier.RESTRICTED.equals(access)) {
            return 1;
        }
        return 0;
    }

    private static Map<String, List<DspacePureCandidate>> indexDoi(List<DspacePureCandidate> outputs) {
        Map<String, List<DspacePureCandidate>> map = new LinkedHashMap<>();
        for (DspacePureCandidate output : outputs) {
            String doi = DoiNormalizer.normalize(output.normalizedDoi);
            if (doi != null) {
                map.computeIfAbsent(doi, key -> new ArrayList<>()).add(output);
            }
        }
        return map;
    }

    private static Map<String, List<DspacePureCandidate>> indexHandle(List<DspacePureCandidate> outputs) {
        Map<String, List<DspacePureCandidate>> map = new LinkedHashMap<>();
        for (DspacePureCandidate output : outputs) {
            String handle = normalizeHandle(output.handle);
            if (handle != null) {
                map.computeIfAbsent(handle, key -> new ArrayList<>()).add(output);
            }
        }
        return map;
    }

    private static Map<String, List<DspacePureCandidate>> indexUuid(List<DspacePureCandidate> outputs) {
        Map<String, List<DspacePureCandidate>> map = new LinkedHashMap<>();
        for (DspacePureCandidate output : outputs) {
            if (output.dspaceUuid != null && !output.dspaceUuid.isBlank()) {
                map.computeIfAbsent(output.dspaceUuid.trim().toLowerCase(Locale.ROOT), key -> new ArrayList<>()).add(output);
            }
        }
        return map;
    }
}
