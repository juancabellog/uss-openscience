package cl.uss.ca.api.dto;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class DataQualityFindingDto {
    public Long id;
    public Long ruleId;
    public String ruleCode;
    public String ruleName;
    public String category;
    public String entityType;
    public Long localRecordId;
    public Long pureId;
    public String recordUuid;
    public String recordLabel;
    public String fieldName;
    public String issueKind;
    public String correctionMode;
    public String status;
    public String summary;
    public String currentValue;
    public String suggestedValue;
    public String evidenceJson;
    public List<DataQualityFindingEvidenceDto> evidences = new ArrayList<>();
    public String description;
    public String ruleText;
    public String alertText;
    public OffsetDateTime firstDetectedAt;
    public OffsetDateTime lastDetectedAt;
    public OffsetDateTime resolvedAt;
    public String resolvedBy;
    public String resolutionNote;
    public String recordRoute;
}
