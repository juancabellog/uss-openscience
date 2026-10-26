package cl.uss.ca.quality.consistency;

public enum ConsistencyField {
    TITLE("EXT-01", "title"),
    PUBLICATION_YEAR("EXT-02", "publication_year"),
    JOURNAL_TITLE("EXT-03", "journal_title"),
    ISSN("EXT-04", "journal_issn"),
    VOLUME("EXT-05", "citation_volume"),
    ISSUE("EXT-06", "citation_issue"),
    PAGES("EXT-07", "citation_pages"),
    PUBLISHER("EXT-08", "publisher"),
    ACCESS_TYPE("EXT-09", "access_type"),
    LICENSE("EXT-10", "license_type"),
    FUNDING_AWARD("EXT-11", "funding_award"),
    AUTHORS("EXT-12", "authors");

    private final String ruleCode;
    private final String fieldName;

    ConsistencyField(String ruleCode, String fieldName) {
        this.ruleCode = ruleCode;
        this.fieldName = fieldName;
    }

    public String ruleCode() {
        return ruleCode;
    }

    public String fieldName() {
        return fieldName;
    }
}
