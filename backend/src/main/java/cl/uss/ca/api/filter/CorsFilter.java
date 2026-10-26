package cl.uss.ca.api.filter;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

@Provider
@PreMatching
@ApplicationScoped
public class CorsFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger LOG = Logger.getLogger(CorsFilter.class.getName());

    @Inject
    @ConfigProperty(
            name = "openscience.frontend.origin",
            defaultValue = "http://localhost:4200,http://localhost:4201,http://localhost:4300,http://192.168.2.131:81,https://apps.cienciavida.org")
    String frontendOrigin;

    @Override
    public void filter(ContainerRequestContext request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            request.abortWith(Response.noContent().build());
        }
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        String allowOrigin = allowOrigin(request.getHeaderString("Origin"));
        response.getHeaders().putSingle("Access-Control-Allow-Origin", allowOrigin);
        response.getHeaders().putSingle(
                "Access-Control-Allow-Headers",
                "origin, content-type, accept, authorization, x-event-verified-session"
        );
        response.getHeaders().putSingle("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
        response.getHeaders().putSingle("Access-Control-Allow-Credentials", "true");
        response.getHeaders().putSingle("Access-Control-Expose-Headers", "Content-Disposition");
    }

    private String allowOrigin(String requestOrigin) {
        List<String> allowed = Arrays.stream(frontendOrigin.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
        String fallback = allowed.isEmpty() ? "http://localhost:4200" : allowed.get(0);
        if (requestOrigin == null || requestOrigin.isBlank()) {
            return fallback;
        }
        if (allowed.contains(requestOrigin) || requestOrigin.endsWith(":81")) {
            return requestOrigin;
        }
        LOG.warning("CORS origin del browser=" + requestOrigin + " no está en " + frontendOrigin);
        return fallback;
    }
}
