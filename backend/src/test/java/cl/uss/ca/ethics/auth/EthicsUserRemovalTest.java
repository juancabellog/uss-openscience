package cl.uss.ca.ethics.auth;

import java.nio.file.Files;
import java.nio.file.Path;

public final class EthicsUserRemovalTest {
    private EthicsUserRemovalTest() {
    }

    public static void main(String[] args) throws Exception {
        check(EthicsUserRemoval.block(facts(0, 0, 0, 0, false, false)) == null, "a new user without history can be removed");
        check(EthicsUserRemoval.block(facts(0, 0, 0, 0, false, true)) == null, "a pending invitation without history can be removed");
        check(EthicsUserRemoval.HAS_HISTORY.equals(EthicsUserRemoval.block(facts(1, 0, 0, 0, false, false))), "a snapshot blocks removal");
        check(EthicsUserRemoval.HAS_HISTORY.equals(EthicsUserRemoval.block(facts(0, 1, 0, 0, false, false))), "a review assignment blocks removal");
        check(EthicsUserRemoval.HAS_HISTORY.equals(EthicsUserRemoval.block(facts(0, 0, 1, 0, false, false))), "a recorded act blocks removal");
        check(EthicsUserRemoval.HAS_HISTORY.equals(EthicsUserRemoval.block(facts(0, 0, 0, 1, false, false))), "a committee function blocks removal");
        check(EthicsUserRemoval.LAST_ADMINISTRATOR.equals(EthicsUserRemoval.block(facts(0, 0, 0, 0, true, false))), "the last administrator is kept");

        String repository = Files.readString(Path.of("src/main/java/cl/uss/ca/ethics/directory/EthicsDirectoryRepository.java"));
        String service = Files.readString(Path.of("src/main/java/cl/uss/ca/ethics/directory/EthicsDirectoryService.java"));
        int removal = repository.indexOf("String removeEthicsEnrollment");
        int next = repository.indexOf("public boolean activeReviewer", removal);
        String body = repository.substring(removal, next);
        check(body.contains("readRemovalFacts") && body.contains("deleteConfigurationRoles") && body.contains("countRoles"), "delete recounts history in the same flow");
        check(!body.contains("pure_person") && !body.contains("DELETE FROM \" + qualified(\"app_user\")"), "delete does not remove the shared account or PURE");
        check(service.contains("invalidateTokens") && service.contains("PURPOSE_ACTIVATION"), "a pending invitation token is cancelled");
        check(service.contains("ETHICS_USER_REMOVED") || service.contains("EthicsUserRemoval.EVENT"), "removal is audited");
        check(service.contains("lastAdministrator()") && service.contains("ACTIVE_REVIEW"), "disable keeps the last administrator and the active review confirmation");
        System.out.println("EthicsUserRemovalTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static EthicsUserRemoval.Facts facts(
            long snapshots,
            long reviewAssignments,
            long recordedActs,
            long committeeAssignments,
            boolean lastAdministrator,
            boolean pendingInvitation
    ) {
        return new EthicsUserRemoval.Facts(snapshots, reviewAssignments, recordedActs, committeeAssignments, lastAdministrator, pendingInvitation);
    }
}
