package cl.uss.ca.external.crossref;

import cl.uss.ca.domain.DoiNormalizer;
import cl.uss.ca.quality.CrossrefComparison;
import cl.uss.ca.quality.DataQualityIdentifiers;
import cl.uss.ca.repository.CrossrefDoiLink;
import cl.uss.ca.service.CrossrefSyncJob;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Proposed Crossref mapper/sync cases. Not executed by the agent.
 */
public final class CrossrefMapperTest {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private CrossrefMapperTest() {
    }

    public static void main(String[] args) throws Exception {
        parsesSuccessfulWork();
        missingOptionalFields();
        authorOrcidAndAffiliationRor();
        fundingLicenseRelationsUpdates();
        malformedPayloadDoesNotThrow();
        agencyOtherVsCrossref();
        uniqueDoiOneRemoteKey();
        incrementalCrossrefCandidateFilter();
        invalidDoiIsNotRemoteCandidate();
        titleDifferenceUsesThreshold();
        missingCrossrefTitleIsNotDifference();
        otherAgencyIsNotInvalid();
        isoDateTimeBecomesMysqlDatetime();
        System.out.println("CrossrefMapperTest OK");
    }

    static void parsesSuccessfulWork() throws Exception {
        CrossrefWorkRecord work = CrossrefMapper.parseWork(MAPPER.readTree(WORK_JSON));
        assert "10.1038/nature12373".equalsIgnoreCase(work.doi) || "10.1038/nature12373".equals(DoiNormalizer.normalize(work.doi));
        assert "journal-article".equals(work.type);
        assert work.title != null && work.title.contains("Sample");
        assert "Nature".equals(work.containerTitle);
        assert work.authors.size() == 1;
        assert work.issns.size() == 2;
        assert work.referenceCount != null && work.referenceCount == 12;
    }

    static void missingOptionalFields() throws Exception {
        CrossrefWorkRecord work = CrossrefMapper.parseWork(MAPPER.readTree("""
                { "message": { "DOI": "10.1000/xyz", "title": ["Only title"], "unknown": true } }
                """));
        assert "Only title".equals(work.title);
        assert work.authors.isEmpty();
        assert work.funders.isEmpty();
        assert work.licenses.isEmpty();
    }

    static void authorOrcidAndAffiliationRor() throws Exception {
        CrossrefWorkRecord work = CrossrefMapper.parseWork(MAPPER.readTree(WORK_JSON));
        CrossrefAuthorRecord author = work.authors.get(0);
        assert DataQualityIdentifiers.isValidOrcid(author.orcid);
        assert Boolean.TRUE.equals(author.authenticatedOrcid);
        assert author.affiliations.size() == 1;
        assert author.affiliations.get(0).ror != null && author.affiliations.get(0).ror.contains("ror.org");
    }

    static void fundingLicenseRelationsUpdates() throws Exception {
        CrossrefWorkRecord work = CrossrefMapper.parseWork(MAPPER.readTree(WORK_JSON));
        assert work.funders.size() == 1;
        assert "ANID".equals(work.funders.get(0).name);
        assert work.licenses.size() == 1;
        assert work.relations.stream().anyMatch(item -> "retraction".equalsIgnoreCase(item.type)
                || (item.type != null && item.type.toLowerCase().contains("retract")));
        assert work.relations.stream().anyMatch(item -> item.type != null && item.type.toLowerCase().contains("update"));
    }

    static void malformedPayloadDoesNotThrow() throws Exception {
        CrossrefWorkRecord work = CrossrefMapper.parseWork(MAPPER.readTree("{}"));
        assert work.title == null;
        CrossrefMapper.Agency agency = CrossrefMapper.parseAgency(MAPPER.readTree("{\"message\":{}}"));
        assert agency.id == null;
    }

    static void agencyOtherVsCrossref() throws Exception {
        CrossrefMapper.Agency datacite = CrossrefMapper.parseAgency(MAPPER.readTree("""
                { "message": { "DOI": "10.5281/zenodo.1", "agency": { "id": "datacite", "label": "DataCite" } } }
                """));
        assert "datacite".equalsIgnoreCase(datacite.id);
        CrossrefFetchResult other = CrossrefFetchResult.otherAgency(200, datacite.id, datacite.label);
        assert CrossrefStatus.OTHER_AGENCY.equals(other.status);
        assert !CrossrefStatus.INVALID_DOI.equals(other.status);
        CrossrefFetchResult missing = CrossrefFetchResult.notFound(404);
        assert CrossrefStatus.NOT_FOUND.equals(missing.status);
        CrossrefFetchResult invalid = CrossrefFetchResult.invalid("bad");
        assert CrossrefStatus.INVALID_DOI.equals(invalid.status);
        CrossrefFetchResult limited = CrossrefFetchResult.failure(CrossrefStatus.RATE_LIMITED, 429, "CROSSREF_RATE_LIMITED", "limited");
        assert limited.retryable();
        CrossrefFetchResult server = CrossrefFetchResult.failure(CrossrefStatus.ERROR, 503, "CROSSREF_TEMPORARY_ERROR", "5xx");
        assert server.retryable();
        CrossrefFetchResult timeout = CrossrefFetchResult.failure(CrossrefStatus.ERROR, 0, "CROSSREF_TIMEOUT", "timeout");
        assert timeout.retryable();
    }

    static void uniqueDoiOneRemoteKey() {
        CrossrefDoiLink a = new CrossrefDoiLink();
        a.doi = "https://doi.org/10.1038/nature12373";
        a.researchOutputId = 1;
        CrossrefDoiLink b = new CrossrefDoiLink();
        b.doi = "DOI:10.1038/NATURE12373";
        b.researchOutputId = 2;
        var unique = CrossrefSyncJob.uniqueDois(List.of(a, b));
        assert unique.size() == 1;
        assert unique.values().iterator().next().size() == 2;
    }

    static void incrementalCrossrefCandidateFilter() {
        java.time.OffsetDateTime now = java.time.OffsetDateTime.parse("2026-10-05T12:00:00Z");
        java.util.Map<String, java.util.List<CrossrefDoiLink>> unique = new java.util.LinkedHashMap<>();
        unique.put("10.1/new", java.util.List.of(a("10.1/new", 1)));
        unique.put("10.1/fresh", java.util.List.of(a("10.1/fresh", 2)));
        unique.put("10.1/shared", java.util.List.of(a("10.1/shared", 3), a("10.1/shared", 4)));
        cl.uss.ca.sync.WorkSyncState fresh = new cl.uss.ca.sync.WorkSyncState();
        fresh.present = true;
        fresh.status = CrossrefStatus.FOUND;
        fresh.lastSyncAt = now.minusDays(1);
        fresh.mapped = true;
        var selected = CrossrefSyncJob.retainIncremental(
                unique,
                java.util.Map.of("10.1/fresh", fresh, "10.1/shared", fresh),
                java.util.Set.of("10.1/fresh", "10.1/shared"),
                30,
                now,
                true
        );
        assert selected.size() == 1;
        assert selected.containsKey("10.1/new");
        assert selected.get("10.1/shared") == null;
    }

    private static CrossrefDoiLink a(String doi, long outputId) {
        CrossrefDoiLink link = new CrossrefDoiLink();
        link.doi = doi;
        link.researchOutputId = outputId;
        return link;
    }

    static void invalidDoiIsNotRemoteCandidate() {
        assert !DataQualityIdentifiers.isValidDoi("https://www.google.com/search?q=10.1038/nature12373");
        assert DataQualityIdentifiers.isValidDoi("https://doi.org/10.1038/nature12373");
        CrossrefFetchResult invalid = CrossrefFetchResult.invalid("DOI format is invalid");
        assert CrossrefStatus.INVALID_DOI.equals(invalid.status);
        assert invalid.httpStatus == 0;
    }

    static void titleDifferenceUsesThreshold() {
        assert !CrossrefComparison.titlesDiffer("Sample Crossref article", "Sample Crossref article");
        assert CrossrefComparison.titlesDiffer("Totally different institutional title", "Quantum gravity in a teacup");
        assert CrossrefComparison.TITLE_JACCARD_THRESHOLD == 0.55;
    }

    static void missingCrossrefTitleIsNotDifference() {
        assert !CrossrefComparison.titlesDiffer("PURE title", null);
        assert !CrossrefComparison.titlesDiffer("PURE title", "");
        assert !CrossrefComparison.authorsDiffer(List.of("Paulina Spaudo Valenzuela"), List.of());
    }

    static void otherAgencyIsNotInvalid() {
        assert !CrossrefStatus.INVALID_DOI.equals(CrossrefStatus.OTHER_AGENCY);
        assert CrossrefStatus.remoteOutcome(CrossrefStatus.OTHER_AGENCY);
        assert !CrossrefStatus.remoteOutcome(CrossrefStatus.INVALID_DOI);
    }

    static void isoDateTimeBecomesMysqlDatetime() {
        assert "2026-09-16 07:58:59.000000".equals(CrossrefMapper.mysqlDateTime("2026-09-16T07:58:59Z"));
        assert "2026-09-16 07:58:59.000000".equals(CrossrefMapper.mysqlDateTime("2026-09-16 07:58:59"));
        assert CrossrefMapper.mysqlDateTime(null) == null;
        assert !CrossrefMapper.mysqlDateTime("2026-09-16T07:58:59Z").contains("T");
        assert !CrossrefMapper.mysqlDateTime("2026-09-16T07:58:59Z").contains("Z");
    }

    private static final String WORK_JSON = """
            {
              "status": "ok",
              "message-type": "work",
              "message": {
                "DOI": "10.1038/nature12373",
                "type": "journal-article",
                "title": ["Sample Crossref article"],
                "container-title": ["Nature"],
                "publisher": "Springer Nature",
                "volume": "498",
                "issue": "7454",
                "page": "1-4",
                "language": "en",
                "reference-count": 12,
                "is-referenced-by-count": 40,
                "published": { "date-parts": [[2013, 6, 1]] },
                "ISSN": ["0028-0836", "1476-4687"],
                "issn-type": [
                  { "value": "0028-0836", "type": "print" },
                  { "value": "1476-4687", "type": "electronic" }
                ],
                "author": [
                  {
                    "given": "Ada",
                    "family": "Lovelace",
                    "ORCID": "https://orcid.org/0000-0002-1825-0097",
                    "authenticated-orcid": true,
                    "affiliation": [
                      { "name": "Universidad San Sebastian", "id": "https://ror.org/00teqrf59" }
                    ]
                  }
                ],
                "funder": [
                  { "name": "ANID", "DOI": "10.13039/501100002848", "award": ["1234"] }
                ],
                "license": [
                  { "URL": "https://creativecommons.org/licenses/by/4.0/", "content-version": "vor", "delay-in-days": 0,
                    "start": { "date-parts": [[2013, 6, 1]] } }
                ],
                "relation": {
                  "is-preprint-of": [ { "id": "10.1101/123", "asserted-by": "subject" } ]
                },
                "update-to": [
                  { "DOI": "10.1038/old", "type": "retraction" },
                  { "DOI": "10.1038/older", "type": "correction" }
                ],
                "future-field": "ignored"
              }
            }
            """;
}
