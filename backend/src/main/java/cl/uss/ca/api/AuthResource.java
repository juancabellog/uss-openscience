package cl.uss.ca.api;

import cl.uss.ca.api.dto.ActivationPreviewDto;
import cl.uss.ca.api.dto.AuthPublicConfigDto;
import cl.uss.ca.api.dto.AuthUserDto;
import cl.uss.ca.api.dto.EmailRequestDto;
import cl.uss.ca.api.dto.GenericMessageDto;
import cl.uss.ca.api.dto.LoginRequestDto;
import cl.uss.ca.api.dto.PasswordSetRequestDto;
import cl.uss.ca.api.dto.RegisterRequestDto;
import cl.uss.ca.api.dto.RegisterResultDto;
import cl.uss.ca.api.filter.AuthFilter;
import cl.uss.ca.security.CurrentUserService;
import cl.uss.ca.service.AuthService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/auth")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    AuthService authService;

    @Inject
    CurrentUserService currentUserService;

    @Inject
    @ConfigProperty(name = "openscience.auth.cookie.secure", defaultValue = "false")
    boolean cookieSecure;

    @Inject
    @ConfigProperty(name = "openscience.auth.session.hours", defaultValue = "12")
    int sessionHours;

    @POST
    @Path("/login")
    public Response login(LoginRequestDto body, @Context UriInfo uriInfo) {
        AuthUserDto user = authService.login(
                body == null ? null : body.email,
                body == null ? null : body.password,
                clientKey(uriInfo)
        );
        String raw = authService.createSession(Long.parseLong(user.id));
        return Response.ok(user).cookie(sessionCookie(raw, false)).build();
    }

    @POST
    @Path("/logout")
    public Response logout(@Context jakarta.ws.rs.container.ContainerRequestContext request) {
        authService.logout(AuthFilter.rawToken(request));
        return Response.ok(GenericMessageDto.of("AUTH_LOGGED_OUT", "Signed out."))
                .cookie(sessionCookie("", true))
                .build();
    }

    @GET
    @Path("/me")
    public AuthUserDto me() {
        return authService.me(currentUserService.current());
    }

    @GET
    @Path("/config")
    public AuthPublicConfigDto config() {
        return authService.publicConfig();
    }

    @POST
    @Path("/register")
    public RegisterResultDto register(RegisterRequestDto body, @Context UriInfo uriInfo) {
        return authService.register(body, clientKey(uriInfo));
    }

    @POST
    @Path("/forgot-password")
    public GenericMessageDto forgot(EmailRequestDto body, @Context UriInfo uriInfo) {
        return authService.forgotPassword(body == null ? null : body.email, clientKey(uriInfo));
    }

    @GET
    @Path("/reset-password")
    public ActivationPreviewDto resetPreview(@QueryParam("token") String token) {
        return authService.previewToken(token, AuthService.PURPOSE_RESET);
    }

    @POST
    @Path("/reset-password")
    public GenericMessageDto reset(PasswordSetRequestDto body) {
        return authService.resetPassword(body == null ? null : body.token, body == null ? null : body.password);
    }

    @GET
    @Path("/activate")
    public ActivationPreviewDto activatePreview(@QueryParam("token") String token) {
        return authService.previewToken(token, AuthService.PURPOSE_ACTIVATION);
    }

    @POST
    @Path("/activate")
    public GenericMessageDto activate(PasswordSetRequestDto body) {
        authService.activate(body == null ? null : body.token, body == null ? null : body.password);
        return GenericMessageDto.of("AUTH_ACTIVATED", "Su cuenta fue activada correctamente.");
    }

    @POST
    @Path("/resend-activation")
    public GenericMessageDto resend(EmailRequestDto body, @Context UriInfo uriInfo) {
        return authService.resendActivationPublic(body == null ? null : body.email, clientKey(uriInfo));
    }

    private NewCookie sessionCookie(String value, boolean clear) {
        return new NewCookie.Builder(AuthService.COOKIE)
                .value(value == null ? "" : value)
                .path("/")
                .httpOnly(true)
                .secure(cookieSecure)
                .maxAge(clear ? 0 : Math.max(1, sessionHours) * 3600)
                .sameSite(NewCookie.SameSite.LAX)
                .build();
    }

    private static String clientKey(UriInfo uriInfo) {
        return uriInfo == null ? "unknown" : uriInfo.getRequestUri().getHost();
    }
}
