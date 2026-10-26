package cl.uss.ca.api.dto;

public class OpenAlexMatchQuery {
    public String search;
    public String status;
    public String position;
    public Boolean hasOrcid;
    public Boolean ussAffiliation;
    public Integer minWorks;
    public String sort = "works";
    public String direction = "desc";
    public int page = 1;
    public int pageSize = 25;
}
