package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class JournalQuartileSummaryDto {
    public Integer year;
    public String compactLabel;
    public boolean multipleCategories;
    public List<JournalQuartileCategoryDto> categories = new ArrayList<>();
    public String lastSyncedAt;
}
