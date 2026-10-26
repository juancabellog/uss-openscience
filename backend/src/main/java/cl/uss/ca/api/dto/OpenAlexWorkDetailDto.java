package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class OpenAlexWorkDetailDto extends OpenAlexWorkSummaryDto {
    public String publicationDate;
    public Integer referencedWorksCount;
    public Boolean isOa;
    public String oaStatus;
    public String language;
    public String primarySourceDisplayName;
    public String primaryTopicDisplayName;
    public String domainDisplayName;
    public String fieldDisplayName;
    public String subfieldDisplayName;
    public String openalexUrl;
    public final List<OpenAlexAuthorshipDto> authorships = new ArrayList<>();
    public final List<OpenAlexTopicDto> topics = new ArrayList<>();
}
