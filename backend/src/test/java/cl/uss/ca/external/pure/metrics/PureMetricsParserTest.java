package cl.uss.ca.external.pure.metrics;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

/**
 * Parser cases for PURE metric collections. Not executed by the agent.
 */
public final class PureMetricsParserTest {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true);

    private PureMetricsParserTest() {
    }

    public static void main(String[] args) throws Exception {
        scopusJournalCiteScoreByYear();
        scopusCitescoreMultipleCategoriesSameYear();
        sciValFwciDecimal();
        personHindexSourcesAndWindows();
        integerDecimalStringPreserved();
        emptyCollection();
        malformedResponse();
        zeroIsValue();
        ownerMismatchInvalid();
        System.out.println("PureMetricsParserTest OK");
    }

    static void scopusJournalCiteScoreByYear() throws Exception {
        PureMetricFetchResult result = PureMetricsParser.parse(MAPPER.readTree("""
                {
                  "uuid": "journal-1",
                  "collectionId": "scopusJournal",
                  "items": [
                    {
                      "year": 2024,
                      "source": { "uri": "scopus", "term": { "en_US": "Scopus", "es_CL": "Scopus" } },
                      "metricValues": [
                        { "metricId": "rating_citescore", "decimalValue": 4.1 },
                        { "metricId": "rating_snip", "decimalValue": 1.2 }
                      ]
                    },
                    {
                      "year": 2023,
                      "metricValues": [
                        { "metricId": "rating_citescore", "decimalValue": 3.5 }
                      ]
                    }
                  ]
                }
                """), "journal-1", "scopusJournal");
        assertEquals(PureMetricFetchOutcome.SUCCESS, result.outcome);
        assertEquals(3, result.observations.size());
        PureMetricObservation cite2024 = result.observations.stream()
                .filter(item -> "rating_citescore".equals(item.metricId) && Integer.valueOf(2024).equals(item.metricYear))
                .findFirst()
                .orElseThrow();
        assertEquals(new BigDecimal("4.1"), cite2024.decimalValue);
        assertEquals(PureMetricValueKind.DECIMAL, cite2024.valueKind);
    }

    static void scopusCitescoreMultipleCategoriesSameYear() throws Exception {
        PureMetricFetchResult result = PureMetricsParser.parse(MAPPER.readTree("""
                {
                  "uuid": "journal-1",
                  "collectionId": "scopusCitescore",
                  "items": [
                    {
                      "year": 2023,
                      "classification": { "uri": "asjc/2700", "term": { "en_US": "Medicine (all)" } },
                      "metricValues": [
                        { "metricId": "quartile", "integerValue": 2 },
                        { "metricId": "percentile", "integerValue": 74 },
                        { "metricId": "rank", "integerValue": 209 },
                        { "metricId": "category", "stringValue": "Medicine (all)" }
                      ]
                    },
                    {
                      "year": 2023,
                      "classification": { "uri": "asjc/1100", "term": { "en_US": "Agricultural" } },
                      "metricValues": [
                        { "metricId": "quartile", "integerValue": 1 },
                        { "metricId": "percentile", "integerValue": 90 },
                        { "metricId": "rank", "integerValue": 12 }
                      ]
                    }
                  ]
                }
                """), "journal-1", "scopusCitescore");
        List<PureMetricObservation> quartiles = result.observations.stream()
                .filter(item -> "quartile".equals(item.metricId))
                .toList();
        assertEquals(2, quartiles.size());
        assertTrue(quartiles.stream().anyMatch(item -> Long.valueOf(1L).equals(item.integerValue)));
        assertTrue(quartiles.stream().anyMatch(item -> Long.valueOf(2L).equals(item.integerValue)));
    }

    static void sciValFwciDecimal() throws Exception {
        PureMetricFetchResult result = PureMetricsParser.parse(MAPPER.readTree("""
                {
                  "uuid": "ro-1",
                  "collectionId": "sciVal",
                  "items": [{
                    "source": { "term": { "en_US": "SciVal" } },
                    "metricValues": [
                      { "metricId": "fwci", "decimalValue": 0.57 },
                      { "metricId": "authorcount", "integerValue": 4 },
                      { "metricId": "totalcitations", "integerValue": 12 }
                    ]
                  }]
                }
                """), "ro-1", "sciVal");
        PureMetricObservation fwci = result.observations.stream()
                .filter(item -> "fwci".equals(item.metricId))
                .findFirst()
                .orElseThrow();
        assertEquals(new BigDecimal("0.57"), fwci.decimalValue);
        assertEquals(PureMetricValueKind.DECIMAL, fwci.valueKind);
    }

    static void personHindexSourcesAndWindows() throws Exception {
        PureMetricFetchResult result = PureMetricsParser.parse(MAPPER.readTree("""
                {
                  "uuid": "person-1",
                  "collectionId": "hIndex",
                  "items": [
                    {
                      "source": { "uri": "scopus", "term": { "en_US": "Scopus h-index" } },
                      "classification": { "uri": "all-years", "term": { "en_US": "All years", "es_CL": "Todos los años" } },
                      "metricValues": [
                        { "metricId": "hindex", "integerValue": 12 },
                        { "metricId": "totalcitations", "integerValue": 450 }
                      ]
                    },
                    {
                      "source": { "term": { "en_US": "Scopus h-index" } },
                      "classification": { "term": { "en_US": "10 years" } },
                      "metricValues": [{ "metricId": "hindex", "integerValue": 10 }]
                    },
                    {
                      "source": { "term": { "en_US": "PlumX h-index" } },
                      "classification": { "term": { "en_US": "All years" } },
                      "metricValues": [{ "metricId": "hindex", "integerValue": 8 }]
                    },
                    {
                      "source": { "term": { "en_US": "SciVal h-index" } },
                      "classification": { "term": { "en_US": "5 years" } },
                      "metricValues": [{ "metricId": "hindex", "integerValue": 7 }]
                    }
                  ]
                }
                """), "person-1", "hIndex");
        assertEquals(5, result.observations.size());
        long scopusAll = result.observations.stream()
                .filter(item -> "hindex".equals(item.metricId)
                        && PureMetricsParser.looksLikeScopus(item.sourceUri, item.sourceTermEn, item.sourceTermEs)
                        && PureMetricsParser.looksLikeAllYears(item.categoryUri, item.categoryTermEn, item.categoryTermEs))
                .count();
        assertEquals(1L, scopusAll);
    }

    static void integerDecimalStringPreserved() throws Exception {
        PureMetricFetchResult result = PureMetricsParser.parse(MAPPER.readTree("""
                {
                  "uuid": "x",
                  "collectionId": "scopusCitescore",
                  "items": [{
                    "year": 2023,
                    "metricValues": [
                      { "metricId": "quartile", "integerValue": 2 },
                      { "metricId": "citescore", "decimalValue": 1.25 },
                      { "metricId": "category", "stringValue": "Medicine (all)" }
                    ]
                  }]
                }
                """), "x", "scopusCitescore");
        assertEquals(PureMetricValueKind.INTEGER, find(result, "quartile").valueKind);
        assertEquals(PureMetricValueKind.DECIMAL, find(result, "citescore").valueKind);
        assertEquals(PureMetricValueKind.STRING, find(result, "category").valueKind);
    }

    static void emptyCollection() throws Exception {
        PureMetricFetchResult result = PureMetricsParser.parse(MAPPER.readTree("""
                { "uuid": "x", "collectionId": "sciVal", "count": 0, "items": [] }
                """), "x", "sciVal");
        assertEquals(PureMetricFetchOutcome.EMPTY, result.outcome);
        assertTrue(result.replacesSnapshot());
        assertEquals(0, result.observations.size());
    }

    static void malformedResponse() throws Exception {
        PureMetricFetchResult result = PureMetricsParser.parse(MAPPER.readTree("""
                { "uuid": "x", "hello": "world" }
                """), "x", "sciVal");
        assertEquals(PureMetricFetchOutcome.INVALID, result.outcome);
        assertTrue(!result.replacesSnapshot());
    }

    static void zeroIsValue() throws Exception {
        PureMetricFetchResult result = PureMetricsParser.parse(MAPPER.readTree("""
                {
                  "uuid": "p",
                  "collectionId": "hIndex",
                  "items": [{ "metricValues": [{ "metricId": "hindex", "integerValue": 0 }] }]
                }
                """), "p", "hIndex");
        assertEquals(Long.valueOf(0L), find(result, "hindex").integerValue);
        assertTrue(find(result, "hindex").isZero());
    }

    static void ownerMismatchInvalid() throws Exception {
        PureMetricFetchResult result = PureMetricsParser.parse(MAPPER.readTree("""
                { "uuid": "other", "collectionId": "sciVal", "items": [] }
                """), "expected", "sciVal");
        assertEquals(PureMetricFetchOutcome.INVALID, result.outcome);
    }

    private static PureMetricObservation find(PureMetricFetchResult result, String metricId) {
        return result.observations.stream().filter(item -> metricId.equals(item.metricId)).findFirst().orElseThrow();
    }

    private static void assertEquals(Object expected, Object actual) {
        if (expected instanceof BigDecimal left && actual instanceof BigDecimal right) {
            if (left.compareTo(right) != 0) {
                throw new AssertionError(expected + " != " + actual);
            }
            return;
        }
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(expected + " != " + actual);
        }
    }

    private static void assertTrue(boolean value) {
        if (!value) {
            throw new AssertionError("expected true");
        }
    }
}
