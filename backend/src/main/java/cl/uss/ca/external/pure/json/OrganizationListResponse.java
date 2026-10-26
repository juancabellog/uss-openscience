package cl.uss.ca.external.pure.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OrganizationListResponse {
    private Integer count;
    private PageInformation pageInformation;
    private List<OrganizationItem> items;

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public PageInformation getPageInformation() {
        return pageInformation;
    }

    public void setPageInformation(PageInformation pageInformation) {
        this.pageInformation = pageInformation;
    }

    public List<OrganizationItem> getItems() {
        return items;
    }

    public void setItems(List<OrganizationItem> items) {
        this.items = items;
    }
}
