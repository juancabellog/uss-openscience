package cl.uss.ca.api.dto;

import java.time.OffsetDateTime;

public class DataQualityFindingEvidenceDto {
    public Long id;
    public String source;
    public String sourceRecordId;
    public String sourceField;
    public String externalValue;
    public String normalizedExternalValue;
    public String comparisonResult;
    public OffsetDateTime externalLastSyncAt;
    public String metadataJson;
}
