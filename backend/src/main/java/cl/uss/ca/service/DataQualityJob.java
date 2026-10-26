package cl.uss.ca.service;

import cl.uss.ca.exception.ErrorLog;
import cl.uss.ca.exception.ExceptionDetail;
import cl.uss.ca.quality.DataQualityScanEngine;
import cl.uss.ca.repository.DataQualityRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.logging.Logger;

@ApplicationScoped
public class DataQualityJob {
    private static final Logger LOG = Logger.getLogger(DataQualityJob.class.getName());

    @Inject
    DataQualityRepository repository;

    @Inject
    DataQualityScanEngine scanEngine;

    public void run(long executionId, String entityType, Long localRecordId) {
        run(executionId, entityType, localRecordId, null);
    }

    public void run(long executionId, String entityType, Long localRecordId, String onlyRule) {
        try {
            DataQualityScanEngine.ScanResult result = scanEngine.run(executionId, entityType, localRecordId, onlyRule);
            boolean failed = result.successfulRules == 0 && result.errors > 0;
            repository.finish(
                    executionId,
                    failed ? "FAILED" : "COMPLETED",
                    result.evaluated,
                    result.findings,
                    result.resolved,
                    result.errors,
                    result.errors > 0 ? "DATA_QUALITY_RULE_FAILED" : null,
                    clip(result.failureDetail())
            );
            LOG.info("data-quality execution=" + executionId
                    + (onlyRule == null || onlyRule.isBlank() ? "" : " rule=" + onlyRule)
                    + " status=" + (failed ? "FAILED" : "COMPLETED")
                    + " findings=" + result.findings
                    + " resolved=" + result.resolved
                    + " evaluated=" + result.evaluated
                    + " errors=" + result.errors);
        } catch (Exception ex) {
            String detail = ExceptionDetail.describe(ex);
            ErrorLog.unexpected(LOG, "data-quality execution=" + executionId + " failed", ex);
            try {
                repository.finish(executionId, "FAILED", 0, 0, 0, 1, "DATA_QUALITY_FAILED", clip(detail));
            } catch (Exception persistEx) {
                ErrorLog.unexpected(LOG, "data-quality execution=" + executionId
                        + " failed to persist failure", persistEx);
            }
        }
    }

    private static String clip(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
