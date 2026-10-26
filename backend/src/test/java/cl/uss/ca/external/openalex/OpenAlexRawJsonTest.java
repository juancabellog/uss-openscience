package cl.uss.ca.external.openalex;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * raw_json must remain valid JSON. Not executed by the agent. Does not call OpenAlex.
 */
public final class OpenAlexRawJsonTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private OpenAlexRawJsonTest() {
    }

    public static void main(String[] args) throws Exception {
        payloadUnderOneMbStaysValid();
        payloadOverOneMbStaysCompleteAndValid();
        neverCutsMidString();
        truncationFlagsAreIndependent();
        System.out.println("OpenAlexRawJsonTest OK");
    }

    static void payloadUnderOneMbStaysValid() throws Exception {
        String body = "{\"id\":\"https://openalex.org/W1\",\"authorships\":[]}";
        OpenAlexWorkRecord work = new OpenAlexWorkRecord();
        OpenAlexRawJson.apply(work, body);
        assert MAPPER.readTree(work.rawJson).isObject();
        assert work.rawJson.equals(body);
        assert !work.rawJsonTruncated;
        assert work.rawJsonOriginalSize == body.length();
        assert !OpenAlexRawJson.legacyClipWouldInvalidate(body);
    }

    static void payloadOverOneMbStaysCompleteAndValid() throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("id", "https://openalex.org/W17073");
        String padding = "x".repeat(1_000_050);
        root.put("display_name", padding);
        String body = MAPPER.writeValueAsString(root);
        assert body.length() > OpenAlexRawJson.LEGACY_CLIP_CHARS;
        OpenAlexWorkRecord work = new OpenAlexWorkRecord();
        OpenAlexRawJson.apply(work, body);
        assert work.rawJson.length() == body.length();
        assert MAPPER.readTree(work.rawJson).path("display_name").asText().length() == padding.length();
        assert !work.rawJsonTruncated;
        assert OpenAlexRawJson.legacyClipWouldInvalidate(body);
        String legacy = body.substring(0, OpenAlexRawJson.LEGACY_CLIP_CHARS);
        boolean legacyValid = true;
        try {
            MAPPER.readTree(legacy);
        } catch (Exception ex) {
            legacyValid = false;
        }
        assert !legacyValid;
    }

    static void neverCutsMidString() throws Exception {
        String body = "{\"note\":\"abcdefghij\"}";
        OpenAlexWorkRecord work = new OpenAlexWorkRecord();
        OpenAlexRawJson.apply(work, body);
        assert work.rawJson.endsWith("\"}");
        assert work.rawJson.equals(body);
    }

    static void truncationFlagsAreIndependent() {
        OpenAlexWorkRecord work = new OpenAlexWorkRecord();
        OpenAlexRawJson.apply(work, "{\"id\":\"W1\"}");
        work.authorshipsIncomplete = true;
        assert work.authorshipsTruncated();
        assert !work.rawJsonTruncated;
        work.authorshipsIncomplete = false;
        work.rawJsonTruncated = true;
        assert !work.authorshipsTruncated();
        assert work.rawJsonTruncated;
    }
}
