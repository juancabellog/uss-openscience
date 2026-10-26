package cl.uss.ca.api.filter;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.ext.Provider;

import java.util.logging.Logger;

@Provider
@PreMatching
public class RequestLogFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger LOG = Logger.getLogger(RequestLogFilter.class.getName());
    private static final String STARTED_NS = "cl.uss.ca.requestStartedNs";

    @Override
    public void filter(ContainerRequestContext request) {
        request.setProperty(STARTED_NS, System.nanoTime());
        LOG.info(() -> "REQUEST "
                + request.getMethod()
                + " "
                + path(request)
                + " host=" + header(request, "Host")
                + " origin=" + header(request, "Origin")
                + " referer=" + header(request, "Referer")
                + " forwarded=" + header(request, "X-Forwarded-For")
                + " proto=" + header(request, "X-Forwarded-Proto"));
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        Object started = request.getProperty(STARTED_NS);
        long ms = started instanceof Long start
                ? (System.nanoTime() - start) / 1_000_000
                : -1;
        LOG.info(() -> "RESPONSE "
                + request.getMethod()
                + " "
                + path(request)
                + " status=" + response.getStatus()
                + " ms=" + ms);
    }

    private static String path(ContainerRequestContext request) {
        var uri = request.getUriInfo().getRequestUri();
        String query = uri.getQuery();
        return query == null || query.isBlank() ? uri.getPath() : uri.getPath() + "?" + query;
    }

    private static String header(ContainerRequestContext request, String name) {
        String value = request.getHeaderString(name);
        return value == null || value.isBlank() ? "-" : value;
    }
}
