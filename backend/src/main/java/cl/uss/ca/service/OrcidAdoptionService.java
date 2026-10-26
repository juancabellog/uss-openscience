package cl.uss.ca.service;

import cl.uss.ca.api.dto.OrcidAdoptionSeriesDto;
import cl.uss.ca.domain.OrcidAdoptionCalculator;
import cl.uss.ca.domain.OrcidAdoptionPerson;
import cl.uss.ca.exception.ValidationException;
import cl.uss.ca.repository.PersonRepository;
import cl.uss.ca.repository.PersonRepository.Population;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

@ApplicationScoped
public class OrcidAdoptionService {
    static final ZoneId ZONE = ZoneId.of("America/Santiago");

    @Inject
    PersonRepository personRepository;

    public OrcidAdoptionSeriesDto series(String fromRaw, String toRaw, String cargo, String population) {
        YearMonth from = OrcidAdoptionCalculator.parseMonth(fromRaw, "from");
        YearMonth to = OrcidAdoptionCalculator.parseMonth(toRaw, "to");
        if (from == null && to == null) {
            to = YearMonth.now(ZONE);
            from = to.minusMonths(11);
        } else if (from == null) {
            from = to.minusMonths(11);
        } else if (to == null) {
            to = from.plusMonths(11);
        }
        Population parsedPopulation = PersonRepository.PersonQuery.parsePopulation(population);
        if (parsedPopulation == null) {
            throw new ValidationException(
                    "INVALID_POPULATION",
                    "population must be ALL, RESEARCHERS or WITHOUT_POSITION"
            );
        }
        LocalDateTime baseline = personRepository.minOrcidFirstSeenAt();
        YearMonth availableFrom = baseline == null ? null : YearMonth.from(baseline);
        List<OrcidAdoptionPerson> people = personRepository.listOrcidAdoptionPeople(
                cargo == null || cargo.isBlank() ? null : cargo.trim(),
                parsedPopulation
        );
        return OrcidAdoptionCalculator.series(from, to, availableFrom, people);
    }
}
