package cl.uss.ca.api.dto;

public class AuthPermissionDto {
    public String module;
    public String permission;

    public static AuthPermissionDto of(String module, String permission) {
        AuthPermissionDto dto = new AuthPermissionDto();
        dto.module = module;
        dto.permission = permission;
        return dto;
    }
}
