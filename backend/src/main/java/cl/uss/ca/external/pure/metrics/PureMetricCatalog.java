package cl.uss.ca.external.pure.metrics;

/**
 * PURE metric collection and metricId constants. UI labels live in i18n, not here.
 */
public final class PureMetricCatalog {
    public static final String COLLECTION_SCOPUS_JOURNAL = "scopusJournal";
    public static final String COLLECTION_SCOPUS_CITESCORE = "scopusCitescore";
    public static final String COLLECTION_SCIVAL = "sciVal";
    public static final String COLLECTION_HINDEX = "hIndex";

    public static final String METRIC_CITESCORE = "rating_citescore";
    public static final String METRIC_QUARTILE = "quartile";
    public static final String METRIC_PERCENTILE = "percentile";
    public static final String METRIC_RANK = "rank";
    public static final String METRIC_CATEGORY = "category";
    public static final String METRIC_FWCI = "fwci";
    public static final String METRIC_HINDEX = "hindex";
    public static final String METRIC_TOTAL_CITATIONS = "totalcitations";

    public static final String RESOURCE_JOURNALS = "journals";
    public static final String RESOURCE_RESEARCH_OUTPUTS = "research-outputs";
    public static final String RESOURCE_PERSONS = "persons";

    private PureMetricCatalog() {
    }
}
