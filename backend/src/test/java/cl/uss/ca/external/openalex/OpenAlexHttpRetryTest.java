package cl.uss.ca.external.openalex;

/**
 * 504 retry policy. Not executed by the agent. Does not call OpenAlex.
 */
public final class OpenAlexHttpRetryTest {
    private OpenAlexHttpRetryTest() {
    }

    public static void main(String[] args) {
        status504ThenSuccessIsNotTerminal();
        status504ExhaustedIsTerminal();
        System.out.println("OpenAlexHttpRetryTest OK");
    }

    static void status504ThenSuccessIsNotTerminal() {
        assert OpenAlexHttpRetry.retryableStatus(504);
        assert OpenAlexHttpRetry.shouldRetry(504, 0, 2);
        assert !OpenAlexHttpRetry.terminalAfterRetries(200, 1, 2);
        assert !OpenAlexFetchResult.found(200, new OpenAlexWorkRecord()).retryable();
    }

    static void status504ExhaustedIsTerminal() {
        assert OpenAlexHttpRetry.terminalAfterRetries(504, 2, 2);
        OpenAlexFetchResult exhausted = OpenAlexFetchResult.failure(
                OpenAlexStatus.ERROR, 504, "OPENALEX_HTTP_ERROR", "OpenAlex HTTP 504"
        );
        assert exhausted.retryable();
    }
}
