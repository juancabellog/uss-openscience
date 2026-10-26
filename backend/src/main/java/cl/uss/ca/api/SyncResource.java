package cl.uss.ca.api;

import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.api.dto.SyncChangeGroupDto;
import cl.uss.ca.api.dto.SyncErrorPageDto;
import cl.uss.ca.api.dto.SyncModePolicyDto;
import cl.uss.ca.api.dto.OpenAlexAuthorRecoveryDto;
import cl.uss.ca.api.dto.SyncRunDto;
import cl.uss.ca.service.PureSyncService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/sync")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class SyncResource {

    @Inject
    PureSyncService pureSyncService;

    @POST
    @Path("/pure/people")
    public Response startPeople(@QueryParam("mode") String mode) {
        return Response.accepted(pureSyncService.startPeople(mode)).build();
    }

    @POST
    @Path("/pure/research-output")
    public Response startResearchOutputs(@QueryParam("mode") String mode) {
        return Response.accepted(pureSyncService.startResearchOutputs(mode)).build();
    }

    @POST
    @Path("/pure/research-outputs")
    public Response startResearchOutputsAlias() {
        return startResearchOutputs(null);
    }

    @POST
    @Path("/pure/journals")
    public Response startJournals(@QueryParam("mode") String mode) {
        return Response.accepted(pureSyncService.startJournals(mode)).build();
    }

    @POST
    @Path("/pure/projects")
    public Response startProjects(@QueryParam("mode") String mode) {
        return Response.accepted(pureSyncService.startProjects(mode)).build();
    }

    @POST
    @Path("/pure/organizations")
    public Response startOrganizations(@QueryParam("mode") String mode) {
        return Response.accepted(pureSyncService.startOrganizations(mode)).build();
    }

    @POST
    @Path("/orcid")
    public Response startOrcid(@QueryParam("mode") String mode) {
        return Response.accepted(pureSyncService.startOrcid(mode)).build();
    }

    @POST
    @Path("/unpaywall")
    public Response startUnpaywall(@QueryParam("mode") String mode) {
        return Response.accepted(pureSyncService.startUnpaywall(mode)).build();
    }

    @POST
    @Path("/crossref")
    public Response startCrossref(@QueryParam("mode") String mode) {
        return Response.accepted(pureSyncService.startCrossref(mode)).build();
    }

    @POST
    @Path("/openalex")
    public Response startOpenAlex(@QueryParam("mode") String mode) {
        return Response.accepted(pureSyncService.startOpenAlex(mode)).build();
    }

    @POST
    @Path("/openalex/{sourceSyncExecutionId}/recover-authors")
    public Response recoverOpenAlexAuthors(@PathParam("sourceSyncExecutionId") String sourceSyncExecutionId) {
        OpenAlexAuthorRecoveryDto dto = pureSyncService.recoverOpenAlexAuthors(sourceSyncExecutionId);
        return Response.accepted(dto).build();
    }

    @POST
    @Path("/dspace")
    public Response startDspace(@QueryParam("mode") String mode) {
        return Response.accepted(pureSyncService.startDspace(mode)).build();
    }

    @POST
    @Path("/dspace/bitstreams")
    public Response startDspaceBitstreamBackfill() {
        return Response.accepted(pureSyncService.startDspaceBitstreamBackfill()).build();
    }

    @POST
    @Path("/pure/journal-metrics")
    public Response startJournalMetrics(@QueryParam("mode") String mode) {
        return Response.accepted(pureSyncService.startJournalMetrics(mode)).build();
    }

    @POST
    @Path("/pure/research-output-metrics")
    public Response startResearchOutputMetrics(@QueryParam("mode") String mode) {
        return Response.accepted(pureSyncService.startResearchOutputMetrics(mode)).build();
    }

    @POST
    @Path("/pure/person-metrics")
    public Response startPersonMetrics(@QueryParam("mode") String mode) {
        return Response.accepted(pureSyncService.startPersonMetrics(mode)).build();
    }

    @GET
    @Path("/running")
    public List<SyncRunDto> running() {
        return pureSyncService.running();
    }

    @GET
    @Path("/latest")
    public Response latest(@QueryParam("entity") String entity) {
        SyncRunDto dto = pureSyncService.latest(entity);
        return dto == null ? Response.noContent().build() : Response.ok(dto).build();
    }

    @GET
    @Path("/history")
    public List<SyncRunDto> history(@QueryParam("entity") List<String> entities) {
        return pureSyncService.history(entities);
    }

    @GET
    @Path("/policy")
    public SyncModePolicyDto policy(@QueryParam("entity") String entity) {
        return pureSyncService.policy(entity);
    }

    @GET
    @Path("/{syncId}/changes")
    public PageResponse<SyncChangeGroupDto> changes(
            @PathParam("syncId") String syncId,
            @QueryParam("changeType") String changeType,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("25") int size
    ) {
        return pureSyncService.changes(syncId, changeType, page, size);
    }

    @GET
    @Path("/{syncId}/errors")
    public SyncErrorPageDto errors(
            @PathParam("syncId") String syncId,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("25") int size
    ) {
        return pureSyncService.errors(syncId, page, size);
    }

    @POST
    @Path("/{syncId}/cancel")
    public Response cancel(@PathParam("syncId") String syncId) {
        return Response.accepted(pureSyncService.cancel(syncId)).build();
    }

    @GET
    @Path("/{syncId}")
    public SyncRunDto get(@PathParam("syncId") String syncId) {
        return pureSyncService.get(syncId);
    }
}
