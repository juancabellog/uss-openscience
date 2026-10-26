package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class CrossrefWorkDetailDto extends CrossrefWorkSummaryDto {
    public String subtitle;
    public String volume;
    public String issue;
    public String pageRange;
    public String articleNumber;
    public String language;
    public Integer referenceCount;
    public Integer isReferencedByCount;
    public String publishedPrintDate;
    public String publishedOnlineDate;
    public String issuedDate;
    public Integer lastHttpStatus;
    public String errorCode;
    public String errorMessage;
    public String rawJson;
    public final List<CrossrefAuthorDto> authors = new ArrayList<>();
    public final List<CrossrefIssnDto> issns = new ArrayList<>();
    public final List<CrossrefFunderDto> funders = new ArrayList<>();
    public final List<CrossrefLicenseDto> licenses = new ArrayList<>();
    public final List<CrossrefRelationDto> relations = new ArrayList<>();
}
