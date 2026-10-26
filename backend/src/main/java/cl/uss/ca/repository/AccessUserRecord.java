package cl.uss.ca.repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class AccessUserRecord {
    public long id;
    public String email;
    public String emailNormalized;
    public String firstName;
    public String lastName;
    public String passwordHash;
    public String status;
    public String authProvider;
    public String preferredLanguage;
    public int failedLoginCount;
    public OffsetDateTime lockedUntil;
    public OffsetDateTime lastLoginAt;
    public OffsetDateTime activatedAt;
    public OffsetDateTime createdAt;
    public OffsetDateTime updatedAt;
    public Long createdBy;
    public OffsetDateTime deletedAt;
    public Long deletedBy;
    public final List<AccessRoleRecord> roles = new ArrayList<>();

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
