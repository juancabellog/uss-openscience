package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class DataQualityApplyResultDto {
    public int applied;
    public int remoteChanged;
    public int localFailed;
    public int reviewRequired;
    public int failed;
    public List<DataQualityApplyItemDto> items = new ArrayList<>();
}
