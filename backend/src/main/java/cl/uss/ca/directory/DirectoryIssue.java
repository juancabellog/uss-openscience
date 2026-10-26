package cl.uss.ca.directory;

public final class DirectoryIssue {
    public final int sourceRowNumber;
    public final String code;
    public final Long leftPersonId;
    public final Long rightPersonId;

    public DirectoryIssue(int sourceRowNumber, String code, Long leftPersonId, Long rightPersonId) {
        this.sourceRowNumber = sourceRowNumber;
        this.code = code;
        this.leftPersonId = leftPersonId;
        this.rightPersonId = rightPersonId;
    }
}
