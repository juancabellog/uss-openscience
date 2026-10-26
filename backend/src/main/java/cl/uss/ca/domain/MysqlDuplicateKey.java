package cl.uss.ca.domain;

import java.sql.SQLException;

/**
 * Identifies a specific MySQL duplicate-key (1062) without swallowing other
 * integrity violations.
 */
public final class MysqlDuplicateKey {
    public static final String OPENALEX_WORK_OA_ID = "uq_openalex_work_oa_id";
    public static final String OPENALEX_WORK_DOI = "uq_openalex_work_normalized_doi";

    private MysqlDuplicateKey() {
    }

    public static boolean of(Throwable error, String indexName) {
        if (indexName == null || indexName.isBlank()) {
            return false;
        }
        Throwable current = error;
        while (current != null) {
            if (current instanceof SQLException sql && sql.getErrorCode() == 1062) {
                String message = sql.getMessage();
                if (message != null && message.contains(indexName)) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
