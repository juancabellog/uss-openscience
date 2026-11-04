package cl.uss.ca.ethics.auth;

/**
 * Alta de Ethics sin historia persistida. No decide sobre proyectos que todavía
 * no tienen tabla: ese hueco se informa aparte y no se trata como "cero proyectos".
 */
public final class EthicsUserRemoval {
    public static final String HAS_HISTORY = "ETHICS_USER_HAS_HISTORY";
    public static final String LAST_ADMINISTRATOR = "LAST_ADMINISTRATOR";
    public static final String EVENT = "ETHICS_USER_REMOVED";

    private EthicsUserRemoval() {
    }

    public record Facts(
            long snapshots,
            long reviewAssignments,
            long recordedActs,
            long committeeAssignments,
            boolean lastAdministrator,
            boolean pendingInvitation
    ) {
    }

    /** @return código de rechazo, o null si el alta de Ethics puede eliminarse */
    public static String block(Facts facts) {
        if (facts == null) {
            return HAS_HISTORY;
        }
        if (facts.lastAdministrator()) {
            return LAST_ADMINISTRATOR;
        }
        if (facts.snapshots() > 0 || facts.reviewAssignments() > 0 || facts.recordedActs() > 0 || facts.committeeAssignments() > 0) {
            return HAS_HISTORY;
        }
        return null;
    }
}
