package cl.uss.ca.security;

import cl.uss.ca.auth.AppModule;
import cl.uss.ca.auth.AppPermission;
import cl.uss.ca.auth.EffectivePermission;
import cl.uss.ca.exception.ForbiddenException;
import cl.uss.ca.exception.UnauthorizedException;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@RequestScoped
public class CurrentUserService {

    @Inject
    @ConfigProperty(name = "openscience.auth.mode", defaultValue = "real")
    String authMode;

    @Inject
    @ConfigProperty(name = "openscience.dev.user.id", defaultValue = "dev-user")
    String userId;

    @Inject
    @ConfigProperty(name = "openscience.dev.user.displayName", defaultValue = "Development User")
    String displayName;

    @Inject
    @ConfigProperty(name = "openscience.dev.user.email", defaultValue = "openscience-dev@uss.cl")
    String email;

    @Inject
    @ConfigProperty(name = "openscience.dev.user.role", defaultValue = "ADMIN")
    String role;

    @Inject
    @ConfigProperty(name = "openscience.dev.user.position", defaultValue = "Technical Coordinator")
    String position;

    private CurrentUser resolved;

    public boolean isDevMock() {
        return "dev-mock".equalsIgnoreCase(authMode == null ? "" : authMode.trim());
    }

    public void bind(CurrentUser user) {
        this.resolved = user;
    }

    public CurrentUser current() {
        if (resolved != null) {
            return resolved;
        }
        if (isDevMock()) {
            return mockAdmin();
        }
        throw new UnauthorizedException("AUTH_REQUIRED", "Authentication is required");
    }

    public CurrentUser currentOrNull() {
        if (resolved != null) {
            return resolved;
        }
        return isDevMock() ? mockAdmin() : null;
    }

    public void require(AppModule module, AppPermission permission) {
        CurrentUser user = current();
        if (!user.has(module, permission)) {
            throw new ForbiddenException("AUTH_FORBIDDEN", "You are not allowed to perform this action");
        }
    }

    public boolean allows(AppModule module, AppPermission permission) {
        CurrentUser user = currentOrNull();
        return user != null && user.has(module, permission);
    }

    private CurrentUser mockAdmin() {
        CurrentUser user = new CurrentUser();
        user.id = userId;
        user.displayName = displayName;
        user.email = email;
        user.role = role;
        user.position = position;
        user.status = "ACTIVE";
        user.roles.add(role);
        for (AppModule module : AppModule.values()) {
            user.permissions.add(new EffectivePermission(module, AppPermission.CONSULT));
            user.permissions.add(new EffectivePermission(module, AppPermission.ADMINISTER));
        }
        return user;
    }
}
