package cl.uss.ca.api;

import cl.uss.ca.api.dto.DataQualityApplyRequestDto;
import cl.uss.ca.api.dto.DataQualityApplyResultDto;
import cl.uss.ca.api.dto.DataQualityBulkApplyRequestDto;
import cl.uss.ca.api.dto.DataQualityExecutionDto;
import cl.uss.ca.api.dto.DataQualityFindingDto;
import cl.uss.ca.api.dto.DataQualityNotApplicableRequestDto;
import cl.uss.ca.api.dto.DataQualityRuleDto;
import cl.uss.ca.api.dto.DataQualityStatsDto;
import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.api.dto.PureCorrectionResultDto;
import cl.uss.ca.repository.DataQualityRepository.FindingQuery;
import cl.uss.ca.service.DataQualityService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
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

@Path("/data-quality")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DataQualityResource {

    @Inject
    DataQualityService dataQualityService;

    @GET
    @Path("/stats")
    public DataQualityStatsDto stats(
            @QueryParam("search") String search,
            @QueryParam("category") String category,
            @QueryParam("rule") String rule,
            @QueryParam("entity") String entity,
            @QueryParam("action") String action,
            @QueryParam("status") String status,
            @QueryParam("source") String source
    ) {
        return dataQualityService.stats(query(search, category, rule, entity, action, status, source, null, null, 0, 25));
    }

    @GET
    public PageResponse<DataQualityFindingDto> list(
            @QueryParam("search") String search,
            @QueryParam("category") String category,
            @QueryParam("rule") String rule,
            @QueryParam("entity") String entity,
            @QueryParam("action") String action,
            @QueryParam("status") String status,
            @QueryParam("sort") String sort,
            @QueryParam("direction") String direction,
            @QueryParam("source") String source,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("25") int size
    ) {
        return dataQualityService.findings(query(search, category, rule, entity, action, status, source, sort, direction, page, size));
    }

    @GET
    @Path("/export.xlsx")
    @Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public Response export(
            @QueryParam("search") String search,
            @QueryParam("category") String category,
            @QueryParam("rule") String rule,
            @QueryParam("entity") String entity,
            @QueryParam("action") String action,
            @QueryParam("status") String status,
            @QueryParam("sort") String sort,
            @QueryParam("direction") String direction,
            @QueryParam("source") String source
    ) {
        return dataQualityService.export(query(search, category, rule, entity, action, status, source, sort, direction, 0, 25));
    }

    @GET
    @Path("/rules")
    public List<DataQualityRuleDto> rules() {
        return dataQualityService.rules();
    }

    @GET
    @Path("/history")
    public List<DataQualityExecutionDto> history() {
        return dataQualityService.history();
    }

    @GET
    @Path("/executions/latest")
    public Response latest() {
        DataQualityExecutionDto dto = dataQualityService.latest();
        return dto == null ? Response.noContent().build() : Response.ok(dto).build();
    }

    @GET
    @Path("/executions/{id}")
    public DataQualityExecutionDto execution(@PathParam("id") long id) {
        return dataQualityService.getExecution(id);
    }

    @POST
    @Path("/executions")
    public Response start(@QueryParam("rule") String rule) {
        if (rule == null || rule.isBlank()) {
            return Response.accepted(dataQualityService.startManual()).build();
        }
        return Response.accepted(dataQualityService.startRule(rule)).build();
    }

    @GET
    @Path("/findings/{id}")
    public DataQualityFindingDto finding(@PathParam("id") long id) {
        return dataQualityService.finding(id);
    }

    @POST
    @Path("/findings/{id}/apply")
    public DataQualityApplyResultDto apply(@PathParam("id") long id, DataQualityApplyRequestDto body) {
        return dataQualityService.apply(id, body);
    }

    @POST
    @Path("/findings/apply-bulk")
    public DataQualityApplyResultDto applyBulk(DataQualityBulkApplyRequestDto body) {
        return dataQualityService.applyBulk(body == null ? List.of() : body.findingIds);
    }

    @POST
    @Path("/findings/{id}/not-applicable")
    public DataQualityFindingDto notApplicable(
            @PathParam("id") long id,
            DataQualityNotApplicableRequestDto body
    ) {
        return dataQualityService.markNotApplicable(id, body == null ? null : body.justification);
    }

    @POST
    @Path("/findings/{id}/in-review")
    public DataQualityFindingDto inReview(@PathParam("id") long id) {
        return dataQualityService.markInReview(id);
    }

    @POST
    @Path("/findings/{id}/retry-local")
    public PureCorrectionResultDto retryLocal(@PathParam("id") long id) {
        return dataQualityService.retryLocal(id);
    }

    private static FindingQuery query(
            String search,
            String category,
            String rule,
            String entity,
            String action,
            String status,
            String source,
            String sort,
            String direction,
            int page,
            int size
    ) {
        FindingQuery query = new FindingQuery();
        query.search = search;
        query.category = category;
        query.ruleCode = rule;
        query.entityType = entity;
        query.correctionMode = action;
        query.status = status;
        query.source = source;
        query.sort = sort;
        query.direction = direction;
        query.page = page;
        query.size = size;
        return query;
    }
}
