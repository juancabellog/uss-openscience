package cl.uss.ca.repository;

public record PureMetricTables(String ownerTable, String ownerColumn, String metricTable, String fetchTable) {
    public static final PureMetricTables JOURNAL = new PureMetricTables(
            "pure_journal",
            "journal_id",
            "pure_journal_metric",
            "pure_journal_metric_fetch"
    );
    public static final PureMetricTables RESEARCH_OUTPUT = new PureMetricTables(
            "pure_research_output",
            "research_output_id",
            "pure_research_output_metric",
            "pure_research_output_metric_fetch"
    );
    public static final PureMetricTables PERSON = new PureMetricTables(
            "pure_person",
            "person_id",
            "pure_person_metric",
            "pure_person_metric_fetch"
    );
}
