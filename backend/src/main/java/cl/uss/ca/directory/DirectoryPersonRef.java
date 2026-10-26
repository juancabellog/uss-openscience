package cl.uss.ca.directory;

import java.util.Set;

public final class DirectoryPersonRef {
    public final long id;
    public final String uuid;
    public final String rutHmac;
    public final boolean activeInDirectory;
    public final String snapshotHash;
    public final Set<String> emails;

    public DirectoryPersonRef(
            long id,
            String uuid,
            String rutHmac,
            boolean activeInDirectory,
            String snapshotHash,
            Set<String> emails
    ) {
        this.id = id;
        this.uuid = uuid;
        this.rutHmac = rutHmac;
        this.activeInDirectory = activeInDirectory;
        this.snapshotHash = snapshotHash;
        this.emails = emails == null ? Set.of() : Set.copyOf(emails);
    }

    public boolean ownsEmail(String emailNormalized) {
        return emailNormalized != null && emails.contains(emailNormalized);
    }
}
