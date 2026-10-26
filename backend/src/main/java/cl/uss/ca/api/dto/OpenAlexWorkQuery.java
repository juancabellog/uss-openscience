package cl.uss.ca.api.dto;

public class OpenAlexWorkQuery {
    public String search;
    public String status;
    public String workType;
    public String sort = "syncedAt";
    public String direction = "desc";
    public int page = 1;
    public int pageSize = 25;
}
