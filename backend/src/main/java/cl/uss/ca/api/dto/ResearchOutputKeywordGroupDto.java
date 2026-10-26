package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class ResearchOutputKeywordGroupDto {
    public String name;
    public String nameEs;
    public String nameEn;
    public List<ResearchOutputKeywordDto> keywords = new ArrayList<>();
}
