package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class JournalDetailDto extends JournalSummaryDto {
    public String publisherUuid;
    public String publisherSystemName;
    public String createdBy;
    public String createdDatePure;
    public String modifiedBy;
    public String modifiedDatePure;
    public String version;
    public String systemName;
    public List<JournalTitleItemDto> titles = new ArrayList<>();
    public List<JournalIdentifierItemDto> identifiers = new ArrayList<>();
    public List<JournalKeywordGroupDto> keywordGroups = new ArrayList<>();
}
