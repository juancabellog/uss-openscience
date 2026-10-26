package cl.uss.ca.api.dto;

public class IndicatorMeasureDto {
    public long numerator;
    public long denominator;
    public Double percentage;
    public String universe;

    public static IndicatorMeasureDto of(long numerator, long denominator, String universe) {
        IndicatorMeasureDto dto = new IndicatorMeasureDto();
        dto.numerator = numerator;
        dto.denominator = denominator;
        dto.universe = universe;
        dto.percentage = denominator <= 0
                ? null
                : Math.round(numerator * 1000.0 / denominator) / 10.0;
        return dto;
    }
}
