package cl.uss.ca.api.dto;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class CrossrefWorkSummaryDto {
    public long id;
    public String doi;
    public String normalizedDoi;
    public String canonicalDoi;
    public String status;
    public String registrationAgency;
    public String workType;
    public String title;
    public String containerTitle;
    public String publisher;
    public String publishedDate;
    public String comparisonStatus;
    public String issnList;
    public boolean hasOrcid;
    public boolean hasAffiliation;
    public boolean hasRor;
    public boolean hasFunding;
    public boolean hasLicense;
    public boolean hasUpdate;
    public boolean hasRetraction;
    public OffsetDateTime lastCrossrefSyncAt;
    public int researchOutputCount;
    public String publicationTitle;
    public final List<CrossrefLinkedOutputDto> researchOutputs = new ArrayList<>();
}
