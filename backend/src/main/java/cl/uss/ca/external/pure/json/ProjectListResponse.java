package cl.uss.ca.external.pure.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectListResponse {
    private Integer count;
    private PageInformation pageInformation;
    private List<ProjectItem> items;

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

    public List<ProjectItem> getItems() {
        return items;
    }

    public void setItems(List<ProjectItem> items) {
        this.items = items;
    }
}
