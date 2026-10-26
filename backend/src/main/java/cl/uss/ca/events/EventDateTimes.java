package cl.uss.ca.events;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

public final class EventDateTimes {
    public static final String FALLBACK_ZONE = "America/Santiago";
    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter LOCAL_MINUTES = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private EventDateTimes() {
    }

    /**
     * Zona del evento. Vacío usa America/Santiago. Un identificador desconocido devuelve null
     * para no desplazar las horas a otra zona.
     */
    public static ZoneId zone(String raw) {
        if (raw == null || raw.isBlank()) {
            return ZoneId.of(FALLBACK_ZONE);
        }
        try {
            return ZoneId.of(raw.trim());
        } catch (DateTimeException ex) {
            return null;
        }
    }

    public static LocalDateTime now(String timezone) {
        ZoneId zone = zone(timezone);
        if (zone == null) {
            zone = ZoneId.of(FALLBACK_ZONE);
        }
        return LocalDateTime.now(zone);
    }

    /**
     * Instantáneo de una hora civil del evento. En un solapamiento de horario de verano
     * se usa el primer instante (el desfase que ocurre antes). Un hueco, cuando el reloj
     * salta hacia adelante y esa hora no existe, no se desplaza: devuelve null.
     */
    public static Instant toInstant(LocalDateTime local, ZoneId zone) {
        if (local == null || zone == null) {
            return null;
        }
        List<ZoneOffset> offsets = zone.getRules().getValidOffsets(local);
        if (offsets.isEmpty()) {
            return null;
        }
        return local.toInstant(offsets.get(0));
    }

    public static boolean isGap(LocalDateTime local, ZoneId zone) {
        return local != null && zone != null && zone.getRules().getValidOffsets(local).isEmpty();
    }

    /**
     * Escribe la hora civil en una columna DATETIME sin convertirla con {@link Timestamp}.
     * {@code Timestamp.valueOf} normaliza los huecos de horario de verano según la zona de la JVM
     * y puede adelantar una hora que el usuario no ingresó.
     */
    public static void setWallClock(PreparedStatement statement, int index, LocalDateTime value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP);
            return;
        }
        statement.setObject(index, value);
    }

    public static LocalDateTime readWallClock(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, LocalDateTime.class);
    }

    public static LocalDateTime parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (value.endsWith("Z")) {
            value = value.substring(0, value.length() - 1);
        }
        int plus = value.indexOf('+', 10);
        int tzSep = value.indexOf('-', 11);
        if (plus > 0) {
            value = value.substring(0, plus);
        } else if (tzSep > 0 && value.charAt(tzSep - 1) != 'T') {
            value = value.substring(0, tzSep);
        }
        if (value.length() > 19) {
            value = value.substring(0, 19);
        }
        try {
            if (value.length() == 16) {
                return LocalDateTime.parse(value, LOCAL_MINUTES);
            }
            return LocalDateTime.parse(value, LOCAL);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    public static String format(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.toLocalDateTime().format(LOCAL);
    }

    public static String format(LocalDateTime value) {
        return value == null ? null : value.format(LOCAL);
    }

    public static Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }
}
