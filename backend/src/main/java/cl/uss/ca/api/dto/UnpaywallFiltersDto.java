package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class UnpaywallFiltersDto {
    public final List<String> oaStatuses = new ArrayList<>();
    public final List<String> hostTypes = new ArrayList<>();
    public final List<String> licenses = new ArrayList<>();
    public final List<String> versions = new ArrayList<>();
}
