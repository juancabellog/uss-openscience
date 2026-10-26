package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class JournalKeywordGroupDto {
    public Long pureId;
    public String typeDiscriminator;
    public String logicalName;
    public String nameEn;
    public String nameEs;
    public List<JournalClassificationItemDto> classifications = new ArrayList<>();
}
