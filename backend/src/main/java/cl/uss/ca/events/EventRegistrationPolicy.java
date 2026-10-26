package cl.uss.ca.events;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Período de inscripción evaluado en la zona horaria del evento.
 * El intervalo es cerrado: el instante exacto de apertura y el instante exacto de cierre
 * están incluidos ({@code apertura <= ahora <= cierre}). Un minuto después del cierre queda fuera.
 * Si la hora civil cae en un hueco de horario de verano, no se desplaza y la inscripción se rechaza.
 */
public final class EventRegistrationPolicy {
    private EventRegistrationPolicy() {
    }

    public static boolean isOpen(
            EventPublicationStatus status,
            LocalDateTime now,
            LocalDateTime opensAt,
            LocalDateTime closesAt
    ) {
        return isOpen(status, now, opensAt, closesAt, null);
    }

    public static boolean isOpen(
            EventPublicationStatus status,
            LocalDateTime now,
            LocalDateTime opensAt,
            LocalDateTime closesAt,
            String timezone
    ) {
        if (now == null) {
            return false;
        }
        ZoneId zone = zoneOrReject(timezone);
        if (zone == null) {
            return false;
        }
        Instant instant = EventDateTimes.toInstant(now, zone);
        if (instant == null) {
            return false;
        }
        return isOpen(status, instant, opensAt, closesAt, timezone);
    }

    public static boolean isOpen(
            EventPublicationStatus status,
            Instant now,
            LocalDateTime opensAt,
            LocalDateTime closesAt,
            String timezone
    ) {
        if (status != EventPublicationStatus.PUBLISHED || now == null) {
            return false;
        }
        ZoneId zone = zoneOrReject(timezone);
        if (zone == null) {
            return false;
        }
        if (opensAt != null) {
            Instant open = EventDateTimes.toInstant(opensAt, zone);
            if (open == null || now.isBefore(open)) {
                return false;
            }
        }
        if (closesAt != null) {
            Instant close = EventDateTimes.toInstant(closesAt, zone);
            if (close == null || now.isAfter(close)) {
                return false;
            }
        }
        return true;
    }

    private static ZoneId zoneOrReject(String timezone) {
        if (timezone != null && !timezone.isBlank() && EventDateTimes.zone(timezone) == null) {
            return null;
        }
        ZoneId zone = EventDateTimes.zone(timezone);
        return zone == null ? ZoneId.of(EventDateTimes.FALLBACK_ZONE) : zone;
    }

    public static Integer remainingCapacity(Integer capacity, int confirmedCount) {
        if (capacity == null) {
            return null;
        }
        return Math.max(0, capacity - Math.max(0, confirmedCount));
    }

    public static boolean hasCapacity(Integer capacity, int confirmedCount) {
        Integer remaining = remainingCapacity(capacity, confirmedCount);
        return remaining == null || remaining > 0;
    }
}
