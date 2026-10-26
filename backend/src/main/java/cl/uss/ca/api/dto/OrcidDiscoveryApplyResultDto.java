package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class OrcidDiscoveryApplyResultDto {
    public int selected;
    public int succeeded;
    public int failed;
    public List<PureCorrectionResultDto> items = new ArrayList<>();
}
