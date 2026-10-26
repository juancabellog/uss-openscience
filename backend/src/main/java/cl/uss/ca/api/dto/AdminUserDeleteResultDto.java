package cl.uss.ca.api.dto;

public class AdminUserDeleteResultDto {
    public String code;
    public String mode;
    public String message;

    public static AdminUserDeleteResultDto of(String code, String mode, String message) {
        AdminUserDeleteResultDto dto = new AdminUserDeleteResultDto();
        dto.code = code;
        dto.mode = mode;
        dto.message = message;
        return dto;
    }
}
