package cl.uss.ca.api;

import cl.uss.ca.api.dto.IndicatorDto;
import cl.uss.ca.api.dto.OrcidAdoptionSeriesDto;
import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.api.dto.ResearcherIdentityCaseDto;
import cl.uss.ca.api.dto.ResearcherIdentityIndicatorsDto;
import cl.uss.ca.api.dto.ResearcherIdentitySignatureDetailDto;
import cl.uss.ca.service.CatalogService;
import cl.uss.ca.service.ExcelExportService;
import cl.uss.ca.service.OrcidAdoptionService;
import cl.uss.ca.service.ResearcherIdentityIndicatorsService;
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

@Path("/indicators")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class IndicatorResource {

    @Inject
    CatalogService catalogService;

    @Inject
    ResearcherIdentityIndicatorsService identityIndicatorsService;

    @Inject
    OrcidAdoptionService orcidAdoptionService;

    @Inject
    ExcelExportService excelExportService;

    @GET
    public List<IndicatorDto> list() {
        return catalogService.layerTwoCatalog();
    }

    @GET
    @Path("/layer/{layer}")
    public List<IndicatorDto> byLayer(@PathParam("layer") int layer) {
        if (layer == 2) {
            return catalogService.layerTwoCatalog();
        }
        return List.of();
    }

    @GET
    @Path("/researcher-identity")
    public ResearcherIdentityIndicatorsDto researcherIdentity() {
        return identityIndicatorsService.summary();
    }

    @GET
    @Path("/orcid-adoption")
    public OrcidAdoptionSeriesDto orcidAdoption(
            @QueryParam("from") String from,
            @QueryParam("to") String to,
            @QueryParam("cargo") String cargo,
            @QueryParam("population") String population
    ) {
        return orcidAdoptionService.series(from, to, cargo, population);
    }

    @GET
    @Path("/researcher-identity/cases")
    public PageResponse<ResearcherIdentityCaseDto> researcherIdentityCases(
            @QueryParam("indicator") String indicator,
            @QueryParam("classification") String classification,
            @QueryParam("search") String search,
            @QueryParam("sort") String sort,
            @QueryParam("direction") String direction,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("25") int size
    ) {
        return identityIndicatorsService.cases(indicator, classification, search, sort, direction, page, size);
    }

    @GET
    @Path("/researcher-identity/cases/export.xlsx")
    @Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public Response exportResearcherIdentityCases(
            @QueryParam("indicator") String indicator,
            @QueryParam("classification") String classification,
            @QueryParam("search") String search,
            @QueryParam("sort") String sort,
            @QueryParam("direction") String direction
    ) {
        return excelExportService.exportResearcherIdentityCases(
                indicator,
                identityIndicatorsService.exportCases(indicator, classification, search, sort, direction)
        );
    }

    @GET
    @Path("/researcher-identity/cases/{personId:\\d+}/signatures")
    public ResearcherIdentitySignatureDetailDto signatureDetail(@PathParam("personId") long personId) {
        return identityIndicatorsService.signatureDetail(personId);
    }
}
