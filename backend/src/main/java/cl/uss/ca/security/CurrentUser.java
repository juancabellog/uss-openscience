package cl.uss.ca.security;

import cl.uss.ca.auth.AppModule;
import cl.uss.ca.auth.AppPermission;
import cl.uss.ca.auth.EffectivePermission;
import cl.uss.ca.auth.PermissionEvaluator;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class CurrentUser {
    public String id;
    public Long numericId;
    public String displayName;
    public String email;
    public String role;
    public String position;
    public String status;
    public String preferredLanguage;
    public final List<String> roles = new ArrayList<>();
    public final Set<EffectivePermission> permissions = new LinkedHashSet<>();

    public boolean has(AppModule module, AppPermission permission) {
        return PermissionEvaluator.allows(permissions, module, permission);
    }
}
