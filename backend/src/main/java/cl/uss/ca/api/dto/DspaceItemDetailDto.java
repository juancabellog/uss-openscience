package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class DspaceItemDetailDto extends DspaceItemListDto {
    public String embargoDate;
    public Boolean inArchive;
    public Boolean discoverable;
    public Boolean withdrawn;
    public String lastErrorCode;
    public String lastErrorMessage;
    public String lastSyncExecutionId;
    public String seenInExecutionId;
    public List<DspaceBitstreamDto> bitstreams = new ArrayList<>();
    public List<DspaceMetadataDto> metadata = new ArrayList<>();
    public List<DspaceMatchListDto> matches = new ArrayList<>();
    public List<DspaceCollectionRefDto> collections = new ArrayList<>();
}
