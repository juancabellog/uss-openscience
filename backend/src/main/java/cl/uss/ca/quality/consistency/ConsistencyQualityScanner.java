package cl.uss.ca.quality.consistency;

import cl.uss.ca.quality.DataQualityCandidate;
import cl.uss.ca.quality.DataQualityCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class ConsistencyQualityScanner {
    private final ObjectMapper mapper = new ObjectMapper();

    @Inject
    ConsistencyEvidenceRepository evidenceRepository;

    @Inject
    ExternalMetadataCapabilityRegistry registry;

    public List<DataQualityCandidate> scan(Long researchOutputId) {
        List<PureSnapshot> pures = evidenceRepository.loadPure(researchOutputId);
        if (pures.isEmpty()) {
            return List.of();
        }
        Set<String> dois = new LinkedHashSet<>();
        for (PureSnapshot snapshot : pures) {
            dois.addAll(snapshot.normalizedDois);
        }
        Map<String, Map<String, SourceSnapshot>> loaded = registry.loadByDois(new ArrayList<>(dois));
        List<DataQualityCandidate> items = new ArrayList<>();
        for (PureSnapshot pure : pures) {
            items.addAll(evaluateOutput(pure, loaded));
        }
        return items;
    }

    List<DataQualityCandidate> evaluateOutput(
            PureSnapshot pure,
            Map<String, Map<String, SourceSnapshot>> loaded
    ) {
        List<DataQualityCandidate> items = new ArrayList<>();
        if (pure.normalizedDois.isEmpty()) {
            return items;
        }
        List<SourceSnapshot> sources = new ArrayList<>();
        if (loaded != null) {
            for (String doi : pure.normalizedDois) {
                for (Map<String, SourceSnapshot> byDoi : loaded.values()) {
                    SourceSnapshot snapshot = byDoi.get(doi);
                    if (snapshot != null) {
                        sources.add(snapshot);
                    }
                }
            }
        }
        for (ConsistencyField field : ConsistencyField.values()) {
            ConsistencyEvaluation evaluation = ConsistencyEvaluator.evaluate(pure, sources, field);
            if (!ConsistencyAggregator.shouldPersistFinding(evaluation.aggregate, field)) {
                continue;
            }
            items.add(toCandidate(pure, evaluation));
        }
        return items;
    }

    private DataQualityCandidate toCandidate(PureSnapshot pure, ConsistencyEvaluation evaluation) {
        ObjectNode evidence = mapper.createObjectNode();
        evidence.put("issueKind", evaluation.aggregate.name());
        evidence.put("aggregate", evaluation.aggregate.name());
        evidence.put("contextKey", evaluation.field.ruleCode());
        evidence.put("pureValue", n(evaluation.pureValue));
        evidence.put("pureNormalized", n(evaluation.pureNormalized));
        if (!pure.normalizedDois.isEmpty()) {
            evidence.put("doi", pure.normalizedDois.get(0));
        }
        ArrayNode sources = evidence.putArray("sources");
        boolean conflict = evaluation.aggregate == ComparisonResult.EXTERNAL_SOURCE_CONFLICT;
        evidence.put("externalConflict", conflict);
        List<ExternalEvidence> rows = evaluation.evidence;
        for (ExternalEvidence item : rows) {
            ObjectNode node = sources.addObject();
            node.put("source", n(item.source));
            node.put("sourceRecordId", n(item.sourceRecordId));
            node.put("value", n(item.originalValue));
            node.put("normalizedValue", n(item.normalizedValue));
            node.put("result", item.result == null ? "" : item.result.name());
            if (item.lastExternalSyncAt != null) {
                node.put("lastExternalSyncAt", item.lastExternalSyncAt.toString());
            }
            if (item.completeAuthorList != null) {
                node.put("completeAuthorList", item.completeAuthorList);
            }
        }
        DataQualityCandidate candidate = DataQualityCandidate.of(
                evaluation.field.ruleCode(),
                "RESEARCH_OUTPUT",
                pure.id,
                pure.pureId,
                pure.uuid,
                evaluation.field.fieldName(),
                evaluation.aggregate.name(),
                DataQualityCatalog.MODE_VIEW,
                summary(evaluation),
                evaluation.pureValue,
                null,
                evidence
        );
        candidate.evidences = rows;
        return candidate;
    }

    static String summary(ConsistencyEvaluation evaluation) {
        if (evaluation.aggregate == ComparisonResult.EXTERNAL_SOURCE_CONFLICT) {
            return "Las fuentes externas presentan valores diferentes.";
        }
        if (evaluation.aggregate == ComparisonResult.NOT_COMPARABLE) {
            return "Hay datos en ambos lados, pero no son semánticamente comparables.";
        }
        return "El valor PURE difiere de al menos una fuente externa comparable.";
    }

    private static String n(String value) {
        return value == null ? "" : value;
    }
}
