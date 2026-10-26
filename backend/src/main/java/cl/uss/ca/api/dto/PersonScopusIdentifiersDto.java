package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class PersonScopusIdentifiersDto {
    public Long personId;
    public int count;
    public int distinctCount;
    public int matchedCount;
    public List<PersonScopusIdentifierDto> items = new ArrayList<>();
}
