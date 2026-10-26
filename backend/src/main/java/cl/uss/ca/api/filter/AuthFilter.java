package cl.uss.ca.api.filter;

import cl.uss.ca.auth.AuthorizationPolicy;
import cl.uss.ca.exception.ForbiddenException;
import cl.uss.ca.exception.UnauthorizedException;
import cl.uss.ca.repository.AccessRepository;
import cl.uss.ca.security.CurrentUser;
import cl.uss.ca.security.CurrentUserService;
import cl.uss.ca.service.AuthService;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.ext.Provider;

@Provider
@Priority(Priorities.AUTHENTICATION)
@ApplicationScoped
public class AuthFilter implements ContainerRequestFilter {

    @Inject
    AuthService authService;

    @Inject
    CurrentUserService currentUserService;

    @Inject
    AccessRepository accessRepository;

    @Override
    public void filter(ContainerRequestContext request) {
        String path = request.getUriInfo().getPath();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        String method = request.getMethod();
        if (AuthorizationPolicy.isPublic(method, path)) {
            return;
        }
        if (currentUserService.isDevMock()) {
            return;
        }
        if (!accessRepository.available()) {
            throw new UnauthorizedException("AUTH_TABLES_UNAVAILABLE", "Access tables are not available");
        }
        CurrentUser user = authService.resolve(rawToken(request));
        if (user == null) {
            throw new UnauthorizedException("AUTH_REQUIRED", "Authentication is required");
        }
        currentUserService.bind(user);
        String entity = request.getUriInfo().getQueryParameters().getFirst("entity");
        AuthorizationPolicy.Requirement requirement = AuthorizationPolicy.requirement(method, path, entity);
        if (requirement.denied) {
            throw new ForbiddenException("AUTH_FORBIDDEN", "You are not allowed to perform this action");
        }
        if (requirement.authenticatedOnly) {
            return;
        }
        if (!user.has(requirement.module, requirement.permission)) {
            throw new ForbiddenException("AUTH_FORBIDDEN", "You are not allowed to perform this action");
        }
    }

    public static String rawToken(ContainerRequestContext request) {
        String header = request.getHeaderString("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = header.substring(7).trim();
            if (!token.isEmpty() && !"mock-token".equals(token)) {
                return token;
            }
        }
        Cookie cookie = request.getCookies().get(AuthService.COOKIE);
        return cookie == null ? null : cookie.getValue();
    }
}
