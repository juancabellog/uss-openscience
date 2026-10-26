package cl.uss.ca.events;

import java.util.Locale;

public enum EventPublicationStatus {
    DRAFT,
    PUBLISHED,
    FINISHED,
    CANCELLED;

    public static EventPublicationStatus parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return EventPublicationStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public boolean isPublicCatalog() {
        return this == PUBLISHED;
    }
}
