package cl.uss.ca.api.dto;

public class DataQualityRuleDto {
    public Long id;
    public String code;
    public String sourceFamily;
    public String entityType;
    public String fieldName;
    public String name;
    public String description;
    public String ruleText;
    public String alertText;
    public String bulkProcedure;
    public String category;
    public String correctionMode;
    public boolean enabled;
    public long openFindingCount;
}
