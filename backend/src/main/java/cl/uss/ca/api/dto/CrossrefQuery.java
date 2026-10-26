package cl.uss.ca.api.dto;

public class CrossrefQuery {
    public String search;
    public String status;
    public String workType;
    public Boolean hasOrcid;
    public Boolean hasAffiliation;
    public Boolean hasRor;
    public Boolean hasFunding;
    public Boolean hasLicense;
    public Boolean hasUpdate;
    public String comparison;
    public String sort = "syncedAt";
    public String direction = "desc";
    public int page = 1;
    public int pageSize = 25;
}
