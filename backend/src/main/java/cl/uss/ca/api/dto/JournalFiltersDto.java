package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class JournalFiltersDto {
    public List<JournalTypeOptionDto> types = new ArrayList<>();
    public List<JournalWorkflowOptionDto> workflows = new ArrayList<>();
}
