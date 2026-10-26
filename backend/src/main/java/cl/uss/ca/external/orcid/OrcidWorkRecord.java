package cl.uss.ca.external.orcid;

import java.util.ArrayList;
import java.util.List;

public class OrcidWorkRecord {
    public String orcid;
    public Long putCode;
    public Long createdDateOrcid;
    public Long lastModifiedOrcid;
    public String title;
    public String subtitle;
    public String translatedTitle;
    public String translatedTitleLanguage;
    public String journalTitle;
    public String shortDescription;
    public String citationType;
    public String citationValue;
    public String workType;
    public Integer publicationYear;
    public Integer publicationMonth;
    public Integer publicationDay;
    public String url;
    public String languageCode;
    public String countryCode;
    public String visibility;
    public String path;
    public Integer displayIndex;
    public String sourceOrcidUri;
    public String sourceOrcidPath;
    public String sourceClientIdUri;
    public String sourceClientIdPath;
    public String sourceName;
    public String assertionOriginOrcidUri;
    public String assertionOriginOrcidPath;
    public String assertionOriginClientIdUri;
    public String assertionOriginClientIdPath;
    public String assertionOriginName;
    public String rawJson;
    public boolean detailLoaded;
    public List<OrcidExternalIdRecord> externalIds = new ArrayList<>();
    public List<OrcidContributorRecord> contributors = new ArrayList<>();
}
