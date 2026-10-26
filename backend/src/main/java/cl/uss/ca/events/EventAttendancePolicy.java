package cl.uss.ca.events;

import cl.uss.ca.exception.ValidationException;

import java.util.Locale;

public final class EventAttendancePolicy {
    private EventAttendancePolicy() {
    }

    public static EventAttendanceStatus requireStatus(String raw) {
        EventAttendanceStatus status = EventAttendanceStatus.parse(raw);
        if (status == null) {
            throw new ValidationException("EVENT_ATTENDANCE_STATUS_INVALID", "Estado de asistencia no válido");
        }
        return status;
    }

    public static String matchStatus(int matches) {
        if (matches == 1) {
            return "MATCHED";
        }
        if (matches > 1) {
            return "AMBIGUOUS";
        }
        return "UNMATCHED";
    }

    public static boolean isConfirmedAttendance(String status) {
        return EventAttendanceStatus.ASISTIO == EventAttendanceStatus.parse(status);
    }

    public static boolean isAbsent(String status) {
        return EventAttendanceStatus.NO_ASISTIO == EventAttendanceStatus.parse(status);
    }

    public static boolean isProposedOnly(String importStatus) {
        return importStatus != null && "PREVIEW".equals(importStatus.toUpperCase(Locale.ROOT));
    }

    public static int blankRows(Integer raw) {
        return EventRules.blankRows(raw);
    }

    public static String requireDocumentKind(String raw) {
        if (raw == null || raw.isBlank()) {
            return "COMPLEMENTARY";
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if ("SIGNED_MINUTES".equals(value) || "PHOTO".equals(value) || "COMPLEMENTARY".equals(value)) {
            return value;
        }
        throw new ValidationException("EVENT_DOCUMENT_KIND_INVALID", "Tipo de evidencia no válido");
    }

    public static String contentType(String filename, String fallback) {
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        if (lower.endsWith(".csv")) {
            return "text/csv";
        }
        return fallback == null || fallback.isBlank() ? "application/octet-stream" : fallback;
    }

    public static boolean allowedEvidence(String filename, String kind) {
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if ("SIGNED_MINUTES".equals(kind)) {
            return lower.endsWith(".pdf");
        }
        if ("PHOTO".equals(kind)) {
            return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp");
        }
        return lower.endsWith(".pdf")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".png")
                || lower.endsWith(".webp");
    }
}
