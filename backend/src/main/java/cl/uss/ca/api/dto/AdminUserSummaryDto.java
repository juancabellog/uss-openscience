package cl.uss.ca.api.dto;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class AdminUserSummaryDto {
    public long id;
    public String firstName;
    public String lastName;
    public String displayName;
    public String email;
    public String status;
    public String preferredLanguage;
    public List<String> roles = new ArrayList<>();
    public List<Long> roleIds = new ArrayList<>();
    public OffsetDateTime lastLoginAt;
    public OffsetDateTime createdAt;
    public OffsetDateTime deletedAt;
    public boolean emailSent;
}
