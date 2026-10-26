package cl.uss.ca.api.dto;

public class MetricFetchStateDto {
    public String collectionId;
    public String lastOutcome;
    public Integer lastHttpStatus;
    public String lastErrorCode;
    public String lastErrorMessage;
    public Integer observationCount;
    public String lastSyncedAt;
    public String lastSuccessAt;
}
