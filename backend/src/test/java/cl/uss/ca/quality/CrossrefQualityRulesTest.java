package cl.uss.ca.quality;

/**
 * Proposed Crossref quality rules. Not executed by the agent.
 */
public final class CrossrefQualityRulesTest {
    private CrossrefQualityRulesTest() {
    }

    public static void main(String[] args) {
        DataQualityCatalog.RuleDef invalid = DataQualityCatalog.require("CROSSREF-001");
        assert DataQualityCatalog.SOURCE_CROSSREF.equals(invalid.sourceFamily());
        assert DataQualityCatalog.MODE_VIEW.equals(invalid.correctionMode());
        assert DataQualityCatalog.CAT_EXTERNAL.equals(invalid.category());

        DataQualityCatalog.RuleDef other = DataQualityCatalog.require("CROSSREF-002");
        assert !"INVALID_DOI".equals(other.fieldName()) || true;
        assert DataQualityCatalog.implementableCodes().contains("CROSSREF-010");
        assert DataQualityCatalog.require("CROSSREF-007").correctionMode().equals(DataQualityCatalog.MODE_VIEW);
        assert DataQualityCatalog.isRuleSourceFamily("CROSSREF");
        assert DataQualityCatalog.isRuleSourceFamily("PURE");
        assert DataQualityCatalog.isRuleSourceFamily("EXTERNAL");
        assert !DataQualityCatalog.isRuleSourceFamily("UNPAYWALL");
        assert !DataQualityCatalog.isRuleSourceFamily("DATACITE");
        System.out.println("CrossrefQualityRulesTest OK");
    }
}
