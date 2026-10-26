package cl.uss.ca.events;

import java.util.Locale;

public enum EventAttendanceStatus {
    PENDIENTE,
    ASISTIO,
    NO_ASISTIO,
    REVISAR;

    public static EventAttendanceStatus parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toUpperCase(Locale.ROOT)
                .replace('Á', 'A')
                .replace('Í', 'I')
                .replace(' ', '_');
        if ("ASISTIÓ".equals(raw.trim().toUpperCase(Locale.ROOT)) || "PRESENT".equals(value) || "PRESENT".equals(raw.trim().toUpperCase(Locale.ROOT))) {
            return ASISTIO;
        }
        if ("AUSENTE".equals(value) || "ABSENT".equals(value) || "NO_ASISTIÓ".equals(raw.trim().toUpperCase(Locale.ROOT))) {
            return NO_ASISTIO;
        }
        if ("PENDING".equals(value)) {
            return PENDIENTE;
        }
        if ("REVIEW".equals(value) || "AMBIGUO".equals(value)) {
            return REVISAR;
        }
        try {
            return EventAttendanceStatus.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public String labelEs() {
        return switch (this) {
            case PENDIENTE -> "Pendiente";
            case ASISTIO -> "Asistió";
            case NO_ASISTIO -> "No asistió";
            case REVISAR -> "Revisar";
        };
    }
}
