package cl.uss.ca.api.dto;

public class RegisterResultDto {
    public static final String OUTCOME_CREATED = "CREATED";
    public static final String OUTCOME_EXISTS_ACTIVE = "EXISTS_ACTIVE";
    public static final String OUTCOME_EXISTS_PENDING = "EXISTS_PENDING";
    public static final String OUTCOME_EXISTS_UNAVAILABLE = "EXISTS_UNAVAILABLE";

    public String code;
    public String message;
    public String outcome;
    public boolean emailSent;
    public boolean canResend;
    public String maskedEmail;

    public static RegisterResultDto of(
            String code,
            String message,
            String outcome,
            boolean emailSent,
            boolean canResend,
            String maskedEmail
    ) {
        RegisterResultDto dto = new RegisterResultDto();
        dto.code = code;
        dto.message = message;
        dto.outcome = outcome;
        dto.emailSent = emailSent;
        dto.canResend = canResend;
        dto.maskedEmail = maskedEmail;
        return dto;
    }
}
