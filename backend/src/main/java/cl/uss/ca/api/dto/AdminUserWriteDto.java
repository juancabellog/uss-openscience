package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class AdminUserWriteDto {
    public String firstName;
    public String lastName;
    public String email;
    public String preferredLanguage;
    public List<Long> roleIds = new ArrayList<>();
}
