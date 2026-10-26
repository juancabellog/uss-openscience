package cl.uss.ca.external.openalex;

import cl.uss.ca.domain.DoiNormalizer;
import cl.uss.ca.quality.DataQualityIdentifiers;
import cl.uss.ca.repository.OpenAlexDoiLink;
import cl.uss.ca.service.OpenAlexSyncJob;
import cl.uss.ca.sync.DoiIncrementalClassifier;
import cl.uss.ca.sync.IncrementalReason;
import cl.uss.ca.sync.WorkSyncState;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OpenAlex mapper and sync candidate cases. Not executed by the agent.
 * Does not call api.openalex.org.
 */
public final class OpenAlexMapperTest {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private OpenAlexMapperTest() {
    }

    public static void main(String[] args) throws Exception {
        doiLookupSuccessful();
        workNotFoundResult();
        invalidDoiNoRemoteCall();
        authorshipsParsed();
        hundredAuthorsNotTruncated();
        workByDoiUrl();
        authorSelectUrl();
        newAuthorshipChangesSnapshot();
        removedAuthorshipChangesSnapshot();
        apiErrorIsRetryable();
        uniqueDoiOneRemoteKey();
        incrementalOpenAlexCandidateFilter();
        secondIncrementalZeroCandidates();
        System.out.println("OpenAlexMapperTest OK");
    }

    static void doiLookupSuccessful() throws Exception {
        OpenAlexWorkRecord work = OpenAlexMapper.parseWork(MAPPER.readTree(WORK_JSON));
        assertEquals("W123", work.openalexWorkId);
        assertEquals("10.x/abc", DoiNormalizer.normalize(work.doi));
        assertEquals("Sample work", work.displayName);
        assert work.publicationYear != null && work.publicationYear == 2024;
        assertEquals("article", work.workType);
        assert work.citedByCount != null && work.citedByCount == 7;
        assert work.referencedWorksCount != null && work.referencedWorksCount == 12;
        assert work.authorships.size() == 1;
        assert work.topics.stream().anyMatch(item -> item.primary);
    }

    static void workNotFoundResult() {
        OpenAlexFetchResult result = OpenAlexFetchResult.notFound(404);
        assertEquals(OpenAlexStatus.NOT_FOUND, result.status);
        assert result.httpStatus == 404;
        assert result.work == null;
    }

    static void invalidDoiNoRemoteCall() {
        assert !DataQualityIdentifiers.isValidDoi("not-a-doi");
        assert !DataQualityIdentifiers.isValidDoi("https://example.com/paper");
        OpenAlexFetchResult result = OpenAlexFetchResult.invalid("DOI format is invalid");
        assertEquals(OpenAlexStatus.INVALID_DOI, result.status);
        assert result.httpStatus == 0;
        String url = OpenAlexUrls.workByDoi("https://api.openalex.org", "10.1000/xyz", null, null);
        assert url.contains("/works/doi:");
    }

    static void authorshipsParsed() throws Exception {
        OpenAlexWorkRecord work = OpenAlexMapper.parseWork(MAPPER.readTree(WORK_JSON));
        OpenAlexAuthorshipRecord authorship = work.authorships.get(0);
        assert authorship.sourceOrder == 0;
        assertEquals("middle", authorship.authorPosition);
        assertEquals("A987", authorship.openalexAuthorId);
        assertEquals("Juan Perez", authorship.rawAuthorName);
        assertEquals("0000-0001-2345-6789", authorship.authorOrcid);
        assertEquals("0000-0001-2345-6789", authorship.observedOrcid);
        assert authorship.institutions.size() == 1;
        assertEquals("I1", authorship.institutions.get(0).openalexInstitutionId);
        assert authorship.institutions.get(0).ror != null && authorship.institutions.get(0).ror.contains("047gc3g35");
        assert OpenAlexMapper.ussAffiliation(authorship);
        assert !authorship.authorPosition.equals(String.valueOf(authorship.sourceOrder));
    }

    static void hundredAuthorsNotTruncated() throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("id", "https://openalex.org/W999");
        root.put("doi", "https://doi.org/10.1000/mass");
        ArrayNode authorships = root.putArray("authorships");
        for (int i = 0; i < 120; i++) {
            ObjectNode row = authorships.addObject();
            row.put("author_position", i == 0 ? "first" : i == 119 ? "last" : "middle");
            row.put("raw_author_name", "Author " + i);
            ObjectNode author = row.putObject("author");
            author.put("id", "https://openalex.org/A" + i);
            author.put("display_name", "Author " + i);
        }
        OpenAlexWorkRecord work = OpenAlexMapper.parseWork(root);
        assert work.authorships.size() == 120;
        assert !work.authorshipsTruncated();
        assert work.authorships.get(3).sourceOrder == 3;
        assertEquals("middle", work.authorships.get(3).authorPosition);
    }

    static void workByDoiUrl() {
        String url = OpenAlexUrls.workByDoi("https://api.openalex.org/", "10.x/abc", "secret-key", "juan.cabello@uss.cl");
        assert url.startsWith("https://api.openalex.org/works/doi:");
        assert url.contains("select=");
        assert url.contains("api_key=");
        assert url.contains("mailto=");
        assert !url.contains("secret-key ");
    }

    static void authorSelectUrl() {
        String url = OpenAlexUrls.author("https://api.openalex.org", "A987", null, null);
        assert url.contains("/authors/A987");
        assert url.contains("select=");
    }

    static void newAuthorshipChangesSnapshot() {
        String before = OpenAlexWorkSnapshot.authorships(workWith(authorship("A1", "first")));
        String after = OpenAlexWorkSnapshot.authorships(workWith(
                authorship("A1", "first"),
                authorship("A2", "last")
        ));
        assert !before.equals(after);
    }

    static void removedAuthorshipChangesSnapshot() {
        String before = OpenAlexWorkSnapshot.authorships(workWith(
                authorship("A1", "first"),
                authorship("A2", "last")
        ));
        String after = OpenAlexWorkSnapshot.authorships(workWith(authorship("A1", "first")));
        assert !before.equals(after);
        String repeat = OpenAlexWorkSnapshot.authorships(workWith(authorship("A1", "first")));
        assertEquals(after, repeat);
    }

    static void apiErrorIsRetryable() {
        OpenAlexFetchResult error = OpenAlexFetchResult.failure(
                OpenAlexStatus.ERROR, 500, "OPENALEX_HTTP_ERROR", "OpenAlex HTTP 500"
        );
        assert error.retryable();
        OpenAlexFetchResult limited = OpenAlexFetchResult.failure(
                OpenAlexStatus.RATE_LIMITED, 429, "OPENALEX_RATE_LIMITED", "rate"
        );
        assert limited.retryable();
        assert !OpenAlexFetchResult.notFound(404).retryable();
    }

    static void uniqueDoiOneRemoteKey() {
        OpenAlexDoiLink a = link("https://doi.org/10.x/abc", 1);
        OpenAlexDoiLink b = link("DOI:10.X/ABC", 2);
        OpenAlexDoiLink c = link("10.x/abc", 3);
        var unique = OpenAlexSyncJob.uniqueDois(List.of(a, b, c));
        assert unique.size() == 1;
        assert unique.values().iterator().next().size() == 3;
    }

    static void incrementalOpenAlexCandidateFilter() {
        OffsetDateTime now = OffsetDateTime.parse("2026-10-05T12:00:00Z");
        Map<String, List<OpenAlexDoiLink>> unique = unique(
                "10.1/new", "10.1/never", "10.1/error", "10.1/stale", "10.1/fresh"
        );
        Map<String, WorkSyncState> stored = new HashMap<>();
        stored.put("10.1/never", present("FOUND", null, true));
        stored.put("10.1/error", present("ERROR", now.minusDays(1), true));
        stored.put("10.1/stale", present("FOUND", now.minusDays(40), true));
        stored.put("10.1/fresh", present("FOUND", now.minusDays(2), true));
        Map<String, List<OpenAlexDoiLink>> selected = OpenAlexSyncJob.retainIncremental(
                unique, stored, Set.of("10.1/never", "10.1/error", "10.1/stale", "10.1/fresh"), 30, now
        );
        assert selected.size() == 4;
        assert selected.containsKey("10.1/new");
        assert !selected.containsKey("10.1/fresh");
        assert IncrementalReason.NEW == DoiIncrementalClassifier.classifyOpenAlex(WorkSyncState.missing(), 30, now);
        WorkSyncState never = stored.get("10.1/never");
        never.mapped = true;
        assert IncrementalReason.NEVER_SYNCED == DoiIncrementalClassifier.classifyOpenAlex(never, 30, now);
        WorkSyncState error = stored.get("10.1/error");
        error.mapped = true;
        assert IncrementalReason.REMOTE_ERROR == DoiIncrementalClassifier.classifyOpenAlex(error, 30, now);
        WorkSyncState stale = stored.get("10.1/stale");
        stale.mapped = true;
        assert IncrementalReason.STALE == DoiIncrementalClassifier.classifyOpenAlex(stale, 30, now);
    }

    static void secondIncrementalZeroCandidates() {
        OffsetDateTime now = OffsetDateTime.parse("2026-10-05T12:00:00Z");
        Map<String, List<OpenAlexDoiLink>> unique = unique("10.1/a", "10.1/b");
        Map<String, WorkSyncState> stored = Map.of(
                "10.1/a", present("FOUND", now.minusDays(1), true),
                "10.1/b", present("NOT_FOUND", now.minusDays(3), true)
        );
        Map<String, List<OpenAlexDoiLink>> first = OpenAlexSyncJob.retainIncremental(
                unique, stored, Set.of("10.1/a", "10.1/b"), 30, now
        );
        assert first.isEmpty();
        Map<String, List<OpenAlexDoiLink>> second = OpenAlexSyncJob.retainIncremental(
                unique, stored, Set.of("10.1/a", "10.1/b"), 30, now
        );
        assert second.isEmpty();
    }

    private static OpenAlexWorkRecord workWith(OpenAlexAuthorshipRecord... rows) {
        OpenAlexWorkRecord work = new OpenAlexWorkRecord();
        for (OpenAlexAuthorshipRecord row : rows) {
            work.authorships.add(row);
        }
        return work;
    }

    private static OpenAlexAuthorshipRecord authorship(String authorId, String position) {
        OpenAlexAuthorshipRecord row = new OpenAlexAuthorshipRecord();
        row.openalexAuthorId = authorId;
        row.authorPosition = position;
        row.rawAuthorName = authorId;
        return row;
    }

    private static WorkSyncState present(String status, OffsetDateTime lastSyncAt, boolean mapped) {
        WorkSyncState state = new WorkSyncState();
        state.present = true;
        state.status = status;
        state.lastSyncAt = lastSyncAt;
        state.mapped = mapped;
        return state;
    }

    private static Map<String, List<OpenAlexDoiLink>> unique(String... dois) {
        Map<String, List<OpenAlexDoiLink>> unique = new HashMap<>();
        long id = 1;
        for (String doi : dois) {
            unique.put(doi, List.of(link(doi, id++)));
        }
        return unique;
    }

    private static OpenAlexDoiLink link(String doi, long outputId) {
        OpenAlexDoiLink link = new OpenAlexDoiLink();
        link.doi = doi;
        link.researchOutputId = outputId;
        return link;
    }

    private static void assertEquals(String expected, String actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }

    private static final String WORK_JSON = """
            {
              "id": "https://openalex.org/W123",
              "doi": "https://doi.org/10.x/abc",
              "display_name": "Sample work",
              "publication_year": 2024,
              "publication_date": "2024-03-01",
              "type": "article",
              "cited_by_count": 7,
              "referenced_works_count": 12,
              "language": "es",
              "updated_date": "2026-01-01T00:00:00.000Z",
              "open_access": { "is_oa": true, "oa_status": "gold" },
              "primary_location": {
                "source": {
                  "id": "https://openalex.org/S1",
                  "display_name": "Sample Journal",
                  "issn_l": "1234-5678"
                }
              },
              "primary_topic": {
                "id": "https://openalex.org/T1",
                "display_name": "Open science",
                "score": 0.9,
                "domain": { "id": "https://openalex.org/D1", "display_name": "Social Sciences" },
                "field": { "id": "https://openalex.org/F1", "display_name": "Library science" },
                "subfield": { "id": "https://openalex.org/SF1", "display_name": "Scholarly communication" }
              },
              "authorships": [
                {
                  "author_position": "middle",
                  "is_corresponding": false,
                  "raw_author_name": "Juan Perez",
                  "raw_orcid": "https://orcid.org/0000-0001-2345-6789",
                  "raw_affiliation_strings": ["Universidad San Sebastián"],
                  "author": {
                    "id": "https://openalex.org/A987",
                    "display_name": "Juan Pérez",
                    "orcid": "https://orcid.org/0000-0001-2345-6789"
                  },
                  "institutions": [
                    {
                      "id": "https://openalex.org/I1",
                      "display_name": "Universidad San Sebastián",
                      "ror": "https://ror.org/047gc3g35",
                      "country_code": "CL",
                      "type": "education"
                    }
                  ]
                }
              ]
            }
            """;
}
