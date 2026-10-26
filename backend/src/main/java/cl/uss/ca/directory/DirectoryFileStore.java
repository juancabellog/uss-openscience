package cl.uss.ca.directory;

import cl.uss.ca.exception.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

@ApplicationScoped
public class DirectoryFileStore {

    @Inject
    @ConfigProperty(name = "directory.storage.dir", defaultValue = "var/openscience-directory")
    String storageDir;

    public String save(byte[] bytes, String originalFilename) {
        try {
            Path dir = Path.of(storageDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String stored = UUID.randomUUID() + extension(originalFilename);
            Files.write(dir.resolve(stored), bytes);
            return stored;
        } catch (IOException ex) {
            throw new ValidationException("DIRECTORY_STORAGE_FAILED", "Unable to store the directory file");
        }
    }

    public String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash directory file");
        }
    }

    private static String extension(String filename) {
        if (filename == null) {
            return ".xlsx";
        }
        String lower = filename.toLowerCase();
        if (lower.endsWith(".xlsx")) {
            return ".xlsx";
        }
        if (lower.endsWith(".xls")) {
            return ".xls";
        }
        return ".bin";
    }
}
