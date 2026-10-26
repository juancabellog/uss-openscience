package cl.uss.ca.api;

import cl.uss.ca.api.dto.AuditEventDto;
import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.api.dto.SyncChangeDto;
import cl.uss.ca.api.dto.SyncErrorDto;
import cl.uss.ca.service.AuditService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/audit")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class AuditResource {

    @Inject
    AuditService auditService;

    @GET
    public PageResponse<AuditEventDto> listRoot(
            @QueryParam("search") String search,
            @QueryParam("from") String from,
            @QueryParam("to") String to,
            @QueryParam("eventType") String eventType,
            @QueryParam("source") String source,
            @QueryParam("entity") String entity,
            @QueryParam("status") String status,
            @QueryParam("syncMode") String syncMode,
            @QueryParam("actor") String actor,
            @QueryParam("sort") String sort,
            @QueryParam("direction") String direction,
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("size") @DefaultValue("25") int size
    ) {
        return auditService.list(
                search, from, to, eventType, source, entity, status, syncMode, actor, sort, direction, page, size
        );
    }

    @GET
    @Path("/events")
    public PageResponse<AuditEventDto> list(
            @QueryParam("search") String search,
            @QueryParam("from") String from,
            @QueryParam("to") String to,
            @QueryParam("eventType") String eventType,
            @QueryParam("source") String source,
            @QueryParam("entity") String entity,
            @QueryParam("status") String status,
            @QueryParam("syncMode") String syncMode,
            @QueryParam("actor") String actor,
            @QueryParam("sort") String sort,
            @QueryParam("direction") String direction,
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("size") @DefaultValue("25") int size
    ) {
        return listRoot(search, from, to, eventType, source, entity, status, syncMode, actor, sort, direction, page, size);
    }

    @GET
    @Path("/events/{id}")
    public AuditEventDto get(@PathParam("id") String id) {
        return auditService.getById(id);
    }

    @GET
    @Path("/events/{id}/changes")
    public PageResponse<SyncChangeDto> changes(
            @PathParam("id") String id,
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("size") @DefaultValue("25") int size
    ) {
        return auditService.changes(id, page, size);
    }

    @GET
    @Path("/events/{id}/errors")
    public PageResponse<SyncErrorDto> errors(
            @PathParam("id") String id,
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("size") @DefaultValue("25") int size
    ) {
        return auditService.errors(id, page, size);
    }
}
