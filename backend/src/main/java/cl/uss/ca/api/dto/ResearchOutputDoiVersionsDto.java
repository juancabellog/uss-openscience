package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class ResearchOutputDoiVersionsDto {
    public Long researchOutputId;
    public int count;
    public int distinctCount;
    public List<ResearchOutputElectronicVersionDto> items = new ArrayList<>();
}
