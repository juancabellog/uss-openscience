package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class SyncChangeGroupDto {
    public Long localRecordId;
    public Long pureId;
    public String uuid;
    public String entity;
    public String displayName;
    public String changeType;
    public List<SyncChangeLineDto> changes = new ArrayList<>();
}
