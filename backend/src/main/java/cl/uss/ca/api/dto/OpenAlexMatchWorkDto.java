package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class OpenAlexMatchWorkDto {
    public Long id;
    public Long researchOutputId;
    public String normalizedDoi;
    public String openalexWorkId;
    public String title;
    public String contributorFirstName;
    public String contributorLastName;
    public Integer contributorPosition;
    public Integer contributorCount;
    public String rawAuthorName;
    public String authorPosition;
    public Integer sourceOrder;
    public Boolean nameCompatible;
    public Boolean namePartitionDifferent;
    public String positionCode;
    public String orcidPure;
    public String orcidOpenAlex;
    public boolean ussAffiliation;
    public String affiliationDisplay;
    public String ror;
    public String reasonCodes;
    public final List<String> reasonCodeList = new ArrayList<>();
    public final List<OpenAlexMatchWorkEvidenceDto> evidences = new ArrayList<>();
}
