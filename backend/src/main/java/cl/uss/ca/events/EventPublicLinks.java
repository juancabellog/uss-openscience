package cl.uss.ca.events;

import cl.uss.ca.service.PublicAppUrl;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Absolute public URLs for OPENSCIENCE Events emails and admin copy.
 * Does not mint tokens; the caller supplies an existing verification token.
 */
public final class EventPublicLinks {
    public static final String PRODUCTION_BASE = "https://apps.cienciavida.org/events";

    private EventPublicLinks() {
    }

    /**
     * Chooses a public Events base for outgoing mail.
     * Development hosts are ignored unless {@code allowLocalLinks} is true, so a
     * packaged developer {@code server.env} cannot leak localhost into production.
     */
    public static String resolveMailBase(boolean allowLocalLinks, String... candidates) {
        String development = null;
        if (candidates != null) {
            for (String raw : candidates) {
                String normalized = normalizeBase(raw);
                if (normalized == null) {
                    continue;
                }
                if (looksDevelopment(normalized)) {
                    if (development == null) {
                        development = normalized;
                    }
                    continue;
                }
                return normalized;
            }
        }
        if (allowLocalLinks) {
            return development != null ? development : "http://localhost:4300";
        }
        return PRODUCTION_BASE;
    }

    public static String normalizeBase(String raw) {
        return PublicAppUrl.normalize(raw);
    }

    public static String eventUrl(String publicBase, String slug) {
        return join(publicBase, "eventos", slug);
    }

    public static String registrationUrl(String publicBase, String slug) {
        return join(publicBase, "eventos", slug, "inscripcion");
    }

    public static String verificationUrl(String publicBase, String slug, String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("verification token is required");
        }
        return registrationUrl(publicBase, slug) + "?token=" + encodeQuery(rawToken);
    }

    public static String confirmationUrl(String publicBase, String slug) {
        return eventUrl(publicBase, slug);
    }

    public static String eventInfoUrl(String publicBase, String slug) {
        return eventUrl(publicBase, slug);
    }

    public static String cancelUrl(String publicBase, String slug) {
        return registrationUrl(publicBase, slug);
    }

    public static boolean looksDevelopment(String url) {
        if (PublicAppUrl.looksLocal(url)) {
            return true;
        }
        if (url == null || url.isBlank()) {
            return true;
        }
        String value = url.toLowerCase(Locale.ROOT);
        return value.contains("://0.0.0.0")
                || value.contains("://[::1]")
                || value.contains(".localhost");
    }

    public static String validate(String publicBase, boolean productionLike) {
        if (publicBase == null || publicBase.isBlank()) {
            return "EVENT_PUBLIC_URL_MISSING";
        }
        if (productionLike && looksDevelopment(publicBase)) {
            return "EVENT_PUBLIC_URL_LOCAL";
        }
        return null;
    }

    static String join(String publicBase, String... segments) {
        String root = normalizeBase(publicBase);
        if (root == null) {
            root = "";
        }
        StringBuilder suffix = new StringBuilder();
        if (segments != null) {
            for (String segment : segments) {
                if (segment == null || segment.isBlank()) {
                    continue;
                }
                for (String part : segment.split("/")) {
                    if (part.isBlank()) {
                        continue;
                    }
                    suffix.append('/').append(encodePath(part));
                }
            }
        }
        String path = suffix.toString();
        if (root.endsWith("/events") && path.startsWith("/events/")) {
            path = path.substring("/events".length());
        }
        return root + path;
    }

    private static String encodePath(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String encodeQuery(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
