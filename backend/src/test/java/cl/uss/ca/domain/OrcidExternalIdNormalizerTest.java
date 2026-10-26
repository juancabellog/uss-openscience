package cl.uss.ca.domain;

/**
 * Proposed cases for ORCID identifier normalization.
 * Not executed by the agent.
 */
public final class OrcidExternalIdNormalizerTest {
    private OrcidExternalIdNormalizerTest() {
    }

    public static void main(String[] args) {
        doiLowercase();
        issnHyphen();
        issnNotIdentity();
        selfRelationship();
    }

    static void doiLowercase() {
        OrcidExternalIdNormalizer.Result result = OrcidExternalIdNormalizer.normalize(
                "doi",
                "10.1016/J.HELIYON.2023.E12345"
        );
        assert "10.1016/j.heliyon.2023.e12345".equals(result.normalizedValue);
        assert result.error == null;
    }

    static void issnHyphen() {
        OrcidExternalIdNormalizer.Result result = OrcidExternalIdNormalizer.normalize("issn", "26246511");
        assert "2624-6511".equals(result.normalizedValue);
    }

    static void issnNotIdentity() {
        assert !OrcidExternalIdNormalizer.isIdentityType("issn");
        assert OrcidExternalIdNormalizer.isIdentityType("doi");
        assert OrcidExternalIdNormalizer.isIdentityType("eid");
        assert OrcidExternalIdNormalizer.isIdentityType("wosuid");
    }

    static void selfRelationship() {
        assert OrcidExternalIdNormalizer.isSelf("self");
        assert OrcidExternalIdNormalizer.isSelf(null);
        assert !OrcidExternalIdNormalizer.isSelf("part-of");
    }
}
