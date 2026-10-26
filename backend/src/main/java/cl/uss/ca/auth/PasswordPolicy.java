package cl.uss.ca.auth;

import cl.uss.ca.exception.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class PasswordPolicy {

    @Inject
    @ConfigProperty(name = "openscience.auth.password.minLength", defaultValue = "10")
    int minLength;

    public void validate(String password) {
        if (password == null || password.isBlank()) {
            throw new ValidationException("AUTH_PASSWORD_REQUIRED", "Password is required");
        }
        if (password.length() < Math.max(8, minLength)) {
            throw new ValidationException(
                    "AUTH_PASSWORD_POLICY",
                    "Password must have at least " + Math.max(8, minLength) + " characters"
            );
        }
    }

    public int minLength() {
        return Math.max(8, minLength);
    }
}
