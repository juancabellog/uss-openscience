package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class ResearchOutputContributorDto {
    public Long id;
    public Long personId;
    public String personUuid;
    public String firstName;
    public String lastName;
    public String fullName;
    public String role;
    public String roleEs;
    public String roleEn;
    public Boolean external;
    public Boolean correspondingAuthor;
    public List<String> organizationUuids = new ArrayList<>();
}
