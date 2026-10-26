package cl.uss.ca.repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class AccessRoleRecord {
    public long id;
    public String code;
    public String name;
    public String description;
    public boolean active;
    public boolean systemRole;
    public long userCount;
    public OffsetDateTime createdAt;
    public OffsetDateTime updatedAt;
    public final List<AccessPermissionGrant> grants = new ArrayList<>();
}
