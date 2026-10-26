package cl.uss.ca.api.dto;

public class ResearcherIdentityIndicatorsDto {
    public IndicatorMeasureDto scopusCoverage;
    public IndicatorMeasureDto multipleScopusProfiles;
    public IndicatorMeasureDto controlledSignature;
    public IndicatorMeasureDto highSignatureFragmentation;
    public IndicatorMeasureDto normalizedUssOrcidAffiliation;
    public long orcidNormalizedCount;
    public long orcidVariantCount;
    public long orcidNotFoundCount;
    public long orcidNotEvaluableCount;
}
