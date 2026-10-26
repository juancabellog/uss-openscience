package cl.uss.ca.domain;

/**
 * URIs observadas en GET /projects de PURE USS. No se infieren roles USS.
 */
public final class PureProjectUris {
    public static final String PROJECT_ID = "/dk/atira/pure/upm/classifiedsource/projectid";
    public static final String FUNDER_REF = "/dk/atira/pure/upm/classifiedsource/funderprojectreference";
    public static final String PI_ROLE = "/dk/atira/pure/upmproject/roles/upmproject/pi";

    private PureProjectUris() {
    }

    public static boolean isProjectId(String typeUri) {
        return PROJECT_ID.equals(typeUri);
    }

    public static boolean isFunderReference(String typeUri) {
        return FUNDER_REF.equals(typeUri);
    }

    public static boolean isPrincipalInvestigator(String roleUri) {
        return PI_ROLE.equals(roleUri);
    }
}
