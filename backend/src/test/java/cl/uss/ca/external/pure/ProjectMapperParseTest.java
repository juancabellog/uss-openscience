package cl.uss.ca.external.pure;

import cl.uss.ca.domain.PureProjectUris;
import cl.uss.ca.external.pure.json.ProjectItem;
import cl.uss.ca.external.pure.json.ProjectListResponse;
import cl.uss.ca.external.pure.model.ProjectIdentifierData;
import cl.uss.ca.external.pure.model.ProjectParticipantData;
import cl.uss.ca.external.pure.model.PureProject;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Parses GET /projects JSON. Not executed by the agent.
 */
public final class ProjectMapperParseTest {

    private static final String SAMPLE = """
            {
              "count": 631,
              "pageInformation": { "offset": 0, "size": 1 },
              "items": [
                {
                  "typeDiscriminator": "AwardManagementProject",
                  "pureId": 25387407,
                  "uuid": "a93fb6e9-3314-4a87-86e4-6d01169dd581",
                  "createdBy": "luis.weky@uss.cl",
                  "createdDate": "2023-09-25T16:23:52.963-03:00",
                  "modifiedBy": "elodie.debona@uss.cl",
                  "modifiedDate": "2025-04-30T15:40:34.286-04:00",
                  "portalUrl": "https://researchers.uss.cl/en/projects/a93fb6e9-3314-4a87-86e4-6d01169dd581",
                  "prettyUrlIdentifiers": [
                    "inmunidad-nutricional-mecanismo-de-defensa-antimicrobiano-emplead"
                  ],
                  "version": "a63f2e81891d06508517c967f328fb9a0852ebca",
                  "acronym": "FONDECYT P-3200418",
                  "participants": [
                    {
                      "typeDiscriminator": "InternalParticipantAssociation",
                      "pureId": 25387421,
                      "name": { "firstName": "Danixa Pamela", "lastName": "Martínez González" },
                      "role": {
                        "uri": "/dk/atira/pure/upmproject/roles/upmproject/pi",
                        "term": {
                          "en_US": "Principal Investigator",
                          "es_CL": "Investigador(a) Responsable"
                        }
                      },
                      "associationHidden": false,
                      "person": {
                        "systemName": "Person",
                        "uuid": "88d900d2-a9a3-4a42-aa14-467a9c768b47"
                      },
                      "organizations": [
                        { "systemName": "Organization", "uuid": "53f83554-58f8-4ef8-b982-a0866076d2ff" }
                      ]
                    }
                  ],
                  "descriptions": [
                    {
                      "pureId": 25387412,
                      "type": {
                        "uri": "/dk/atira/pure/upmproject/descriptions/projectdescription",
                        "term": { "en_US": "Description", "es_CL": "Descripción" }
                      }
                    }
                  ],
                  "period": { "startDate": "2022-09-13", "endDate": "2023-04-14" },
                  "identifiers": [
                    {
                      "typeDiscriminator": "ClassifiedId",
                      "pureId": 25387410,
                      "id": "3200418",
                      "type": {
                        "uri": "/dk/atira/pure/upm/classifiedsource/projectid",
                        "term": { "en_US": "Project ID", "es_CL": "ID de proyecto" }
                      }
                    },
                    {
                      "typeDiscriminator": "ClassifiedId",
                      "pureId": 25387411,
                      "id": "FONDECYT P",
                      "type": {
                        "uri": "/dk/atira/pure/upm/classifiedsource/funderprojectreference",
                        "term": {
                          "en_US": "Funder Project Reference",
                          "es_CL": "Referencia del proyecto del financiador"
                        }
                      }
                    }
                  ],
                  "natureTypes": [
                    {
                      "uri": "/dk/atira/pure/upm/nature/research",
                      "term": { "en_US": "Research", "es_CL": "Investigación" }
                    }
                  ],
                  "organizations": [
                    { "systemName": "Organization", "uuid": "53f83554-58f8-4ef8-b982-a0866076d2ff" }
                  ],
                  "managingOrganization": {
                    "systemName": "Organization",
                    "uuid": "43932ef7-9625-484d-b2d1-ae49d80915f4"
                  },
                  "title": {
                    "es_CL": "Inmunidad Nutricional: Mecanismo de Defensa Antimicrobiano..."
                  },
                  "type": {
                    "uri": "/dk/atira/pure/upmproject/upmprojecttypes/upmproject/other",
                    "term": { "en_US": "Other", "es_CL": "Otros" }
                  },
                  "workflow": {
                    "step": "forApproval",
                    "description": { "en_US": "For approval", "es_CL": "Para su aprobación" }
                  },
                  "visibility": {
                    "key": "FREE",
                    "description": {
                      "en_US": "Public - No restriction",
                      "es_CL": "Público - Sin restricción"
                    }
                  },
                  "customDefinedFields": {},
                  "systemName": "Project"
                }
              ]
            }
            """;

    private ProjectMapperParseTest() {
    }

    public static void main(String[] args) throws Exception {
        parseListAndItem();
        missingUuidIsRejected();
        missingOptionalFields();
        System.out.println("ProjectMapperParseTest OK");
    }

    static void parseListAndItem() throws Exception {
        ObjectMapper mapper = mapper();
        ProjectListResponse page = mapper.readValue(SAMPLE, ProjectListResponse.class);
        assertEquals(Integer.valueOf(631), page.getCount());
        assertEquals(Integer.valueOf(0), page.getPageInformation().getOffset());
        assertEquals(Integer.valueOf(1), page.getPageInformation().getSize());
        assertEquals(1, page.getItems().size());
        ProjectItem item = page.getItems().get(0);
        PureProject project = new ProjectMapper().mapear(item);
        assertEquals("a93fb6e9-3314-4a87-86e4-6d01169dd581", project.getUuid());
        assertEquals(Long.valueOf(25387407L), project.getPureId());
        assertEquals("AwardManagementProject", project.getTypeDiscriminator());
        assertTrue(project.getTitleEs().startsWith("Inmunidad Nutricional"));
        assertEquals(null, project.getTitleEn());
        assertEquals("FONDECYT P-3200418", project.getAcronym());
        assertEquals("2022-09-13", project.getPeriodStartDate().toString());
        assertEquals("2023-04-14", project.getPeriodEndDate().toString());
        assertEquals("43932ef7-9625-484d-b2d1-ae49d80915f4", project.getManagingOrganizationUuid());
        assertEquals("forApproval", project.getWorkflowStep());
        assertEquals("FREE", project.getVisibilityKey());
        assertEquals(1, project.getPrettyUrlIdentifiers().size());
        assertEquals(1, project.getParticipants().size());
        ProjectParticipantData participant = project.getParticipants().get(0);
        assertEquals("88d900d2-a9a3-4a42-aa14-467a9c768b47", participant.getPersonUuid());
        assertEquals("Danixa Pamela", participant.getFirstName());
        assertEquals("Martínez González", participant.getLastName());
        assertTrue(PureProjectUris.isPrincipalInvestigator(participant.getRoleUri()));
        assertEquals(1, participant.getOrganizations().size());
        assertEquals("53f83554-58f8-4ef8-b982-a0866076d2ff", participant.getOrganizations().get(0).getOrganizationUuid());
        assertEquals(2, project.getIdentifiers().size());
        ProjectIdentifierData projectId = project.getIdentifiers().get(0);
        assertEquals("3200418", projectId.getValue());
        assertTrue(PureProjectUris.isProjectId(projectId.getTypeUri()));
        assertTrue(PureProjectUris.isFunderReference(project.getIdentifiers().get(1).getTypeUri()));
        assertEquals(1, project.getNatureTypes().size());
        assertEquals("/dk/atira/pure/upm/nature/research", project.getNatureTypes().get(0).getUri());
        assertEquals(1, project.getOrganizations().size());
        assertEquals(1, project.getDescriptions().size());
        assertEquals(Long.valueOf(25387412L), project.getDescriptions().get(0).getPureId());
    }

    static void missingUuidIsRejected() {
        ProjectItem item = new ProjectItem();
        item.setPureId(1L);
        assertEquals(null, new ProjectMapper().mapear(item));
    }

    static void missingOptionalFields() {
        ProjectItem item = new ProjectItem();
        item.setUuid("11111111-1111-1111-1111-111111111111");
        PureProject project = new ProjectMapper().mapear(item);
        assertEquals("11111111-1111-1111-1111-111111111111", project.getUuid());
        assertEquals(null, project.getTitleEs());
        assertEquals(null, project.getAcronym());
        assertTrue(project.getParticipants().isEmpty());
        assertTrue(project.getIdentifiers().isEmpty());
        assertEquals(null, project.displayTitle());
    }

    private static ObjectMapper mapper() {
        return new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }

    private static void assertTrue(boolean value) {
        if (!value) {
            throw new AssertionError("expected true");
        }
    }
}
