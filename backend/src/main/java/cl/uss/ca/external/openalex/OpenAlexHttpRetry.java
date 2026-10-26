package cl.uss.ca.external.openalex;

/**
 * Transient OpenAlex HTTP failures. A later successful attempt in the same
 * send loop is the only outcome that matters.
 */
public final class OpenAlexHttpRetry {
    private OpenAlexHttpRetry() {
    }

    public static boolean retryableStatus(int status) {
        return status == 429 || status == 500 || status == 502 || status == 503 || status == 504;
    }

    public static boolean shouldRetry(int status, int attempt, int maxAttempts) {
        return retryableStatus(status) && attempt < maxAttempts;
    }

    public static boolean terminalAfterRetries(int status, int attempt, int maxAttempts) {
        return retryableStatus(status) && attempt >= maxAttempts;
    }

    public static long backoffMs(int attempt) {
        return 500L * (attempt + 1);
    }
}
