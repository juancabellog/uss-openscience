package cl.uss.ca.external.pure.metrics;

import java.util.List;

public final class PureMetricFetchResult {
    public final PureMetricFetchOutcome outcome;
    public final List<PureMetricObservation> observations;
    public final String errorCode;
    public final String errorMessage;
    public final Integer httpStatus;

    private PureMetricFetchResult(
            PureMetricFetchOutcome outcome,
            List<PureMetricObservation> observations,
            String errorCode,
            String errorMessage,
            Integer httpStatus
    ) {
        this.outcome = outcome;
        this.observations = observations == null ? List.of() : List.copyOf(observations);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.httpStatus = httpStatus;
    }

    public static PureMetricFetchResult success(List<PureMetricObservation> observations) {
        if (observations == null || observations.isEmpty()) {
            return empty();
        }
        return new PureMetricFetchResult(PureMetricFetchOutcome.SUCCESS, observations, null, null, 200);
    }

    public static PureMetricFetchResult empty() {
        return new PureMetricFetchResult(PureMetricFetchOutcome.EMPTY, List.of(), null, null, 200);
    }

    public static PureMetricFetchResult failed(Integer httpStatus, String code, String message) {
        return new PureMetricFetchResult(PureMetricFetchOutcome.FAILED, List.of(), code, message, httpStatus);
    }

    public static PureMetricFetchResult invalid(String code, String message) {
        return new PureMetricFetchResult(PureMetricFetchOutcome.INVALID, List.of(), code, message, 200);
    }

    public boolean replacesSnapshot() {
        return outcome == PureMetricFetchOutcome.SUCCESS || outcome == PureMetricFetchOutcome.EMPTY;
    }
}
