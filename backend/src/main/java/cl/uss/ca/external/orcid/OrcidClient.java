package cl.uss.ca.external.orcid;

import java.util.List;

public interface OrcidClient {
    void assertConfigured();

    boolean isConfigured();

    List<OrcidWorkGroupRecord> fetchWorks(String orcid);

    OrcidWorkRecord fetchWorkDetail(String orcid, long putCode);

    List<OrcidEmploymentRecord> fetchEmployments(String orcid);

    OrcidSearchPage searchByName(String givenName, String familyName);
}
