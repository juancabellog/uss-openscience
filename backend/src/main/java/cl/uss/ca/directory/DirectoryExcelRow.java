package cl.uss.ca.directory;

public final class DirectoryExcelRow {
    public final int sourceRowNumber;
    public final String empresa;
    public final String rut;
    public final String givenName;
    public final String middleName;
    public final String familyName;
    public final String secondFamilyName;
    public final String campus;
    public final String faculty;
    public final String position;
    public final String email;

    public DirectoryExcelRow(
            int sourceRowNumber,
            String empresa,
            String rut,
            String givenName,
            String middleName,
            String familyName,
            String secondFamilyName,
            String campus,
            String faculty,
            String position,
            String email
    ) {
        this.sourceRowNumber = sourceRowNumber;
        this.empresa = empresa;
        this.rut = rut;
        this.givenName = givenName;
        this.middleName = middleName;
        this.familyName = familyName;
        this.secondFamilyName = secondFamilyName;
        this.campus = campus;
        this.faculty = faculty;
        this.position = position;
        this.email = email;
    }
}
