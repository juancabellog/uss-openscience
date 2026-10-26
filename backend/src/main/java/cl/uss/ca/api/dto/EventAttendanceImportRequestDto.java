package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class EventAttendanceImportRequestDto {
    public Integer artifactVersion;
    public String source;
    public final List<EventAttendanceImportRowDto> rows = new ArrayList<>();
}
