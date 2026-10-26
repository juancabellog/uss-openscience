package cl.uss.ca.sync.diff;

import cl.uss.ca.external.pure.AcademicTitle;
import cl.uss.ca.external.pure.PersonIdentifier;
import cl.uss.ca.external.pure.StaffAssociation;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class PersonSnapshot {
    public Long id;
    public String uuid;
    public Long pureId;
    public String firstName;
    public String lastName;
    public String orcid;
    public Boolean orcidAuthenticated;
    public String email;
    public LocalDate fechaIngreso;
    public String cargo;
    public String titulo;
    public String createdBy;
    public OffsetDateTime fechaCreacionPure;
    public String modifiedBy;
    public OffsetDateTime fechaModificacionPure;
    public Boolean activeInPure;
    public OffsetDateTime orcidFirstSeenAt;
    public String orcidFirstSeenSource;
    public final List<PersonIdentifier> identifiers = new ArrayList<>();
    public final List<StaffAssociation> asociaciones = new ArrayList<>();
    public final List<AcademicTitle> titulos = new ArrayList<>();
}
