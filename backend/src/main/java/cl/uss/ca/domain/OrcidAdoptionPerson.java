package cl.uss.ca.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class OrcidAdoptionPerson {
    public LocalDate fechaIngreso;
    public LocalDateTime removedFromPureAt;
    public LocalDateTime orcidFirstSeenAt;

    public OrcidAdoptionPerson() {
    }

    public OrcidAdoptionPerson(
            LocalDate fechaIngreso,
            LocalDateTime removedFromPureAt,
            LocalDateTime orcidFirstSeenAt
    ) {
        this.fechaIngreso = fechaIngreso;
        this.removedFromPureAt = removedFromPureAt;
        this.orcidFirstSeenAt = orcidFirstSeenAt;
    }
}
