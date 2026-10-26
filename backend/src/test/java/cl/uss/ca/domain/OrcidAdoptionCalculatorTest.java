package cl.uss.ca.domain;

import cl.uss.ca.api.dto.OrcidAdoptionMonthDto;
import cl.uss.ca.api.dto.OrcidAdoptionSeriesDto;
import cl.uss.ca.exception.ValidationException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

/**
 * Serie mensual de adopción acumulada ORCID. No ejecutado por el agente.
 */
public final class OrcidAdoptionCalculatorTest {
    private OrcidAdoptionCalculatorTest() {
    }

    public static void main(String[] args) {
        personNotYetEnteredExcludedFromDenominator();
        firstSeenAfterMonthExcludedFromNumerator();
        firstSeenBeforeMonthEndIncluded();
        zeroDenominatorDoesNotDivide();
        monthsBeforeTrackingMarkedIncomplete();
        seriesIsChronological();
        System.out.println("OrcidAdoptionCalculatorTest OK");
    }

    static void personNotYetEnteredExcludedFromDenominator() {
        OrcidAdoptionPerson late = new OrcidAdoptionPerson(
                LocalDate.of(2026, 3, 1),
                null,
                LocalDateTime.of(2026, 3, 2, 9, 0)
        );
        OrcidAdoptionPerson unknownIngreso = new OrcidAdoptionPerson(
                null,
                null,
                LocalDateTime.of(2026, 1, 10, 9, 0)
        );
        OrcidAdoptionPerson removedInJanuary = new OrcidAdoptionPerson(
                LocalDate.of(2025, 1, 1),
                LocalDateTime.of(2026, 1, 31, 23, 59, 59),
                LocalDateTime.of(2025, 6, 1, 9, 0)
        );
        OrcidAdoptionMonthDto january = OrcidAdoptionCalculator.month(
                YearMonth.of(2026, 1),
                YearMonth.of(2026, 1),
                List.of(late, unknownIngreso, removedInJanuary)
        );
        assertEquals(1L, january.totalPersons);
        assertEquals(1L, january.personsWithOrcid);
        assertEquals(100.0, january.percentage);
    }

    static void firstSeenAfterMonthExcludedFromNumerator() {
        OrcidAdoptionPerson person = new OrcidAdoptionPerson(
                LocalDate.of(2025, 1, 1),
                null,
                LocalDateTime.of(2026, 3, 1, 0, 0)
        );
        OrcidAdoptionMonthDto february = OrcidAdoptionCalculator.month(
                YearMonth.of(2026, 2),
                YearMonth.of(2026, 1),
                List.of(person)
        );
        assertEquals(1L, february.totalPersons);
        assertEquals(0L, february.personsWithOrcid);
        assertEquals(0.0, february.percentage);
    }

    static void firstSeenBeforeMonthEndIncluded() {
        OrcidAdoptionPerson person = new OrcidAdoptionPerson(
                LocalDate.of(2025, 1, 1),
                null,
                LocalDateTime.of(2026, 2, 28, 23, 59, 59)
        );
        OrcidAdoptionMonthDto february = OrcidAdoptionCalculator.month(
                YearMonth.of(2026, 2),
                YearMonth.of(2026, 1),
                List.of(person)
        );
        assertEquals(1L, february.totalPersons);
        assertEquals(1L, february.personsWithOrcid);
        assertEquals(100.0, february.percentage);
    }

    static void zeroDenominatorDoesNotDivide() {
        OrcidAdoptionMonthDto empty = OrcidAdoptionCalculator.month(
                YearMonth.of(2026, 4),
                YearMonth.of(2026, 1),
                List.of()
        );
        assertEquals(0L, empty.totalPersons);
        assertEquals(0L, empty.personsWithOrcid);
        assertEquals(null, empty.percentage);
        assertTrue(empty.complete);
    }

    static void monthsBeforeTrackingMarkedIncomplete() {
        OrcidAdoptionPerson person = new OrcidAdoptionPerson(
                LocalDate.of(2024, 1, 1),
                null,
                LocalDateTime.of(2026, 10, 15, 12, 0)
        );
        OrcidAdoptionSeriesDto series = OrcidAdoptionCalculator.series(
                YearMonth.of(2026, 9),
                YearMonth.of(2026, 10),
                YearMonth.of(2026, 10),
                List.of(person)
        );
        assertEquals("2026-10", series.availableFrom);
        assertTrue(!series.months.get(0).complete);
        assertEquals(null, series.months.get(0).totalPersons);
        assertTrue(series.months.get(1).complete);
        assertEquals(1L, series.months.get(1).totalPersons);
        assertEquals(1L, series.months.get(1).personsWithOrcid);
    }

    static void seriesIsChronological() {
        OrcidAdoptionSeriesDto series = OrcidAdoptionCalculator.series(
                YearMonth.of(2026, 10),
                YearMonth.of(2026, 12),
                YearMonth.of(2026, 10),
                List.of()
        );
        assertEquals("2026-10", series.months.get(0).month);
        assertEquals("2026-11", series.months.get(1).month);
        assertEquals("2026-12", series.months.get(2).month);
        try {
            OrcidAdoptionCalculator.parseMonth("2026/01", "from");
            throw new AssertionError("expected invalid month");
        } catch (ValidationException ex) {
            assertEquals("INVALID_FROM", ex.getCode());
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }

    private static void assertTrue(boolean value) {
        if (!value) {
            throw new AssertionError("expected true");
        }
    }
}
