package cl.uss.ca.quality;

import cl.uss.ca.repository.CrossrefRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * CROSSREF-007 representation. Not executed by the agent.
 */
public final class CrossrefOrcidFindingTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CrossrefOrcidFindingTest() {
    }

    public static void main(String[] args) throws Exception {
        oneExternalOrcid();
        severalExternalOrcids();
        duplicateCrossrefOrcids();
        megaPaperKeepsShortSuggestion();
        evidenceKeepsFullList();
        externalOrcidCountMatchesList();
        findingKeyUnchanged();
        rerunDoesNotChangeKey();
        viewOnlyPreserved();
        noPureWriteSurface();
        megaPaperJsonIsValid();
        currentValueStaysReasonable();
        System.out.println("CrossrefOrcidFindingTest OK");
    }

    static void oneExternalOrcid() throws Exception {
        DataQualityCandidate candidate = CrossrefOrcidFinding.create(
                row(15L, 88L, "10.1000/one"),
                List.of("https://orcid.org/0000-0002-1825-0097"),
                List.of(),
                MAPPER
        );
        assert candidate != null;
        assert "0000-0002-1825-0097".equals(evidence(candidate).get("externalOrcids").get(0).asText());
        assert candidate.suggestedValue.contains("1 ORCID de autor");
        assert candidate.suggestedValue.length() < 200;
        assert candidate.currentValue == null;
    }

    static void severalExternalOrcids() throws Exception {
        DataQualityCandidate candidate = CrossrefOrcidFinding.create(
                row(15L, 88L, "10.1000/several"),
                List.of("0000-0002-1825-0097", "0000-0001-5109-3700", "0000-0003-2140-263X"),
                List.of("0000-0002-1825-0097"),
                MAPPER
        );
        assert candidate != null;
        JsonNode orcids = evidence(candidate).get("externalOrcids");
        assert orcids.size() == 2;
        assert "0000-0001-5109-3700".equals(orcids.get(0).asText());
        assert "0000-0003-2140-263X".equals(orcids.get(1).asText());
        assert "0000-0002-1825-0097".equals(candidate.currentValue);
        assert candidate.suggestedValue.contains("2 ORCID de autores");
        assert !candidate.suggestedValue.contains("0000-0001-5109-3700");
    }

    static void duplicateCrossrefOrcids() throws Exception {
        DataQualityCandidate candidate = CrossrefOrcidFinding.create(
                row(15L, 88L, "10.1000/dup"),
                List.of(
                        "0000-0002-1825-0097",
                        "https://orcid.org/0000-0002-1825-0097",
                        "0000-0002-1825-0097",
                        "0000-0001-5109-3700"
                ),
                List.of(),
                MAPPER
        );
        assert candidate != null;
        JsonNode orcids = evidence(candidate).get("externalOrcids");
        assert orcids.size() == 2;
        assert evidence(candidate).get("externalOrcidCount").asInt() == 2;
        assert "0000-0002-1825-0097".equals(orcids.get(0).asText());
        assert "0000-0001-5109-3700".equals(orcids.get(1).asText());
        assert candidate.suggestedValue.contains("2 ORCID");
    }

    static void megaPaperKeepsShortSuggestion() throws Exception {
        DataQualityCandidate candidate = mega(3013);
        assert candidate != null;
        assert candidate.suggestedValue.length() < 200;
        assert !candidate.suggestedValue.contains(",");
        assert candidate.suggestedValue.contains("3.013 ORCID de autores");
        assert candidate.suggestedValue.startsWith("Crossref informa ");
    }

    static void evidenceKeepsFullList() throws Exception {
        DataQualityCandidate candidate = mega(3013);
        JsonNode evidence = evidence(candidate);
        assert evidence.get("externalOrcids").size() == 3013;
        assert "CROSSREF".equals(evidence.get("source").asText());
        assert "10.1000/mega".equals(evidence.get("doi").asText());
        assert "FOUND".equals(evidence.get("status").asText());
        assert "ORCID_EXTERNAL|99".equals(evidence.get("contextKey").asText());
    }

    static void externalOrcidCountMatchesList() throws Exception {
        DataQualityCandidate candidate = mega(2876);
        JsonNode evidence = evidence(candidate);
        assert evidence.get("externalOrcidCount").asInt() == 2876;
        assert evidence.get("externalOrcidCount").asInt() == evidence.get("externalOrcids").size();
        assert candidate.suggestedValue.contains("2.876");
    }

    static void findingKeyUnchanged() {
        DataQualityCandidate candidate = CrossrefOrcidFinding.create(
                row(15L, 88L, "10.1000/key"),
                List.of("0000-0002-1825-0097"),
                List.of(),
                MAPPER
        );
        assert candidate != null;
        assert CrossrefOrcidFinding.findingKey(15L, 88L).equals(candidate.findingKey);
        assert "CROSSREF-007|RESEARCH_OUTPUT|15|orcid|ORCID_EXTERNAL|88".equals(candidate.findingKey);
    }

    static void rerunDoesNotChangeKey() {
        CrossrefRepository.QualityRow row = row(15L, 88L, "10.1000/rerun");
        List<String> external = List.of("0000-0002-1825-0097", "0000-0001-5109-3700");
        DataQualityCandidate first = CrossrefOrcidFinding.create(row, external, List.of(), MAPPER);
        DataQualityCandidate second = CrossrefOrcidFinding.create(row, external, List.of(), MAPPER);
        assert first != null && second != null;
        assert first.findingKey.equals(second.findingKey);
        assert first.findingKey.equals(CrossrefOrcidFinding.findingKey(15L, 88L));
    }

    static void viewOnlyPreserved() {
        DataQualityCandidate candidate = CrossrefOrcidFinding.create(
                row(15L, 88L, "10.1000/view"),
                List.of("0000-0002-1825-0097"),
                List.of(),
                MAPPER
        );
        assert candidate != null;
        assert DataQualityCatalog.MODE_VIEW.equals(candidate.correctionMode);
        assert DataQualityCatalog.MODE_VIEW.equals(DataQualityCatalog.require("CROSSREF-007").correctionMode());
        assert !"SAFE_SUGGESTION".equals(candidate.correctionMode);
        assert !"MANUAL_REVIEW".equals(candidate.correctionMode);
    }

    static void noPureWriteSurface() {
        DataQualityCandidate candidate = CrossrefOrcidFinding.create(
                row(15L, 88L, "10.1000/nowrite"),
                List.of("0000-0002-1825-0097"),
                List.of(),
                MAPPER
        );
        assert candidate != null;
        assert DataQualityCatalog.MODE_VIEW.equals(candidate.correctionMode);
        assert !"EXACT_DUPLICATE".equals(candidate.issueKind);
        assert "ORCID_EXTERNAL".equals(candidate.issueKind);
        assert DataQualityCatalog.require("CROSSREF-007").ruleText().contains("No escribir en PURE");
    }

    static void currentValueStaysReasonable() {
        List<String> institutional = new ArrayList<>();
        for (int index = 0; index < 41; index++) {
            institutional.add(syntheticOrcid(index));
        }
        DataQualityCandidate candidate = CrossrefOrcidFinding.create(
                row(15L, 88L, "10.1000/current"),
                List.of(syntheticOrcid(90)),
                institutional,
                MAPPER
        );
        assert candidate != null;
        assert candidate.currentValue != null;
        assert candidate.currentValue.length() < 120;
        assert !candidate.currentValue.contains(",");
        assert candidate.currentValue.contains("41");
    }

    static void megaPaperJsonIsValid() throws Exception {
        DataQualityCandidate candidate = mega(3013);
        JsonNode evidence = MAPPER.readTree(candidate.evidenceJson);
        assert evidence.isObject();
        assert evidence.get("externalOrcids").isArray();
        assert evidence.get("externalOrcids").size() == 3013;
        assert evidence.get("externalOrcidCount").asInt() == 3013;
        assert candidate.evidenceJson.length() > 50_000;
        assert candidate.suggestedValue.length() < 200;
    }

    private static DataQualityCandidate mega(int count) {
        List<String> orcids = new ArrayList<>(count + 8);
        for (int index = 0; index < count; index++) {
            orcids.add(syntheticOrcid(index));
        }
        orcids.add(syntheticOrcid(0));
        orcids.add("https://orcid.org/" + syntheticOrcid(1).toLowerCase());
        return CrossrefOrcidFinding.create(row(7L, 99L, "10.1000/mega"), orcids, List.of(), MAPPER);
    }

    private static String syntheticOrcid(int index) {
        return String.format("0000-0002-%04d-%04d", index / 10_000, index % 10_000);
    }

    private static CrossrefRepository.QualityRow row(long outputId, long workId, String doi) {
        CrossrefRepository.QualityRow row = new CrossrefRepository.QualityRow();
        row.researchOutputId = outputId;
        row.workId = workId;
        row.normalizedDoi = doi;
        row.status = "FOUND";
        row.pureId = 1001L;
        row.uuid = "11111111-1111-1111-1111-111111111111";
        row.hasOrcid = true;
        return row;
    }

    private static JsonNode evidence(DataQualityCandidate candidate) throws Exception {
        return MAPPER.readTree(candidate.evidenceJson);
    }
}
