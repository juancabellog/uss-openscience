package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class OpenAlexFiltersDto {
    public final List<String> statuses = new ArrayList<>();
    public final List<String> matchStatuses = new ArrayList<>();
    public final List<String> workTypes = new ArrayList<>();
    public final List<String> positions = new ArrayList<>();
}
