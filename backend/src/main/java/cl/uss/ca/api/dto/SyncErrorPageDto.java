package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class SyncErrorPageDto {
    public String syncId;
    public long totalElements;
    public int page;
    public int size;
    public int totalPages;
    public List<SyncErrorGroupDto> groups = new ArrayList<>();
    public List<SyncErrorDto> items = new ArrayList<>();
}
