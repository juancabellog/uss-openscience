package cl.uss.ca.api.dto;

public class UnpaywallQuery {
    public String search;
    public String oaStatus;
    public String hostType;
    public String license;
    public String version;
    public Boolean isOa;
    public Boolean repositoryCopy;
    public String outcome;
    public String sort = "retrievedAt";
    public String direction = "desc";
    public int page = 1;
    public int pageSize = 25;
}
