package cl.uss.ca.api.dto;

public class OpenAlexAuthorQuery {
    public String search;
    public String sort = "syncedAt";
    public String direction = "desc";
    public int page = 1;
    public int pageSize = 25;
}
