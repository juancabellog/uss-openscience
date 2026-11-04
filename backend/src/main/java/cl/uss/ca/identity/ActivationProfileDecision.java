package cl.uss.ca.identity;

/**
 * Una invitación de Ética sin ficha PURE exige el perfil local.
 * Una ficha PURE, o una cuenta que ya tiene nombre y no viene de esa invitación, no lo exige.
 */
public final class ActivationProfileDecision {
    private ActivationProfileDecision() {
    }

    public static boolean profileRequired(boolean ethicsInvitation, boolean pureReadonly, boolean namesMissing) {
        if (pureReadonly) {
            return false;
        }
        if (ethicsInvitation) {
            return true;
        }
        return namesMissing;
    }

    public static boolean passwordAloneRejected(
            boolean ethicsInvitation,
            boolean pureReadonly,
            boolean suppliedProfile,
            boolean namesMissing
    ) {
        if (pureReadonly) {
            return false;
        }
        if (ethicsInvitation) {
            return !suppliedProfile;
        }
        return namesMissing && !suppliedProfile;
    }
}
