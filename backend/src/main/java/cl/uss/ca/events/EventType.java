package cl.uss.ca.events;

import java.util.Locale;

public enum EventType {
    TALK,
    SEMINAR,
    WORK_TABLE,
    WORKSHOP,
    JOURNEY,
    OTHER;

    public static EventType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return EventType.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public String labelEs() {
        return switch (this) {
            case TALK -> "Charla";
            case SEMINAR -> "Seminario";
            case WORK_TABLE -> "Mesa de trabajo";
            case WORKSHOP -> "Taller";
            case JOURNEY -> "Jornada";
            case OTHER -> "Otro";
        };
    }
}
