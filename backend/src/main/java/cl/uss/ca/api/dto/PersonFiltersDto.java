package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class PersonFiltersDto {
    public List<String> positions = new ArrayList<>();
    public boolean withoutPositionAvailable;
    public List<Integer> scopusIdCounts = new ArrayList<>();
}
