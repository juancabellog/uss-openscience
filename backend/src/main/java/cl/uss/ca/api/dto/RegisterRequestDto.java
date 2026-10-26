package cl.uss.ca.api.dto;

/**
 * Public self-registration body. Privilege, status and password fields are
 * intentionally absent so JSON-B cannot bind a mass-assignment attempt.
 */
public class RegisterRequestDto {
    public String firstName;
    public String lastName;
    public String email;
}
