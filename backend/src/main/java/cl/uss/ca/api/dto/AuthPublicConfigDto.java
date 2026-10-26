package cl.uss.ca.api.dto;

public class AuthPublicConfigDto {
    public boolean registrationEnabled;
    public boolean registrationUssOnly;

    public static AuthPublicConfigDto of(boolean registrationEnabled, boolean registrationUssOnly) {
        AuthPublicConfigDto dto = new AuthPublicConfigDto();
        dto.registrationEnabled = registrationEnabled;
        dto.registrationUssOnly = registrationUssOnly;
        return dto;
    }
}
