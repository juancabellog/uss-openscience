package cl.uss.ca.auth;

import java.util.LinkedHashSet;
import java.util.Set;

public final class PermissionEvaluator {
    private PermissionEvaluator() {
    }

    public static Set<EffectivePermission> union(Iterable<EffectivePermission> grants) {
        Set<EffectivePermission> result = new LinkedHashSet<>();
        if (grants == null) {
            return result;
        }
        for (EffectivePermission grant : grants) {
            if (grant == null || grant.module == null || grant.permission == null) {
                continue;
            }
            result.add(grant);
            if (grant.permission == AppPermission.ADMINISTER) {
                result.add(new EffectivePermission(grant.module, AppPermission.CONSULT));
            }
        }
        return result;
    }

    public static boolean allows(Set<EffectivePermission> granted, AppModule module, AppPermission required) {
        if (granted == null || module == null || required == null) {
            return false;
        }
        return granted.contains(new EffectivePermission(module, required))
                || AppPermission.administerImplies(required, AppPermission.ADMINISTER)
                && granted.contains(new EffectivePermission(module, AppPermission.ADMINISTER));
    }
}
