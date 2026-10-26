package cl.uss.ca.external.pure;

import cl.uss.ca.external.pure.json.JournalItem;
import cl.uss.ca.external.pure.json.OrganizationItem;
import cl.uss.ca.external.pure.json.PersonItem;
import cl.uss.ca.external.pure.json.ProjectItem;
import cl.uss.ca.external.pure.json.ResearchOutputItem;
import com.fasterxml.jackson.databind.JsonNode;

public interface PureClient {
    void assertConfigured();

    PurePage<PersonItem> fetchPeoplePage(int offset, int size);

    PurePage<ResearchOutputItem> fetchResearchOutputsPage(int offset, int size);

    PurePage<JournalItem> fetchJournalsPage(int offset, int size);

    PurePage<ProjectItem> fetchProjectsPage(int offset, int size);

    PurePage<OrganizationItem> fetchOrganizationsPage(int offset, int size);

    JsonNode getPerson(String uuid);

    JsonNode putPerson(String uuid, JsonNode body);

    JsonNode getResearchOutput(String uuid);

    JsonNode putResearchOutput(String uuid, JsonNode body);

    JsonNode getJournal(String uuid);

    JsonNode putJournal(String uuid, JsonNode body);

    JsonNode getProject(String uuid);

    JsonNode putProject(String uuid, JsonNode body);

    JsonNode getOrganization(String uuid);

    JsonNode putOrganization(String uuid, JsonNode body);

    JsonNode getJournalMetrics(String uuid, String collectionId);

    JsonNode getResearchOutputMetrics(String uuid, String collectionId);

    JsonNode getPersonMetrics(String uuid, String collectionId);
}
