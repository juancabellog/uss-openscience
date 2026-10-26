package cl.uss.ca.api;

import cl.uss.ca.api.dto.DspaceCollectionListDto;
import cl.uss.ca.api.dto.DspaceCommunityListDto;
import cl.uss.ca.api.dto.DspaceItemDetailDto;
import cl.uss.ca.api.dto.DspaceItemListDto;
import cl.uss.ca.api.dto.DspaceItemQuery;
import cl.uss.ca.api.dto.DspaceItemSummaryDto;
import cl.uss.ca.api.dto.DspaceStatsDto;
import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.api.dto.SyncErrorDto;
import cl.uss.ca.api.dto.SyncRunDto;
import cl.uss.ca.service.DspaceService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/dspace")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class DspaceResource {

    @Inject
    DspaceService dspaceService;

    @GET
    @Path("/stats")
    public DspaceStatsDto stats(
            @QueryParam("search") String search,
            @QueryParam("outcome") String outcome,
            @QueryParam("accessStatus") String accessStatus,
            @QueryParam("matchStatus") String matchStatus,
            @QueryParam("matchMethod") String matchMethod,
            @QueryParam("collectionUuid") String collectionUuid,
            @QueryParam("communityUuid") String communityUuid,
            @QueryParam("doi") String doi,
            @QueryParam("sharedDoi") Boolean sharedDoi,
            @QueryParam("hasFile") String hasFile,
            @QueryParam("accessComparison") String accessComparison,
            @QueryParam("uuid") List<String> uuids
    ) {
        DspaceItemQuery query = itemQuery(
                search, outcome, accessStatus, matchStatus, matchMethod, collectionUuid, communityUuid,
                doi, sharedDoi, hasFile, accessComparison, uuids, "syncedAt", "desc", 1, 1
        );
        if (catalogStatsRequest(search, outcome, accessStatus, matchStatus, matchMethod,
                collectionUuid, communityUuid, doi, sharedDoi, hasFile, accessComparison, uuids)) {
            return dspaceService.stats();
        }
        return dspaceService.stats(query);
    }

    @GET
    @Path("/by-research-output/{researchOutputId}")
    public List<DspaceItemSummaryDto> byOutput(@PathParam("researchOutputId") long researchOutputId) {
        return dspaceService.byResearchOutput(researchOutputId);
    }

    @GET
    @Path("/items")
    public PageResponse<DspaceItemListDto> items(
            @QueryParam("search") String search,
            @QueryParam("outcome") String outcome,
            @QueryParam("accessStatus") String accessStatus,
            @QueryParam("matchStatus") String matchStatus,
            @QueryParam("matchMethod") String matchMethod,
            @QueryParam("collectionUuid") String collectionUuid,
            @QueryParam("communityUuid") String communityUuid,
            @QueryParam("doi") String doi,
            @QueryParam("sharedDoi") Boolean sharedDoi,
            @QueryParam("hasFile") String hasFile,
            @QueryParam("accessComparison") String accessComparison,
            @QueryParam("uuid") List<String> uuids,
            @QueryParam("sort") @DefaultValue("syncedAt") String sort,
            @QueryParam("direction") @DefaultValue("desc") String direction,
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("size") @DefaultValue("25") int size
    ) {
        return dspaceService.listItems(itemQuery(
                search, outcome, accessStatus, matchStatus, matchMethod, collectionUuid, communityUuid,
                doi, sharedDoi, hasFile, accessComparison, uuids, sort, direction, page, size
        ));
    }

    @GET
    @Path("/matches")
    public PageResponse<DspaceItemListDto> matches(
            @QueryParam("search") String search,
            @QueryParam("outcome") String outcome,
            @QueryParam("accessStatus") String accessStatus,
            @QueryParam("matchStatus") String matchStatus,
            @QueryParam("matchMethod") String matchMethod,
            @QueryParam("collectionUuid") String collectionUuid,
            @QueryParam("communityUuid") String communityUuid,
            @QueryParam("doi") String doi,
            @QueryParam("sharedDoi") Boolean sharedDoi,
            @QueryParam("hasFile") String hasFile,
            @QueryParam("accessComparison") String accessComparison,
            @QueryParam("uuid") List<String> uuids,
            @QueryParam("sort") @DefaultValue("syncedAt") String sort,
            @QueryParam("direction") @DefaultValue("desc") String direction,
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("size") @DefaultValue("25") int size
    ) {
        return items(
                search, outcome, accessStatus, matchStatus, matchMethod, collectionUuid, communityUuid,
                doi, sharedDoi, hasFile, accessComparison, uuids, sort, direction, page, size
        );
    }

    @GET
    @Path("/export.xlsx")
    @Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public Response export(
            @QueryParam("search") String search,
            @QueryParam("outcome") String outcome,
            @QueryParam("accessStatus") String accessStatus,
            @QueryParam("matchStatus") String matchStatus,
            @QueryParam("matchMethod") String matchMethod,
            @QueryParam("collectionUuid") String collectionUuid,
            @QueryParam("communityUuid") String communityUuid,
            @QueryParam("doi") String doi,
            @QueryParam("sharedDoi") Boolean sharedDoi,
            @QueryParam("hasFile") String hasFile,
            @QueryParam("accessComparison") String accessComparison,
            @QueryParam("uuid") List<String> uuids,
            @QueryParam("sort") @DefaultValue("syncedAt") String sort,
            @QueryParam("direction") @DefaultValue("desc") String direction
    ) {
        return dspaceService.exportItems(itemQuery(
                search, outcome, accessStatus, matchStatus, matchMethod, collectionUuid, communityUuid,
                doi, sharedDoi, hasFile, accessComparison, uuids, sort, direction, 1, 20000
        ));
    }

    @GET
    @Path("/items/{uuid}")
    public DspaceItemDetailDto item(@PathParam("uuid") String uuid) {
        return dspaceService.itemDetail(uuid);
    }

    @GET
    @Path("/items/{itemUuid}/bitstreams/{bitstreamUuid}/download")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response downloadBitstream(
            @PathParam("itemUuid") String itemUuid,
            @PathParam("bitstreamUuid") String bitstreamUuid
    ) {
        return dspaceService.downloadBitstream(itemUuid, bitstreamUuid);
    }

    @GET
    @Path("/collections")
    public PageResponse<DspaceCollectionListDto> collections(
            @QueryParam("search") String search,
            @QueryParam("sort") @DefaultValue("syncedAt") String sort,
            @QueryParam("direction") @DefaultValue("desc") String direction,
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("size") @DefaultValue("25") int size
    ) {
        return dspaceService.listCollections(page, size, search, sort, direction);
    }

    @GET
    @Path("/communities")
    public PageResponse<DspaceCommunityListDto> communities(
            @QueryParam("search") String search,
            @QueryParam("sort") @DefaultValue("syncedAt") String sort,
            @QueryParam("direction") @DefaultValue("desc") String direction,
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("size") @DefaultValue("25") int size
    ) {
        return dspaceService.listCommunities(page, size, search, sort, direction);
    }

    @GET
    @Path("/errors")
    public PageResponse<SyncErrorDto> errors(
            @QueryParam("stage") String stage,
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("size") @DefaultValue("25") int size
    ) {
        return dspaceService.incidents(stage, page, size);
    }

    @GET
    @Path("/history")
    public List<SyncRunDto> history() {
        return dspaceService.history();
    }

    private static DspaceItemQuery itemQuery(
            String search,
            String outcome,
            String accessStatus,
            String matchStatus,
            String matchMethod,
            String collectionUuid,
            String communityUuid,
            String doi,
            Boolean sharedDoi,
            String hasFile,
            String accessComparison,
            List<String> uuids,
            String sort,
            String direction,
            int page,
            int size
    ) {
        DspaceItemQuery query = new DspaceItemQuery();
        query.search = search;
        query.outcome = outcome;
        query.accessStatus = accessStatus;
        query.matchStatus = matchStatus;
        query.matchMethod = matchMethod;
        query.collectionUuid = collectionUuid;
        query.communityUuid = communityUuid;
        query.doi = doi;
        query.sharedDoi = sharedDoi;
        query.hasFile = hasFile;
        query.accessComparison = accessComparison;
        query.uuids = uuids;
        query.sort = sort;
        query.direction = direction;
        query.page = page;
        query.pageSize = size;
        return query;
    }

    private static boolean catalogStatsRequest(
            String search,
            String outcome,
            String accessStatus,
            String matchStatus,
            String matchMethod,
            String collectionUuid,
            String communityUuid,
            String doi,
            Boolean sharedDoi,
            String hasFile,
            String accessComparison,
            List<String> uuids
    ) {
        if (outcome != null && !outcome.isBlank()) {
            return false;
        }
        DspaceItemQuery probe = itemQuery(
                search, outcome, accessStatus, matchStatus, matchMethod, collectionUuid, communityUuid,
                doi, sharedDoi, hasFile, accessComparison, uuids, "syncedAt", "desc", 1, 1
        );
        return !probe.hasActiveFilters();
    }
}
