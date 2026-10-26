package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class ResearchOutputDetailDto extends ResearchOutputSummaryDto {
    public String typeEs;
    public String typeEn;
    public Integer submissionYear;
    public String volume;
    public String issue;
    public String pages;
    public Integer numberOfPages;
    public ResearchOutputJournalDto journalDetail;
    public List<ResearchOutputStatusDto> publicationStatuses = new ArrayList<>();
    public List<ResearchOutputIdentifierDto> identifiers = new ArrayList<>();
    public List<ResearchOutputElectronicVersionDto> electronicVersions = new ArrayList<>();
    public List<ResearchOutputContributorDto> contributors = new ArrayList<>();
    public List<ResearchOutputKeywordGroupDto> keywordGroups = new ArrayList<>();
    public List<ResearchOutputLinkDto> links = new ArrayList<>();
    public List<String> externalOrganizationUuids = new ArrayList<>();
    public Integer doiElectronicVersionCount;
    public Integer distinctDoiCount;
}
