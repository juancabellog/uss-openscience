package cl.uss.ca.events;

import cl.uss.ca.exception.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@ApplicationScoped
public class EventFileStore {

    @Inject
    @ConfigProperty(name = "openscience.events.storage.dir", defaultValue = "var/openscience-events")
    String storageDir;

    public Stored save(byte[] bytes, String originalFilename) {
        if (bytes == null || bytes.length == 0) {
            throw new ValidationException("EVENT_FILE_EMPTY", "El archivo está vacío");
        }
        if (bytes.length > 25 * 1024 * 1024) {
            throw new ValidationException("EVENT_FILE_TOO_LARGE", "El archivo supera 25 MB");
        }
        try {
            Path dir = Path.of(storageDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String stored = UUID.randomUUID() + extension(originalFilename);
            Files.write(dir.resolve(stored), bytes);
            return new Stored(stored, sha256(bytes), bytes.length);
        } catch (IOException ex) {
            throw new ValidationException("EVENT_STORAGE_FAILED", "No fue posible guardar el archivo del evento");
        }
    }

    public byte[] read(String storedName) {
        try {
            Path file = resolve(storedName);
            if (!Files.exists(file)) {
                throw new ValidationException("EVENT_FILE_MISSING", "El archivo ya no está disponible");
            }
            return Files.readAllBytes(file);
        } catch (IOException ex) {
            throw new ValidationException("EVENT_FILE_MISSING", "El archivo ya no está disponible");
        }
    }

    public String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash event file");
        }
    }

    private Path resolve(String storedName) {
        if (storedName == null || storedName.isBlank() || storedName.contains("/") || storedName.contains("\\") || storedName.contains("..")) {
            throw new ValidationException("EVENT_FILE_MISSING", "El archivo ya no está disponible");
        }
        return Path.of(storageDir).toAbsolutePath().normalize().resolve(storedName).normalize();
    }

    private static String extension(String filename) {
        if (filename == null) {
            return ".bin";
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        if (dot < 0) {
            return ".bin";
        }
        return lower.substring(dot);
    }

    public record Stored(String storedName, String sha256, int byteSize) {
    }
}
