package cl.uss.ca.api.dto;

public class GenericMessageDto {
    public String code;
    public String message;
    public boolean emailSent;

    public static GenericMessageDto of(String code, String message) {
        return of(code, message, false);
    }

    public static GenericMessageDto of(String code, String message, boolean emailSent) {
        GenericMessageDto dto = new GenericMessageDto();
        dto.code = code;
        dto.message = message;
        dto.emailSent = emailSent;
        return dto;
    }
}
