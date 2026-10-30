package cl.uss.ca.api;

import cl.uss.ca.api.dto.EthicsAccessWriteDto;
import cl.uss.ca.api.dto.EthicsDirectoryUserDto;
import cl.uss.ca.api.dto.EthicsInstitutionalProfileDto;
import cl.uss.ca.api.dto.EthicsMeDto;
import cl.uss.ca.api.dto.EthicsPermissionsDto;
import cl.uss.ca.api.dto.EthicsProfileWriteDto;
import cl.uss.ca.api.dto.EthicsRoleWriteDto;
import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.api.dto.PurePersonIdentity;
import cl.uss.ca.ethics.auth.AuthorizationContext;
import cl.uss.ca.ethics.auth.AuthorizationService;
import cl.uss.ca.ethics.auth.EthicsEntryService;
import cl.uss.ca.ethics.auth.EthicsUserCatalog;
import cl.uss.ca.api.dto.EmailRequestDto;
import cl.uss.ca.api.dto.EthicsOnboardingDto;
import cl.uss.ca.ethics.directory.EthicsDirectoryService;
import cl.uss.ca.ethics.directory.EthicsOnboardingService;
import cl.uss.ca.exception.NotFoundException;
import cl.uss.ca.identity.EffectivePersonProfile;
import cl.uss.ca.identity.PersonProfileResolver;
import cl.uss.ca.identity.PersonSource;
import cl.uss.ca.identity.PurePersonView;
import cl.uss.ca.identity.SharedPerson;
import cl.uss.ca.repository.PersonRepository;
import cl.uss.ca.security.CurrentUser;
import cl.uss.ca.security.CurrentUserService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/ethics")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class EthicsAuthorizationResource {

    @Inject
    AuthorizationService authorizationService;

    @Inject
    CurrentUserService currentUserService;

    @Inject
    PersonRepository personRepository;

    @Inject
    EthicsEntryService ethicsEntry;

    @Inject
    EthicsDirectoryService directory;

    @Inject
    EthicsOnboardingService onboarding;

    @POST
    @Path("/users/resolve")
    @Consumes(MediaType.APPLICATION_JSON)
    public EthicsOnboardingDto resolveUser(EmailRequestDto body) {
        directory.assertMayManage(currentUserService.current());
        return onboarding.resolve(body == null ? null : body.email);
    }

    @POST
    @Path("/users/invite")
    @Consumes(MediaType.APPLICATION_JSON)
    public EthicsOnboardingDto inviteUser(EmailRequestDto body) {
        CurrentUser actor = currentUserService.current();
        directory.assertMayManage(actor);
        return onboarding.invite(actor, body == null ? null : body.email);
    }

    @GET
    @Path("/users")
    public PageResponse<EthicsDirectoryUserDto> users(
            @QueryParam("search") String search,
            @QueryParam("type") @DefaultValue("ALL") String type,
            @QueryParam("status") @DefaultValue("ALL") String status,
            @QueryParam("ethicsAccess") @DefaultValue("ALL") String ethicsAccess,
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("size") @DefaultValue("25") int size
    ) {
        directory.assertMayManage(currentUserService.current());
        return directory.list(new EthicsUserCatalog.Query(search, type, status, ethicsAccess, page, size));
    }

    @GET
    @Path("/users/{id}")
    public EthicsDirectoryUserDto user(@PathParam("id") String id) {
        directory.assertMayManage(currentUserService.current());
        return directory.get(userId(id));
    }

    @POST
    @Path("/users/{id}/ethics-access")
    @Consumes(MediaType.APPLICATION_JSON)
    public EthicsDirectoryUserDto ethicsAccess(@PathParam("id") String id, EthicsAccessWriteDto body) {
        directory.assertMayManage(currentUserService.current());
        boolean active = body != null && body.active;
        boolean confirmed = body != null && body.confirmed;
        return directory.setAccess(userId(id), active, confirmed);
    }

    @POST
    @Path("/users/{id}/roles")
    @Consumes(MediaType.APPLICATION_JSON)
    public EthicsDirectoryUserDto grantRole(@PathParam("id") String id, EthicsRoleWriteDto body) {
        directory.assertMayManage(currentUserService.current());
        return directory.grantRole(userId(id), body == null ? null : body.roleId);
    }

    @DELETE
    @Path("/users/{id}/roles/{assignmentId}")
    public EthicsDirectoryUserDto revokeRole(@PathParam("id") String id, @PathParam("assignmentId") String assignmentId) {
        directory.assertMayManage(currentUserService.current());
        return directory.revokeRole(userId(id), assignmentId(assignmentId));
    }

    @PUT
    @Path("/users/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public EthicsDirectoryUserDto updateLocal(@PathParam("id") String id, EthicsProfileWriteDto body) {
        directory.assertMayManage(currentUserService.current());
        return directory.updateLocal(userId(id), body);
    }

    private static long userId(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            throw new NotFoundException("USER_NOT_FOUND", "User was not found");
        }
    }

    private static long assignmentId(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            throw new NotFoundException("ASSIGNMENT_NOT_FOUND", "La asignación no existe.");
        }
    }

    @GET
    @Path("/me")
    public EthicsMeDto me() {
        CurrentUser user = currentUserService.current();
        ethicsEntry.assertMayEnter(user);
        return authorizationService.profile(user);
    }

    @GET
    @Path("/me/profile")
    public EthicsInstitutionalProfileDto institutionalProfile() {
        CurrentUser user = currentUserService.current();
        ethicsEntry.assertMayEnter(user);
        EthicsInstitutionalProfileDto profile = fromAuthenticatedUser(user);
        if (user == null || user.email == null || user.email.isBlank()) {
            return profile;
        }
        try {
            PurePersonIdentity person = personRepository.findIdentityByEmail(user.email);
            if (person == null || person.id == null) {
                return profile;
            }
            EffectivePersonProfile effective = PersonProfileResolver.resolve(
                    new SharedPerson(null, person.id, null, null, null, null, null, null, null, null, null, cl.uss.ca.identity.PersonKind.INSTITUTIONAL),
                    PurePersonView.institutional(person.id, person.firstName, person.lastName, person.email, person.position)
            );
            profile.purePersonId = effective.purePersonId();
            profile.source = effective.source().name();
            if (notBlank(effective.firstName())) {
                profile.givenName = effective.firstName();
            }
            if (notBlank(effective.lastName())) {
                profile.familyName = effective.lastName();
            }
            if (notBlank(effective.email())) {
                profile.email = effective.email();
            }
            if (notBlank(effective.position())) {
                profile.position = effective.position();
            }
        } catch (RuntimeException ex) {
            return profile;
        }
        return profile;
    }

    private static EthicsInstitutionalProfileDto fromAuthenticatedUser(CurrentUser user) {
        EthicsInstitutionalProfileDto profile = new EthicsInstitutionalProfileDto();
        if (user == null) {
            return profile;
        }
        profile.email = user.email;
        profile.source = PersonSource.LOCAL.name();
        String name = user.displayName == null ? "" : user.displayName.trim();
        int space = name.indexOf(' ');
        if (space < 0) {
            profile.givenName = name;
            profile.familyName = "";
        } else {
            profile.givenName = name.substring(0, space);
            profile.familyName = name.substring(space + 1).trim();
        }
        return profile;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    @GET
    @Path("/permissions")
    public EthicsPermissionsDto permissions(
            @QueryParam("committeeType") String committeeType,
            @QueryParam("projectId") String projectId,
            @QueryParam("committeeCaseId") String committeeCaseId,
            @QueryParam("processId") String processId,
            @QueryParam("workflowState") String workflowState
    ) {
        CurrentUser user = currentUserService.current();
        ethicsEntry.assertMayEnter(user);
        return authorizationService.permissions(
                user,
                new AuthorizationContext(committeeType, projectId, committeeCaseId, processId, workflowState)
        );
    }
}
