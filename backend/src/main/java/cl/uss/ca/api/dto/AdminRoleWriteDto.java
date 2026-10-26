package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class AdminRoleWriteDto {
    public String code;
    public String name;
    public String description;
    public Boolean active;
    public List<AuthPermissionDto> permissions = new ArrayList<>();
}
