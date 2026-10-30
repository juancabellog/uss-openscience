package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class EthicsMeDto {
    public String id;
    public String name;
    public String email;
    public List<EthicsRoleDto> roles = new ArrayList<>();
}
