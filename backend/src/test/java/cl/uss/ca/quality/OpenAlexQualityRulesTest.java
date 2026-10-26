package cl.uss.ca.quality;

/**
 * Proposed OpenAlex quality rules. Not executed by the agent.
 */
public final class OpenAlexQualityRulesTest {
    private OpenAlexQualityRulesTest() {
    }

    public static void main(String[] args) {
        DataQualityCatalog.RuleDef strong = DataQualityCatalog.require("OPENALEX-001");
        assert DataQualityCatalog.SOURCE_OPENALEX.equals(strong.sourceFamily());
        assert DataQualityCatalog.MODE_VIEW.equals(strong.correctionMode());
        assert DataQualityCatalog.CAT_EXTERNAL.equals(strong.category());

        DataQualityCatalog.RuleDef orcid = DataQualityCatalog.require("OPENALEX-002");
        assert "orcid".equals(orcid.fieldName());
        assert DataQualityCatalog.MODE_VIEW.equals(orcid.correctionMode());

        assert DataQualityCatalog.implementableCodes().contains("OPENALEX-003");
        assert DataQualityCatalog.implementableCodes().contains("OPENALEX-004");
        assert DataQualityCatalog.require("OPENALEX-003").correctionMode().equals(DataQualityCatalog.MODE_VIEW);
        assert DataQualityCatalog.require("OPENALEX-004").correctionMode().equals(DataQualityCatalog.MODE_VIEW);
        System.out.println("OpenAlexQualityRulesTest OK");
    }
}
