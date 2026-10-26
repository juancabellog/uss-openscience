package cl.uss.ca.external.orcid;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Proposed cases from real ORCID payloads.
 * ORCID: 0000-0001-7556-8352
 * put-code: 135533371
 * Not executed by the agent.
 */
public final class OrcidWorksMapperTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ORCID = "0000-0001-7556-8352";

    private OrcidWorksMapperTest() {
    }

    public static void main(String[] args) throws Exception {
        detail135533371();
        groupMultiSource();
    }

    static void detail135533371() throws Exception {
        OrcidWorkRecord work = OrcidWorksMapper.mapDetail(ORCID, MAPPER.readTree(DETAIL_JSON), MAPPER);
        assert Long.valueOf(135533371L).equals(work.putCode);
        assert "Scopus - Elsevier".equals(work.sourceName);
        assert work.title != null && work.title.contains("15-Minute City");
        assert "Smart Cities".equals(work.journalTitle);
        assert "journal-article".equals(work.workType);
        assert Integer.valueOf(2023).equals(work.publicationYear);
        assert work.publicationMonth == null;
        assert work.publicationDay == null;
        assert "bibtex".equals(work.citationType);
        assert work.contributors.size() == 5;
        assert work.detailLoaded;
        OrcidExternalIdRecord doi = work.externalIds.stream()
                .filter(id -> "doi".equalsIgnoreCase(id.type))
                .findFirst()
                .orElseThrow();
        assert "10.3390/smartcities6020050".equals(doi.value) || "10.3390/smartcities6020050".equals(doi.normalizedValue);
        assert "self".equalsIgnoreCase(doi.relationship);
        OrcidExternalIdRecord issn = work.externalIds.stream()
                .filter(id -> "issn".equalsIgnoreCase(id.type))
                .findFirst()
                .orElseThrow();
        assert "2624-6511".equals(issn.normalizedValue);
        assert "part-of".equalsIgnoreCase(issn.relationship);
    }

    static void groupMultiSource() throws Exception {
        List<OrcidWorkGroupRecord> groups = OrcidWorksMapper.parseWorks(ORCID, MAPPER.readTree(WORKS_JSON), MAPPER);
        assert groups.size() == 1;
        assert groups.get(0).summaries.size() == 3;
    }

    private static final String DETAIL_JSON = """
            {
              "put-code": 135533371,
              "path": "/0000-0001-7556-8352/work/135533371",
              "title": { "title": { "value": "“15-Minute City” and Elderly People: Thinking about Healthy Cities" } },
              "journal-title": { "value": "Smart Cities" },
              "short-description": null,
              "citation": { "citation-type": "bibtex", "citation-value": "@article{Test2023}" },
              "type": "journal-article",
              "publication-date": { "year": { "value": "2023" }, "month": null, "day": null },
              "external-ids": {
                "external-id": [
                  { "external-id-type": "doi", "external-id-value": "10.3390/smartcities6020050", "external-id-relationship": "self" },
                  { "external-id-type": "eid", "external-id-value": "2-s2.0-85153698761", "external-id-relationship": "self" },
                  { "external-id-type": "issn", "external-id-value": "26246511", "external-id-relationship": "part-of" }
                ]
              },
              "source": {
                "source-name": { "value": "Scopus - Elsevier" },
                "source-client-id": { "path": "APP-XXXX", "uri": "https://orcid.org/client/APP-XXXX" }
              },
              "contributors": {
                "contributor": [
                  { "credit-name": { "value": "A" }, "contributor-attributes": null },
                  { "credit-name": { "value": "B" }, "contributor-attributes": null },
                  { "credit-name": { "value": "C" }, "contributor-attributes": null },
                  { "credit-name": { "value": "D" }, "contributor-attributes": null },
                  { "credit-name": { "value": "E" }, "contributor-attributes": null }
                ]
              }
            }
            """;

    private static final String WORKS_JSON = """
            {
              "group": [
                {
                  "external-ids": {
                    "external-id": [
                      { "external-id-type": "doi", "external-id-value": "10.3390/smartcities6020050", "external-id-relationship": "self" }
                    ]
                  },
                  "work-summary": [
                    { "put-code": 1, "type": "journal-article", "title": { "title": { "value": "“15-Minute City” and Elderly People: Thinking about Healthy Cities" } }, "source": { "source-name": { "value": "Crossref" } } },
                    { "put-code": 2, "type": "journal-article", "title": { "title": { "value": "“15-Minute City” and Elderly People: Thinking about Healthy Cities" } }, "source": { "source-name": { "value": "MDPI" } } },
                    { "put-code": 135533371, "type": "journal-article", "title": { "title": { "value": "“15-Minute City” and Elderly People: Thinking about Healthy Cities" } }, "source": { "source-name": { "value": "Scopus - Elsevier" } } }
                  ]
                }
              ]
            }
            """;
}
