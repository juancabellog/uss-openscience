package cl.uss.ca.api;

import cl.uss.ca.api.dto.EventEmailRequestDto;
import cl.uss.ca.api.dto.EventPublicDetailDto;
import cl.uss.ca.api.dto.EventRegistrationConfirmDto;
import cl.uss.ca.api.dto.EventRegistrationProfileDto;
import cl.uss.ca.api.dto.EventRegistrationReceiptDto;
import cl.uss.ca.api.dto.EventVerificationPreviewDto;
import cl.uss.ca.api.dto.EventVerifyRequestDto;
import cl.uss.ca.api.dto.EventsCatalogDto;
import cl.uss.ca.api.dto.EventsModuleStatusDto;
import cl.uss.ca.api.dto.GenericMessageDto;
import cl.uss.ca.service.EventRegistrationService;
import cl.uss.ca.service.EventService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/events")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EventResource {

    @Inject
    EventService eventService;

    @Inject
    EventRegistrationService registrationService;

    @Inject
    @ConfigProperty(name = "openscience.auth.cookie.secure", defaultValue = "false")
    boolean cookieSecure;

    @GET
    @Path("/health")
    public EventsModuleStatusDto health() {
        return eventService.status();
    }

    @GET
    public EventsCatalogDto catalog() {
        return eventService.catalog();
    }

    @GET
    @Path("/{slug}")
    public EventPublicDetailDto get(@PathParam("slug") String slug) {
        return eventService.getPublished(slug);
    }

    @POST
    @Path("/{slug}/register/request")
    public GenericMessageDto request(
            @PathParam("slug") String slug,
            EventEmailRequestDto body,
            @Context UriInfo uriInfo,
            @HeaderParam("X-Forwarded-For") String forwardedFor
    ) {
        return registrationService.request(slug, body, clientKey(uriInfo, forwardedFor));
    }

    @GET
    @Path("/{slug}/register/verify")
    public EventVerificationPreviewDto inspect(@PathParam("slug") String slug, @QueryParam("token") String token) {
        return registrationService.inspect(slug, token);
    }

    @POST
    @Path("/{slug}/register/verify")
    public Response consume(
            @PathParam("slug") String slug,
            EventVerifyRequestDto body,
            @QueryParam("token") String queryToken
    ) {
        String token = body != null && body.token != null && !body.token.isBlank() ? body.token : queryToken;
        EventRegistrationProfileDto dto = registrationService.consume(slug, token);
        return Response.ok(dto).cookie(verifiedCookie(dto.sessionToken, false)).build();
    }

    @GET
    @Path("/{slug}/register/profile")
    public EventRegistrationProfileDto profile(
            @PathParam("slug") String slug,
            @CookieParam(EventRegistrationService.COOKIE) String cookie,
            @HeaderParam("X-Event-Verified-Session") String header
    ) {
        return registrationService.profile(slug, firstToken(header, cookie));
    }

    @POST
    @Path("/{slug}/register/confirm")
    public EventRegistrationReceiptDto confirm(
            @PathParam("slug") String slug,
            EventRegistrationConfirmDto body,
            @CookieParam(EventRegistrationService.COOKIE) String cookie,
            @HeaderParam("X-Event-Verified-Session") String header
    ) {
        return registrationService.confirm(slug, firstToken(header, cookie), body);
    }

    private NewCookie verifiedCookie(String value, boolean clear) {
        return new NewCookie.Builder(EventRegistrationService.COOKIE)
                .value(value == null ? "" : value)
                .path("/")
                .httpOnly(true)
                .secure(cookieSecure)
                .maxAge(clear ? 0 : 2 * 3600)
                .sameSite(NewCookie.SameSite.LAX)
                .build();
    }

    private static String firstToken(String header, String cookie) {
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        return cookie;
    }

    private static String clientKey(UriInfo uriInfo, String forwardedFor) {
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return uriInfo == null ? "unknown" : uriInfo.getRequestUri().getHost();
    }
}
