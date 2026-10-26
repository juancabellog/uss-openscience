package cl.uss.ca.pure;

public class AcademicTitle {
    private Long pureId;
    private String value;
    private String type;
    private TitleKind kind;

    public Long getPureId() {
        return pureId;
    }

    public void setPureId(Long pureId) {
        this.pureId = pureId;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public TitleKind getKind() {
        return kind;
    }

    public void setKind(TitleKind kind) {
        this.kind = kind;
    }
}
