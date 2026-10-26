package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class ResearcherDetailDto extends ResearcherSummaryDto {
    public String gender;
    public String primaryId;
    public List<IdentifierDto> identities = new ArrayList<>();
    public List<ResearchOutputSummaryDto> publications = new ArrayList<>();
}
