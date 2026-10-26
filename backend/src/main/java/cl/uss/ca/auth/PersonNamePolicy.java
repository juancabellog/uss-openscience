package cl.uss.ca.auth;

public final class PersonNamePolicy {
    public static final int MAX_LENGTH = 120;

    private PersonNamePolicy() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().replaceAll("\\s+", " ");
        return value.isEmpty() ? null : value;
    }

    public static boolean isAcceptable(String raw) {
        String value = normalize(raw);
        if (value == null || value.length() > MAX_LENGTH) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
