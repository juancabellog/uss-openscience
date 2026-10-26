package cl.uss.ca.service;

import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.api.dto.PersonDetailDto;
import cl.uss.ca.api.dto.PersonSummaryDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Compatibility facade for {@code /api/v1/researchers}.
 * The canonical directory is {@link PersonService}.
 */
@ApplicationScoped
public class ResearcherService {

    @Inject
    PersonService personService;

    public PageResponse<PersonSummaryDto> list(
            Integer page,
            Integer size,
            String search,
            String cargo,
            String population,
            String orcidStatus,
            Boolean hasOrcid,
            String scopusStatus,
            Boolean hasScopus,
            String scopusCount,
            String pureStatus,
            String sort,
            String direction
    ) {
        return personService.list(
                page,
                size,
                search,
                cargo,
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

    public PersonDetailDto getById(long id) {
        return personService.getById(id);
    }
}
