package cl.uss.ca.api;

import cl.uss.ca.api.dto.OrcidCandidateSelectDto;
import cl.uss.ca.api.dto.OrcidDiscoveryApplyRequestDto;
import cl.uss.ca.api.dto.OrcidDiscoveryApplyResultDto;
import cl.uss.ca.api.dto.OrcidDiscoveryExecutionDto;
import cl.uss.ca.api.dto.OrcidDiscoveryPersonDto;
import cl.uss.ca.api.dto.OrcidDiscoverySelectedItemDto;
import cl.uss.ca.api.dto.OrcidDiscoveryStatsDto;
import cl.uss.ca.api.dto.OrcidStatsDto;
import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.api.dto.PureCorrectionResultDto;
import cl.uss.ca.service.OrcidDiscoveryService;
import cl.uss.ca.service.OrcidSyncService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/orcid")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrcidResource {

    @Inject
    OrcidSyncService orcidSyncService;

    @Inject
    OrcidDiscoveryService discoveryService;

    @GET
    @Path("/stats")
    public OrcidStatsDto stats() {
        return orcidSyncService.stats();
    }

    @GET
    @Path("/discovery/stats")
    public OrcidDiscoveryStatsDto discoveryStats() {
        return discoveryService.stats();
    }

    @GET
    @Path("/discovery/latest")
    public Response latestDiscovery() {
        OrcidDiscoveryExecutionDto dto = discoveryService.latest();
        return dto == null ? Response.noContent().build() : Response.ok(dto).build();
    }

    @GET
    @Path("/discovery/history")
    public List<OrcidDiscoveryExecutionDto> discoveryHistory() {
        return discoveryService.history();
    }

    @POST
    @Path("/discovery")
    public Response startDiscovery() {
        return Response.accepted(discoveryService.start()).build();
    }

    @GET
    @Path("/discovery/{id}")
    public OrcidDiscoveryExecutionDto discovery(@PathParam("id") long id) {
        return discoveryService.get(id);
    }

    @GET
    @Path("/discovery/{id}/people")
    public PageResponse<OrcidDiscoveryPersonDto> discoveryPeople(
            @PathParam("id") long id,
            @QueryParam("status") String status,
            @QueryParam("cargo") String cargo,
            @QueryParam("search") String search,
            @QueryParam("sort") String sort,
            @QueryParam("direction") String direction,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("25") int size
    ) {
        return discoveryService.people(id, status, cargo, search, sort, direction, page, size);
    }

    @GET
    @Path("/discovery/{id}/cargos")
    public List<String> discoveryCargos(@PathParam("id") long id) {
        return discoveryService.cargos(id);
    }

    @GET
    @Path("/discovery/{id}/people/{personId}")
    public OrcidDiscoveryPersonDto discoveryPerson(
            @PathParam("id") long id,
            @PathParam("personId") long personId
    ) {
        return discoveryService.person(id, personId);
    }

    @POST
    @Path("/discovery/{id}/people/{personId}/select")
    public Response selectCandidate(
            @PathParam("id") long id,
            @PathParam("personId") long personId,
            OrcidCandidateSelectDto body
    ) {
        discoveryService.select(id, personId, body == null ? null : body.candidateId, body == null ? null : body.queued);
        return Response.ok(discoveryService.person(id, personId)).build();
    }

    @GET
    @Path("/discovery/{id}/selected")
    public List<OrcidDiscoverySelectedItemDto> queuedForApply(@PathParam("id") long id) {
        return discoveryService.queued(id);
    }

    @POST
    @Path("/discovery/{id}/select-strong")
    public Response selectStrong(@PathParam("id") long id) {
        int updated = discoveryService.selectStrong(id);
        return Response.ok(java.util.Map.of("updated", updated, "execution", discoveryService.get(id))).build();
    }

    @POST
    @Path("/discovery/{id}/recalculate")
    public OrcidDiscoveryExecutionDto recalculate(@PathParam("id") long id) {
        return discoveryService.recalculate(id);
    }

    @POST
    @Path("/discovery/{id}/apply")
    public OrcidDiscoveryApplyResultDto apply(@PathParam("id") long id, OrcidDiscoveryApplyRequestDto body) {
        return discoveryService.apply(id, body == null ? null : body.personIds);
    }

    @POST
    @Path("/discovery/{id}/people/{personId}/reconcile-local")
    public PureCorrectionResultDto reconcileLocal(
            @PathParam("id") long id,
            @PathParam("personId") long personId
    ) {
        return discoveryService.reconcileLocal(id, personId);
    }
}
