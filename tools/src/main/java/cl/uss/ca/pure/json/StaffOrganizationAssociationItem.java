package cl.uss.ca.pure.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StaffOrganizationAssociationItem {

    private Long pureId;
    private ClassificationRef employmentType;
    private OrganizationRef organization;
    private List<EmailItem> emails;
    private DateRange period;
    private Boolean primaryAssociation;
    private ClassificationRef contractType;
    private java.math.BigDecimal fte;
    private LocalizedText jobDescription;
    private ClassificationRef jobTitle;
    private ClassificationRef staffType;

    public Long getPureId() {
        return pureId;
    }

    public void setPureId(Long pureId) {
        this.pureId = pureId;
    }

    public ClassificationRef getEmploymentType() {
        return employmentType;
    }

    public void setEmploymentType(ClassificationRef employmentType) {
        this.employmentType = employmentType;
    }

    public OrganizationRef getOrganization() {
        return organization;
    }

    public void setOrganization(OrganizationRef organization) {
        this.organization = organization;
    }

    public List<EmailItem> getEmails() {
        return emails;
    }

    public void setEmails(List<EmailItem> emails) {
        this.emails = emails;
    }

    public DateRange getPeriod() {
        return period;
    }

    public void setPeriod(DateRange period) {
        this.period = period;
    }

    public Boolean getPrimaryAssociation() {
        return primaryAssociation;
    }

    public void setPrimaryAssociation(Boolean primaryAssociation) {
        this.primaryAssociation = primaryAssociation;
    }

    public ClassificationRef getContractType() {
        return contractType;
    }

    public void setContractType(ClassificationRef contractType) {
        this.contractType = contractType;
    }

    public java.math.BigDecimal getFte() {
        return fte;
    }

    public void setFte(java.math.BigDecimal fte) {
        this.fte = fte;
    }

    public LocalizedText getJobDescription() {
        return jobDescription;
    }

    public void setJobDescription(LocalizedText jobDescription) {
        this.jobDescription = jobDescription;
    }

    public ClassificationRef getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(ClassificationRef jobTitle) {
        this.jobTitle = jobTitle;
    }

    public ClassificationRef getStaffType() {
        return staffType;
    }

    public void setStaffType(ClassificationRef staffType) {
        this.staffType = staffType;
    }
}
