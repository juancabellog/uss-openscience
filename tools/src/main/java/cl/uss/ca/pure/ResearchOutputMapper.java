package cl.uss.ca.pure;

import cl.uss.ca.pure.json.ClassificationRef;
import cl.uss.ca.pure.json.ContributorItem;
import cl.uss.ca.pure.json.ElectronicVersionItem;
import cl.uss.ca.pure.json.FreeKeywordLocaleItem;
import cl.uss.ca.pure.json.IdentifierItem;
import cl.uss.ca.pure.json.IssnItem;
import cl.uss.ca.pure.json.JournalAssociationItem;
import cl.uss.ca.pure.json.JournalTitleItem;
import cl.uss.ca.pure.json.KeywordContainerItem;
import cl.uss.ca.pure.json.KeywordGroupItem;
import cl.uss.ca.pure.json.LinkItem;
import cl.uss.ca.pure.json.OrganizationRef;
import cl.uss.ca.pure.json.PersonName;
import cl.uss.ca.pure.json.PublicationDateItem;
import cl.uss.ca.pure.json.PublicationStatusItem;
import cl.uss.ca.pure.json.ResearchOutputItem;
import cl.uss.ca.pure.json.VisibilityItem;
import cl.uss.ca.pure.json.WorkflowItem;
import cl.uss.ca.pure.ro.ElectronicVersion;
import cl.uss.ca.pure.ro.JournalData;
import cl.uss.ca.pure.ro.KeywordData;
import cl.uss.ca.pure.ro.KeywordGroupData;
import cl.uss.ca.pure.ro.OutputContributor;
import cl.uss.ca.pure.ro.OutputIdentifier;
import cl.uss.ca.pure.ro.OutputLink;
import cl.uss.ca.pure.ro.OutputStatus;
import cl.uss.ca.pure.ro.ResearchOutput;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ResearchOutputMapper {

    public ResearchOutput mapear(ResearchOutputItem item) {
        if (item == null) {
            return null;
        }
        String uuid = LocalizedTexts.trimToNull(item.getUuid());
        if (uuid == null) {
            return null;
        }

        ResearchOutput output = new ResearchOutput();
        output.setUuid(uuid);
        output.setPureId(item.getPureId());
        output.setTypeDiscriminator(LocalizedTexts.trimToNull(item.getTypeDiscriminator()));
        output.setTitle(tituloDe(item.getTitle()));
        mapearTipo(output, item.getType());
        mapearCategoria(output, item.getCategory());
        output.setPeerReview(item.getPeerReview());
        mapearIdioma(output, item.getLanguage());
        output.setSubmissionYear(item.getSubmissionYear());
        output.setTotalNumberOfContributors(item.getTotalNumberOfContributors());
        output.setManagingOrganizationUuid(uuidDe(item.getManagingOrganization()));
        output.setPages(LocalizedTexts.trimToNull(item.getPages()));
        output.setVolume(LocalizedTexts.trimToNull(item.getVolume()));
        output.setJournalNumber(LocalizedTexts.trimToNull(item.getJournalNumber()));
        output.setNumberOfPages(item.getNumberOfPages());
        output.setAbstractEs(LocalizedTexts.es(item.getAbstractText()));
        output.setAbstractEn(LocalizedTexts.en(item.getAbstractText()));
        output.setPortalUrl(LocalizedTexts.trimToNull(item.getPortalUrl()));
        mapearVisibilidad(output, item.getVisibility());
        mapearWorkflow(output, item.getWorkflow());
        output.setCreatedDate(parseDateTime(item.getCreatedDate()));
        output.setModifiedDate(parseDateTime(item.getModifiedDate()));
        output.setVersion(LocalizedTexts.trimToNull(item.getVersion()));
        output.setStatuses(mapearStatuses(item.getPublicationStatuses()));
        output.setContributors(mapearContributors(item.getContributors()));
        output.setElectronicVersions(mapearElectronicVersions(item.getElectronicVersions()));
        mapearIdentifiers(item.getUuid(), item.getIdentifiers(), output);
        output.setLinks(mapearLinks(item.getLinks()));
        mapearJournal(output, item.getJournalAssociation());
        output.setExternalOrganizationUuids(uuidsUnicos(item.getExternalOrganizations()));
        output.setKeywordGroups(mapearKeywordGroups(item.getKeywordGroups()));
        return output;
    }

    private void mapearTipo(ResearchOutput output, ClassificationRef type) {
        output.setTypeUri(LocalizedTexts.uri(type));
        output.setTypeEs(LocalizedTexts.es(type));
        output.setTypeEn(LocalizedTexts.en(type));
    }

    private void mapearCategoria(ResearchOutput output, ClassificationRef category) {
        output.setCategoryUri(LocalizedTexts.uri(category));
        output.setCategoryEs(LocalizedTexts.es(category));
        output.setCategoryEn(LocalizedTexts.en(category));
    }

    private void mapearIdioma(ResearchOutput output, ClassificationRef language) {
        output.setLanguageUri(LocalizedTexts.uri(language));
        output.setLanguageEs(LocalizedTexts.es(language));
        output.setLanguageEn(LocalizedTexts.en(language));
    }

    private void mapearVisibilidad(ResearchOutput output, VisibilityItem visibility) {
        if (visibility == null) {
            return;
        }
        output.setVisibilityKey(LocalizedTexts.trimToNull(visibility.getKey()));
        output.setVisibilityEs(LocalizedTexts.es(visibility.getDescription()));
        output.setVisibilityEn(LocalizedTexts.en(visibility.getDescription()));
    }

    private void mapearWorkflow(ResearchOutput output, WorkflowItem workflow) {
        if (workflow == null) {
            return;
        }
        output.setWorkflowStep(LocalizedTexts.trimToNull(workflow.getStep()));
        output.setWorkflowEs(LocalizedTexts.es(workflow.getDescription()));
        output.setWorkflowEn(LocalizedTexts.en(workflow.getDescription()));
    }

    List<OutputStatus> mapearStatuses(List<PublicationStatusItem> items) {
        List<OutputStatus> resultado = new ArrayList<>();
        for (PublicationStatusItem item : emptyIfNull(items)) {
            if (item == null) {
                continue;
            }
            OutputStatus status = new OutputStatus();
            status.setPureId(item.getPureId());
            status.setCurrentStatus(item.getCurrent());
            status.setStatusUri(LocalizedTexts.uri(item.getPublicationStatus()));
            status.setStatusEs(LocalizedTexts.es(item.getPublicationStatus()));
            status.setStatusEn(LocalizedTexts.en(item.getPublicationStatus()));
            PublicationDateItem date = item.getPublicationDate();
            if (date != null) {
                status.setPublicationYear(date.getYear());
                status.setPublicationMonth(date.getMonth());
                status.setPublicationDay(date.getDay());
            }
            resultado.add(status);
        }
        return resultado;
    }

    List<OutputContributor> mapearContributors(List<ContributorItem> items) {
        List<OutputContributor> resultado = new ArrayList<>();
        for (ContributorItem item : emptyIfNull(items)) {
            if (item == null) {
                continue;
            }
            OutputContributor contributor = new OutputContributor();
            contributor.setPureId(item.getPureId());
            contributor.setTypeDiscriminator(LocalizedTexts.trimToNull(item.getTypeDiscriminator()));
            PersonName name = item.getName();
            if (name != null) {
                contributor.setFirstName(LocalizedTexts.trimToNull(name.getFirstName()));
                contributor.setLastName(LocalizedTexts.trimToNull(name.getLastName()));
            }
            contributor.setRoleUri(LocalizedTexts.uri(item.getRole()));
            contributor.setRoleEs(LocalizedTexts.es(item.getRole()));
            contributor.setRoleEn(LocalizedTexts.en(item.getRole()));
            contributor.setPersonUuid(uuidDe(item.getPerson()));
            contributor.setExternalPersonUuid(uuidDe(item.getExternalPerson()));
            contributor.setHidden(item.getHidden());
            contributor.setCorrespondingAuthor(item.getCorrespondingAuthor());
            contributor.setAssociationHidden(item.getAssociationHidden());
            contributor.setOrganizationUuids(uuidsUnicos(item.getExternalOrganizations()));
            resultado.add(contributor);
        }
        return resultado;
    }

    List<ElectronicVersion> mapearElectronicVersions(List<ElectronicVersionItem> items) {
        List<ElectronicVersion> resultado = new ArrayList<>();
        Set<String> vistos = new LinkedHashSet<>();
        for (ElectronicVersionItem item : emptyIfNull(items)) {
            if (item == null) {
                continue;
            }
            String doi = LocalizedTexts.trimToNull(item.getDoi());
            String clave = (item.getPureId() == null ? "" : item.getPureId())
                    + "|" + (item.getTypeDiscriminator() == null ? "" : item.getTypeDiscriminator())
                    + "|" + (doi == null ? "" : doi);
            if (!vistos.add(clave)) {
                continue;
            }
            ElectronicVersion version = new ElectronicVersion();
            version.setPureId(item.getPureId());
            version.setTypeDiscriminator(LocalizedTexts.trimToNull(item.getTypeDiscriminator()));
            version.setDoi(doi);
            version.setAccessTypeUri(LocalizedTexts.uri(item.getAccessType()));
            version.setAccessTypeEs(LocalizedTexts.es(item.getAccessType()));
            version.setAccessTypeEn(LocalizedTexts.en(item.getAccessType()));
            version.setVersionTypeUri(LocalizedTexts.uri(item.getVersionType()));
            version.setVersionTypeEs(LocalizedTexts.es(item.getVersionType()));
            version.setVersionTypeEn(LocalizedTexts.en(item.getVersionType()));
            resultado.add(version);
        }
        return resultado;
    }

    void mapearIdentifiers(String researchOutputUuid, List<IdentifierItem> items, ResearchOutput output) {
        List<OutputIdentifier> resultado = new ArrayList<>();
        Set<String> identifiersProcesados = new HashSet<>();
        int duplicados = 0;
        for (IdentifierItem item : emptyIfNull(items)) {
            if (item == null) {
                continue;
            }
            String source = LocalizedTexts.trimToNull(item.getIdSource());
            String value = firstNonNull(
                    LocalizedTexts.trimToNull(item.getValue()),
                    LocalizedTexts.trimToNull(item.getId())
            );
            if (value == null) {
                continue;
            }
            String key = (source == null ? "" : source) + "|" + value;
            if (!identifiersProcesados.add(key)) {
                duplicados++;
                continue;
            }
            OutputIdentifier identifier = new OutputIdentifier();
            identifier.setPureId(item.getPureId());
            identifier.setTypeDiscriminator(LocalizedTexts.trimToNull(item.getTypeDiscriminator()));
            identifier.setIdSource(source);
            identifier.setValue(value);
            resultado.add(identifier);
        }
        output.setIdentifiers(resultado);
        output.setIdentifiersDuplicadosOmitidos(duplicados);
    }

    List<OutputLink> mapearLinks(List<LinkItem> items) {
        List<OutputLink> resultado = new ArrayList<>();
        Set<String> urls = new LinkedHashSet<>();
        for (LinkItem item : emptyIfNull(items)) {
            if (item == null) {
                continue;
            }
            String url = LocalizedTexts.trimToNull(item.getUrl());
            if (url != null && !urls.add(url)) {
                continue;
            }
            OutputLink link = new OutputLink();
            link.setPureId(item.getPureId());
            link.setUrl(url);
            link.setDescriptionEs(LocalizedTexts.es(item.getDescription()));
            link.setDescriptionEn(LocalizedTexts.en(item.getDescription()));
            link.setLinkTypeUri(LocalizedTexts.uri(item.getLinkType()));
            link.setLinkTypeEs(LocalizedTexts.es(item.getLinkType()));
            link.setLinkTypeEn(LocalizedTexts.en(item.getLinkType()));
            resultado.add(link);
        }
        return resultado;
    }

    private void mapearJournal(ResearchOutput output, JournalAssociationItem association) {
        if (association == null) {
            return;
        }
        output.setJournalAssociationPureId(association.getPureId());
        JournalData journal = new JournalData();
        OrganizationRef ref = association.getJournal();
        journal.setUuid(uuidDe(ref));
        JournalTitleItem title = association.getTitle();
        if (title != null) {
            journal.setTitlePureId(title.getPureId());
            journal.setTitle(LocalizedTexts.trimToNull(title.getTitle()));
        }
        IssnItem issn = association.getIssn();
        if (issn != null) {
            journal.setIssnPureId(issn.getPureId());
            journal.setIssn(LocalizedTexts.trimToNull(issn.getIssn()));
        }
        if (journal.getUuid() != null || journal.getTitle() != null || journal.getIssn() != null) {
            output.setJournal(journal);
        }
    }

    List<KeywordGroupData> mapearKeywordGroups(List<KeywordGroupItem> items) {
        List<KeywordGroupData> resultado = new ArrayList<>();
        for (KeywordGroupItem item : emptyIfNull(items)) {
            if (item == null) {
                continue;
            }
            KeywordGroupData group = new KeywordGroupData();
            group.setPureId(item.getPureId());
            group.setTypeDiscriminator(LocalizedTexts.trimToNull(item.getTypeDiscriminator()));
            group.setLogicalName(LocalizedTexts.trimToNull(item.getLogicalName()));
            group.setNameEs(LocalizedTexts.es(item.getName()));
            group.setNameEn(LocalizedTexts.en(item.getName()));
            group.setKeywords(mapearKeywords(item));
            resultado.add(group);
        }
        return resultado;
    }

    private List<KeywordData> mapearKeywords(KeywordGroupItem group) {
        List<KeywordData> keywords = new ArrayList<>();
        Set<String> freeVistos = new LinkedHashSet<>();
        Set<String> structuredVistos = new LinkedHashSet<>();

        for (KeywordContainerItem container : emptyIfNull(group.getKeywordContainers())) {
            if (container == null) {
                continue;
            }
            if (container.getStructuredKeyword() != null) {
                String uri = LocalizedTexts.uri(container.getStructuredKeyword());
                String claveStructured = uri == null
                        ? "id:" + container.getPureId()
                        : uri;
                if (structuredVistos.add(claveStructured)) {
                    KeywordData structured = new KeywordData();
                    structured.setKeywordType(KeywordData.TYPE_STRUCTURED);
                    structured.setContainerPureId(container.getPureId());
                    structured.setState(LocalizedTexts.trimToNull(container.getState()));
                    structured.setOrigin(LocalizedTexts.trimToNull(container.getOrigin()));
                    structured.setKeywordUri(uri);
                    structured.setKeywordEs(LocalizedTexts.es(container.getStructuredKeyword()));
                    structured.setKeywordEn(LocalizedTexts.en(container.getStructuredKeyword()));
                    keywords.add(structured);
                }
            }
            agregarFreeKeywords(keywords, freeVistos, container.getPureId(),
                    container.getState(), container.getOrigin(), container.getFreeKeywords());
        }
        agregarFreeKeywordsDesdeNodo(keywords, freeVistos, group.getKeywords());
        return keywords;
    }

    private void agregarFreeKeywords(List<KeywordData> destino, Set<String> vistos,
                                     Long containerPureId, String state, String origin,
                                     List<FreeKeywordLocaleItem> bundles) {
        for (FreeKeywordLocaleItem bundle : emptyIfNull(bundles)) {
            if (bundle == null) {
                continue;
            }
            String locale = LocalizedTexts.trimToNull(bundle.getLocale());
            for (String palabra : emptyIfNull(bundle.getFreeKeywords())) {
                agregarFreeKeyword(destino, vistos, containerPureId, state, origin, locale, palabra);
            }
        }
    }

    private void agregarFreeKeywordsDesdeNodo(List<KeywordData> destino, Set<String> vistos, JsonNode node) {
        if (node == null || node.isNull() || !node.isArray()) {
            return;
        }
        for (JsonNode element : node) {
            if (element == null || element.isNull()) {
                continue;
            }
            if (element.isTextual()) {
                agregarFreeKeyword(destino, vistos, null, null, null, null, element.asText());
            } else if (element.isObject()) {
                String locale = LocalizedTexts.trimToNull(text(element, "locale"));
                JsonNode palabras = element.get("freeKeywords");
                if (palabras != null && palabras.isArray()) {
                    for (JsonNode palabra : palabras) {
                        if (palabra != null && palabra.isTextual()) {
                            agregarFreeKeyword(destino, vistos, null, null, null, locale, palabra.asText());
                        }
                    }
                } else if (element.hasNonNull("value")) {
                    agregarFreeKeyword(destino, vistos, null, null, null, locale, element.get("value").asText());
                }
            }
        }
    }

    private void agregarFreeKeyword(List<KeywordData> destino, Set<String> vistos,
                                    Long containerPureId, String state, String origin,
                                    String locale, String palabra) {
        String valor = LocalizedTexts.trimToNull(palabra);
        if (valor == null) {
            return;
        }
        String clave = (locale == null ? "" : locale.toLowerCase(Locale.ROOT)) + "\0" + valor.toLowerCase(Locale.ROOT);
        if (!vistos.add(clave)) {
            return;
        }
        KeywordData keyword = new KeywordData();
        keyword.setKeywordType(KeywordData.TYPE_FREE);
        keyword.setContainerPureId(containerPureId);
        keyword.setState(LocalizedTexts.trimToNull(state));
        keyword.setOrigin(LocalizedTexts.trimToNull(origin));
        keyword.setLocale(locale);
        keyword.setFreeKeyword(valor);
        destino.add(keyword);
    }

    private List<String> uuidsUnicos(List<OrganizationRef> refs) {
        LinkedHashSet<String> unicos = new LinkedHashSet<>();
        for (OrganizationRef ref : emptyIfNull(refs)) {
            String uuid = uuidDe(ref);
            if (uuid != null) {
                unicos.add(uuid);
            }
        }
        return new ArrayList<>(unicos);
    }

    private String tituloDe(JsonNode title) {
        if (title == null || title.isNull()) {
            return null;
        }
        if (title.isTextual()) {
            return LocalizedTexts.trimToNull(title.asText());
        }
        if (title.hasNonNull("value")) {
            return LocalizedTexts.trimToNull(title.get("value").asText());
        }
        String es = title.hasNonNull("es_CL") ? LocalizedTexts.trimToNull(title.get("es_CL").asText()) : null;
        if (es != null) {
            return es;
        }
        return title.hasNonNull("en_US") ? LocalizedTexts.trimToNull(title.get("en_US").asText()) : null;
    }

    private String uuidDe(OrganizationRef ref) {
        return ref == null ? null : LocalizedTexts.trimToNull(ref.getUuid());
    }

    private OffsetDateTime parseDateTime(String raw) {
        String value = LocalizedTexts.trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String text(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return null;
        }
        return node.get(field).asText();
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
