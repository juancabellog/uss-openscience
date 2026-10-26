package cl.uss.ca.repository;

import cl.uss.ca.external.pure.metrics.PureMetricCatalog;

/**
 * Correlated/latest SQL fragments for list endpoints. No N+1 from Angular.
 */
public final class PureMetricSql {
    private PureMetricSql() {
    }

    public static String journalCiteScoreJoin(String metricTable) {
        return """
                LEFT JOIN (
                    SELECT m.journal_id,
                           COALESCE(m.decimal_value, m.integer_value) AS citescore,
                           m.metric_year AS citescore_year
                    FROM %s m
                    INNER JOIN (
                        SELECT journal_id, MAX(metric_year) AS max_year
                        FROM %s
                        WHERE collection_id = '%s' AND metric_id = '%s' AND metric_year IS NOT NULL
                        GROUP BY journal_id
                    ) latest ON latest.journal_id = m.journal_id AND latest.max_year = m.metric_year
                    WHERE m.collection_id = '%s' AND m.metric_id = '%s'
                ) cs ON cs.journal_id = j.id
                """.formatted(
                metricTable, metricTable,
                PureMetricCatalog.COLLECTION_SCOPUS_JOURNAL, PureMetricCatalog.METRIC_CITESCORE,
                PureMetricCatalog.COLLECTION_SCOPUS_JOURNAL, PureMetricCatalog.METRIC_CITESCORE
        );
    }

    public static String journalQuartileJoin(String metricTable) {
        return """
                LEFT JOIN (
                    SELECT q.journal_id,
                           q.metric_year AS quartile_year,
                           GROUP_CONCAT(DISTINCT CONCAT('Q', q.integer_value) ORDER BY q.integer_value SEPARATOR ' · ') AS quartiles_compact,
                           COUNT(*) AS quartile_count
                    FROM %s q
                    INNER JOIN (
                        SELECT journal_id, MAX(metric_year) AS max_year
                        FROM %s
                        WHERE collection_id = '%s' AND metric_id = '%s' AND integer_value IS NOT NULL
                        GROUP BY journal_id
                    ) ly ON ly.journal_id = q.journal_id AND ly.max_year = q.metric_year
                    WHERE q.collection_id = '%s' AND q.metric_id = '%s'
                    GROUP BY q.journal_id, q.metric_year
                ) qs ON qs.journal_id = j.id
                """.formatted(
                metricTable, metricTable,
                PureMetricCatalog.COLLECTION_SCOPUS_CITESCORE, PureMetricCatalog.METRIC_QUARTILE,
                PureMetricCatalog.COLLECTION_SCOPUS_CITESCORE, PureMetricCatalog.METRIC_QUARTILE
        );
    }

    public static String fwciJoin(String metricTable) {
        return """
                LEFT JOIN (
                    SELECT m.research_output_id,
                           COALESCE(m.decimal_value, m.integer_value) AS fwci,
                           m.metric_year AS fwci_year
                    FROM %s m
                    INNER JOIN (
                        SELECT research_output_id, MAX(COALESCE(metric_year, -1)) AS max_year
                        FROM %s
                        WHERE collection_id = '%s' AND metric_id = '%s'
                        GROUP BY research_output_id
                    ) latest ON latest.research_output_id = m.research_output_id
                            AND COALESCE(m.metric_year, -1) = latest.max_year
                    WHERE m.collection_id = '%s' AND m.metric_id = '%s'
                ) fw ON fw.research_output_id = o.id
                """.formatted(
                metricTable, metricTable,
                PureMetricCatalog.COLLECTION_SCIVAL, PureMetricCatalog.METRIC_FWCI,
                PureMetricCatalog.COLLECTION_SCIVAL, PureMetricCatalog.METRIC_FWCI
        );
    }

    public static String scopusHindexSelect(String metricTable, String personAlias) {
        return """
                (SELECT m.integer_value FROM %s m
                 WHERE m.person_id = %s.id
                   AND m.collection_id = '%s'
                   AND LOWER(m.metric_id) = '%s'
                   AND (
                        LOWER(COALESCE(m.source_term_en, '')) LIKE '%%scopus%%'
                        OR LOWER(COALESCE(m.source_uri, '')) LIKE '%%scopus%%'
                   )
                   AND (
                        LOWER(COALESCE(m.category_term_en, '')) LIKE '%%all year%%'
                        OR LOWER(COALESCE(m.category_term_es, '')) LIKE '%%todos los a%%'
                        OR LOWER(COALESCE(m.category_uri, '')) LIKE '%%all-years%%'
                        OR LOWER(COALESCE(m.category_uri, '')) LIKE '%%allyears%%'
                   )
                   AND LOWER(COALESCE(m.category_term_en, '')) NOT LIKE '%%10 year%%'
                   AND LOWER(COALESCE(m.category_term_en, '')) NOT LIKE '%%5 year%%'
                 ORDER BY m.id LIMIT 1)
                """.formatted(
                metricTable,
                personAlias,
                PureMetricCatalog.COLLECTION_HINDEX,
                PureMetricCatalog.METRIC_HINDEX
        );
    }

    public static String scopusCitationsSelect(String metricTable, String personAlias) {
        return """
                (SELECT COALESCE(m.integer_value, m.decimal_value) FROM %s m
                 WHERE m.person_id = %s.id
                   AND m.collection_id = '%s'
                   AND LOWER(m.metric_id) = '%s'
                   AND (
                        LOWER(COALESCE(m.source_term_en, '')) LIKE '%%scopus%%'
                        OR LOWER(COALESCE(m.source_uri, '')) LIKE '%%scopus%%'
                   )
                   AND (
                        LOWER(COALESCE(m.category_term_en, '')) LIKE '%%all year%%'
                        OR LOWER(COALESCE(m.category_term_es, '')) LIKE '%%todos los a%%'
                        OR LOWER(COALESCE(m.category_uri, '')) LIKE '%%all-years%%'
                        OR LOWER(COALESCE(m.category_uri, '')) LIKE '%%allyears%%'
                   )
                   AND LOWER(COALESCE(m.category_term_en, '')) NOT LIKE '%%10 year%%'
                   AND LOWER(COALESCE(m.category_term_en, '')) NOT LIKE '%%5 year%%'
                 ORDER BY m.id LIMIT 1)
                """.formatted(
                metricTable,
                personAlias,
                PureMetricCatalog.COLLECTION_HINDEX,
                PureMetricCatalog.METRIC_TOTAL_CITATIONS
        );
    }

    public static String latestQuartileExists(String metricTable, String journalAlias, int quartile) {
        return """
                EXISTS (
                    SELECT 1 FROM %s q
                    INNER JOIN (
                        SELECT journal_id, MAX(metric_year) AS max_year
                        FROM %s
                        WHERE collection_id = '%s' AND metric_id = '%s' AND integer_value IS NOT NULL
                        GROUP BY journal_id
                    ) ly ON ly.journal_id = q.journal_id AND ly.max_year = q.metric_year
                    WHERE q.journal_id = %s.id
                      AND q.collection_id = '%s'
                      AND q.metric_id = '%s'
                      AND q.integer_value = %d
                )
                """.formatted(
                metricTable, metricTable,
                PureMetricCatalog.COLLECTION_SCOPUS_CITESCORE, PureMetricCatalog.METRIC_QUARTILE,
                journalAlias,
                PureMetricCatalog.COLLECTION_SCOPUS_CITESCORE, PureMetricCatalog.METRIC_QUARTILE,
                quartile
        );
    }
}
