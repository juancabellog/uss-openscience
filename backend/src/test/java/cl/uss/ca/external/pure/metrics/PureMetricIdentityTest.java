package cl.uss.ca.external.pure.metrics;

/**
 * Unique identity and 0 vs NULL. Not executed by the agent.
 */
public final class PureMetricIdentityTest {
    private PureMetricIdentityTest() {
    }

    public static void main(String[] args) {
        sameInputsSameKey();
        nullYearDistinctFromZero();
        nullSourceDoesNotCollideWithEmptyIfTrimmedSame();
        categoryDifferentiatesSameYear();
        System.out.println("PureMetricIdentityTest OK");
    }

    static void sameInputsSameKey() {
        String left = PureMetricIdentity.key(10, "scopusJournal", "rating_citescore", 2024, "scopus", null);
        String right = PureMetricIdentity.key(10, "scopusJournal", "rating_citescore", 2024, "scopus", null);
        if (!left.equals(right)) {
            throw new AssertionError("identity must be deterministic");
        }
    }

    static void nullYearDistinctFromZero() {
        String missing = PureMetricIdentity.key(1, "sciVal", "fwci", null, null, null);
        String zero = PureMetricIdentity.key(1, "sciVal", "fwci", 0, null, null);
        if (missing.equals(zero)) {
            throw new AssertionError("NULL year must not collide with year 0");
        }
    }

    static void nullSourceDoesNotCollideWithEmptyIfTrimmedSame() {
        String n = PureMetricIdentity.key(1, "hIndex", "hindex", null, null, "all-years");
        String e = PureMetricIdentity.key(1, "hIndex", "hindex", null, "  ", "all-years");
        if (!n.equals(e)) {
            throw new AssertionError("blank source must sentinel to empty like NULL");
        }
    }

    static void categoryDifferentiatesSameYear() {
        String medicine = PureMetricIdentity.key(9, "scopusCitescore", "quartile", 2023, "scopus", "asjc/2700");
        String agri = PureMetricIdentity.key(9, "scopusCitescore", "quartile", 2023, "scopus", "asjc/1100");
        if (medicine.equals(agri)) {
            throw new AssertionError("categories must remain distinct");
        }
    }
}
