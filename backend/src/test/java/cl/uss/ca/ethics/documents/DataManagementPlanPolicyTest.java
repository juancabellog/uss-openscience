package cl.uss.ca.ethics.documents;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class DataManagementPlanPolicyTest {
    private DataManagementPlanPolicyTest() {
    }

    public static void main(String[] args) throws Exception {
        check(DataManagementPlanPolicy.current(List.of()) == null, "A. a request without a plan has no current version");

        var first = DataManagementPlanPolicy.attach(
                List.of(),
                DataManagementPlanPolicy.Channel.DATA_MANAGEMENT_PLAN,
                "documento_final_2026.pdf",
                "u-inv-1",
                "2026-03-02T10:00:00");
        check(DataManagementPlanPolicy.TYPE.equals(first.documentType()), "B. the plan channel sets DATA_MANAGEMENT_PLAN");
        check(DataManagementPlanPolicy.TYPE.equals(first.logicalId()), "B. the logical id is the stable type");
        check("documento_final_2026.pdf".equals(first.fileName()), "E. an arbitrary file name is stored and does not choose the type");

        var second = DataManagementPlanPolicy.attach(
                List.of(first),
                DataManagementPlanPolicy.Channel.DATA_MANAGEMENT_PLAN,
                "otro_archivo.pdf",
                "u-inv-1",
                "2026-03-12T10:00:00");
        check(first.logicalId().equals(second.logicalId()), "C. a new upload keeps the same logical document");
        check(second.version() == 2 && first.version() == 1, "C. the new upload is the next version");
        var series = List.of(first, second);
        check(DataManagementPlanPolicy.current(series).version() == 2, "the highest version is current");
        check(DataManagementPlanPolicy.history(series).size() == 1
                && DataManagementPlanPolicy.history(series).get(0).version() == 1, "D. the previous version stays in history");

        var namedLikePlan = DataManagementPlanPolicy.attach(
                series,
                DataManagementPlanPolicy.Channel.OTHER,
                "Plan de Gestion de Datos.pdf",
                "u-inv-1",
                "2026-06-02T12:00:00");
        check(!DataManagementPlanPolicy.isPlan(namedLikePlan.documentType()), "F. a generic upload is not a plan");
        check(DataManagementPlanPolicy.current(List.of(first, second, namedLikePlan)).id().equals(second.id()),
                "F. the generic file does not become the current plan");

        var again = new ArrayList<>(List.of(first, second, namedLikePlan));
        var third = DataManagementPlanPolicy.attach(
                again,
                DataManagementPlanPolicy.Channel.DATA_MANAGEMENT_PLAN,
                "pgd.pdf",
                "u-inv-1",
                "2026-06-03T12:00:00");
        again.add(third);
        long logicalPlans = again.stream()
                .filter(row -> DataManagementPlanPolicy.isPlan(row.documentType()))
                .map(DataManagementPlanPolicy.Version::logicalId)
                .distinct()
                .count();
        check(DataManagementPlanPolicy.TYPE.equals(third.documentType()), "a file named pgd.pdf is a plan only because of the channel");
        check(logicalPlans == 1, "one active logical plan");
        check(third.version() == 3 && again.contains(first) && again.contains(second), "earlier versions are not replaced");

        String permissions = resourceText("/ethics/permissions-V4.yml");
        check(!permissions.contains("VIEW_DATA_MANAGEMENT_PLAN") && !permissions.contains("UPLOAD_DATA_MANAGEMENT_PLAN"),
                "no plan-specific capabilities");

        System.out.println("DataManagementPlanPolicyTest OK");
    }

    private static String resourceText(String path) throws Exception {
        try (var input = DataManagementPlanPolicyTest.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("missing " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
