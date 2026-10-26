package cl.uss.ca.external.pure.metrics;

import java.math.BigDecimal;
import java.util.Objects;

public final class PureMetricObservation {
    public String collectionId;
    public String sourceUri;
    public String sourceTermEs;
    public String sourceTermEn;
    public String categoryUri;
    public String categoryTermEs;
    public String categoryTermEn;
    public Integer metricYear;
    public String metricId;
    public PureMetricValueKind valueKind;
    public Long integerValue;
    public BigDecimal decimalValue;
    public String stringValue;

    public static PureMetricObservation integer(
            String collectionId,
            String metricId,
            Integer year,
            Long value
    ) {
        PureMetricObservation observation = base(collectionId, metricId, year);
        observation.valueKind = PureMetricValueKind.INTEGER;
        observation.integerValue = value;
        return observation;
    }

    public static PureMetricObservation decimal(
            String collectionId,
            String metricId,
            Integer year,
            BigDecimal value
    ) {
        PureMetricObservation observation = base(collectionId, metricId, year);
        observation.valueKind = PureMetricValueKind.DECIMAL;
        observation.decimalValue = value;
        return observation;
    }

    public static PureMetricObservation string(
            String collectionId,
            String metricId,
            Integer year,
            String value
    ) {
        PureMetricObservation observation = base(collectionId, metricId, year);
        observation.valueKind = PureMetricValueKind.STRING;
        observation.stringValue = value;
        return observation;
    }

    private static PureMetricObservation base(String collectionId, String metricId, Integer year) {
        PureMetricObservation observation = new PureMetricObservation();
        observation.collectionId = collectionId;
        observation.metricId = metricId;
        observation.metricYear = year;
        return observation;
    }

    public PureMetricObservation withSource(String uri, String termEs, String termEn) {
        this.sourceUri = uri;
        this.sourceTermEs = termEs;
        this.sourceTermEn = termEn;
        return this;
    }

    public PureMetricObservation withCategory(String uri, String termEs, String termEn) {
        this.categoryUri = uri;
        this.categoryTermEs = termEs;
        this.categoryTermEn = termEn;
        return this;
    }

    public String identityKey(long ownerId) {
        return PureMetricIdentity.key(
                ownerId,
                collectionId,
                metricId,
                metricYear,
                sourceUri,
                categoryUri
        );
    }

    public boolean isZero() {
        if (valueKind == PureMetricValueKind.INTEGER) {
            return Objects.equals(integerValue, 0L);
        }
        if (valueKind == PureMetricValueKind.DECIMAL) {
            return decimalValue != null && decimalValue.compareTo(BigDecimal.ZERO) == 0;
        }
        return "0".equals(stringValue);
    }
}
