package cl.uss.ca.api.dto;

import java.time.OffsetDateTime;

public class DspaceMatchListDto {
    public Long id;
    public String itemUuid;
    public String itemName;
    public String itemHandle;
    public String itemDoi;
    public String matchMethod;
    public String matchStatus;
    public boolean canonical;
    public Double confidence;
    public String evidenceJson;
    public OffsetDateTime updatedAt;
    public Long researchOutputId;
    public String researchOutputUuid;
    public String researchOutputTitle;
    public String researchOutputDoi;
}
