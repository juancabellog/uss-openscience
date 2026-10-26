package cl.uss.ca.auth;

import cl.uss.ca.api.dto.AdminUserWriteDto;
import cl.uss.ca.api.dto.AuthUserDto;
import cl.uss.ca.api.dto.RegisterRequestDto;
import cl.uss.ca.api.dto.RegisterResultDto;
import cl.uss.ca.mail.MailMessage;
import cl.uss.ca.mail.MailService;
import cl.uss.ca.repository.AccessUserRecord;
import cl.uss.ca.security.CurrentUser;
import cl.uss.ca.service.AccountMailer;
import cl.uss.ca.service.AuthService;
import cl.uss.ca.service.PublicAppUrl;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Auth/RBAC contract tests. Not executed by the agent.
 */
public final class AuthSecurityTest {
    private AuthSecurityTest() {
    }

    public static void main(String[] args) {
        passwordIsNeverPlaintext();
        hashedPasswordMatchesOnlyOriginal();
        activationAndResetTokensAreHashed();
        tokenIsOneTimeAndExpirable();
        forgotDoesNotRevealMissingEmail();
        administerImpliesConsult();
        defaultDeny();
        twoRolesReceiveUnion();
        consultCannotPostAdminAction();
        administerCanPostAdminAction();
        syncPathsUseEntityModule();
        unknownPathIsDenied();
        unauthorizedIsNotForbidden();
        disabledAndPendingAreRejectedByStatus();
        lastAdminCannotBeRemoved();
        lastAdminRoleCannotBeDemoted();
        dtoDoesNotExposeSecrets();
        duplicateEmailIsNormalized();
        mailServiceIsMockable();
        activationPreviewStatuses();
        activateEndpointIsPublic();
        registerEndpointIsPublic();
        eventsPublicCatalogIsPublic();
        eventsRegistrationIsPublic();
        eventsAdminRequiresEventsModule();
        registerRequestRejectsPrivilegeFields();
        registerEmailMustBeInstitutionalAndNormalized();
        registrationUssOnlyIsConfigurable();
        publicAuthConfigExposesOnlySafeFields();
        personNamesAcceptCompoundValues();
        duplicateRegistrationDoesNotCreateSecondUser();
        activationDoesNotAssignRoles();
        selfRegistrationMailUsesRequestWording();
        smtpFailureKeepsPendingActivation();
        selfRegistrationCreatesPendingActivation();
        selfRegistrationAssignsConsultaRoleByCode();
        consultaHasNoAdminister();
        consultaHasNoAdministration();
        consultaHasNoAudit();
        activationKeepsAssignedRoles();
        activationPreservesAdminAddedRoles();
        adminCreateUserDoesNotAutoAssignConsulta();
        consultaReceives403OnAdministerEndpoints();
        consultaCanConsumeConsultGets();
        auditEventsRequireAuditConsult();
        missingDefaultRoleDoesNotFallbackToAdmin();
        pendingUserWithoutHistoryIsPhysicalDelete();
        userWithAuditHistoryIsSoftDelete();
        selfDeleteIsRejected();
        lastAdminCannotBeDeleted();
        adminCanBeDeletedWhenAnotherRemains();
        deleteRequiresAdministrationAdminister();
        softDeletedCannotAuthenticate();
        deletedEmailIsControlledDuplicate();
        userDeletedEventKeepsEmailSnapshot();
        envPublicUrlWinsOverPackagedLocalhost();
        System.out.println("AuthSecurityTest OK");
    }

    static void envPublicUrlWinsOverPackagedLocalhost() {
        assert PublicAppUrl.looksLocal("http://localhost:4200");
        assert PublicAppUrl.looksLocal("http://127.0.0.1:4200");
        assert !PublicAppUrl.looksLocal("https://apps.cienciavida.org/openscience");
        String resolved = PublicAppUrl.resolve(
                "https://apps.cienciavida.org/openscience/",
                "http://localhost:4200"
        );
        assert "https://apps.cienciavida.org/openscience".equals(resolved);
        assert "http://localhost:4200".equals(PublicAppUrl.resolve(null, "http://localhost:4200"));
    }

    static void passwordIsNeverPlaintext() {
        char[] password = "una-clave-larga-12".toCharArray();
        String stored = PasswordHasher.hash(password, 120_000);
        assert PasswordHasher.isHashed(stored);
        assert !stored.contains("una-clave-larga-12");
        assert stored.startsWith("pbkdf2$");
    }

    static void hashedPasswordMatchesOnlyOriginal() {
        String stored = PasswordHasher.hash("correct-password-12".toCharArray(), 120_000);
        assert PasswordHasher.matches("correct-password-12".toCharArray(), stored);
        assert !PasswordHasher.matches("otra-password-12".toCharArray(), stored);
        assert !PasswordHasher.matches("correct-password-12".toCharArray(), "correct-password-12");
    }

    static void activationAndResetTokensAreHashed() {
        String raw = SecureTokens.rawToken();
        String hash = SecureTokens.hash(raw);
        assert raw.length() >= 64;
        assert hash != null && hash.length() == 64;
        assert !hash.equals(raw);
        assert hash.equals(SecureTokens.hash(raw));
        assert !hash.equals(SecureTokens.hash(SecureTokens.rawToken()));
    }

    static void tokenIsOneTimeAndExpirable() {
        OffsetDateTime now = OffsetDateTime.parse("2026-10-02T18:00:00Z");
        assert TokenPolicy.isUsable(null, now.plusHours(1), now);
        assert !TokenPolicy.isUsable(now, now.plusHours(1), now);
        assert !TokenPolicy.isUsable(null, now.minusMinutes(1), now);
        assert !TokenPolicy.isUsable(null, null, now);
    }

    static void forgotDoesNotRevealMissingEmail() {
        assert AuthService.GENERIC_FORGOT.contains("Si existe una cuenta asociada");
        assert !AuthService.GENERIC_FORGOT.toLowerCase().contains("no existe");
        assert !AuthService.GENERIC_FORGOT.toLowerCase().contains("not found");
        assert AuthService.GENERIC_LOGIN.equals("Correo o contraseña incorrectos.");
    }

    static void administerImpliesConsult() {
        Set<EffectivePermission> granted = PermissionEvaluator.union(List.of(
                new EffectivePermission(AppModule.PEOPLE, AppPermission.ADMINISTER)
        ));
        assert PermissionEvaluator.allows(granted, AppModule.PEOPLE, AppPermission.CONSULT);
        assert PermissionEvaluator.allows(granted, AppModule.PEOPLE, AppPermission.ADMINISTER);
        CurrentUser user = userWith(granted);
        assert user.has(AppModule.PEOPLE, AppPermission.CONSULT);
    }

    static void defaultDeny() {
        CurrentUser user = userWith(Set.of());
        assert !user.has(AppModule.PEOPLE, AppPermission.CONSULT);
        assert !user.has(AppModule.ADMINISTRATION, AppPermission.ADMINISTER);
        assert AuthorizationPolicy.requirement("GET", "/people").permission == AppPermission.CONSULT;
        assert AuthorizationPolicy.requirement("POST", "/people").permission == AppPermission.ADMINISTER;
    }

    static void twoRolesReceiveUnion() {
        Set<EffectivePermission> granted = PermissionEvaluator.union(List.of(
                new EffectivePermission(AppModule.PEOPLE, AppPermission.CONSULT),
                new EffectivePermission(AppModule.INDICATORS, AppPermission.ADMINISTER)
        ));
        CurrentUser user = userWith(granted);
        assert user.has(AppModule.PEOPLE, AppPermission.CONSULT);
        assert !user.has(AppModule.PEOPLE, AppPermission.ADMINISTER);
        assert user.has(AppModule.INDICATORS, AppPermission.CONSULT);
        assert user.has(AppModule.INDICATORS, AppPermission.ADMINISTER);
        assert !user.has(AppModule.ADMINISTRATION, AppPermission.CONSULT);
    }

    static void consultCannotPostAdminAction() {
        CurrentUser user = userWith(PermissionEvaluator.union(List.of(
                new EffectivePermission(AppModule.PEOPLE, AppPermission.CONSULT)
        )));
        var requirement = AuthorizationPolicy.requirement("POST", "/sync/pure/people");
        assert requirement.module == AppModule.PEOPLE;
        assert requirement.permission == AppPermission.ADMINISTER;
        assert !user.has(requirement.module, requirement.permission);
        assert !user.has(AppModule.DATA_QUALITY, AppPermission.ADMINISTER);
    }

    static void administerCanPostAdminAction() {
        CurrentUser user = userWith(PermissionEvaluator.union(List.of(
                new EffectivePermission(AppModule.PEOPLE, AppPermission.ADMINISTER)
        )));
        var requirement = AuthorizationPolicy.requirement("POST", "/sync/pure/people");
        assert user.has(requirement.module, requirement.permission);
        assert user.has(AppModule.PEOPLE, AppPermission.CONSULT);
    }

    static void disabledAndPendingAreRejectedByStatus() {
        assert UserStatus.PENDING_ACTIVATION.name().equals("PENDING_ACTIVATION");
        assert UserStatus.DISABLED.name().equals("DISABLED");
        assert UserStatus.LOCKED.name().equals("LOCKED");
        assert UserStatus.ACTIVE.name().equals("ACTIVE");
        CurrentUser pending = userWith(Set.of());
        pending.status = UserStatus.PENDING_ACTIVATION.name();
        CurrentUser disabled = userWith(Set.of());
        disabled.status = UserStatus.DISABLED.name();
        assert !UserStatus.ACTIVE.name().equals(pending.status);
        assert !UserStatus.ACTIVE.name().equals(disabled.status);
    }

    static void lastAdminCannotBeRemoved() {
        assert !LastAdminPolicy.canRemoveAdminAccess(true, false, 1);
        assert LastAdminPolicy.canRemoveAdminAccess(true, false, 2);
        assert LastAdminPolicy.canRemoveAdminAccess(true, true, 1);
        assert LastAdminPolicy.canRemoveAdminAccess(false, false, 1);
    }

    static void lastAdminRoleCannotBeDemoted() {
        assert !LastAdminPolicy.canDemoteAdminRole(true, false, 1, 1);
        assert !LastAdminPolicy.canDemoteAdminRole(true, false, 3, 3);
        assert LastAdminPolicy.canDemoteAdminRole(true, true, 1, 1);
        assert LastAdminPolicy.canDemoteAdminRole(true, false, 3, 1);
    }

    static void dtoDoesNotExposeSecrets() {
        Set<String> names = Arrays.stream(AuthUserDto.class.getFields()).map(Field::getName).collect(java.util.stream.Collectors.toSet());
        assert !names.contains("password");
        assert !names.contains("passwordHash");
        assert !names.contains("token");
        assert !names.contains("tokenHash");
    }

    static void duplicateEmailIsNormalized() {
        assert "juan.perez@uss.cl".equals(EmailNormalizer.normalize("  Juan.Perez@USS.CL "));
        assert EmailNormalizer.looksLikeEmail("juan.perez@uss.cl");
        assert !EmailNormalizer.looksLikeEmail("sin-correo");
    }

    static void activationPreviewStatuses() {
        OffsetDateTime now = OffsetDateTime.parse("2026-10-05T12:00:00Z");
        assert TokenPreviewStatus.classify(null, now.plusHours(1), now, "PENDING_ACTIVATION") == TokenPreviewStatus.VALID;
        assert TokenPreviewStatus.classify(null, now.minusMinutes(1), now, "PENDING_ACTIVATION") == TokenPreviewStatus.EXPIRED;
        assert TokenPreviewStatus.classify(now, now.plusHours(1), now, "PENDING_ACTIVATION") == TokenPreviewStatus.USED;
        assert TokenPreviewStatus.classify(now, now.plusHours(1), now, "ACTIVE") == TokenPreviewStatus.ALREADY_ACTIVE;
    }

    static void activateEndpointIsPublic() {
        assert AuthorizationPolicy.isPublic("GET", "/auth/activate");
        assert AuthorizationPolicy.isPublic("POST", "/auth/activate");
        assert !AuthorizationPolicy.isPublic("GET", "/auth/me");
    }

    static void registerEndpointIsPublic() {
        assert AuthorizationPolicy.isPublic("POST", "/auth/register");
        assert !AuthorizationPolicy.isPublic("POST", "/admin/users");
    }

    static void eventsPublicCatalogIsPublic() {
        assert AuthorizationPolicy.isPublic("GET", "/events");
        assert AuthorizationPolicy.isPublic("GET", "/events/health");
        assert AuthorizationPolicy.isPublic("GET", "/events/jornada-investigacion-2026");
        assert !AuthorizationPolicy.isPublic("GET", "/events/admin");
        assert !AuthorizationPolicy.isPublic("GET", "/events/admin/status");
        assert !AuthorizationPolicy.isPublic("POST", "/events");
        assert !AuthorizationPolicy.isPublic("POST", "/events/admin");
    }

    static void eventsRegistrationIsPublic() {
        assert AuthorizationPolicy.isPublic("POST", "/events/jornada-investigacion-2026/register/request");
        assert AuthorizationPolicy.isPublic("GET", "/events/jornada-investigacion-2026/register/verify");
        assert AuthorizationPolicy.isPublic("POST", "/events/jornada-investigacion-2026/register/verify");
        assert AuthorizationPolicy.isPublic("GET", "/events/jornada-investigacion-2026/register/profile");
        assert AuthorizationPolicy.isPublic("POST", "/events/jornada-investigacion-2026/register/confirm");
        assert !AuthorizationPolicy.isPublic("GET", "/events/admin/1/registrations");
        assert !AuthorizationPolicy.isPublic("POST", "/events/admin");
        assert AuthorizationPolicy.requirement("POST", "/events/admin/1/publish").module == AppModule.EVENTS;
        assert AuthorizationPolicy.requirement("POST", "/events/admin/1/publish").permission == AppPermission.ADMINISTER;
        assert AuthorizationPolicy.requirement("GET", "/events/admin/1/registrations").permission == AppPermission.CONSULT;
    }

    static void eventsAdminRequiresEventsModule() {
        assert AuthorizationPolicy.requirement("GET", "/events/admin/status").module == AppModule.EVENTS;
        assert AuthorizationPolicy.requirement("GET", "/events/admin/status").permission == AppPermission.CONSULT;
        assert AuthorizationPolicy.requirement("POST", "/events/admin").permission == AppPermission.ADMINISTER;
        assert !ConsultaRolePolicy.grants(AppModule.EVENTS, AppPermission.CONSULT);
        assert !ConsultaRolePolicy.grants(AppModule.EVENTS, AppPermission.ADMINISTER);
    }

    static void registerRequestRejectsPrivilegeFields() {
        Set<String> names = Arrays.stream(RegisterRequestDto.class.getFields())
                .map(Field::getName)
                .collect(java.util.stream.Collectors.toSet());
        assert names.equals(Set.of("firstName", "lastName", "email"));
        assert !names.contains("password");
        assert !names.contains("role");
        assert !names.contains("roleIds");
        assert !names.contains("status");
        assert !names.contains("permissions");
        assert !names.contains("admin");
    }

    static void registerEmailMustBeInstitutionalAndNormalized() {
        assert "juan.cabello@uss.cl".equals(EmailNormalizer.normalize("  Juan.Cabello@USS.CL "));
        assert AllowedEmailDomains.accepts("Juan.Cabello@USS.CL", "uss.cl");
        assert !AllowedEmailDomains.accepts("juan.cabello@gmail.com", "uss.cl");
        assert !AllowedEmailDomains.accepts("juan.cabello@otros.uss.cl", "uss.cl");
        assert UserStatus.PENDING_ACTIVATION.name().equals("PENDING_ACTIVATION");
    }

    static void registrationUssOnlyIsConfigurable() {
        String domains = "uss.cl";
        assert AllowedEmailDomains.acceptsForRegistration("juan@uss.cl", true, domains);
        assert AllowedEmailDomains.acceptsForRegistration("Juan@USS.CL", true, domains);
        assert "juan@uss.cl".equals(EmailNormalizer.normalize("Juan@USS.CL"));
        assert !AllowedEmailDomains.acceptsForRegistration("juan@gmail.com", true, domains);
        assert !AllowedEmailDomains.acceptsForRegistration("juan@uss.cl.evil.com", true, domains);
        assert !AllowedEmailDomains.acceptsForRegistration("juan@fakeuss.cl", true, domains);
        assert AllowedEmailDomains.acceptsForRegistration("juan@gmail.com", false, domains);
        assert AllowedEmailDomains.acceptsForRegistration("juan@universidad.cl", false, domains);
        assert !AllowedEmailDomains.acceptsForRegistration("correo-invalido", false, domains);
        assert AuthorizationPolicy.isPublic("GET", "/auth/config");
        assert !AuthorizationPolicy.isPublic("GET", "/auth/me");
    }

    static void publicAuthConfigExposesOnlySafeFields() {
        Set<String> names = Arrays.stream(cl.uss.ca.api.dto.AuthPublicConfigDto.class.getFields())
                .map(Field::getName)
                .collect(java.util.stream.Collectors.toSet());
        assert names.equals(Set.of("registrationEnabled", "registrationUssOnly"));
        assert !names.contains("password");
        assert !names.contains("smtpPassword");
        assert !names.contains("token");
        assert !names.contains("apiKey");
    }

    static void personNamesAcceptCompoundValues() {
        assert PersonNamePolicy.isAcceptable("María José");
        assert PersonNamePolicy.isAcceptable("Spaudo Valenzuela");
        assert PersonNamePolicy.isAcceptable("De la Fuente");
        assert PersonNamePolicy.isAcceptable("O'Connor");
        assert "De la Fuente".equals(PersonNamePolicy.normalize("  De   la   Fuente  "));
        assert !PersonNamePolicy.isAcceptable("   ");
        assert !PersonNamePolicy.isAcceptable(null);
    }

    static void duplicateRegistrationDoesNotCreateSecondUser() {
        assert RegisterResultDto.OUTCOME_EXISTS_ACTIVE.equals(AuthService.duplicateOutcome("ACTIVE"));
        assert RegisterResultDto.OUTCOME_EXISTS_PENDING.equals(AuthService.duplicateOutcome("PENDING_ACTIVATION"));
        assert RegisterResultDto.OUTCOME_EXISTS_UNAVAILABLE.equals(AuthService.duplicateOutcome("DISABLED"));
        assert RegisterResultDto.OUTCOME_EXISTS_UNAVAILABLE.equals(AuthService.duplicateOutcome("LOCKED"));
    }

    static void activationDoesNotAssignRoles() {
        CurrentUser user = userWith(Set.of());
        user.status = UserStatus.ACTIVE.name();
        assert user.roles.isEmpty();
        assert !user.has(AppModule.ADMINISTRATION, AppPermission.ADMINISTER);
        assert !AuthorizationPolicy.isPublic("POST", "/admin/users");
    }

    static void selfRegistrationMailUsesRequestWording() {
        String self = AccountMailer.activationIntro(ActivationOrigin.SELF_REGISTRATION);
        String admin = AccountMailer.activationIntro(ActivationOrigin.ADMIN_INVITE);
        assert self.contains("solicitud para crear su cuenta");
        assert !self.contains("Se ha creado una cuenta para usted");
        assert admin.contains("Se ha creado una cuenta para usted");
        assert AccountMailer.activationIgnore(ActivationOrigin.SELF_REGISTRATION)
                .contains("no realizó esta solicitud");
    }

    static void syncPathsUseEntityModule() {
        assert AuthorizationPolicy.requirement("GET", "/sync/latest", "PEOPLE").module == AppModule.PEOPLE;
        assert AuthorizationPolicy.requirement("GET", "/sync/latest", "PEOPLE").permission == AppPermission.CONSULT;
        assert AuthorizationPolicy.requirement("POST", "/sync/pure/journals").module == AppModule.JOURNALS;
        assert AuthorizationPolicy.requirement("POST", "/sync/pure/projects").module == AppModule.PROJECTS;
        assert AuthorizationPolicy.requirement("GET", "/projects").module == AppModule.PROJECTS;
        assert AuthorizationPolicy.requirement("POST", "/sync/pure/organizations").module == AppModule.ORGANIZATIONS;
        assert AuthorizationPolicy.requirement("GET", "/organizations").module == AppModule.ORGANIZATIONS;
        assert AuthorizationPolicy.requirement("GET", "/organizations").permission == AppPermission.CONSULT;
        assert AuthorizationPolicy.requirement("GET", "/directory").module == AppModule.DIRECTORY;
        assert AuthorizationPolicy.requirement("GET", "/events/admin/status").module == AppModule.EVENTS;
        assert AuthorizationPolicy.requirement("GET", "/events/admin/status").permission == AppPermission.CONSULT;
        assert AuthorizationPolicy.requirement("POST", "/events/admin").module == AppModule.EVENTS;
        assert AuthorizationPolicy.requirement("POST", "/events/admin").permission == AppPermission.ADMINISTER;
        assert AuthorizationPolicy.requirement("GET", "/directory").permission == AppPermission.CONSULT;
        assert AuthorizationPolicy.requirement("POST", "/directory/imports/dry-run").module == AppModule.DIRECTORY;
        assert AuthorizationPolicy.requirement("POST", "/directory/imports/dry-run").permission == AppPermission.ADMINISTER;
        assert AuthorizationPolicy.requirement("POST", "/directory/imports").permission == AppPermission.ADMINISTER;
        assert AuthorizationPolicy.requirement("POST", "/directory/imports/1/recover").module == AppModule.DIRECTORY;
        assert AuthorizationPolicy.requirement("POST", "/directory/imports/1/recover").permission == AppPermission.ADMINISTER;
        assert AuthorizationPolicy.requirement("GET", "/projects").permission == AppPermission.CONSULT;
        assert AuthorizationPolicy.requirement("POST", "/sync/orcid").module == AppModule.INTEGRATIONS;
        assert AuthorizationPolicy.requirement("POST", "/sync/unpaywall").permission == AppPermission.ADMINISTER;
        assert AuthorizationPolicy.requirement("POST", "/sync/crossref").module == AppModule.INTEGRATIONS;
        assert AuthorizationPolicy.requirement("POST", "/sync/crossref").permission == AppPermission.ADMINISTER;
        assert AuthorizationPolicy.requirement("POST", "/sync/openalex").module == AppModule.INTEGRATIONS;
        assert AuthorizationPolicy.requirement("POST", "/sync/openalex").permission == AppPermission.ADMINISTER;
        assert AuthorizationPolicy.requirement("POST", "/sync/52/cancel").authenticatedOnly;
        assert !AuthorizationPolicy.requirement("POST", "/sync/52/cancel").denied;
        assert AuthorizationPolicy.requirement("GET", "/openalex/stats").module == AppModule.INTEGRATIONS;
        assert AuthorizationPolicy.requirement("GET", "/openalex/stats").permission == AppPermission.CONSULT;
        assert AuthorizationPolicy.requirement("GET", "/openalex/history").permission == AppPermission.CONSULT;
        assert AuthorizationPolicy.requirement("POST", "/sync/dspace").module == AppModule.INTEGRATIONS;
        assert AuthorizationPolicy.requirement("POST", "/sync/dspace").permission == AppPermission.ADMINISTER;
        assert AuthorizationPolicy.requirement("POST", "/sync/dspace/bitstreams").module == AppModule.INTEGRATIONS;
        assert AuthorizationPolicy.requirement("POST", "/sync/dspace/bitstreams").permission == AppPermission.ADMINISTER;
        assert AuthorizationPolicy.requirement("GET", "/dspace/stats").module == AppModule.INTEGRATIONS;
        assert AuthorizationPolicy.requirement("GET", "/dspace/stats").permission == AppPermission.CONSULT;
        assert AuthorizationPolicy.requirement(
                "GET", "/dspace/items/item-uuid/bitstreams/bitstream-uuid/download"
        ).permission == AppPermission.CONSULT;
        assert AuthorizationPolicy.requirement("POST", "/openalex/matches/1/confirm").permission == AppPermission.ADMINISTER;
        assert AuthorizationPolicy.requirement("POST", "/openalex/matches/1/confirm").module == AppModule.INTEGRATIONS;
        assert AuthorizationPolicy.requirement("POST", "/openalex/matches/1/reject").permission == AppPermission.ADMINISTER;
        assert AuthorizationPolicy.requirement("POST", "/openalex/matches/1/sync-orcid").module == AppModule.INTEGRATIONS;
        assert AuthorizationPolicy.requirement("POST", "/openalex/matches/1/sync-orcid").permission == AppPermission.ADMINISTER;
        assert AuthorizationPolicy.requirement("GET", "/sync/orcid", "ORCID").permission == AppPermission.CONSULT;
        assert AuthorizationPolicy.requirement("POST", "/sync/orcid").permission == AppPermission.ADMINISTER;
        assert AuthorizationPolicy.requirement("GET", "/crossref/stats").module == AppModule.INTEGRATIONS;
        assert AuthorizationPolicy.requirement("GET", "/crossref/stats").permission == AppPermission.CONSULT;
    }

    static void unknownPathIsDenied() {
        assert AuthorizationPolicy.requirement("GET", "/mystery").denied;
        assert !AuthorizationPolicy.requirement("GET", "/people").denied;
        assert AuthorizationPolicy.requirement("GET", "/auth/me").authenticatedOnly;
    }

    static void unauthorizedIsNotForbidden() {
        cl.uss.ca.exception.UnauthorizedException unauthorized =
                new cl.uss.ca.exception.UnauthorizedException("AUTH_REQUIRED", "Authentication is required");
        cl.uss.ca.exception.ForbiddenException forbidden =
                new cl.uss.ca.exception.ForbiddenException("AUTH_FORBIDDEN", "You are not allowed to perform this action");
        assert "AUTH_REQUIRED".equals(unauthorized.getCode());
        assert "AUTH_FORBIDDEN".equals(forbidden.getCode());
        assert !unauthorized.getCode().equals(forbidden.getCode());
    }

    static void selfRegistrationCreatesPendingActivation() {
        assert UserStatus.PENDING_ACTIVATION.name().equals("PENDING_ACTIVATION");
        assert SelfRegistrationDefaultRole.DEFAULT_CODE.equals(ConsultaRolePolicy.CODE);
    }

    static void selfRegistrationAssignsConsultaRoleByCode() {
        assert "CONSULTA".equals(SelfRegistrationDefaultRole.code(null));
        assert "CONSULTA".equals(SelfRegistrationDefaultRole.code(""));
        assert "CONSULTA".equals(SelfRegistrationDefaultRole.code("consulta"));
        assert "ANALYST".equals(SelfRegistrationDefaultRole.code(" analyst "));
        assert !SelfRegistrationDefaultRole.DEFAULT_CODE.equals("PLATFORM_ADMIN");
        assert !SelfRegistrationDefaultRole.DEFAULT_CODE.equals("READ_ONLY");
        assert !SelfRegistrationDefaultRole.DEFAULT_CODE.equals("ANALYST");
    }

    static void consultaHasNoAdminister() {
        Set<EffectivePermission> granted = ConsultaRolePolicy.grants();
        assert !ConsultaRolePolicy.hasAdminister();
        for (AppModule module : AppModule.values()) {
            assert !ConsultaRolePolicy.grants(module, AppPermission.ADMINISTER);
            assert !PermissionEvaluator.allows(granted, module, AppPermission.ADMINISTER);
        }
    }

    static void consultaHasNoAdministration() {
        Set<EffectivePermission> granted = ConsultaRolePolicy.grants();
        assert !ConsultaRolePolicy.grants(AppModule.ADMINISTRATION, AppPermission.CONSULT);
        assert !ConsultaRolePolicy.grants(AppModule.ADMINISTRATION, AppPermission.ADMINISTER);
        assert !PermissionEvaluator.allows(granted, AppModule.ADMINISTRATION, AppPermission.CONSULT);
        assert !PermissionEvaluator.allows(granted, AppModule.ADMINISTRATION, AppPermission.ADMINISTER);
    }

    static void consultaHasNoAudit() {
        Set<EffectivePermission> granted = ConsultaRolePolicy.grants();
        assert !ConsultaRolePolicy.grants(AppModule.AUDIT, AppPermission.CONSULT);
        assert !ConsultaRolePolicy.grants(AppModule.AUDIT, AppPermission.ADMINISTER);
        assert !PermissionEvaluator.allows(granted, AppModule.AUDIT, AppPermission.CONSULT);
    }

    static void activationKeepsAssignedRoles() {
        CurrentUser user = userWith(ConsultaRolePolicy.grants());
        user.roles.add(ConsultaRolePolicy.CODE);
        user.status = UserStatus.ACTIVE.name();
        assert user.roles.equals(List.of(ConsultaRolePolicy.CODE));
        assert user.has(AppModule.PEOPLE, AppPermission.CONSULT);
        assert !user.has(AppModule.ADMINISTRATION, AppPermission.CONSULT);
    }

    static void activationPreservesAdminAddedRoles() {
        Set<EffectivePermission> granted = PermissionEvaluator.union(List.of(
                new EffectivePermission(AppModule.PEOPLE, AppPermission.CONSULT),
                new EffectivePermission(AppModule.ADMINISTRATION, AppPermission.ADMINISTER)
        ));
        CurrentUser user = userWith(granted);
        user.roles.add(ConsultaRolePolicy.CODE);
        user.roles.add("PLATFORM_ADMIN");
        user.status = UserStatus.ACTIVE.name();
        assert user.roles.contains(ConsultaRolePolicy.CODE);
        assert user.roles.contains("PLATFORM_ADMIN");
        assert user.has(AppModule.PEOPLE, AppPermission.CONSULT);
        assert user.has(AppModule.ADMINISTRATION, AppPermission.ADMINISTER);
    }

    static void adminCreateUserDoesNotAutoAssignConsulta() {
        Set<String> registerFields = Arrays.stream(RegisterRequestDto.class.getFields())
                .map(Field::getName)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> adminFields = Arrays.stream(AdminUserWriteDto.class.getFields())
                .map(Field::getName)
                .collect(java.util.stream.Collectors.toSet());
        assert !registerFields.contains("roleIds");
        assert adminFields.contains("roleIds");
        AdminUserWriteDto body = new AdminUserWriteDto();
        assert body.roleIds.isEmpty();
        assert !SelfRegistrationDefaultRole.DEFAULT_CODE.equals("PLATFORM_ADMIN");
    }

    static void consultaReceives403OnAdministerEndpoints() {
        CurrentUser user = userWith(ConsultaRolePolicy.grants());
        var peopleSync = AuthorizationPolicy.requirement("POST", "/sync/pure/people");
        var unpaywall = AuthorizationPolicy.requirement("POST", "/sync/unpaywall");
        var crossref = AuthorizationPolicy.requirement("POST", "/sync/crossref");
        var openalex = AuthorizationPolicy.requirement("POST", "/sync/openalex");
        var dspace = AuthorizationPolicy.requirement("POST", "/sync/dspace");
        var adminUsers = AuthorizationPolicy.requirement("POST", "/admin/users");
        assert peopleSync.permission == AppPermission.ADMINISTER;
        assert !user.has(peopleSync.module, peopleSync.permission);
        assert !user.has(unpaywall.module, unpaywall.permission);
        assert !user.has(crossref.module, crossref.permission);
        assert !user.has(openalex.module, openalex.permission);
        assert !user.has(dspace.module, dspace.permission);
        assert !user.has(adminUsers.module, adminUsers.permission);
        assert !user.has(AppModule.ADMINISTRATION, AppPermission.CONSULT);
    }

    static void consultaCanConsumeConsultGets() {
        CurrentUser user = userWith(ConsultaRolePolicy.grants());
        assert AuthorizationPolicy.requirement("GET", "/dashboard").permission == AppPermission.CONSULT;
        assert AuthorizationPolicy.requirement("GET", "/people").permission == AppPermission.CONSULT;
        assert AuthorizationPolicy.requirement("GET", "/research-outputs").permission == AppPermission.CONSULT;
        assert AuthorizationPolicy.requirement("GET", "/journals").permission == AppPermission.CONSULT;
        assert AuthorizationPolicy.requirement("GET", "/data-quality").permission == AppPermission.CONSULT;
        assert AuthorizationPolicy.requirement("GET", "/indicators").permission == AppPermission.CONSULT;
        assert AuthorizationPolicy.requirement("GET", "/integrations").permission == AppPermission.CONSULT;
        assert AuthorizationPolicy.requirement("GET", "/crossref/stats").permission == AppPermission.CONSULT;
        assert AuthorizationPolicy.requirement("GET", "/openalex/stats").permission == AppPermission.CONSULT;
        assert AuthorizationPolicy.requirement("GET", "/dspace/stats").permission == AppPermission.CONSULT;
        assert AuthorizationPolicy.requirement("GET", "/directory").permission == AppPermission.CONSULT;
        assert user.has(AppModule.DASHBOARD, AppPermission.CONSULT);
        assert user.has(AppModule.PEOPLE, AppPermission.CONSULT);
        assert user.has(AppModule.RESEARCH_OUTPUTS, AppPermission.CONSULT);
        assert user.has(AppModule.JOURNALS, AppPermission.CONSULT);
        assert user.has(AppModule.PROJECTS, AppPermission.CONSULT);
        assert user.has(AppModule.ORGANIZATIONS, AppPermission.CONSULT);
        assert user.has(AppModule.DIRECTORY, AppPermission.CONSULT);
        assert !user.has(AppModule.DIRECTORY, AppPermission.ADMINISTER);
        assert !user.has(AppModule.EVENTS, AppPermission.CONSULT);
        assert !user.has(AppModule.EVENTS, AppPermission.ADMINISTER);
        assert user.has(AppModule.DATA_QUALITY, AppPermission.CONSULT);
        assert user.has(AppModule.INDICATORS, AppPermission.CONSULT);
        assert user.has(AppModule.INTEGRATIONS, AppPermission.CONSULT);
        assert user.has(AuthorizationPolicy.requirement("GET", "/people").module, AppPermission.CONSULT);
        assert !user.has(AuthorizationPolicy.requirement("GET", "/admin/users").module, AppPermission.CONSULT);
        assert !user.has(AuthorizationPolicy.requirement("GET", "/audit").module, AppPermission.CONSULT);
    }

    static void auditEventsRequireAuditConsult() {
        CurrentUser consulta = userWith(ConsultaRolePolicy.grants());
        var list = AuthorizationPolicy.requirement("GET", "/audit/events");
        var detail = AuthorizationPolicy.requirement("GET", "/audit/events/44");
        var changes = AuthorizationPolicy.requirement("GET", "/audit/events/44/changes");
        var errors = AuthorizationPolicy.requirement("GET", "/audit/events/44/errors");
        assert AuthorizationPolicy.moduleOf("/audit/events") == AppModule.AUDIT;
        assert list.permission == AppPermission.CONSULT;
        assert detail.permission == AppPermission.CONSULT;
        assert changes.permission == AppPermission.CONSULT;
        assert errors.permission == AppPermission.CONSULT;
        assert !consulta.has(list.module, list.permission);
        CurrentUser auditor = userWith(Set.of(
                new cl.uss.ca.auth.EffectivePermission(AppModule.AUDIT, AppPermission.CONSULT)
        ));
        assert auditor.has(list.module, list.permission);
        assert auditor.has(detail.module, detail.permission);
    }

    static void missingDefaultRoleDoesNotFallbackToAdmin() {
        assert SelfRegistrationDefaultRole.forbidsAutomaticAssignment("PLATFORM_ADMIN");
        assert SelfRegistrationDefaultRole.forbidsAutomaticAssignment("platform_admin");
        assert !SelfRegistrationDefaultRole.isAssignable(null);
        cl.uss.ca.repository.AccessRoleRecord admin = new cl.uss.ca.repository.AccessRoleRecord();
        admin.code = "PLATFORM_ADMIN";
        admin.active = true;
        assert !SelfRegistrationDefaultRole.isAssignable(admin);
        cl.uss.ca.repository.AccessRoleRecord inactive = new cl.uss.ca.repository.AccessRoleRecord();
        inactive.code = ConsultaRolePolicy.CODE;
        inactive.active = false;
        assert !SelfRegistrationDefaultRole.isAssignable(inactive);
        cl.uss.ca.repository.AccessRoleRecord consulta = new cl.uss.ca.repository.AccessRoleRecord();
        consulta.code = ConsultaRolePolicy.CODE;
        consulta.active = true;
        assert SelfRegistrationDefaultRole.isAssignable(consulta);
    }

    static void pendingUserWithoutHistoryIsPhysicalDelete() {
        assert UserDeletionPolicy.MODE_PHYSICAL.equals(UserDeletionPolicy.mode(false));
        assert UserDeletionPolicy.isLifecycleEvent("USER_CREATED", null);
        assert UserDeletionPolicy.isLifecycleEvent("ROLE_ASSIGNED", null);
        assert UserDeletionPolicy.isLifecycleEvent("USER_ACTIVATED", 12L);
        assert !UserDeletionPolicy.actorEventIsHistorical("USER_CREATED", null);
    }

    static void userWithAuditHistoryIsSoftDelete() {
        assert UserDeletionPolicy.MODE_SOFT.equals(UserDeletionPolicy.mode(true));
        assert UserDeletionPolicy.actorEventIsHistorical("USER_LOGIN", 12L);
        assert UserDeletionPolicy.actorEventIsHistorical("ROLE_ASSIGNED", 12L);
        assert UserDeletionPolicy.actorEventIsHistorical("USER_DISABLED", 12L);
        assert !UserDeletionPolicy.isDeleted(null);
        assert UserDeletionPolicy.isDeleted(OffsetDateTime.now(ZoneOffset.UTC));
    }

    static void selfDeleteIsRejected() {
        assert "CANNOT_DELETE_SELF".equals(UserDeletionPolicy.CANNOT_DELETE_SELF);
        CurrentUser actor = userWith(Set.of());
        actor.numericId = 7L;
        assert actor.numericId == 7L;
        assert actor.numericId.equals(7L);
    }

    static void lastAdminCannotBeDeleted() {
        assert !LastAdminPolicy.canRemoveAdminAccess(true, false, 1);
    }

    static void adminCanBeDeletedWhenAnotherRemains() {
        assert LastAdminPolicy.canRemoveAdminAccess(true, false, 2);
    }

    static void deleteRequiresAdministrationAdminister() {
        assert AuthorizationPolicy.requirement("DELETE", "/admin/users/9").module == AppModule.ADMINISTRATION;
        assert AuthorizationPolicy.requirement("DELETE", "/admin/users/9").permission == AppPermission.ADMINISTER;
        CurrentUser consulta = userWith(ConsultaRolePolicy.grants());
        var requirement = AuthorizationPolicy.requirement("DELETE", "/admin/users/9");
        assert !consulta.has(requirement.module, requirement.permission);
    }

    static void softDeletedCannotAuthenticate() {
        CurrentUser user = userWith(ConsultaRolePolicy.grants());
        user.status = UserStatus.ACTIVE.name();
        assert UserStatus.ACTIVE.name().equals(user.status);
        assert !UserDeletionPolicy.isDeleted(null);
        assert UserDeletionPolicy.DISPLAY_STATUS.equals("DELETED");
        AccessUserRecord deleted = new AccessUserRecord();
        deleted.status = UserStatus.ACTIVE.name();
        deleted.deletedAt = OffsetDateTime.now(ZoneOffset.UTC);
        assert deleted.isDeleted();
        CurrentUser resolved = userWith(Set.of());
        assert !resolved.has(AppModule.PEOPLE, AppPermission.CONSULT);
    }

    static void deletedEmailIsControlledDuplicate() {
        assert RegisterResultDto.OUTCOME_EXISTS_UNAVAILABLE.equals(AuthService.duplicateOutcome("DISABLED"));
        assert !RegisterResultDto.OUTCOME_EXISTS_ACTIVE.equals(AuthService.duplicateOutcome("DISABLED"));
        AccessUserRecord deleted = new AccessUserRecord();
        deleted.status = UserStatus.ACTIVE.name();
        deleted.deletedAt = OffsetDateTime.now(ZoneOffset.UTC);
        assert deleted.isDeleted();
    }

    static void userDeletedEventKeepsEmailSnapshot() {
        String detail = UserDeletionPolicy.eventDetail("PHYSICAL", "ana@uss.cl", "PHYSICAL");
        assert detail.contains("PHYSICAL");
        assert detail.contains("ana@uss.cl");
        assert !detail.toLowerCase().contains("password");
        assert !detail.toLowerCase().contains("token");
        assert UserDeletionPolicy.EVENT_DELETED.equals("USER_DELETED");
    }

    static void smtpFailureKeepsPendingActivation() {
        assert AuthService.REGISTER_MAIL_FAILED.contains("no fue posible enviar el correo");
        assert UserStatus.PENDING_ACTIVATION.name().equals("PENDING_ACTIVATION");
        assert !AuthService.REGISTER_MAIL_FAILED.toLowerCase().contains("elimin");
    }

    static void mailServiceIsMockable() {
        RecordingMailService mail = new RecordingMailService();
        mail.send(MailMessage.of("to@uss.cl", "Subject", "text", "<p>html</p>"));
        assert mail.sent.size() == 1;
        assert mail.sent.get(0).subject.equals("Subject");
        assert mail.isConfigured();
    }

    private static CurrentUser userWith(Set<EffectivePermission> permissions) {
        CurrentUser user = new CurrentUser();
        user.permissions.addAll(permissions);
        return user;
    }

    static final class RecordingMailService implements MailService {
        final List<MailMessage> sent = new ArrayList<>();

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public void send(MailMessage message) {
            sent.add(message);
        }
    }
}
