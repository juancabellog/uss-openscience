package cl.uss.ca.pure;

import cl.uss.ca.pure.json.ClassificationRef;
import cl.uss.ca.pure.json.DateRange;
import cl.uss.ca.pure.json.EmailItem;
import cl.uss.ca.pure.json.IdentifierItem;
import cl.uss.ca.pure.json.LocalizedText;
import cl.uss.ca.pure.json.OrganizationRef;
import cl.uss.ca.pure.json.PersonItem;
import cl.uss.ca.pure.json.PersonName;
import cl.uss.ca.pure.json.StaffOrganizationAssociationItem;
import cl.uss.ca.pure.json.TitleItem;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class PersonMapper {
    private static final String USS_EMAIL_SUFFIX = "@uss.cl";
    private static final int TITULO_MAX_LENGTH = 255;

    public PurePerson mapearPersona(PersonItem item) {
        if (item == null) {
            return null;
        }
        String uuid = trimToNull(item.getUuid());
        if (uuid == null) {
            return null;
        }

        List<StaffOrganizationAssociationItem> asociacionesJson =
                emptyIfNull(item.getStaffOrganizationAssociations());

        PurePerson person = new PurePerson();
        person.setUuid(uuid);
        person.setPureId(item.getPureId());
        person.setOrcid(trimToNull(item.getOrcid()));
        person.setOrcidAuthenticated(Boolean.TRUE.equals(item.getOrcidAuthenticated()));
        person.setFechaCreacionPure(parseDateTime(item.getCreatedDate()));
        person.setFechaModificacionPure(parseDateTime(item.getModifiedDate()));

        PersonName name = item.getName();
        if (name != null) {
            person.setFirstName(trimToNull(name.getFirstName()));
            person.setLastName(trimToNull(name.getLastName()));
        }

        List<StaffAssociation> asociaciones = mapearAsociaciones(asociacionesJson);
        person.setAsociaciones(asociaciones);
        person.setEmail(resolverEmail(asociacionesJson));
        person.setFechaIngreso(resolverFechaIngreso(asociacionesJson));
        person.setCargo(resolverCargo(asociacionesJson));

        List<AcademicTitle> titulos = resolverTitulos(item.getTitles());
        person.setTitulos(titulos);
        person.setTitulo(resolverTituloPrincipal(titulos));
        return person;
    }

    public String obtenerTextoPreferido(LocalizedText texto) {
        return LocalizedTexts.obtenerTextoPreferido(texto);
    }

    public String obtenerTextoPreferido(ClassificationRef clasificacion) {
        return LocalizedTexts.obtenerTextoPreferido(clasificacion);
    }

    public String extraerGender(PersonItem item) {
        if (item == null) {
            return null;
        }
        return obtenerTextoPreferido(item.getGender());
    }

    public String extraerPrimaryId(PersonItem item) {
        if (item == null || item.getIdentifiers() == null) {
            return null;
        }
        IdentifierItem primero = null;
        IdentifierItem sincronizado = null;
        int primarios = 0;
        for (IdentifierItem identifier : item.getIdentifiers()) {
            if (identifier == null || !"PrimaryId".equals(identifier.getTypeDiscriminator())) {
                continue;
            }
            primarios++;
            if (primero == null) {
                primero = identifier;
            }
            if (sincronizado == null && "synchronisedPerson".equals(identifier.getIdSource())) {
                sincronizado = identifier;
            }
        }
        if (primarios == 0) {
            return null;
        }
        IdentifierItem elegido = sincronizado != null ? sincronizado : primero;
        if (primarios > 1 && sincronizado == null) {
            System.err.println("WARN Varios PrimaryId sin idSource=synchronisedPerson; se usa el primero. uuid="
                    + item.getUuid() + " pureId=" + item.getPureId());
        }
        return elegido == null ? null : LocalizedTexts.trimToNull(elegido.getValue());
    }

    public String resolverEmail(List<StaffOrganizationAssociationItem> asociaciones) {
        List<String> unicos = new ArrayList<>();
        Set<String> vistos = new LinkedHashSet<>();
        for (StaffOrganizationAssociationItem asociacion : emptyIfNull(asociaciones)) {
            String email = resolverEmailAsociacion(asociacion);
            if (email == null) {
                continue;
            }
            String clave = email.toLowerCase(Locale.ROOT);
            if (vistos.add(clave)) {
                unicos.add(email);
            }
        }
        return unicos.stream()
                .filter(email -> email.toLowerCase(Locale.ROOT).endsWith(USS_EMAIL_SUFFIX))
                .findFirst()
                .orElse(unicos.isEmpty() ? null : unicos.get(0));
    }

    public String resolverEmailAsociacion(StaffOrganizationAssociationItem asociacion) {
        if (asociacion == null || asociacion.getEmails() == null) {
            return null;
        }
        List<String> valores = new ArrayList<>();
        for (EmailItem email : asociacion.getEmails()) {
            String valor = email == null ? null : trimToNull(email.getValue());
            if (valor != null) {
                valores.add(valor);
            }
        }
        return valores.stream()
                .filter(email -> email.toLowerCase(Locale.ROOT).endsWith(USS_EMAIL_SUFFIX))
                .findFirst()
                .orElse(valores.isEmpty() ? null : valores.get(0));
    }

    public LocalDate resolverFechaIngreso(List<StaffOrganizationAssociationItem> asociaciones) {
        return emptyIfNull(asociaciones).stream()
                .map(this::startDate)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    public String resolverCargo(List<StaffOrganizationAssociationItem> asociaciones) {
        List<StaffOrganizationAssociationItem> vigentes = emptyIfNull(asociaciones).stream()
                .filter(Objects::nonNull)
                .filter(this::esVigente)
                .toList();

        StaffOrganizationAssociationItem primaria = vigentes.stream()
                .filter(asociacion -> Boolean.TRUE.equals(asociacion.getPrimaryAssociation()))
                .findFirst()
                .orElse(null);
        if (primaria != null) {
            return firstNonNull(
                    jobTitleEsCl(primaria),
                    obtenerTextoPreferido(primaria.getJobDescription())
            );
        }

        StaffOrganizationAssociationItem conCargo = vigentes.stream()
                .filter(asociacion -> jobTitleEsCl(asociacion) != null)
                .findFirst()
                .orElse(null);
        if (conCargo != null) {
            return jobTitleEsCl(conCargo);
        }

        return vigentes.stream()
                .map(asociacion -> obtenerTextoPreferido(asociacion.getJobDescription()))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    public String resolverTituloPrincipal(List<AcademicTitle> titulos) {
        if (titulos == null || titulos.isEmpty()) {
            return null;
        }
        String designation = titulos.stream()
                .filter(titulo -> titulo != null && titulo.getKind() == TitleKind.DESIGNATION)
                .map(AcademicTitle::getValue)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        String valor = designation != null
                ? designation
                : titulos.stream()
                    .filter(Objects::nonNull)
                    .map(AcademicTitle::getValue)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        if (valor == null) {
            return null;
        }
        return valor.length() <= TITULO_MAX_LENGTH ? valor : valor.substring(0, TITULO_MAX_LENGTH);
    }

    public List<AcademicTitle> resolverTitulos(List<TitleItem> titles) {
        List<AcademicTitle> resultado = new ArrayList<>();
        for (TitleItem item : emptyIfNull(titles)) {
            if (item == null) {
                continue;
            }
            String valor = obtenerTextoPreferido(item.getValue());
            if (valor == null) {
                System.err.println("WARN Título Pure " + valorODesconocido(item.getPureId()) + " sin valor");
                continue;
            }
            AcademicTitle titulo = new AcademicTitle();
            titulo.setPureId(item.getPureId());
            titulo.setValue(valor);
            titulo.setType(obtenerTextoPreferido(item.getType()));
            titulo.setKind(clasificarTitulo(item.getType()));
            resultado.add(titulo);
        }
        return resultado;
    }

    boolean esVigente(StaffOrganizationAssociationItem asociacion) {
        LocalDate endDate = endDate(asociacion);
        return endDate == null || !endDate.isBefore(LocalDate.now());
    }

    private List<StaffAssociation> mapearAsociaciones(List<StaffOrganizationAssociationItem> asociaciones) {
        List<StaffAssociation> resultado = new ArrayList<>();
        for (StaffOrganizationAssociationItem item : emptyIfNull(asociaciones)) {
            if (item == null) {
                continue;
            }
            StaffAssociation asociacion = new StaffAssociation();
            asociacion.setPureId(item.getPureId());
            OrganizationRef organization = item.getOrganization();
            asociacion.setOrganizationUuid(organization == null ? null : trimToNull(organization.getUuid()));
            asociacion.setEmploymentType(obtenerTextoPreferido(item.getEmploymentType()));
            asociacion.setEmail(resolverEmailAsociacion(item));
            asociacion.setStartDate(startDate(item));
            asociacion.setEndDate(endDate(item));
            asociacion.setPrimaryAssociation(Boolean.TRUE.equals(item.getPrimaryAssociation()));
            asociacion.setContractType(obtenerTextoPreferido(item.getContractType()));
            asociacion.setFte(item.getFte());
            asociacion.setJobDescription(obtenerTextoPreferido(item.getJobDescription()));
            asociacion.setJobTitle(obtenerTextoPreferido(item.getJobTitle()));
            asociacion.setStaffType(obtenerTextoPreferido(item.getStaffType()));
            resultado.add(asociacion);
        }
        return resultado;
    }

    private TitleKind clasificarTitulo(ClassificationRef type) {
        String uri = type == null ? null : type.getUri();
        String term = obtenerTextoPreferido(type);
        String texto = ((uri == null ? "" : uri) + " " + (term == null ? "" : term)).toLowerCase(Locale.ROOT);
        if (texto.contains("designation") || texto.contains("nombramiento")) {
            return TitleKind.DESIGNATION;
        }
        if (texto.contains("postnominal") || texto.contains("post-nominal")
                || texto.contains("título postnominal") || texto.contains("titulo postnominal")) {
            return TitleKind.POST_NOMINAL;
        }
        return TitleKind.OTHER;
    }

    private String jobTitleEsCl(StaffOrganizationAssociationItem asociacion) {
        if (asociacion == null || asociacion.getJobTitle() == null) {
            return null;
        }
        return trimToNull(asociacion.getJobTitle().termEsCl());
    }

    private LocalDate startDate(StaffOrganizationAssociationItem asociacion) {
        DateRange period = asociacion == null ? null : asociacion.getPeriod();
        return period == null ? null : parseDate(period.getStartDate());
    }

    private LocalDate endDate(StaffOrganizationAssociationItem asociacion) {
        DateRange period = asociacion == null ? null : asociacion.getPeriod();
        return period == null ? null : parseDate(period.getEndDate());
    }

    private LocalDate parseDate(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return value.length() >= 10 ? LocalDate.parse(value.substring(0, 10)) : LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private OffsetDateTime parseDateTime(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String valorODesconocido(Long pureId) {
        return pureId == null ? "?" : String.valueOf(pureId);
    }

    private static <T> List<T> emptyIfNull(List<T> values) {
        return values == null ? List.of() : values;
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
