package cl.uss.ca.directory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class DirectoryRowMapper {

    private final DirectoryRutCrypto crypto;

    protected DirectoryRowMapper() {
        this.crypto = null;
    }

    @Inject
    public DirectoryRowMapper(DirectoryRutCrypto crypto) {
        this.crypto = crypto;
    }

    public List<DirectoryIncomingRow> map(List<DirectoryExcelRow> rows, List<DirectoryIssue> issues) {
        List<DirectoryIncomingRow> incoming = new ArrayList<>();
        if (rows == null) {
            return incoming;
        }
        for (DirectoryExcelRow row : rows) {
            incoming.add(mapOne(row, issues));
        }
        return incoming;
    }

    public DirectoryIncomingRow mapOne(DirectoryExcelRow row, List<DirectoryIssue> issues) {
        String email = DirectoryEmail.normalize(row.email);
        String empresa = DirectoryEmail.normalize(row.empresa);
        String rut = DirectoryRut.normalize(row.rut);
        String hmac = null;
        if (rut != null && DirectoryRut.isValid(rut) && crypto.configured()) {
            hmac = crypto.hmac(rut);
        }
        DirectoryIncomingRow incoming = new DirectoryIncomingRow(
                row.sourceRowNumber,
                row.empresa,
                rut,
                hmac,
                trim(row.givenName),
                trim(row.middleName),
                trim(row.familyName),
                trim(row.secondFamilyName),
                trim(row.campus),
                DirectoryFaculty.normalizeRaw(row.faculty),
                trim(row.position),
                email,
                null
        );
        incoming = withHash(incoming);
        if (row.email != null && !DirectoryEmail.isUsable(email)) {
            issues.add(new DirectoryIssue(row.sourceRowNumber, "INVALID_EMAIL", null, null));
        }
        if (row.rut != null && (rut == null || !DirectoryRut.isValid(rut))) {
            issues.add(new DirectoryIssue(row.sourceRowNumber, "INVALID_RUT", null, null));
        }
        if (empresa != null && DirectoryEmail.isUsable(empresa) && email != null && !empresa.equals(email)
                && !DirectoryEmail.isUsable(email)) {
            issues.add(new DirectoryIssue(row.sourceRowNumber, "INVALID_EMAIL", null, null));
        }
        return incoming;
    }

    public static DirectoryIncomingRow withHash(DirectoryIncomingRow row) {
        return new DirectoryIncomingRow(
                row.sourceRowNumber,
                row.empresaRaw,
                row.rutNormalized,
                row.rutHmac,
                row.givenName,
                row.middleName,
                row.familyName,
                row.secondFamilyName,
                row.campusName,
                row.facultyRaw,
                row.positionTitle,
                row.emailNormalized,
                DirectorySnapshotHash.of(row)
        );
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
