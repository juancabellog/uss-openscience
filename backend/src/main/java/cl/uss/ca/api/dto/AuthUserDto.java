package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class AuthUserDto {
    public String id;
    public String displayName;
    public String firstName;
    public String lastName;
    public String email;
    public String preferredLanguage;
    public String status;
    public List<String> roles = new ArrayList<>();
    public List<AuthPermissionDto> effectivePermissions = new ArrayList<>();
}
