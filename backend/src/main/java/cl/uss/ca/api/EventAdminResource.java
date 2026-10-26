package cl.uss.ca.api;

import cl.uss.ca.api.dto.EventAdminDto;
import cl.uss.ca.api.dto.EventAdminListDto;
import cl.uss.ca.api.dto.EventAdminRegistrationDto;
import cl.uss.ca.api.dto.EventArtifactDto;
import cl.uss.ca.api.dto.EventAttendanceDto;
import cl.uss.ca.api.dto.EventAttendanceImportDto;
import cl.uss.ca.api.dto.EventAttendanceImportRequestDto;
import cl.uss.ca.api.dto.EventAttendanceWriteDto;
import cl.uss.ca.api.dto.EventDocumentDto;
import cl.uss.ca.api.dto.EventIndicatorsDto;
import cl.uss.ca.api.dto.EventRegistrationListDto;
import cl.uss.ca.api.dto.EventRegistrationReceiptDto;
import cl.uss.ca.api.dto.EventOrganizingUnitDto;
import cl.uss.ca.api.dto.EventWriteDto;
import cl.uss.ca.api.dto.EventsModuleStatusDto;
import cl.uss.ca.repository.EventOpsRecord;
import cl.uss.ca.security.CurrentUserService;
import cl.uss.ca.service.EventOpsService;
import cl.uss.ca.service.EventRegistrationService;
import cl.uss.ca.service.EventService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/events/admin")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EventAdminResource {

    @Inject
    EventService eventService;

    @Inject
    CurrentUserService currentUserService;

    @Inject
    EventRegistrationService registrationService;

    @Inject
    EventOpsService opsService;

    @GET
    @Path("/status")
    public EventsModuleStatusDto status() {
        return eventService.status();
    }

    @GET
    public EventAdminListDto list(
            @QueryParam("status") String status,
            @QueryParam("type") String type,
            @QueryParam("q") String query
    ) {
        return eventService.listAdmin(status, type, query);
    }

    @POST
    public EventAdminDto create(EventWriteDto body) {
        return eventService.create(body, currentUserService.current());
    }

    @GET
    @Path("/campuses")
    public List<String> campuses() {
        return eventService.campusSuggestions();
    }

    @GET
    @Path("/organizing-units")
    public List<EventOrganizingUnitDto> organizingUnits() {
        return eventService.organizingUnits();
    }

    @GET
    @Path("/{id}")
    public EventAdminDto get(@PathParam("id") long id) {
        return eventService.getAdmin(id);
    }

    @PUT
    @Path("/{id}")
    public EventAdminDto update(@PathParam("id") long id, EventWriteDto body) {
        return eventService.update(id, body);
    }

    @POST
    @Path("/{id}/publish")
    public EventAdminDto publish(@PathParam("id") long id) {
        return eventService.publish(id);
    }

    @POST
    @Path("/{id}/unpublish")
    public EventAdminDto unpublish(@PathParam("id") long id) {
        return eventService.unpublish(id);
    }

    @GET
    @Path("/{id}/registrations")
    public EventRegistrationListDto registrations(@PathParam("id") long id) {
        return eventService.registrations(id);
    }

    @POST
    @Path("/{id}/finish")
    public EventAdminDto finish(@PathParam("id") long id) {
        return eventService.finish(id, currentUserService.current());
    }

    @POST
    @Path("/{id}/cancel")
    public EventAdminDto cancel(@PathParam("id") long id) {
        return eventService.cancel(id, currentUserService.current());
    }

    @POST
    @Path("/{id}/registrations")
    public EventRegistrationReceiptDto addRegistration(@PathParam("id") long id, EventAdminRegistrationDto body) {
        return registrationService.adminRegister(id, body, currentUserService.current());
    }

    @POST
    @Path("/{id}/minutes")
    public EventArtifactDto generateMinutes(
            @PathParam("id") long id,
            @QueryParam("blankRows") Integer blankRows,
            @QueryParam("language") String language
    ) {
        return opsService.generateMinutes(id, blankRows, language, currentUserService.current());
    }

    @GET
    @Path("/{id}/minutes")
    public List<EventArtifactDto> minutes(@PathParam("id") long id) {
        return opsService.listMinutes(id);
    }

    @GET
    @Path("/{id}/minutes/{artifactId}")
    public Response downloadMinutes(@PathParam("id") long id, @PathParam("artifactId") long artifactId) {
        EventOpsRecord.Artifact artifact = opsService.minutesMeta(id, artifactId);
        return file(opsService.minutesBytes(id, artifactId), artifact.originalFilename, "application/pdf");
    }

    @GET
    @Path("/{id}/documents")
    public List<EventDocumentDto> documents(@PathParam("id") long id) {
        return opsService.listDocuments(id);
    }

    @POST
    @Path("/{id}/documents")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public EventDocumentDto uploadDocument(
            @PathParam("id") long id,
            byte[] body,
            @HeaderParam("X-Filename") String filename,
            @HeaderParam("X-Document-Kind") String kind,
            @HeaderParam("X-Document-Note") String note
    ) {
        return opsService.uploadDocument(id, body, filename, kind, note, currentUserService.current());
    }

    @GET
    @Path("/{id}/documents/{documentId}")
    public Response downloadDocument(@PathParam("id") long id, @PathParam("documentId") long documentId) {
        EventOpsRecord.Document document = opsService.documentMeta(id, documentId);
        return file(opsService.documentBytes(id, documentId), document.originalFilename, document.contentType);
    }

    @GET
    @Path("/{id}/attendance")
    public List<EventAttendanceDto> attendance(@PathParam("id") long id) {
        return opsService.listAttendance(id);
    }

    @POST
    @Path("/{id}/attendance")
    public EventAttendanceDto setAttendance(@PathParam("id") long id, EventAttendanceWriteDto body) {
        return opsService.setAttendance(id, body, currentUserService.current());
    }

    @POST
    @Path("/{id}/attendance/walk-in")
    public EventRegistrationReceiptDto walkIn(@PathParam("id") long id, EventAdminRegistrationDto body) {
        return opsService.walkIn(id, body, currentUserService.current());
    }

    @POST
    @Path("/{id}/attendance/imports")
    public EventAttendanceImportDto previewImport(@PathParam("id") long id, EventAttendanceImportRequestDto body) {
        return opsService.previewImport(id, body, currentUserService.current());
    }

    @POST
    @Path("/{id}/attendance/imports/file")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public EventAttendanceImportDto previewFile(
            @PathParam("id") long id,
            byte[] body,
            @HeaderParam("X-Filename") String filename,
            @QueryParam("artifactVersion") Integer artifactVersion
    ) {
        return opsService.previewFile(id, body, filename, artifactVersion, currentUserService.current());
    }

    @POST
    @Path("/{id}/attendance/imports/{importId}/confirm")
    public EventAttendanceImportDto confirmImport(@PathParam("id") long id, @PathParam("importId") long importId) {
        return opsService.confirmImport(id, importId, currentUserService.current());
    }

    @POST
    @Path("/{id}/close-documentation")
    public EventAdminDto closeDocumentation(@PathParam("id") long id) {
        opsService.closeDocumentation(id, currentUserService.current());
        return eventService.getAdmin(id);
    }

    @GET
    @Path("/indicators")
    public EventIndicatorsDto indicators(@QueryParam("from") String from, @QueryParam("to") String to) {
        return opsService.indicators(from, to);
    }

    private static Response file(byte[] bytes, String filename, String type) {
        String safe = filename == null ? "archivo" : filename.replace("\"", "");
        return Response.ok(bytes)
                .type(type == null ? MediaType.APPLICATION_OCTET_STREAM : type)
                .header("Content-Disposition", "attachment; filename=\"" + safe + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .build();
    }
}
