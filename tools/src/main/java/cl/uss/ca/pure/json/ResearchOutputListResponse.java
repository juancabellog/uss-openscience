package cl.uss.ca.pure.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ResearchOutputListResponse {
    private Integer count;
    private PageInformation pageInformation;
    private List<ResearchOutputItem> items;

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

    public List<ResearchOutputItem> getItems() {
        return items;
    }

    public void setItems(List<ResearchOutputItem> items) {
        this.items = items;
    }
}
