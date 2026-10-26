package cl.uss.ca.auth;

public final class LastAdminPolicy {
    private LastAdminPolicy() {
    }

    public static boolean canRemoveAdminAccess(boolean currentlyAdmin, boolean stillAdmin, long remainingAdmins) {
        if (!currentlyAdmin || stillAdmin) {
            return true;
        }
        return remainingAdmins > 1;
    }

    public static boolean canDemoteAdminRole(
            boolean roleCurrentlyAdmin,
            boolean stillAdmin,
            long remainingAdmins,
            long usersOnRole
    ) {
        if (!roleCurrentlyAdmin || stillAdmin) {
            return true;
        }
        if (remainingAdmins > 0 && usersOnRole > 0 && remainingAdmins <= usersOnRole) {
            return false;
        }
        return remainingAdmins > 1;
    }
}
