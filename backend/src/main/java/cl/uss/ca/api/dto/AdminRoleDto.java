package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class AdminRoleDto {
    public long id;
    public String code;
    public String name;
    public String description;
    public boolean active;
    public boolean systemRole;
    public long userCount;
    public List<AuthPermissionDto> permissions = new ArrayList<>();
}
