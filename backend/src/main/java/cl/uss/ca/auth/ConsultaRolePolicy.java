package cl.uss.ca.auth;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Default functional matrix of the CONSULTA system role.
 * Permissions still live in app_role_permission; this class documents
 * the intended seed and is used by contract tests.
 */
public final class ConsultaRolePolicy {
    public static final String CODE = "CONSULTA";
    public static final String NAME = "Consulta";
    public static final String DESCRIPTION =
            "Consulta de los módulos funcionales. Sin administración ni auditoría.";

    private ConsultaRolePolicy() {
    }

    public static Set<AppModule> consultModules() {
        return EnumSet.of(
                AppModule.DASHBOARD,
                AppModule.PEOPLE,
                AppModule.RESEARCH_OUTPUTS,
                AppModule.JOURNALS,
                AppModule.PROJECTS,
                AppModule.ORGANIZATIONS,
                AppModule.DIRECTORY,
                AppModule.DATA_QUALITY,
                AppModule.INDICATORS,
                AppModule.INTEGRATIONS
        );
    }

    public static Set<EffectivePermission> grants() {
        Set<EffectivePermission> granted = new LinkedHashSet<>();
        for (AppModule module : consultModules()) {
            granted.add(new EffectivePermission(module, AppPermission.CONSULT));
        }
        return granted;
    }

    public static boolean grants(AppModule module, AppPermission permission) {
        return permission == AppPermission.CONSULT && consultModules().contains(module);
    }

    public static boolean hasAdminister() {
        for (EffectivePermission grant : grants()) {
            if (grant.permission == AppPermission.ADMINISTER) {
                return true;
            }
        }
        return false;
    }
}
