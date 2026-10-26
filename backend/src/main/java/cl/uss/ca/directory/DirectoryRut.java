package cl.uss.ca.directory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Protected RUT helpers. The raw RUT is never directory identity; HMAC is the
 * reconciliation key between spreadsheet imports.
 */
public final class DirectoryRut {
    private DirectoryRut() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String compact = raw.trim().toUpperCase(Locale.ROOT).replaceAll("[^0-9K]", "");
        return compact.isEmpty() ? null : compact;
    }

    public static boolean isValid(String normalized) {
        if (normalized == null || normalized.length() < 2 || normalized.length() > 9) {
            return false;
        }
        String body = normalized.substring(0, normalized.length() - 1);
        char expected = checkDigit(body);
        if (expected == 0) {
            return false;
        }
        return expected == normalized.charAt(normalized.length() - 1);
    }

    public static char checkDigit(String body) {
        if (body == null || body.isEmpty() || !body.chars().allMatch(Character::isDigit)) {
            return 0;
        }
        int sum = 0;
        int factor = 2;
        for (int i = body.length() - 1; i >= 0; i--) {
            sum += (body.charAt(i) - '0') * factor;
            factor = factor == 7 ? 2 : factor + 1;
        }
        int remainder = 11 - (sum % 11);
        if (remainder == 11) {
            return '0';
        }
        if (remainder == 10) {
            return 'K';
        }
        return (char) ('0' + remainder);
    }

    public static String hmac(String normalized, byte[] pepper) {
        if (normalized == null || pepper == null || pepper.length == 0) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to HMAC directory RUT", ex);
        }
    }
}
