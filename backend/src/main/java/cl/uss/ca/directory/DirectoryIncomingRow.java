package cl.uss.ca.directory;

import java.util.Objects;

public final class DirectoryIncomingRow {
    public final int sourceRowNumber;
    public final String empresaRaw;
    public final String rutNormalized;
    public final String rutHmac;
    public final String givenName;
    public final String middleName;
    public final String familyName;
    public final String secondFamilyName;
    public final String campusName;
    public final String facultyRaw;
    public final String positionTitle;
    public final String emailNormalized;
    public final String snapshotHash;

    public DirectoryIncomingRow(
            int sourceRowNumber,
            String empresaRaw,
            String rutNormalized,
            String rutHmac,
            String givenName,
            String middleName,
            String familyName,
            String secondFamilyName,
            String campusName,
            String facultyRaw,
            String positionTitle,
            String emailNormalized,
            String snapshotHash
    ) {
        this.sourceRowNumber = sourceRowNumber;
        this.empresaRaw = empresaRaw;
        this.rutNormalized = rutNormalized;
        this.rutHmac = rutHmac;
        this.givenName = givenName;
        this.middleName = middleName;
        this.familyName = familyName;
        this.secondFamilyName = secondFamilyName;
        this.campusName = campusName;
        this.facultyRaw = facultyRaw;
        this.positionTitle = positionTitle;
        this.emailNormalized = emailNormalized;
        this.snapshotHash = snapshotHash;
    }

    public boolean sameSnapshot(DirectoryPersonRef existing) {
        return existing != null && Objects.equals(snapshotHash, existing.snapshotHash);
    }
}
