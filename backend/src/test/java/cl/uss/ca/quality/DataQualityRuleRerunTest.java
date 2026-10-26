package cl.uss.ca.quality;

/**
 * Single-rule data-quality rerun. Not executed by the agent.
 */
public final class DataQualityRuleRerunTest {
    private DataQualityRuleRerunTest() {
    }

    public static void main(String[] args) {
        fullScanKeepsEveryRule();
        blankFilterKeepsEveryRule();
        namedRuleSelectsOnlyThatCode();
        catalogRejectsUnknownRule();
        catalogAcceptsCrossref007();
        System.out.println("DataQualityRuleRerunTest OK");
    }

    static void fullScanKeepsEveryRule() {
        assert DataQualityScanEngine.selected(null, "CROSSREF-007");
        assert DataQualityScanEngine.selected(null, "PURE-001");
        assert DataQualityScanEngine.selected(null, "OPENALEX-001");
    }

    static void blankFilterKeepsEveryRule() {
        assert DataQualityScanEngine.selected("  ", "CROSSREF-007");
        assert DataQualityScanEngine.selected("", "PURE-013");
    }

    static void namedRuleSelectsOnlyThatCode() {
        assert DataQualityScanEngine.selected("CROSSREF-007", "CROSSREF-007");
        assert !DataQualityScanEngine.selected("CROSSREF-007", "CROSSREF-001");
        assert !DataQualityScanEngine.selected("CROSSREF-007", "PURE-001");
        assert !DataQualityScanEngine.selected("CROSSREF-007", "OPENALEX-001");
        assert !DataQualityScanEngine.selected("CROSSREF-007", "EXT-01");
    }

    static void catalogRejectsUnknownRule() {
        try {
            DataQualityCatalog.require("NOT-A-RULE");
            throw new AssertionError("expected unknown rule");
        } catch (IllegalArgumentException ex) {
            assert ex.getMessage().contains("NOT-A-RULE");
        }
    }

    static void catalogAcceptsCrossref007() {
        DataQualityCatalog.RuleDef rule = DataQualityCatalog.require("CROSSREF-007");
        assert "CROSSREF-007".equals(rule.code());
        assert "RESEARCH_OUTPUT".equals(rule.entityType());
        assert DataQualityCatalog.implementableCodes().contains("CROSSREF-007");
    }
}
