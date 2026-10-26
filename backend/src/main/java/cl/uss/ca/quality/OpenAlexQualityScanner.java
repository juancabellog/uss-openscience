package cl.uss.ca.quality;

import cl.uss.ca.domain.OpenAlexPersonMatcher;
import cl.uss.ca.repository.OpenAlexRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class OpenAlexQualityScanner {
    private final ObjectMapper mapper = new ObjectMapper();

    @Inject
    OpenAlexRepository repository;

    public List<DataQualityCandidate> scan(String ruleCode) {
        return scan(ruleCode, null);
    }

    public List<DataQualityCandidate> scan(String ruleCode, Long personId) {
        if (!repository.available()) {
            return List.of();
        }
        List<DataQualityCandidate> items = new ArrayList<>();
        Map<Long, List<OpenAlexRepository.QualityRow>> byPerson = new LinkedHashMap<>();
        Map<String, List<OpenAlexRepository.QualityRow>> byAuthor = new LinkedHashMap<>();
        for (OpenAlexRepository.QualityRow row : repository.listQualityRows(personId)) {
            byPerson.computeIfAbsent(row.personId, key -> new ArrayList<>()).add(row);
            if (row.openalexAuthorId != null) {
                byAuthor.computeIfAbsent(row.openalexAuthorId, key -> new ArrayList<>()).add(row);
            }
        }
        for (List<OpenAlexRepository.QualityRow> rows : byPerson.values()) {
            DataQualityCandidate candidate = switch (ruleCode) {
                case "OPENALEX-001" -> strongUnconfirmed(rows);
                case "OPENALEX-002" -> orcidConflict(rows);
                case "OPENALEX-003" -> multipleAuthors(rows);
                default -> null;
            };
            if (candidate != null) {
                items.add(candidate);
            }
        }
        if ("OPENALEX-004".equals(ruleCode)) {
            for (List<OpenAlexRepository.QualityRow> rows : byAuthor.values()) {
                DataQualityCandidate candidate = multiplePersons(rows);
                if (candidate != null) {
                    items.add(candidate);
                }
            }
        }
        return items;
    }

    private DataQualityCandidate strongUnconfirmed(List<OpenAlexRepository.QualityRow> rows) {
        boolean confirmed = rows.stream().anyMatch(row -> OpenAlexPersonMatcher.STATUS_CONFIRMED.equals(row.status));
        OpenAlexRepository.QualityRow strong = rows.stream()
                .filter(row -> OpenAlexPersonMatcher.STATUS_STRONG.equals(row.status))
                .findFirst()
                .orElse(null);
        if (confirmed || strong == null) {
            return null;
        }
        return finding(strong, "OPENALEX-001", "openalex_author_id", "STRONG_UNCONFIRMED",
                "Hay un match OpenAlex fuerte pendiente de confirmación.",
                null, strong.openalexAuthorId);
    }

    private DataQualityCandidate orcidConflict(List<OpenAlexRepository.QualityRow> rows) {
        for (OpenAlexRepository.QualityRow row : rows) {
            if (row.personOrcid == null || row.authorOrcid == null || row.orcidMatch) {
                continue;
            }
            if (!row.personOrcid.equalsIgnoreCase(row.authorOrcid)
                    && significant(row)) {
                return finding(row, "OPENALEX-002", "orcid", "ORCID_CONFLICT",
                        "ORCID PURE y OpenAlex presentan conflicto.",
                        row.personOrcid, row.authorOrcid);
            }
        }
        return null;
    }

    private DataQualityCandidate multipleAuthors(List<OpenAlexRepository.QualityRow> rows) {
        long significant = rows.stream().filter(this::significant).count();
        if (significant < 2) {
            return null;
        }
        OpenAlexRepository.QualityRow row = rows.get(0);
        return finding(row, "OPENALEX-003", "openalex_author_id", "MULTIPLE_AUTHORS",
                "Una persona coincide con múltiples OpenAlex Authors.",
                null, String.valueOf(significant));
    }

    private DataQualityCandidate multiplePersons(List<OpenAlexRepository.QualityRow> rows) {
        long significant = rows.stream().filter(this::significant).map(row -> row.personId).distinct().count();
        if (significant < 2) {
            return null;
        }
        OpenAlexRepository.QualityRow row = rows.get(0);
        return finding(row, "OPENALEX-004", "openalex_author_id", "MULTIPLE_PERSONS",
                "Un OpenAlex Author coincide con múltiples personas PURE.",
                row.openalexAuthorId, String.valueOf(significant));
    }

    private boolean significant(OpenAlexRepository.QualityRow row) {
        if (row.openalexAuthorId == null || row.status == null) {
            return false;
        }
        if (OpenAlexPersonMatcher.STATUS_REJECTED.equals(row.status)) {
            return false;
        }
        return row.matchingWorkCount != null && row.matchingWorkCount >= 1;
    }

    private DataQualityCandidate finding(
            OpenAlexRepository.QualityRow row,
            String code,
            String field,
            String kind,
            String summary,
            String current,
            String suggested
    ) {
        ObjectNode evidence = DataQualityCandidate.evidence(mapper);
        evidence.put("source", "OPENALEX");
        evidence.put("openalexAuthorId", row.openalexAuthorId == null ? "" : row.openalexAuthorId);
        evidence.put("status", row.status == null ? "" : row.status);
        evidence.put("contextKey", kind + "|" + row.personId + "|" + (row.openalexAuthorId == null ? "" : row.openalexAuthorId));
        return DataQualityCandidate.of(
                code,
                "PERSON",
                row.personId,
                row.pureId,
                row.uuid,
                field,
                kind,
                DataQualityCatalog.MODE_VIEW,
                summary,
                current,
                suggested,
                evidence
        );
    }
}
