package cl.uss.ca.quality;

import cl.uss.ca.repository.CrossrefRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * CROSSREF-007 representation: one finding per research output + Crossref work.
 * suggested_value is a short VIEW_ONLY summary; the ORCID list lives in evidence_json.
 */
final class CrossrefOrcidFinding {
    static final String RULE_CODE = "CROSSREF-007";
    static final String FIELD = "orcid";
    static final String ISSUE_KIND = "ORCID_EXTERNAL";
    static final String SUMMARY = "ORCID informado por Crossref y ausente institucionalmente.";

    private CrossrefOrcidFinding() {
    }

    static DataQualityCandidate create(
            CrossrefRepository.QualityRow row,
            List<String> crossrefOrcids,
            List<String> pureOrcids,
            ObjectMapper mapper
    ) {
        List<String> external = DataQualityIdentifiers.uniqueNormalizedOrcids(crossrefOrcids);
        List<String> institutional = DataQualityIdentifiers.uniqueNormalizedOrcids(pureOrcids);
        Set<String> local = new HashSet<>(institutional);
        List<String> missing = new ArrayList<>();
        for (String orcid : external) {
            if (!local.contains(orcid)) {
                missing.add(orcid);
            }
        }
        if (missing.isEmpty()) {
            return null;
        }
        ObjectNode evidence = DataQualityCandidate.evidence(mapper);
        evidence.put("source", "CROSSREF");
        evidence.put("doi", row.normalizedDoi == null ? "" : row.normalizedDoi);
        evidence.put("status", row.status == null ? "" : row.status);
        evidence.put("contextKey", ISSUE_KIND + "|" + row.workId);
        evidence.put("externalOrcidCount", missing.size());
        ArrayNode orcids = evidence.putArray("externalOrcids");
        for (String orcid : missing) {
            orcids.add(orcid);
        }
        return DataQualityCandidate.of(
                RULE_CODE,
                "RESEARCH_OUTPUT",
                row.researchOutputId,
                row.pureId,
                row.uuid,
                FIELD,
                ISSUE_KIND,
                DataQualityCatalog.MODE_VIEW,
                SUMMARY,
                currentValue(institutional),
                suggestedValue(missing.size()),
                evidence
        );
    }

    static String suggestedValue(int missingCount) {
        if (missingCount == 1) {
            return "Crossref informa 1 ORCID de autor sin equivalencia institucional en este registro.";
        }
        return "Crossref informa " + formatCount(missingCount)
                + " ORCID de autores sin equivalencia institucional en este registro.";
    }

    static String currentValue(List<String> institutional) {
        if (institutional == null || institutional.isEmpty()) {
            return null;
        }
        if (institutional.size() <= 40) {
            return String.join(",", institutional);
        }
        return formatCount(institutional.size())
                + " ORCID institucionales vinculados a este registro.";
    }

    static String formatCount(int count) {
        String digits = Integer.toString(count);
        StringBuilder builder = new StringBuilder();
        int remaining = digits.length();
        for (int index = 0; index < digits.length(); index++) {
            if (index > 0 && remaining % 3 == 0) {
                builder.append('.');
            }
            builder.append(digits.charAt(index));
            remaining--;
        }
        return builder.toString();
    }

    static String findingKey(long researchOutputId, long workId) {
        return RULE_CODE + "|RESEARCH_OUTPUT|" + researchOutputId + "|" + FIELD + "|"
                + ISSUE_KIND + "|" + workId;
    }
}
