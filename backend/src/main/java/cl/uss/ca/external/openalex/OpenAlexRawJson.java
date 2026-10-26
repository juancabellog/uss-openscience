package cl.uss.ca.external.openalex;

/**
 * Persist the OpenAlex work payload as valid JSON. The previous 1_000_000-char
 * substring produced invalid JSON on mega-author papers.
 */
public final class OpenAlexRawJson {
    public static final int LEGACY_CLIP_CHARS = 1_000_000;

    private OpenAlexRawJson() {
    }

    public static void apply(OpenAlexWorkRecord work, String body) {
        if (work == null) {
            return;
        }
        work.rawJson = body;
        work.rawJsonOriginalSize = body == null ? 0 : body.length();
        work.rawJsonTruncated = false;
    }

    public static boolean legacyClipWouldInvalidate(String body) {
        return body != null && body.length() > LEGACY_CLIP_CHARS;
    }
}
