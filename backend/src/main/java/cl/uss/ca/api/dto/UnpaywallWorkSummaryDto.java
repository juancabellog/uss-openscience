package cl.uss.ca.api.dto;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class UnpaywallWorkSummaryDto {
    public long id;
    public String doi;
    public String normalizedDoi;
    public String doiUrl;
    public String lastOutcome;
    public Boolean isOa;
    public String oaStatus;
    public String title;
    public Integer year;
    public String publisher;
    public String journalName;
    public Boolean journalIsInDoaj;
    public Boolean hasRepositoryCopy;
    public String bestOaHostType;
    public String bestOaLicense;
    public String bestOaVersion;
    public String bestOaUrl;
    public String bestOaLandingPageUrl;
    public String bestOaPdfUrl;
    public OffsetDateTime retrievedAt;
    public OffsetDateTime apiUpdatedAt;
    public int researchOutputCount;
    public String publicationTitle;
    public final List<UnpaywallLinkedOutputDto> researchOutputs = new ArrayList<>();
}
