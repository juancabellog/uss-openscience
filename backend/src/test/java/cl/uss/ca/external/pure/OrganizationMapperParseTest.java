package cl.uss.ca.external.pure;

import cl.uss.ca.external.pure.json.OrganizationItem;
import cl.uss.ca.external.pure.json.OrganizationListResponse;
import cl.uss.ca.external.pure.model.PureOrganization;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Parses GET /organizations JSON. Not executed by the agent.
 * Sample uses only fields found in OrganizationRef + content-item envelope.
 */
public final class OrganizationMapperParseTest {

    private static final String SAMPLE = """
            {
              "count": 2,
              "pageInformation": { "offset": 0, "size": 1 },
              "items": [
                {
                  "typeDiscriminator": "Organization",
                  "pureId": 101,
                  "uuid": "53f83554-58f8-4ef8-b982-a0866076d2ff",
                  "createdBy": "system",
                  "createdDate": "2020-01-15T10:00:00.000-03:00",
                  "modifiedBy": "system",
                  "modifiedDate": "2025-04-30T15:40:34.286-04:00",
                  "version": "etag-1",
                  "systemName": "Organization",
                  "name": {
                    "es_CL": "Facultad de Medicina",
                    "en_US": "Faculty of Medicine"
                  },
                  "type": {
                    "uri": "/dk/atira/pure/organisation/organisationtypes/organisation/faculty",
                    "term": { "en_US": "Faculty", "es_CL": "Facultad" }
                  },
                  "parentOrganization": {
                    "systemName": "Organization",
                    "uuid": "43932ef7-9625-484d-b2d1-ae49d80915f4",
                    "name": { "es_CL": "Universidad San Sebastián" }
                  },
                  "unknownFutureField": { "nested": true }
                }
              ]
            }
            """;

    private OrganizationMapperParseTest() {
    }

    public static void main(String[] args) throws Exception {
        parseListAndItem();
        missingUuidIsRejected();
        missingOptionalFields();
        unknownFieldsDoNotBreakParser();
        parentFromParentsArrayWhenParentOrganizationAbsent();
        System.out.println("OrganizationMapperParseTest OK");
    }

    static void parseListAndItem() throws Exception {
        OrganizationListResponse page = mapper().readValue(SAMPLE, OrganizationListResponse.class);
        assertEquals(Integer.valueOf(2), page.getCount());
        assertEquals(Integer.valueOf(0), page.getPageInformation().getOffset());
        assertEquals(Integer.valueOf(1), page.getPageInformation().getSize());
        assertEquals(1, page.getItems().size());
        OrganizationItem item = page.getItems().get(0);
        PureOrganization organization = new OrganizationMapper().mapear(item);
        assertEquals("53f83554-58f8-4ef8-b982-a0866076d2ff", organization.getUuid());
        assertEquals(Long.valueOf(101L), organization.getPureId());
        assertEquals("Organization", organization.getTypeDiscriminator());
        assertEquals("Facultad de Medicina", organization.getNameEs());
        assertEquals("Faculty of Medicine", organization.getNameEn());
        assertEquals("Organization", organization.getSystemName());
        assertEquals("/dk/atira/pure/organisation/organisationtypes/organisation/faculty", organization.getTypeUri());
        assertEquals("Facultad", organization.getTypeEs());
        assertEquals("Faculty", organization.getTypeEn());
        assertEquals("43932ef7-9625-484d-b2d1-ae49d80915f4", organization.getParentUuid());
        assertEquals("system", organization.getCreatedBy());
        assertEquals("system", organization.getModifiedBy());
        assertEquals("etag-1", organization.getVersion());
        assertEquals(false, organization.getCreatedDatePure() == null);
        assertEquals(false, organization.getModifiedDatePure() == null);
    }

    static void missingUuidIsRejected() {
        OrganizationItem item = new OrganizationItem();
        item.setPureId(1L);
        assertEquals(null, new OrganizationMapper().mapear(item));
    }

    static void missingOptionalFields() {
        OrganizationItem item = new OrganizationItem();
        item.setUuid("11111111-1111-1111-1111-111111111111");
        PureOrganization organization = new OrganizationMapper().mapear(item);
        assertEquals("11111111-1111-1111-1111-111111111111", organization.getUuid());
        assertEquals(null, organization.getNameEs());
        assertEquals(null, organization.getTypeUri());
        assertEquals(null, organization.getParentUuid());
        assertEquals(null, organization.getPureId());
    }

    static void unknownFieldsDoNotBreakParser() throws Exception {
        OrganizationListResponse page = mapper().readValue(SAMPLE, OrganizationListResponse.class);
        assertEquals(1, page.getItems().size());
        assertEquals("53f83554-58f8-4ef8-b982-a0866076d2ff", page.getItems().get(0).getUuid());
    }

    static void parentFromParentsArrayWhenParentOrganizationAbsent() throws Exception {
        String json = """
                {
                  "uuid": "11111111-1111-1111-1111-111111111111",
                  "parents": [
                    { "uuid": "22222222-2222-2222-2222-222222222222", "systemName": "Organization" }
                  ]
                }
                """;
        OrganizationItem item = mapper().readValue(json, OrganizationItem.class);
        PureOrganization organization = new OrganizationMapper().mapear(item);
        assertEquals("22222222-2222-2222-2222-222222222222", organization.getParentUuid());
    }

    private static ObjectMapper mapper() {
        return new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }
}
