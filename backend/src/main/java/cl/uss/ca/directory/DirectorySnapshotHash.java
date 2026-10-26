package cl.uss.ca.directory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

public final class DirectorySnapshotHash {
    private DirectorySnapshotHash() {
    }

    public static String of(DirectoryIncomingRow row) {
        if (row == null) {
            return null;
        }
        return sha256(String.join(
                "\u001f",
                nz(row.givenName),
                nz(row.middleName),
                nz(row.familyName),
                nz(row.secondFamilyName),
                nz(row.campusName),
                nz(row.facultyRaw),
                nz(row.positionTitle),
                nz(row.emailNormalized),
                nz(DirectoryEmail.normalize(row.empresaRaw))
        ));
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash directory snapshot", ex);
        }
    }

    private static String nz(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
