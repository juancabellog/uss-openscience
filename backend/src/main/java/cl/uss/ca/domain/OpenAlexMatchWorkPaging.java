package cl.uss.ca.domain;

/**
 * Presentation paging for OpenAlex match-work evidence. Does not change matching.
 */
public final class OpenAlexMatchWorkPaging {
    public static final int DEFAULT_SIZE = 25;
    public static final int MAX_SIZE = 100;

    private OpenAlexMatchWorkPaging() {
    }

    public static int page(Integer value) {
        return value == null || value < 1 ? 1 : value;
    }

    public static int size(Integer value) {
        if (value == null || value < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(value, MAX_SIZE);
    }

    public static String search(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public static int offset(int page, int size) {
        return Math.max(0, (page(page) - 1) * size(size));
    }
}
