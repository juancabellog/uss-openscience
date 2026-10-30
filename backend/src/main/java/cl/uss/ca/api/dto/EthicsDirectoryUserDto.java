package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class EthicsDirectoryUserDto {
    public String id;
    public String givenName;
    public String familyName;
    public String displayName;
    public String email;
    public String personKind;
    public String dataSource;
    public Long purePersonId;
    public String accountStatus;
    public boolean ethicsAccess;
    public List<EthicsDirectoryRoleDto> roles = new ArrayList<>();
}
