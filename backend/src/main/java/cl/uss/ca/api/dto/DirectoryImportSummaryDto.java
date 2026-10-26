package cl.uss.ca.api.dto;

public class DirectoryImportSummaryDto {
    public Long id;
    public String publicId;
    public String filename;
    public String status;
    public String importedBy;
    public String startedAt;
    public String finishedAt;
    public int rowCount;
    public int createdCount;
    public int updatedCount;
    public int unchangedCount;
    public int reactivatedCount;
    public int inactivatedCount;
    public int issueCount;
    public int expectedCount;
    public int processedCount;
    public String errorMessage;
    public String heartbeatAt;
    public boolean owned;
    public boolean interrupted;
}
