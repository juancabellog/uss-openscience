package cl.uss.ca.domain;

import cl.uss.ca.api.dto.OrcidAdoptionMonthDto;
import cl.uss.ca.api.dto.OrcidAdoptionSeriesDto;
import cl.uss.ca.exception.ValidationException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Cumulative ORCID adoption by month. Operational live calculation, not a frozen official snapshot.
 */
public final class OrcidAdoptionCalculator {
    public static final int MAX_MONTHS = 36;
    public static final String UNIVERSE = "PERSONS_IN_TENURE";
    public static final String DEFINITION = "CUMULATIVE_ADOPTION";

    private OrcidAdoptionCalculator() {
    }

    public static YearMonth parseMonth(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            throw new ValidationException("INVALID_" + field.toUpperCase(), field + " must be YYYY-MM");
        }
    }

    public static OrcidAdoptionSeriesDto series(
            YearMonth from,
            YearMonth to,
            YearMonth availableFrom,
            List<OrcidAdoptionPerson> people
    ) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new ValidationException("INVALID_MONTH_RANGE", "from must be on or before to");
        }
        if (monthsBetween(from, to) + 1 > MAX_MONTHS) {
            throw new ValidationException("INVALID_MONTH_RANGE", "range cannot exceed " + MAX_MONTHS + " months");
        }
        OrcidAdoptionSeriesDto dto = new OrcidAdoptionSeriesDto();
        dto.metric = "orcidAdoption";
        dto.definition = DEFINITION;
        dto.universe = UNIVERSE;
        dto.availableFrom = availableFrom == null ? null : availableFrom.toString();
        dto.from = from.toString();
        dto.to = to.toString();
        YearMonth cursor = from;
        while (!cursor.isAfter(to)) {
            dto.months.add(month(cursor, availableFrom, people == null ? List.of() : people));
            cursor = cursor.plusMonths(1);
        }
        return dto;
    }

    static OrcidAdoptionMonthDto month(
            YearMonth month,
            YearMonth availableFrom,
            List<OrcidAdoptionPerson> people
    ) {
        OrcidAdoptionMonthDto dto = new OrcidAdoptionMonthDto();
        dto.month = month.toString();
        boolean complete = availableFrom != null && !month.isBefore(availableFrom);
        dto.complete = complete;
        if (!complete) {
            return dto;
        }
        LocalDate monthEndDate = month.atEndOfMonth();
        LocalDateTime monthEnd = monthEndDate.atTime(23, 59, 59, 999_000_000);
        long total = 0;
        long withOrcid = 0;
        for (OrcidAdoptionPerson person : people) {
            if (!inDenominator(person, monthEndDate, monthEnd)) {
                continue;
            }
            total++;
            if (inNumerator(person, monthEnd)) {
                withOrcid++;
            }
        }
        dto.totalPersons = total;
        dto.personsWithOrcid = withOrcid;
        dto.percentage = total <= 0 ? null : Math.round(withOrcid * 1000.0 / total) / 10.0;
        return dto;
    }

    static boolean inDenominator(OrcidAdoptionPerson person, LocalDate monthEndDate, LocalDateTime monthEnd) {
        if (person == null) {
            return false;
        }
        if (person.fechaIngreso != null && person.fechaIngreso.isAfter(monthEndDate)) {
            return false;
        }
        return person.removedFromPureAt == null || person.removedFromPureAt.isAfter(monthEnd);
    }

    static boolean inNumerator(OrcidAdoptionPerson person, LocalDateTime monthEnd) {
        return person != null
                && person.orcidFirstSeenAt != null
                && !person.orcidFirstSeenAt.isAfter(monthEnd);
    }

    static int monthsBetween(YearMonth from, YearMonth to) {
        return (to.getYear() - from.getYear()) * 12 + (to.getMonthValue() - from.getMonthValue());
    }
}
