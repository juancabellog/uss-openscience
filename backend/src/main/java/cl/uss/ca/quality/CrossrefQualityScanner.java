package cl.uss.ca.quality;

import cl.uss.ca.external.crossref.CrossrefStatus;
import cl.uss.ca.repository.CrossrefRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class CrossrefQualityScanner {
    private final ObjectMapper mapper = new ObjectMapper();

    @Inject
    CrossrefRepository repository;

    public List<DataQualityCandidate> scan(String ruleCode) {
        return scan(ruleCode, null);
    }

    public List<DataQualityCandidate> scan(String ruleCode, Long researchOutputId) {
        if (!repository.available()) {
            return List.of();
        }
        List<DataQualityCandidate> items = new ArrayList<>();
        for (CrossrefRepository.QualityRow row : repository.listQualityRows(researchOutputId)) {
            DataQualityCandidate candidate = switch (ruleCode) {
                case "CROSSREF-001" -> invalid(row);
                case "CROSSREF-002" -> notFound(row);
                case "CROSSREF-003" -> title(row);
                case "CROSSREF-004" -> container(row);
                case "CROSSREF-005" -> issn(row);
                case "CROSSREF-006" -> authors(row);
                case "CROSSREF-007" -> orcid(row);
                case "CROSSREF-008" -> affiliation(row);
                case "CROSSREF-009" -> funding(row);
                case "CROSSREF-010" -> update(row);
                default -> null;
            };
            if (candidate != null) {
                items.add(candidate);
            }
        }
        return items;
    }

    private DataQualityCandidate invalid(CrossrefRepository.QualityRow row) {
        if (!CrossrefStatus.INVALID_DOI.equals(row.status)) {
            return null;
        }
        return finding(row, "CROSSREF-001", "doi", "INVALID_DOI",
                "DOI con formato inválido.", row.normalizedDoi, null);
    }

    private DataQualityCandidate notFound(CrossrefRepository.QualityRow row) {
        if (!CrossrefStatus.NOT_FOUND.equals(row.status)) {
            return null;
        }
        return finding(row, "CROSSREF-002", "doi", "NOT_FOUND",
                "DOI no encontrado en Crossref ni en otra agencia conocida.", row.normalizedDoi, null);
    }

    private DataQualityCandidate title(CrossrefRepository.QualityRow row) {
        if (!CrossrefStatus.FOUND.equals(row.status) || !CrossrefComparison.titlesDiffer(row.pureTitle, row.crossrefTitle)) {
            return null;
        }
        return finding(row, "CROSSREF-003", "title", "TITLE_DIFFERS",
                "El título PURE difiere significativamente del título Crossref.", row.pureTitle, row.crossrefTitle);
    }

    private DataQualityCandidate container(CrossrefRepository.QualityRow row) {
        if (!CrossrefStatus.FOUND.equals(row.status)) {
            return null;
        }
        String journal = repository.pureJournalTitle(row.researchOutputId);
        if (!CrossrefComparison.containersDiffer(journal, row.containerTitle)) {
            return null;
        }
        return finding(row, "CROSSREF-004", "container_title", "CONTAINER_DIFFERS",
                "Container/journal Crossref difiere de la revista PURE.", journal, row.containerTitle);
    }

    private DataQualityCandidate issn(CrossrefRepository.QualityRow row) {
        if (!CrossrefStatus.FOUND.equals(row.status)) {
            return null;
        }
        List<String> crossref = repository.listIssns(row.workId);
        List<String> pure = repository.pureJournalIssns(row.researchOutputId);
        if (crossref.isEmpty() || pure.isEmpty()) {
            return null;
        }
        Set<String> left = new HashSet<>(pure);
        boolean overlap = false;
        for (String issn : crossref) {
            if (left.contains(issn)) {
                overlap = true;
                break;
            }
        }
        if (overlap) {
            return null;
        }
        return finding(row, "CROSSREF-005", "issn", "ISSN_DIFFERS",
                "ISSN Crossref no coincide con la revista PURE.", String.join(",", pure), String.join(",", crossref));
    }

    private DataQualityCandidate authors(CrossrefRepository.QualityRow row) {
        if (!CrossrefStatus.FOUND.equals(row.status)) {
            return null;
        }
        List<String> pure = repository.listPureAuthorNames(row.researchOutputId);
        List<String> crossref = repository.listCrossrefAuthorNames(row.workId);
        if (!CrossrefComparison.authorsDiffer(pure, crossref)) {
            return null;
        }
        return finding(row, "CROSSREF-006", "authors", "AUTHORS_DIFFERS",
                "Autoría PURE y Crossref presentan diferencias relevantes.",
                String.join(" | ", pure), String.join(" | ", crossref));
    }

    private DataQualityCandidate orcid(CrossrefRepository.QualityRow row) {
        if (!row.hasOrcid) {
            return null;
        }
        return CrossrefOrcidFinding.create(
                row,
                repository.listCrossrefOrcids(row.workId),
                repository.listPureOrcids(row.researchOutputId),
                mapper
        );
    }

    private DataQualityCandidate affiliation(CrossrefRepository.QualityRow row) {
        if (!row.hasAffiliation && !row.hasRor) {
            return null;
        }
        if (repository.pureHasAffiliation(row.researchOutputId) && !row.hasRor) {
            return null;
        }
        return finding(row, "CROSSREF-008", "affiliation", "AFFILIATION_EXTERNAL",
                "Afiliación disponible en Crossref y ausente o diferente en PURE.", null, "Crossref");
    }

    private DataQualityCandidate funding(CrossrefRepository.QualityRow row) {
        if (!row.hasFunding || repository.pureHasFunding(row.researchOutputId)) {
            return null;
        }
        return finding(row, "CROSSREF-009", "funding", "FUNDING_EXTERNAL",
                "Funding informado por Crossref y no disponible en PURE.", null, "Crossref");
    }

    private DataQualityCandidate update(CrossrefRepository.QualityRow row) {
        if (!row.hasUpdate && !row.hasRetraction) {
            return null;
        }
        String kind = row.hasRetraction ? "RETRACTION" : "UPDATE";
        return finding(row, "CROSSREF-010", "update", kind,
                "Crossref informa correction, retraction o update relevante.", null, kind);
    }

    private DataQualityCandidate finding(
            CrossrefRepository.QualityRow row,
            String code,
            String field,
            String kind,
            String summary,
            String current,
            String suggested
    ) {
        ObjectNode evidence = DataQualityCandidate.evidence(mapper);
        evidence.put("source", "CROSSREF");
        evidence.put("doi", row.normalizedDoi == null ? "" : row.normalizedDoi);
        evidence.put("status", row.status == null ? "" : row.status);
        evidence.put("contextKey", kind + "|" + row.workId);
        return DataQualityCandidate.of(
                code,
                "RESEARCH_OUTPUT",
                row.researchOutputId,
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
