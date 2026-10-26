package cl.uss.ca.directory;

import cl.uss.ca.exception.ValidationException;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

public final class DirectoryRutCryptoTest {
    private DirectoryRutCryptoTest() {
    }

    public static void main(String[] args) {
        roundTripDoesNotKeepPlaintext();
        hmacIsStableAndNotReversibleToRut();
        missingSecretsFailClosed();
        System.out.println("DirectoryRutCryptoTest OK");
    }

    static void roundTripDoesNotKeepPlaintext() {
        byte[] key = HexFormat.of().parseHex("00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff");
        DirectoryRutCrypto crypto = new DirectoryRutCrypto("pepper-test".getBytes(StandardCharsets.UTF_8), key);
        String rut = "12345678" + DirectoryRut.checkDigit("12345678");
        byte[] encrypted = crypto.encrypt(rut);
        assertEquals(true, encrypted != null && encrypted.length > 12);
        assertEquals(false, new String(encrypted, StandardCharsets.UTF_8).contains(rut));
        assertEquals(rut, crypto.decrypt(encrypted));
    }

    static void hmacIsStableAndNotReversibleToRut() {
        DirectoryRutCrypto crypto = new DirectoryRutCrypto(
                "pepper-test".getBytes(StandardCharsets.UTF_8),
                HexFormat.of().parseHex("00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff")
        );
        String rut = "12345678" + DirectoryRut.checkDigit("12345678");
        String hmac = crypto.hmac(rut);
        assertEquals(64, hmac.length());
        assertEquals(hmac, crypto.hmac(rut));
        assertEquals(false, hmac.toLowerCase().contains(rut.toLowerCase()));
    }

    static void missingSecretsFailClosed() {
        DirectoryRutCrypto crypto = new DirectoryRutCrypto(new byte[0], new byte[0]);
        try {
            crypto.requireConfigured();
            throw new AssertionError("expected crypto configuration error");
        } catch (ValidationException ex) {
            assertEquals("DIRECTORY_CRYPTO_NOT_CONFIGURED", ex.getCode());
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }
}
