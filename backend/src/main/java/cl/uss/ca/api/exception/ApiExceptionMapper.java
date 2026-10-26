package cl.uss.ca.api.exception;

import cl.uss.ca.api.dto.ApiErrorDto;
import cl.uss.ca.exception.ConflictException;
import cl.uss.ca.exception.ErrorLog;
import cl.uss.ca.exception.DatabaseException;
import cl.uss.ca.exception.ForbiddenException;
import cl.uss.ca.exception.NotFoundException;
import cl.uss.ca.exception.UnauthorizedException;
import cl.uss.ca.exception.ValidationException;
import cl.uss.ca.external.pure.PureClientException;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.logging.Level;
import java.util.logging.Logger;

@Provider
public class ApiExceptionMapper implements ExceptionMapper<Exception> {
    private static final Logger LOG = Logger.getLogger(ApiExceptionMapper.class.getName());

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(Exception exception) {
        if (exception instanceof WebApplicationException web) {
            Response response = web.getResponse();
            return response != null ? response : error(Response.Status.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected error");
        }
        if (exception instanceof NotFoundException notFound) {
            return error(Response.Status.NOT_FOUND, notFound.getCode(), notFound.getMessage());
        }
        if (exception instanceof UnauthorizedException unauthorized) {
            return error(Response.Status.UNAUTHORIZED, unauthorized.getCode(), unauthorized.getMessage());
        }
        if (exception instanceof ForbiddenException forbidden) {
            return error(Response.Status.FORBIDDEN, forbidden.getCode(), forbidden.getMessage());
        }
        if (exception instanceof ValidationException validation) {
            LOG.log(Level.WARNING, "Validation error: " + validation.getCode() + " " + validation.getMessage());
            int status = switch (validation.getCode()) {
                case "EXPORT_EMPTY" -> 422;
                case "EVENT_RATE_LIMITED" -> 429;
                case "SYNC_FORBIDDEN", "CORRECTION_FORBIDDEN", "LAST_ADMIN", "AUTH_FORBIDDEN" -> 403;
                default -> 400;
            };
            return error(status, validation.getCode(), validation.getMessage());
        }
        if (exception instanceof PureClientException pure) {
            if (pure.getStatusCode() == 409 || pure.getStatusCode() == 412) {
                return error(Response.Status.CONFLICT, "PURE_RECORD_CHANGED",
                        "The PURE record changed before the update could be applied.");
            }
            ErrorLog.unexpected(LOG, "PURE remote error", pure);
            return error(Response.Status.BAD_GATEWAY, "PURE_UPDATE_FAILED", "PURE could not be updated.");
        }
        if (exception instanceof ConflictException conflict) {
            return conflict(conflict);
        }
        if (exception instanceof ConstraintViolationException) {
            return error(Response.Status.BAD_REQUEST, "VALIDATION_ERROR", "Invalid request parameters");
        }
        if (exception instanceof DatabaseException) {
            ErrorLog.unexpected(LOG, "Database error", exception);
            return error(Response.Status.INTERNAL_SERVER_ERROR, "DATABASE_ERROR", "Database operation failed");
        }
        ErrorLog.unexpected(LOG, "Unhandled error", exception);
        return error(Response.Status.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected error");
    }

    private Response conflict(ConflictException conflict) {
        ApiErrorDto body = baseError(Response.Status.CONFLICT.getStatusCode(), conflict.getCode(), conflict.getMessage());
        body.entity = conflict.getEntity();
        body.activeSyncId = conflict.getActiveSyncId();
        body.startedAt = conflict.getStartedAt();
        body.startedBy = conflict.getStartedBy();
        return Response.status(Response.Status.CONFLICT).entity(body).build();
    }

    private Response error(Response.Status status, String code, String message) {
        return error(status.getStatusCode(), code, message);
    }

    private Response error(int status, String code, String message) {
        return Response.status(status).entity(baseError(status, code, message)).build();
    }

    private ApiErrorDto baseError(int status, String code, String message) {
        ApiErrorDto body = new ApiErrorDto();
        body.timestamp = OffsetDateTime.now(ZoneOffset.UTC);
        body.status = status;
        body.code = code;
        body.message = message;
        body.path = uriInfo == null ? null : uriInfo.getRequestUri().getPath();
        return body;
    }

}
