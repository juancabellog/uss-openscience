package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class EventAttendanceImportDto {
    public Long id;
    public Integer artifactVersion;
    public String source;
    public String status;
    public String filename;
    public String createdBy;
    public String createdAt;
    public int matched;
    public int unmatched;
    public int ambiguous;
    public final List<EventAttendanceImportRowDto> rows = new ArrayList<>();
}
