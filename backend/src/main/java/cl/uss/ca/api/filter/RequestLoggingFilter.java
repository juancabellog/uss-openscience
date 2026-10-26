package cl.uss.ca.api.filter;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;
import java.util.logging.Logger;

@Provider
public class RequestLoggingFilter implements ContainerRequestFilter, ContainerResponseFilter {
    private static final Logger LOG = Logger.getLogger(RequestLoggingFilter.class.getName());
    private static final String STARTED = "openscience.startedAt";

    @Override
    public void filter(ContainerRequestContext request) {
        request.setProperty(STARTED, System.nanoTime());
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) throws IOException {
        Object started = request.getProperty(STARTED);
        long elapsedMs = started instanceof Long
                ? (System.nanoTime() - (Long) started) / 1_000_000L
                : -1;
        LOG.info(() -> {
            String path = request.getUriInfo().getPath();
            String query = request.getUriInfo().getRequestUri().getRawQuery();
            return request.getMethod() + " " + path
                    + (query == null || query.isBlank() ? "" : "?" + query)
                    + " status=" + response.getStatus()
                    + " durationMs=" + elapsedMs;
        });
    }
}
