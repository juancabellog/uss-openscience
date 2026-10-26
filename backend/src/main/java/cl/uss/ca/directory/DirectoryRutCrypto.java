package cl.uss.ca.directory;

import cl.uss.ca.exception.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@ApplicationScoped
public class DirectoryRutCrypto {
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private final byte[] pepper;
    private final SecretKey aesKey;
    private final SecureRandom random = new SecureRandom();

    protected DirectoryRutCrypto() {
        this.pepper = new byte[0];
        this.aesKey = null;
    }

    @Inject
    public DirectoryRutCrypto(
            @ConfigProperty(name = "directory.rut.pepper") Optional<String> pepper,
            @ConfigProperty(name = "directory.rut.aesKey") Optional<String> aesKey
    ) {
        this.pepper = pepper.filter(value -> !value.isBlank()).orElse("").getBytes(StandardCharsets.UTF_8);
        this.aesKey = parseAesKey(aesKey.filter(value -> !value.isBlank()).orElse(""));
    }

    DirectoryRutCrypto(byte[] pepper, byte[] aesKeyBytes) {
        this.pepper = pepper == null ? new byte[0] : pepper;
        this.aesKey = aesKeyBytes == null || aesKeyBytes.length == 0
                ? null
                : new SecretKeySpec(aesKeyBytes, "AES");
    }

    public boolean configured() {
        return pepper.length > 0 && aesKey != null;
    }

    public void requireConfigured() {
        if (!configured()) {
            throw new ValidationException(
                    "DIRECTORY_CRYPTO_NOT_CONFIGURED",
                    "directory.rut.pepper and directory.rut.aesKey must be set"
            );
        }
    }

    public String hmac(String normalizedRut) {
        requireConfigured();
        return DirectoryRut.hmac(normalizedRut, pepper);
    }

    public byte[] encrypt(String normalizedRut) {
        requireConfigured();
        if (normalizedRut == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(normalizedRut.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(iv.length + cipherText.length).put(iv).put(cipherText).array();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to encrypt directory RUT", ex);
        }
    }

    public String decrypt(byte[] encrypted) {
        requireConfigured();
        if (encrypted == null || encrypted.length <= GCM_IV_LENGTH) {
            return null;
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(encrypted);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to decrypt directory RUT", ex);
        }
    }

    private static SecretKey parseAesKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        byte[] bytes;
        if (value.matches("(?i)[0-9a-f]{64}")) {
            bytes = HexFormat.of().parseHex(value);
        } else {
            bytes = Base64.getDecoder().decode(value);
        }
        if (bytes.length != 16 && bytes.length != 32) {
            throw new ValidationException("DIRECTORY_CRYPTO_NOT_CONFIGURED", "directory.rut.aesKey must be 16 or 32 bytes");
        }
        return new SecretKeySpec(bytes, "AES");
    }
}
