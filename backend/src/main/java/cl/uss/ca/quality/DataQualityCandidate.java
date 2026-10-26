package cl.uss.ca.quality;

import cl.uss.ca.quality.consistency.ExternalEvidence;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

public class DataQualityCandidate {
    public String ruleCode;
    public String entityType;
    public long localRecordId;
    public Long pureId;
    public String recordUuid;
    public String fieldName;
    public String issueKind;
    public String correctionMode;
    public String summary;
    public String currentValue;
    public String suggestedValue;
    public String evidenceJson;
    public String findingKey;
    public List<ExternalEvidence> evidences;

    public static DataQualityCandidate of(
            String ruleCode,
            String entityType,
            long localRecordId,
            Long pureId,
            String recordUuid,
            String fieldName,
            String issueKind,
            String correctionMode,
            String summary,
            String currentValue,
            String suggestedValue,
            ObjectNode evidence
    ) {
        DataQualityCandidate candidate = new DataQualityCandidate();
        candidate.ruleCode = ruleCode;
        candidate.entityType = entityType;
        candidate.localRecordId = localRecordId;
        candidate.pureId = pureId;
        candidate.recordUuid = recordUuid;
        candidate.fieldName = fieldName;
        candidate.issueKind = issueKind;
        candidate.correctionMode = correctionMode;
        candidate.summary = summary;
        candidate.currentValue = currentValue;
        candidate.suggestedValue = suggestedValue;
        candidate.evidenceJson = evidence == null ? null : evidence.toString();
        String context = evidence != null && evidence.hasNonNull("contextKey")
                ? evidence.get("contextKey").asText()
                : issueKind;
        candidate.findingKey = ruleCode + "|" + entityType + "|" + localRecordId + "|"
                + (fieldName == null ? "" : fieldName) + "|" + context;
        if (candidate.findingKey.length() > 191) {
            candidate.findingKey = candidate.findingKey.substring(0, 191);
        }
        return candidate;
    }

    public static ObjectNode evidence(ObjectMapper mapper) {
        return mapper.createObjectNode();
    }
}
