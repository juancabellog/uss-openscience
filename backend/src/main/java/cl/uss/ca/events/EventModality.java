package cl.uss.ca.events;

import java.util.Locale;

public enum EventModality {
    PRESENTIAL,
    VIRTUAL,
    HYBRID;

    public static EventModality parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return EventModality.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public String labelEs() {
        return switch (this) {
            case PRESENTIAL -> "Presencial";
            case VIRTUAL -> "Virtual";
            case HYBRID -> "Híbrida";
        };
    }
}
