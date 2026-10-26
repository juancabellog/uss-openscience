package cl.uss.ca.exception;

import cl.uss.ca.api.dto.ApiErrorDto;
import cl.uss.ca.api.exception.ApiExceptionMapper;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import jakarta.ws.rs.core.Response;

/**
 * Error logging policy. Not executed by the agent.
 */
public final class ErrorLogPolicyTest {
    private ErrorLogPolicyTest() {
    }

    public static void main(String[] args) {
        databaseExceptionKeepsCause();
        validationExceptionKeepsCause();
        errorLogPassesThrowableWithoutStringifyingStack();
        errorLogRedactsSecretsInContext();
        warningRetryHasNoThrown();
        restDoesNotReturnStack();
        exceptionDetailIsForPersistenceNotLogMessage();
        System.out.println("ErrorLogPolicyTest OK");
    }

    static void databaseExceptionKeepsCause() {
        SQLException sql = new SQLException(
                "Data truncation: Data too long for column 'suggested_value' at row 1",
                "22001",
                1406
        );
        DatabaseException wrapped = new DatabaseException("Failed to upsert data-quality finding", sql);
        assert wrapped.getCause() == sql;
        assert wrapped.getMessage().contains("upsert data-quality finding");
    }

    static void validationExceptionKeepsCause() {
        Exception io = new Exception("timeout");
        ValidationException wrapped = new ValidationException("ORCID_TEMPORARY_ERROR", "ORCID request failed", io);
        assert wrapped.getCause() == io;
    }

    static void errorLogPassesThrowableWithoutStringifyingStack() {
        Logger logger = Logger.getLogger("cl.uss.ca.exception.ErrorLogPolicyTest");
        CapturingHandler handler = new CapturingHandler();
        logger.addHandler(handler);
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.SEVERE);
        DatabaseException error = new DatabaseException(
                "Failed to upsert data-quality finding",
                new SQLException("Data too long for column 'suggested_value' at row 1", "22001", 1406)
        );
        ErrorLog.unexpected(logger, "data-quality execution=4 rule=CROSSREF-007 failed", error);
        assert handler.records.size() == 1;
        LogRecord record = handler.records.get(0);
        assert record.getLevel() == Level.SEVERE;
        assert "data-quality execution=4 rule=CROSSREF-007 failed".equals(record.getMessage());
        assert !record.getMessage().contains("caused by");
        assert !record.getMessage().contains("at cl.uss");
        assert record.getThrown() == error;
        assert record.getThrown().getCause() instanceof SQLException;
        logger.removeHandler(handler);
    }

    static void errorLogRedactsSecretsInContext() {
        Logger logger = Logger.getLogger("cl.uss.ca.exception.ErrorLogPolicyTest.secrets");
        CapturingHandler handler = new CapturingHandler();
        logger.addHandler(handler);
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.SEVERE);
        ErrorLog.unexpected(logger, "url=https://x?api_key=supersecret token=abc", new RuntimeException("x"));
        assert handler.records.get(0).getMessage().contains("[REDACTED]");
        assert !handler.records.get(0).getMessage().contains("supersecret");
        logger.removeHandler(handler);
    }

    static void warningRetryHasNoThrown() {
        Logger logger = Logger.getLogger("cl.uss.ca.exception.ErrorLogPolicyTest.retry");
        CapturingHandler handler = new CapturingHandler();
        logger.addHandler(handler);
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.WARNING);
        logger.warning("OpenAlex key=10.1000/xyz endpoint=works status=504");
        logger.warning("OpenAlex key=10.1000/xyz retry after HTTP 504 in 500 ms");
        assert handler.records.size() == 2;
        for (LogRecord record : handler.records) {
            assert record.getLevel() == Level.WARNING;
            assert record.getThrown() == null;
        }
        logger.removeHandler(handler);
    }

    static void restDoesNotReturnStack() {
        ApiExceptionMapper mapper = new ApiExceptionMapper();
        DatabaseException error = new DatabaseException(
                "Failed to upsert data-quality finding",
                new SQLException("Data too long for column 'suggested_value' at row 1")
        );
        Response response = mapper.toResponse(error);
        assert response.getStatus() == 500;
        ApiErrorDto body = (ApiErrorDto) response.getEntity();
        assert "DATABASE_ERROR".equals(body.code);
        assert body.message != null;
        assert !body.message.contains("at cl.uss");
        assert !body.message.contains("suggested_value");
        assert !String.valueOf(body.message).contains("Caused by");
    }

    static void exceptionDetailIsForPersistenceNotLogMessage() {
        DatabaseException error = new DatabaseException(
                "Failed to upsert data-quality finding",
                new SQLException("Data too long for column 'suggested_value' at row 1", "22001", 1406)
        );
        String described = ExceptionDetail.describe(error);
        assert described.contains("caused by");
        assert described.contains("suggested_value");
        assert !described.contains("at cl.uss.ca");
    }

    private static final class CapturingHandler extends Handler {
        final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
