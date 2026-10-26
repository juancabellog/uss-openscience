package cl.uss.ca.api;

import cl.uss.ca.api.dto.IdentifierDto;
import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.api.dto.PersonDetailDto;
import cl.uss.ca.api.dto.PersonSummaryDto;
import cl.uss.ca.api.dto.ResearchOutputSummaryDto;
import cl.uss.ca.service.PersonService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * Compatibility route for the previous researchers directory.
 * Prefer {@link PersonResource} {@code /api/v1/people}.
 */
@Path("/researchers")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class ResearcherResource {

    @Inject
    PersonService personService;

    @GET
    public PageResponse<PersonSummaryDto> list(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("25") int size,
            @QueryParam("search") String search,
            @QueryParam("position") String position,
            @QueryParam("cargo") String cargo,
            @QueryParam("population") String population,
            @QueryParam("orcidStatus") String orcidStatus,
            @QueryParam("hasOrcid") Boolean hasOrcid,
            @QueryParam("scopusStatus") String scopusStatus,
            @QueryParam("hasScopus") Boolean hasScopus,
            @QueryParam("scopusCount") String scopusCount,
            @QueryParam("pureStatus") String pureStatus,
            @QueryParam("sort") String sort,
            @QueryParam("direction") String direction
    ) {
        String resolvedCargo = cargo != null && !cargo.isBlank() ? cargo : position;
        return personService.list(
                page,
                size,
                search,
                resolvedCargo,
                population,
                orcidStatus,
                hasOrcid,
                scopusStatus,
                hasScopus,
                scopusCount,
                null,
                pureStatus,
                sort,
                direction
        );
    }

    @GET
    @Path("/{id}")
    public PersonDetailDto get(@PathParam("id") long id) {
        return personService.getById(id);
    }

    @GET
    @Path("/{id}/identities")
    public List<IdentifierDto> identities(@PathParam("id") long id) {
        return personService.getById(id).identities;
    }

    @GET
    @Path("/{id}/publications")
    public List<ResearchOutputSummaryDto> publications(@PathParam("id") long id) {
        return personService.getById(id).publications;
    }
}
