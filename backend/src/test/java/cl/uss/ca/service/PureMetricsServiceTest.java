package cl.uss.ca.service;

import cl.uss.ca.api.dto.JournalQuartileCategoryDto;
import cl.uss.ca.api.dto.JournalQuartileSummaryDto;
import cl.uss.ca.api.dto.MetricObservationDto;
import cl.uss.ca.api.dto.PersonHindexSummaryDto;
import cl.uss.ca.api.dto.ResearchOutputFwciSummaryDto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Latest/summary rules. Not executed by the agent.
 */
public final class PureMetricsServiceTest {
    private PureMetricsServiceTest() {
    }

    public static void main(String[] args) {
        latestCiteScoreUsesMaxYear();
        multipleQuartilesKeepBoth();
        fwciSummary();
        scopusAllYearsHindex();
        System.out.println("PureMetricsServiceTest OK");
    }

    static void latestCiteScoreUsesMaxYear() {
        MetricObservationDto older = observation("scopusJournal", "rating_citescore", 2023, new BigDecimal("3.1"));
        MetricObservationDto newer = observation("scopusJournal", "rating_citescore", 2025, new BigDecimal("4.1"));
        var summary = PureMetricsService.latestCiteScore(List.of(older, newer));
        if (summary == null || summary.year != 2025 || summary.value.compareTo(new BigDecimal("4.1")) != 0) {
            throw new AssertionError("latest CiteScore must use MAX(year)");
        }
    }

    static void multipleQuartilesKeepBoth() {
        MetricObservationDto q1 = observation("scopusCitescore", "quartile", 2023, 1L);
        q1.categoryTermEn = "Agricultural";
        MetricObservationDto q2 = observation("scopusCitescore", "quartile", 2023, 2L);
        q2.categoryTermEn = "Medicine (all)";
        List<JournalQuartileCategoryDto> pivoted = PureMetricsService.pivotQuartiles(List.of(q1, q2));
        JournalQuartileSummaryDto summary = PureMetricsService.latestQuartiles(pivoted, List.of(q1, q2));
        if (summary == null || !"Q1 · Q2".equals(summary.compactLabel) || !summary.multipleCategories) {
            throw new AssertionError("compact quartiles=" + (summary == null ? null : summary.compactLabel));
        }
    }

    static void fwciSummary() {
        MetricObservationDto fwci = observation("sciVal", "fwci", null, new BigDecimal("0.57"));
        fwci.sourceTermEn = "SciVal";
        ResearchOutputFwciSummaryDto summary = PureMetricsService.latestFwci(List.of(fwci));
        if (summary == null || summary.value.compareTo(new BigDecimal("0.57")) != 0) {
            throw new AssertionError("FWCI summary");
        }
    }

    static void scopusAllYearsHindex() {
        MetricObservationDto h = observation("hIndex", "hindex", null, 12L);
        h.sourceTermEn = "Scopus h-index";
        h.categoryTermEn = "All years";
        MetricObservationDto c = observation("hIndex", "totalcitations", null, 450L);
        c.sourceTermEn = "Scopus h-index";
        c.categoryTermEn = "All years";
        MetricObservationDto other = observation("hIndex", "hindex", null, 8L);
        other.sourceTermEn = "PlumX h-index";
        other.categoryTermEn = "All years";
        PersonHindexSummaryDto summary = PureMetricsService.scopusAllYears(List.of(h, c, other));
        if (summary == null || summary.hindex != 12 || summary.totalCitations != 450) {
            throw new AssertionError("Scopus all-years summary");
        }
    }

    private static MetricObservationDto observation(String collection, String metricId, Integer year, Object value) {
        MetricObservationDto dto = new MetricObservationDto();
        dto.collectionId = collection;
        dto.metricId = metricId;
        dto.metricYear = year;
        if (value instanceof BigDecimal decimal) {
            dto.valueKind = "DECIMAL";
            dto.decimalValue = decimal;
        } else {
            dto.valueKind = "INTEGER";
            dto.integerValue = (Long) value;
        }
        return dto;
    }
}
