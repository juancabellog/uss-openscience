package cl.uss.ca.pure.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JournalAssociationItem {
    private Long pureId;
    private JournalTitleItem title;
    private IssnItem issn;
    private OrganizationRef journal;

    public Long getPureId() {
        return pureId;
    }

    public void setPureId(Long pureId) {
        this.pureId = pureId;
    }

    public JournalTitleItem getTitle() {
        return title;
    }

    public void setTitle(JournalTitleItem title) {
        this.title = title;
    }

    public IssnItem getIssn() {
        return issn;
    }

    public void setIssn(IssnItem issn) {
        this.issn = issn;
    }

    public OrganizationRef getJournal() {
        return journal;
    }

    public void setJournal(OrganizationRef journal) {
        this.journal = journal;
    }
}
