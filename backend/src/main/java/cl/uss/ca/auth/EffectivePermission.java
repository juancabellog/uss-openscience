package cl.uss.ca.auth;

import java.util.Objects;

public final class EffectivePermission {
    public final AppModule module;
    public final AppPermission permission;

    public EffectivePermission(AppModule module, AppPermission permission) {
        this.module = module;
        this.permission = permission;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EffectivePermission that)) {
            return false;
        }
        return module == that.module && permission == that.permission;
    }

    @Override
    public int hashCode() {
        return Objects.hash(module, permission);
    }
}
