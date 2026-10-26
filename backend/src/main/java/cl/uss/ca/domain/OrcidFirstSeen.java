package cl.uss.ca.domain;

import java.time.OffsetDateTime;

/**
 * Local observation of the first time OPENSCIENCE-USS saw an ORCID on a PURE person.
 * Not the date PURE added the identifier.
 */
public final class OrcidFirstSeen {
    public static final String BASELINE = "BASELINE";
    public static final String OBSERVED = "OBSERVED";

    private OrcidFirstSeen() {
    }

    /**
     * @return {@link #BASELINE}, {@link #OBSERVED}, or {@code null} when the stamp must not change
     */
    public static String sourceToStamp(String previousOrcid, OffsetDateTime previousSeen, String incomingOrcid) {
        if (OrcidFormat.normalize(incomingOrcid) == null) {
            return null;
        }
        if (previousSeen != null) {
            return null;
        }
        if (OrcidFormat.normalize(previousOrcid) != null) {
            return BASELINE;
        }
        return OBSERVED;
    }

    public static boolean isKnownSource(String source) {
        return BASELINE.equals(source) || OBSERVED.equals(source);
    }
}
