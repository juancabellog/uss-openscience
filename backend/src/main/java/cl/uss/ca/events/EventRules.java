package cl.uss.ca.events;

import cl.uss.ca.api.dto.EventWriteDto;
import cl.uss.ca.exception.FieldViolation;
import cl.uss.ca.exception.ValidationException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public final class EventRules {
    public static final int TITLE_MAX = 255;
    public static final int DESCRIPTION_MAX = 20000;
    public static final int LOCATION_MAX = 255;
    public static final int CAMPUS_MAX = 128;
    public static final int UNIT_MAX = 255;
    public static final int TIMEZONE_MAX = 64;
    public static final int VIRTUAL_ACCESS_MAX = 1000;
    public static final int SLUG_MAX = 80;
    public static final String DEFAULT_TIMEZONE = "America/Santiago";
    public static final int DEFAULT_BLANK_ROWS = 10;
    static final String VIRTUAL_FALLBACK_SEPARATOR = " || ";

    private EventRules() {
    }

    public static String requireTitle(String raw) {
        EventFieldIssue issue = titleIssue(raw);
        if (issue != null) {
            throw new ValidationException(issue.code(), issue.message());
        }
        return trimToNull(raw);
    }

    public static String optionalText(String raw, int max, String code) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        if (value.length() > max) {
            throw new ValidationException(code, "El texto supera el máximo permitido");
        }
        return value;
    }

    public static EventType requireType(String raw) {
        EventType type = EventType.parse(raw);
        if (type == null) {
            throw new ValidationException("EVENT_TYPE_INVALID", "Tipo de evento no válido");
        }
        return type;
    }

    public static EventType optionalType(String raw) {
        if (raw == null || raw.isBlank()) {
            return EventType.OTHER;
        }
        return requireType(raw);
    }

    public static EventModality optionalModality(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        EventModality modality = EventModality.parse(raw);
        if (modality == null) {
            throw new ValidationException("EVENT_MODALITY_INVALID", "Modalidad no válida");
        }
        return modality;
    }

    public static EventPublicationStatus parseStatus(String raw, EventPublicationStatus fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        EventPublicationStatus status = EventPublicationStatus.parse(raw);
        if (status == null) {
            throw new ValidationException("EVENT_STATUS_INVALID", "Estado no válido");
        }
        return status;
    }

    public static Integer optionalCapacity(Integer raw) {
        EventFieldIssue issue = capacityIssue(raw == null ? null : String.valueOf(raw));
        if (issue != null) {
            throw new ValidationException(issue.code(), issue.message());
        }
        return raw;
    }

    public static int blankRows(Integer raw) {
        int value = raw == null ? DEFAULT_BLANK_ROWS : raw;
        EventFieldIssue issue = blankRowsIssue(String.valueOf(value));
        if (issue != null) {
            throw new ValidationException(issue.code(), issue.message());
        }
        return value;
    }

    public static String timezone(String raw) {
        String value = trimToNull(raw);
        return value == null ? DEFAULT_TIMEZONE : optionalText(value, TIMEZONE_MAX, "EVENT_TIMEZONE_TOO_LONG");
    }

    public static void validateWindow(LocalDateTime starts, LocalDateTime ends, LocalDateTime opens, LocalDateTime closes) {
        validateWindow(starts, ends, opens, closes, inferAllowDuring(starts, closes), null);
    }

    public static void validateWindow(
            LocalDateTime starts,
            LocalDateTime ends,
            LocalDateTime opens,
            LocalDateTime closes,
            boolean allowDuringEvent
    ) {
        validateWindow(starts, ends, opens, closes, allowDuringEvent, null);
    }

    public static void validateWindow(
            LocalDateTime starts,
            LocalDateTime ends,
            LocalDateTime opens,
            LocalDateTime closes,
            boolean allowDuringEvent,
            String timezone
    ) {
        List<EventFieldIssue> issues = windowIssues(starts, ends, opens, closes, allowDuringEvent, timezone);
        if (!issues.isEmpty()) {
            throw toException(issues);
        }
    }

    public static void requirePublishable(EventWriteDto body, LocalDateTime starts, EventModality modality) {
        List<EventFieldIssue> issues = collect(body, true);
        if (!issues.isEmpty()) {
            throw toException(issues);
        }
        if (starts == null) {
            throw new ValidationException("EVENT_START_REQUIRED", "La fecha de inicio es obligatoria para publicar");
        }
        if (modality == null) {
            throw new ValidationException("EVENT_MODALITY_REQUIRED", "La modalidad es obligatoria para publicar");
        }
    }

    public static List<EventFieldIssue> collect(EventWriteDto body, boolean publish) {
        List<EventFieldIssue> issues = new ArrayList<>();
        if (body == null) {
            issues.add(new EventFieldIssue("title", "EVENT_REQUIRED", "El evento es obligatorio"));
            return issues;
        }
        add(issues, titleIssue(body.title));
        add(issues, lengthIssue("description", body.description, DESCRIPTION_MAX, "EVENT_DESCRIPTION_TOO_LONG"));
        add(issues, lengthIssue("locationName", body.locationName, LOCATION_MAX, "EVENT_LOCATION_TOO_LONG"));
        add(issues, lengthIssue("campusName", body.campusName, CAMPUS_MAX, "EVENT_CAMPUS_TOO_LONG"));
        add(issues, lengthIssue("organizingUnit", body.organizingUnit, UNIT_MAX, "EVENT_UNIT_TOO_LONG"));
        add(issues, unitIssue(body, publish));
        add(issues, lengthIssue("timezone", body.timezone, TIMEZONE_MAX, "EVENT_TIMEZONE_TOO_LONG"));
        add(issues, lengthIssue("virtualAccess", body.virtualAccess, VIRTUAL_ACCESS_MAX, "EVENT_VIRTUAL_ACCESS_TOO_LONG"));
        add(issues, lengthIssue("publicSlug", body.publicSlug, SLUG_MAX, "EVENT_SLUG_TOO_LONG"));
        if (body.eventType != null && !body.eventType.isBlank() && EventType.parse(body.eventType) == null) {
            issues.add(new EventFieldIssue("eventType", "EVENT_TYPE_INVALID", "Tipo de evento no válido"));
        }
        if (body.modality != null && !body.modality.isBlank() && EventModality.parse(body.modality) == null) {
            issues.add(new EventFieldIssue("modality", "EVENT_MODALITY_INVALID", "Modalidad no válida"));
        }
        add(issues, capacityIssue(body.capacity == null ? null : String.valueOf(body.capacity)));
        add(issues, blankRowsIssue(body.blankSignatureRows == null ? null : String.valueOf(body.blankSignatureRows)));
        LocalDateTime starts = EventDateTimes.parse(body.startsAt);
        LocalDateTime ends = EventDateTimes.parse(body.endsAt);
        LocalDateTime opens = EventDateTimes.parse(body.registrationOpensAt);
        LocalDateTime closes = EventDateTimes.parse(body.registrationClosesAt);
        issues.addAll(windowIssues(starts, ends, opens, closes, body.allowRegistrationDuringEvent, body.timezone));
        if (publish) {
            issues.addAll(publishIssues(body, starts, ends, opens, closes));
        }
        return issues;
    }

    public static void throwFirst(EventWriteDto body, boolean publish) {
        List<EventFieldIssue> issues = collect(body, publish);
        if (!issues.isEmpty()) {
            throw toException(issues);
        }
    }

    public static ValidationException toException(List<EventFieldIssue> issues) {
        List<FieldViolation> violations = new ArrayList<>();
        for (EventFieldIssue issue : issues) {
            violations.add(new FieldViolation(issue.field(), issue.code(), issue.message()));
        }
        return new ValidationException(violations);
    }

    public static boolean inferAllowDuring(LocalDateTime starts, LocalDateTime closes) {
        return starts != null && closes != null && closes.isAfter(starts);
    }

    public static String encodeVirtualFallback(String locationName, String virtualAccess) {
        String place = trimToNull(locationName);
        String access = trimToNull(virtualAccess);
        if (access == null) {
            return place;
        }
        if (place == null) {
            return access;
        }
        return place + VIRTUAL_FALLBACK_SEPARATOR + access;
    }

    public static String[] placeAndAccess(String modalityRaw, String locationName, String virtualAccess) {
        String place = trimToNull(locationName);
        String access = trimToNull(virtualAccess);
        if (access != null) {
            return new String[] {place, access};
        }
        EventModality modality = EventModality.parse(modalityRaw);
        if (modality == EventModality.VIRTUAL) {
            return new String[] {null, place};
        }
        if (modality == EventModality.HYBRID) {
            String[] parts = decodeVirtualFallback(place);
            if (parts[1] != null) {
                return parts;
            }
        }
        return new String[] {place, access};
    }

    public static String[] decodeVirtualFallback(String stored) {
        String value = trimToNull(stored);
        if (value == null) {
            return new String[] {null, null};
        }
        int index = value.indexOf(VIRTUAL_FALLBACK_SEPARATOR);
        if (index < 0) {
            return new String[] {value, null};
        }
        return new String[] {
                trimToNull(value.substring(0, index)),
                trimToNull(value.substring(index + VIRTUAL_FALLBACK_SEPARATOR.length()))
        };
    }

    public static String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        return value.isEmpty() ? null : value;
    }

    private static List<EventFieldIssue> publishIssues(
            EventWriteDto body,
            LocalDateTime starts,
            LocalDateTime ends,
            LocalDateTime opens,
            LocalDateTime closes
    ) {
        List<EventFieldIssue> issues = new ArrayList<>();
        if (trimToNull(body.description) == null) {
            issues.add(new EventFieldIssue("description", "EVENT_DESCRIPTION_REQUIRED", "La descripción es obligatoria para publicar"));
        }
        if (EventType.parse(body.eventType) == null) {
            issues.add(new EventFieldIssue("eventType", "EVENT_TYPE_REQUIRED", "El tipo de evento es obligatorio para publicar"));
        }
        EventModality modality = EventModality.parse(body.modality);
        if (modality == null) {
            issues.add(new EventFieldIssue("modality", "EVENT_MODALITY_REQUIRED", "La modalidad es obligatoria para publicar"));
        }
        if (starts == null) {
            issues.add(new EventFieldIssue("startsAt", "EVENT_START_REQUIRED", "La fecha de inicio es obligatoria para publicar"));
        }
        if (ends == null) {
            issues.add(new EventFieldIssue("endsAt", "EVENT_END_REQUIRED", "La fecha de término es obligatoria para publicar"));
        }
        if (trimToNull(body.timezone) == null) {
            issues.add(new EventFieldIssue("timezone", "EVENT_TIMEZONE_REQUIRED", "La zona horaria es obligatoria para publicar"));
        }
        if (opens == null) {
            issues.add(new EventFieldIssue("registrationOpensAt", "EVENT_REGISTRATION_OPEN_REQUIRED", "La apertura de inscripciones es obligatoria para publicar"));
        }
        if (closes == null) {
            issues.add(new EventFieldIssue("registrationClosesAt", "EVENT_REGISTRATION_CLOSE_REQUIRED", "El cierre de inscripciones es obligatorio para publicar"));
        }
        String place = trimToNull(body.locationName);
        String campus = trimToNull(body.campusName);
        String access = trimToNull(body.virtualAccess);
        boolean hasPlace = place != null || campus != null;
        if (modality == EventModality.PRESENTIAL && !hasPlace) {
            issues.add(new EventFieldIssue("locationName", "EVENT_PLACE_REQUIRED", "Indique la sede o el lugar para un evento presencial"));
        }
        if (modality == EventModality.VIRTUAL && access == null) {
            issues.add(new EventFieldIssue("virtualAccess", "EVENT_VIRTUAL_ACCESS_REQUIRED", "Indique el enlace o las instrucciones de acceso virtual"));
        }
        if (modality == EventModality.HYBRID) {
            if (!hasPlace) {
                issues.add(new EventFieldIssue("locationName", "EVENT_PLACE_REQUIRED", "Indique la sede o el lugar para un evento híbrido"));
            }
            if (access == null) {
                issues.add(new EventFieldIssue("virtualAccess", "EVENT_VIRTUAL_ACCESS_REQUIRED", "Indique el enlace o las instrucciones de acceso virtual"));
            }
        }
        return issues;
    }

    private static List<EventFieldIssue> windowIssues(
            LocalDateTime starts,
            LocalDateTime ends,
            LocalDateTime opens,
            LocalDateTime closes,
            boolean allowDuringEvent,
            String timezone
    ) {
        List<EventFieldIssue> issues = new ArrayList<>();
        ZoneId zone = EventDateTimes.zone(timezone);
        if (timezone != null && !timezone.isBlank() && zone == null) {
            issues.add(new EventFieldIssue("timezone", "EVENT_TIMEZONE_INVALID", "La zona horaria del evento no es válida"));
        }
        boolean startGap = EventDateTimes.isGap(starts, zone);
        boolean endGap = EventDateTimes.isGap(ends, zone);
        boolean openGap = EventDateTimes.isGap(opens, zone);
        boolean closeGap = EventDateTimes.isGap(closes, zone);
        if (startGap) {
            issues.add(gap("startsAt"));
        }
        if (endGap) {
            issues.add(gap("endsAt"));
        }
        if (openGap) {
            issues.add(gap("registrationOpensAt"));
        }
        if (closeGap) {
            issues.add(gap("registrationClosesAt"));
        }
        if (starts != null && ends != null && !startGap && !endGap && compare(ends, starts, zone) <= 0) {
            issues.add(new EventFieldIssue(
                    "endsAt",
                    "EVENT_ENDS_BEFORE_START",
                    "El término del evento debe ser posterior al inicio"
            ));
        }
        if (opens != null && closes != null && !openGap && !closeGap && compare(closes, opens, zone) <= 0) {
            issues.add(new EventFieldIssue(
                    "registrationClosesAt",
                    "EVENT_REGISTRATION_WINDOW_INVALID",
                    "El cierre de inscripciones debe ser posterior a la apertura"
            ));
        }
        if (opens != null && starts != null && !openGap && !startGap && compare(opens, starts, zone) >= 0) {
            issues.add(new EventFieldIssue(
                    "registrationOpensAt",
                    "EVENT_REGISTRATION_OPENS_AFTER_START",
                    "La apertura de inscripciones debe ser anterior al inicio del evento"
            ));
        }
        if (!allowDuringEvent && starts != null && closes != null && !startGap && !closeGap && compare(closes, starts, zone) > 0) {
            issues.add(new EventFieldIssue(
                    "registrationClosesAt",
                    "EVENT_REGISTRATION_AFTER_START",
                    "El cierre de inscripciones no puede superar el inicio del evento"
            ));
        }
        if (allowDuringEvent && ends != null && closes != null && !endGap && !closeGap && compare(closes, ends, zone) > 0) {
            issues.add(new EventFieldIssue(
                    "registrationClosesAt",
                    "EVENT_REGISTRATION_AFTER_END",
                    "Si se permite inscripción durante la actividad, el cierre no puede superar el término del evento"
            ));
        }
        return issues;
    }

    private static int compare(LocalDateTime left, LocalDateTime right, ZoneId zone) {
        if (zone == null) {
            return left.compareTo(right);
        }
        Instant leftInstant = EventDateTimes.toInstant(left, zone);
        Instant rightInstant = EventDateTimes.toInstant(right, zone);
        if (leftInstant == null || rightInstant == null) {
            return left.compareTo(right);
        }
        return leftInstant.compareTo(rightInstant);
    }

    private static EventFieldIssue gap(String field) {
        return new EventFieldIssue(
                field,
                "EVENT_LOCAL_TIME_INVALID",
                "Esa hora no existe en la zona horaria del evento por el cambio de hora"
        );
    }

    private static EventFieldIssue titleIssue(String raw) {
        String title = trimToNull(raw);
        if (title == null) {
            return new EventFieldIssue("title", "EVENT_TITLE_REQUIRED", "El nombre del evento es obligatorio");
        }
        if (title.length() > TITLE_MAX) {
            return new EventFieldIssue("title", "EVENT_TITLE_TOO_LONG", "El nombre no puede superar 255 caracteres");
        }
        return null;
    }

    private static EventFieldIssue capacityIssue(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            int capacity = Integer.parseInt(value);
            if (capacity < 1 || capacity > 100000) {
                return new EventFieldIssue("capacity", "EVENT_CAPACITY_INVALID", "El cupo debe ser un entero positivo, o vacío si es ilimitado");
            }
        } catch (NumberFormatException ex) {
            return new EventFieldIssue("capacity", "EVENT_CAPACITY_INVALID", "El cupo debe ser un entero positivo, o vacío si es ilimitado");
        }
        return null;
    }

    private static EventFieldIssue unitIssue(EventWriteDto body, boolean publish) {
        EventOrganizingUnits.Resolved resolved = EventOrganizingUnits.resolve(
                body == null ? null : body.organizingUnitCode,
                body == null ? null : body.organizingUnit
        );
        if (resolved.invalid) {
            return new EventFieldIssue("organizingUnitCode", "EVENT_UNIT_INVALID", "La unidad organizadora no es válida");
        }
        if (!publish) {
            return null;
        }
        if (resolved.isEmpty()) {
            return new EventFieldIssue("organizingUnitCode", "EVENT_UNIT_REQUIRED", "La unidad organizadora es obligatoria para publicar");
        }
        if (resolved.other() && trimToNull(resolved.storedText) == null) {
            return new EventFieldIssue("organizingUnit", "EVENT_UNIT_OTHER_REQUIRED", "Indique el nombre de la unidad organizadora");
        }
        return null;
    }

    private static EventFieldIssue blankRowsIssue(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < 0 || value > 200) {
                return new EventFieldIssue("blankSignatureRows", "EVENT_BLANK_ROWS_INVALID", "Las filas en blanco del acta deben estar entre 0 y 200");
            }
        } catch (NumberFormatException ex) {
            return new EventFieldIssue("blankSignatureRows", "EVENT_BLANK_ROWS_INVALID", "Las filas en blanco del acta deben estar entre 0 y 200");
        }
        return null;
    }

    private static EventFieldIssue lengthIssue(String field, String raw, int max, String code) {
        String value = trimToNull(raw);
        if (value != null && value.length() > max) {
            return new EventFieldIssue(field, code, "El texto supera el máximo permitido");
        }
        return null;
    }

    private static void add(List<EventFieldIssue> issues, EventFieldIssue issue) {
        if (issue != null) {
            issues.add(issue);
        }
    }
}
