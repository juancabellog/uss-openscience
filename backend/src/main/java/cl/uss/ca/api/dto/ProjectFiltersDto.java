package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class ProjectFiltersDto {
    public List<ProjectTypeOptionDto> types = new ArrayList<>();
    public List<ProjectWorkflowOptionDto> workflows = new ArrayList<>();
    public List<ProjectVisibilityOptionDto> visibilities = new ArrayList<>();
    public List<Integer> startYears = new ArrayList<>();
}
