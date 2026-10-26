package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class DirectoryImportResultDto {
    public boolean dryRun;
    public String importPublicId;
    public String filename;
    public String sheetName;
    public String status;
    public int rowCount;
    public int created;
    public int updated;
    public int unchanged;
    public int reactivated;
    public int rejected;
    public int wouldInactivate;
    public int inactivated;
    public int issueCount;
    public boolean censusReliable;
    public List<String> unmappedHeaders = new ArrayList<>();
    public List<DirectoryIssueDto> issues = new ArrayList<>();
}
