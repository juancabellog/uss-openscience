package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class DirectoryPersonDetailDto extends DirectoryPersonSummaryDto {
    public String givenName;
    public String middleName;
    public String familyName;
    public String secondFamilyName;
    public String facultyRaw;
    public String lastSeenAt;
    public String removedAt;
    public List<DirectoryEmailDto> emails = new ArrayList<>();
}
