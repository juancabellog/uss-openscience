package cl.uss.ca.external.pure.metrics;

/**
 * Snapshot replace rules without a database. Not executed by the agent.
 */
public final class PureMetricSnapshotLogicTest {
    private PureMetricSnapshotLogicTest() {
    }

    public static void main(String[] args) {
        successReplaces();
        emptyReplaces();
        failedKeepsPrevious();
        invalidKeepsPrevious();
        System.out.println("PureMetricSnapshotLogicTest OK");
    }

    static void successReplaces() {
        if (!PureMetricFetchResult.success(java.util.List.of(
                PureMetricObservation.integer("sciVal", "fwci", null, 1L)
        )).replacesSnapshot()) {
            throw new AssertionError("SUCCESS must replace");
        }
    }

    static void emptyReplaces() {
        if (!PureMetricFetchResult.empty().replacesSnapshot()) {
            throw new AssertionError("confirmed EMPTY must replace previous snapshot");
        }
    }

    static void failedKeepsPrevious() {
        if (PureMetricFetchResult.failed(429, "PURE_HTTP_429", "rate limited").replacesSnapshot()) {
            throw new AssertionError("FAILED must keep previous metrics");
        }
    }

    static void invalidKeepsPrevious() {
        if (PureMetricFetchResult.invalid("OWNER_MISMATCH", "uuid").replacesSnapshot()) {
            throw new AssertionError("INVALID must keep previous metrics");
        }
    }
}
