package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class ResearchOutputFiltersDto {
    public List<ResearchOutputTypeDto> types = new ArrayList<>();
    public List<Integer> years = new ArrayList<>();
    public List<ResearchOutputWorkflowOptionDto> workflows = new ArrayList<>();
}
