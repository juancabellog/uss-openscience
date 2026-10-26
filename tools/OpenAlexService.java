package com.sisgic.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sisgic.dto.*;
import com.sisgic.dto.PublicationPreviewDTO;
import com.sisgic.dto.PublicationPreviewDataDTO;
import com.sisgic.dto.JournalPreviewDTO;
import com.sisgic.dto.AuthorPreviewDTO;
import com.sisgic.dto.AffiliationPreviewDTO;
import com.sisgic.entity.Publicacion;
import com.sisgic.repository.JournalRepository;
import com.sisgic.repository.PublicacionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.FileNotFoundException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Servicio para obtener datos de publicaciones desde OpenAlex usando DOI
 * NO persiste nada en la base de datos, solo construye DTOs
 */
@Service
public class OpenAlexService {
    
    private static final String OPENALEX_BASE_URL = "https://api.openalex.org/works/";
    private static final String DOI_PREFIX = "https://doi.org/";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private JournalRepository journalRepository;
    
    @Autowired
    private ResearcherMatchingService researcherMatchingService;
    
    @Autowired
    private InstitutionMatchingService institutionMatchingService;
    
    @Autowired
    private com.sisgic.repository.RRHHRepository rrhhRepository;

    @Autowired
    private PdfFileService pdfFileService;

    @Autowired
    private PublicacionRepository publicacionRepository;
    
    @Value("${openalex.default-project-code:PRJ0000000000004}")
    private String defaultProjectCode;
    
    /**
     * Obtiene el preview de una publicación desde OpenAlex usando su DOI
     * NO persiste nada en la base de datos
     * Retorna un DTO con estados normalizados (matched/new/review) para que el frontend muestre decisiones
     * 
     * @param doi El DOI de la publicación (con o sin prefijo https://doi.org/)
     * @return PublicationPreviewDTO con todos los datos parseados y estados de matching
     * @throws Exception Si hay error al llamar a OpenAlex o parsear datos
     */
    public PublicationPreviewDTO getPreviewByDoi(String doi) throws Exception {
        // Primero obtener los datos usando el método existente
        PublicationFromDoiDTO data = getPublicationByDoi(doi);
        
        // Convertir a PublicationPreviewDTO con estados normalizados
        return convertToPreviewDTO(data);
    }
    
    /**
     * Obtiene los datos de una publicación desde OpenAlex usando su DOI
     * NO persiste nada en la base de datos
     * 
     * @param doi El DOI de la publicación (con o sin prefijo https://doi.org/)
     * @return PublicationFromDoiDTO con todos los datos parseados y con matching de autores/journals
     * @throws Exception Si hay error al llamar a OpenAlex o parsear datos
     */
    public PublicationFromDoiDTO getPublicationByDoi(String doi) throws Exception {
        // 1. Normalizar DOI (quitar https://doi.org/ si viene)
        String normalizedDoi = normalizeDoi(doi);
        
        // 2. Construir URL de OpenAlex
        String openAlexUrl = OPENALEX_BASE_URL + DOI_PREFIX + normalizedDoi;
        
        try {
            // 3. Llamar a OpenAlex API
            ResponseEntity<String> response = restTemplate.getForEntity(openAlexUrl, String.class);
            
            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                throw new Exception("OpenAlex API returned error: " + response.getStatusCode());
            }
            
            // 4. Parsear JSON
            JsonNode json = objectMapper.readTree(response.getBody());
            
            // 5. Validar que tenga authorships
            if (!json.has("authorships") || json.get("authorships").isEmpty()) {
                throw new Exception("Publication does not have authorships data");
            }
            
            // 6. Construir DTO con matching
            return parseOpenAlexJson(json, normalizedDoi, openAlexUrl);
            
        } catch (RestClientException e) {
            if (e.getMessage() != null && e.getMessage().contains("404")) {
                throw new FileNotFoundException("DOI not found in OpenAlex: " + normalizedDoi);
            }
            throw new Exception("Error calling OpenAlex API: " + e.getMessage(), e);
        } catch (Exception e) {
            if (e instanceof FileNotFoundException) {
                throw e;
            }
            throw new Exception("Error parsing OpenAlex response: " + e.getMessage(), e);
        }
    }
    
    /**
     * Normaliza un DOI removiendo el prefijo https://doi.org/ si existe
     */
    private String normalizeDoi(String doi) {
        if (doi == null || doi.trim().isEmpty()) {
            throw new IllegalArgumentException("DOI cannot be null or empty");
        }
        
        String normalized = doi.trim();
        if (normalized.startsWith(DOI_PREFIX)) {
            normalized = normalized.substring(DOI_PREFIX.length());
        }
        if (normalized.startsWith("doi.org/")) {
            normalized = normalized.substring(8);
        }
        
        return normalized;
    }
    
    /**
     * Parsea el JSON de OpenAlex y construye el DTO
     */
    private PublicationFromDoiDTO parseOpenAlexJson(JsonNode json, String doi, String openAlexUrl) throws Exception {
        PublicationFromDoiDTO dto = new PublicationFromDoiDTO();
        
        // Datos básicos
        dto.setTitle(json.has("title") && !json.get("title").isNull() ? json.get("title").asText() : null);
        dto.setDisplayName(json.has("display_name") && !json.get("display_name").isNull() ? 
                          json.get("display_name").asText() : null);
        dto.setDoi(doi);
        dto.setOpenAlexUrl(openAlexUrl);
        
        // Fecha de publicación
        if (json.has("publication_date") && !json.get("publication_date").isNull()) {
            dto.setPublicationDate(json.get("publication_date").asText());
        }
        if (json.has("publication_year") && !json.get("publication_year").isNull()) {
            dto.setPublicationYear(json.get("publication_year").asInt());
        }

        // Intentar obtener y descargar el PDF si existe primary_location.pdf_url
        try {
            if (json.has("primary_location") && !json.get("primary_location").isNull()) {
                JsonNode primaryLocation = json.get("primary_location");
                if (primaryLocation.has("pdf_url") && !primaryLocation.get("pdf_url").isNull()) {
                    String pdfUrl = primaryLocation.get("pdf_url").asText(null);
                    if (pdfUrl != null && !pdfUrl.trim().isEmpty()) {
                        String linkPdf = pdfFileService.downloadAndSavePdfFromUrl(pdfUrl);
                        if (linkPdf != null && !linkPdf.trim().isEmpty()) {
                            dto.setLinkPDF(linkPdf);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("OpenAlexService: Error al descargar PDF en parseOpenAlexJson: " + e.getMessage());
            e.printStackTrace();
        }
        
        // Journal matching
        dto.setJournalMatch(matchJournal(json));
        
        // Datos bibliográficos
        if (json.has("biblio") && !json.get("biblio").isNull()) {
            JsonNode biblio = json.get("biblio");
            dto.setVolume(biblio.has("volume") && !biblio.get("volume").isNull() ? 
                         biblio.get("volume").asText() : null);
            dto.setFirstPage(biblio.has("first_page") && !biblio.get("first_page").isNull() ? 
                            biblio.get("first_page").asText() : null);
            dto.setLastPage(biblio.has("last_page") && !biblio.get("last_page").isNull() ? 
                           biblio.get("last_page").asText() : null);
        }
        
        // Autores
        if (json.has("authorships") && json.get("authorships").isArray()) {
            dto.setAuthors(processAuthors(json.get("authorships")));
        }
        
        return dto;
    }
    
    /**
     * Normaliza un ISSN removiendo guiones y espacios
     * Ejemplo: "2575-9108" -> "25759108"
     */
    private String normalizeIssn(String issn) {
        if (issn == null || issn.isEmpty()) {
            return null;
        }
        // Remover guiones y espacios, convertir a mayúsculas
        return issn.replaceAll("[\\s-]", "").toUpperCase();
    }
    
    /**
     * Hace matching de un journal por ISSN
     */
    private JournalMatchDTO matchJournal(JsonNode json) {
        JournalMatchDTO journalMatch = new JournalMatchDTO();
        
        try {
            String issn = null;
            String journalName = null;
            
            // Intentar obtener ISSN desde primary_location.source.issn_l
            if (json.has("primary_location") && !json.get("primary_location").isNull()) {
                JsonNode primaryLocation = json.get("primary_location");
                if (primaryLocation.has("source") && !primaryLocation.get("source").isNull()) {
                    JsonNode source = primaryLocation.get("source");
                    if (source.has("issn_l") && !source.get("issn_l").isNull()) {
                        issn = source.get("issn_l").asText();
                    }
                    // Si no hay issn_l, intentar obtener del array issn
                    if (issn == null && source.has("issn") && !source.get("issn").isNull()) {
                        JsonNode issnArray = source.get("issn");
                        if (issnArray.isArray() && issnArray.size() > 0) {
                            issn = issnArray.get(0).asText();
                        }
                    }
                    if (source.has("display_name") && !source.get("display_name").isNull()) {
                        journalName = source.get("display_name").asText();
                    }
                }
            }
            
            journalMatch.setIssn(issn);
            journalMatch.setName(journalName);
            
            // Buscar en BD por ISSN (normalizando ambos lados)
            if (issn != null && !issn.isEmpty()) {
                String normalizedIssn = normalizeIssn(issn);
                
                // Buscar con el ISSN normalizado
                List<com.sisgic.entity.Journal> journals = findJournalByNormalizedIssn(normalizedIssn);
                
                // Si no encuentra, intentar con el formato original (por si acaso hay algunos con guiones en la BD)
                if (journals.isEmpty()) {
                    journals = journalRepository.findByIssn(issn);
                }
                
                if (!journals.isEmpty()) {
                    journalMatch.setId(journals.get(0).getId());
                    journalMatch.setFoundInDatabase(true);
                } else {
                    journalMatch.setFoundInDatabase(false);
                }
            } else {
                journalMatch.setFoundInDatabase(false);
            }
            
        } catch (Exception e) {
            System.err.println("Error matching journal: " + e.getMessage());
            journalMatch.setFoundInDatabase(false);
        }
        
        return journalMatch;
    }
    
    /**
     * Busca journals normalizando el ISSN (removiendo guiones de ambos lados)
     */
    private List<com.sisgic.entity.Journal> findJournalByNormalizedIssn(String normalizedIssn) {
        if (normalizedIssn == null || normalizedIssn.isEmpty()) {
            return List.of();
        }
        
        // Obtener todos los journals y filtrar normalizando sus ISSNs
        List<com.sisgic.entity.Journal> allJournals = journalRepository.findAll();
        return allJournals.stream()
                .filter(journal -> {
                    String journalIssn = journal.getIssn();
                    if (journalIssn == null || journalIssn.isEmpty()) {
                        return false;
                    }
                    String normalizedJournalIssn = normalizeIssn(journalIssn);
                    return normalizedIssn.equals(normalizedJournalIssn);
                })
                .toList();
    }
    
    /**
     * Procesa los autores del JSON de OpenAlex y hace matching con nuestra BD
     */
    private List<AuthorFromOpenAlexDTO> processAuthors(JsonNode authorships) {
        List<AuthorFromOpenAlexDTO> authors = new ArrayList<>();
        
        for (int i = 0; i < authorships.size(); i++) {
            JsonNode authorNode = authorships.get(i);
            AuthorFromOpenAlexDTO author = new AuthorFromOpenAlexDTO();
            
            try {
                // Nombre del autor
                String rawAuthorName = authorNode.has("raw_author_name") && !authorNode.get("raw_author_name").isNull() ?
                                      authorNode.get("raw_author_name").asText() : null;
                
                // Limpiar nombre (remover paréntesis si hay)
                if (rawAuthorName != null) {
                    int index = rawAuthorName.indexOf("(");
                    if (index != -1) {
                        rawAuthorName = rawAuthorName.substring(0, index).trim();
                    }
                }
                
                // Si no hay raw_author_name, usar display_name del author
                String openAlexAuthorId = null;
                if (authorNode.has("author") && !authorNode.get("author").isNull()) {
                    JsonNode authorObj = authorNode.get("author");
                    if (authorObj.has("id") && !authorObj.get("id").isNull()) {
                        openAlexAuthorId = authorObj.get("id").asText();
                    }
                    if (rawAuthorName == null || rawAuthorName.isEmpty()) {
                        if (authorObj.has("display_name") && !authorObj.get("display_name").isNull()) {
                            rawAuthorName = authorObj.get("display_name").asText();
                        }
                    }
                }
                
                author.setFullName(rawAuthorName);
                author.setOpenAlexId(openAlexAuthorId);
                
                // ORCID
                if (authorNode.has("author") && !authorNode.get("author").isNull()) {
                    JsonNode authorObj = authorNode.get("author");
                    if (authorObj.has("orcid") && !authorObj.get("orcid").isNull()) {
                        String orcid = authorObj.get("orcid").asText();
                        // Extraer solo el ID del ORCID (después de la última /)
                        if (orcid.contains("/")) {
                            orcid = orcid.substring(orcid.lastIndexOf('/') + 1);
                        }
                        author.setOrcid(orcid);
                    }
                }
                
                // Orden y posición
                author.setOrder(i + 1);
                if (authorNode.has("author_position") && !authorNode.get("author_position").isNull()) {
                    author.setAuthorPosition(authorNode.get("author_position").asText());
                } else {
                    // Si OpenAlex no proporciona author_position, calcularlo basado en el índice
                    // Primer autor (índice 0) = "first", último autor = "last", otros = "middle"
                    int totalAuthors = authorships.size();
                    if (i == 0) {
                        author.setAuthorPosition("first");
                    } else if (i == totalAuthors - 1) {
                        author.setAuthorPosition("last");
                    } else {
                        author.setAuthorPosition("middle");
                    }
                }
                
                // Corresponding author
                if (authorNode.has("is_corresponding") && !authorNode.get("is_corresponding").isNull()) {
                    author.setIsCorresponding(authorNode.get("is_corresponding").asBoolean());
                } else {
                    author.setIsCorresponding(false);
                }
                
                // Matching con nuestra BD (pasar ORCID para matching prioritario)
                String openAlexOrcid = author.getOrcid();
                if (rawAuthorName != null && !rawAuthorName.isEmpty()) {
                    ResearcherMatchDTO match = matchResearcher(rawAuthorName, authorNode, openAlexOrcid);
                    author.setResearcherMatch(match);
                }
                
                // Afiliaciones
                author.setAffiliations(processAffiliations(authorNode));
                
                authors.add(author);
                
            } catch (Exception e) {
                System.err.println("Error processing author " + i + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        return authors;
    }
    
    
    /**
     * Hace matching de un investigador usando ResearcherMatchingService
     * Prioriza matching por ORCID, luego por nombre
     * NO crea investigadores automáticamente - eso se hará cuando el usuario confirme la publicación
     */
    private ResearcherMatchDTO matchResearcher(String name, JsonNode authorNode, String openAlexOrcid) {
        ResearcherMatchDTO match = new ResearcherMatchDTO();
        
        try {
            // Casos especiales hardcodeados (del código original)
            Long specialCaseId = getSpecialCaseResearcher(name);
            if (specialCaseId != null) {
                match.setMatchedId(specialCaseId);
                match.setMatchStatus("UNICA");
                match.setMatchedName(name);
                match.setMatchMethod("name");
                // Obtener datos completos de BD
                com.sisgic.entity.RRHH rrhh = rrhhRepository.findByIdWithTipoRRHH(specialCaseId);
                if (rrhh != null) {
                    match.setMatchedName(rrhh.getFullname());
                    match.setMatchedOrcid(rrhh.getOrcid());
                    // Verificar estado de ORCID
                    checkOrcidSyncStatus(match, openAlexOrcid, rrhh.getOrcid());
                }
                return match;
            }
            
            // PASO 1: Intentar matching por ORCID primero (si OpenAlex tiene ORCID)
            if (openAlexOrcid != null && !openAlexOrcid.trim().isEmpty()) {
                Long rrhhIdByOrcid = researcherMatchingService.getRRHHByOrcid(openAlexOrcid);
                if (rrhhIdByOrcid != null) {
                    // Match encontrado por ORCID
                    com.sisgic.entity.RRHH rrhh = rrhhRepository.findByIdWithTipoRRHH(rrhhIdByOrcid);
                    if (rrhh != null) {
                        match.setMatchedId(rrhhIdByOrcid);
                        match.setMatchStatus("UNICA");
                        match.setMatchedName(rrhh.getFullname());
                        match.setMatchedOrcid(rrhh.getOrcid());
                        match.setMatchMethod("orcid");
                        // ORCID coincide, estado OK
                        match.setOrcidSyncStatus("ok");
                        return match;
                    }
                }
            }
            
            // PASO 2: Si no hay match por ORCID, intentar por nombre
            ResearcherMatchingService.Resultado resultado = researcherMatchingService.encontrarMejor(name);
            
            // Si no encuentra, intentar con display_name
            if (resultado.estatus == ResearcherMatchingService.Estatus.SIN_COINCIDENCIAS || 
                (resultado.estatus == ResearcherMatchingService.Estatus.MAS_DE_UNA && resultado.elegido == null)) {
                
                if (authorNode.has("author") && !authorNode.get("author").isNull()) {
                    JsonNode authorObj = authorNode.get("author");
                    if (authorObj.has("display_name") && !authorObj.get("display_name").isNull()) {
                        String displayName = authorObj.get("display_name").asText();
                        if (!displayName.equals(name)) {
                            resultado = researcherMatchingService.encontrarMejor(displayName);
                        }
                    }
                }
            }
            
            // Mapear resultado
            match.setMatchStatus(resultado.estatus.name());
            match.setMatchMethod("name");
            
            if (resultado.elegido != null) {
                Long rrhhId = resultado.getIdRRHH();
                match.setMatchedId(rrhhId);
                
                // Obtener datos completos de BD
                com.sisgic.entity.RRHH rrhh = rrhhRepository.findByIdWithTipoRRHH(rrhhId);
                if (rrhh != null) {
                    match.setMatchedName(rrhh.getFullname());
                    match.setMatchedOrcid(rrhh.getOrcid());
                    // Verificar estado de sincronización de ORCID
                    checkOrcidSyncStatus(match, openAlexOrcid, rrhh.getOrcid());
                } else {
                    match.setMatchedName(resultado.getName());
                    match.setMatchedOrcid(resultado.getOrcid());
                }
            }
            
            if (resultado.detalle != null) {
                match.setDetail(resultado.detalle);
                // Parsear candidatos si hay múltiples
                if (resultado.detalle.contains("/")) {
                    List<String> candidates = new ArrayList<>();
                    String[] parts = resultado.detalle.split("/");
                    for (String part : parts) {
                        candidates.add(part.trim());
                    }
                    match.setCandidates(candidates);
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error matching researcher " + name + ": " + e.getMessage());
            e.printStackTrace();
            match.setMatchStatus("SIN_COINCIDENCIAS");
        }
        
        return match;
    }
    
    /**
     * Verifica el estado de sincronización de ORCID entre OpenAlex y BD
     */
    private void checkOrcidSyncStatus(ResearcherMatchDTO match, String openAlexOrcid, String bdOrcid) {
        if (openAlexOrcid == null || openAlexOrcid.trim().isEmpty()) {
            // OpenAlex no tiene ORCID, no hay nada que sincronizar
            match.setOrcidSyncStatus("ok");
            return;
        }
        
        if (bdOrcid == null || bdOrcid.trim().isEmpty()) {
            // BD no tiene ORCID, OpenAlex sí → se puede agregar
            match.setOrcidSyncStatus("missing_local");
            return;
        }
        
        // Ambos tienen ORCID, verificar si coinciden
        if (openAlexOrcid.equals(bdOrcid)) {
            match.setOrcidSyncStatus("ok");
        } else {
            // Conflicto: ORCIDs diferentes
            match.setOrcidSyncStatus("conflict");
        }
    }
    
    /**
     * Casos especiales de investigadores con nombres que no matchean bien
     */
    private Long getSpecialCaseResearcher(String name) {
        // Mapeo de casos especiales del código original
        if (name.equals("Nicolás A. Muena")) return 158L;
        if (name.equals("Pablo Díaz")) return 436L;
        if (name.equals("S. Poblete")) return 2386L;
        if (name.equals("MariaLoreto Bravo")) return 1899L;
        if (name.equals("BastiánI Rivera")) return 2208L;
        if (name.equals("CatalinaM Polanco")) return 1706L;
        if (name.equals("Rodrigo A. Villanueva")) return 215L;
        if (name.equals("A. R. Ruiz-Fernández")) return 58L;
        if (name.equals("AngieK Torres")) return 1641L;
        if (name.equals("Fernando D. Gonzalez-Nilo")) return 1533L;
        if (name.equals("Fernanda M. Rozas-Villanueva")) return 2301L;
        if (name.equals("P. Contreras")) return null; // Se maneja como "Patricio Contreras"
        
        return null;
    }
    
    /**
     * Procesa las afiliaciones de un autor
     */
    private List<InstitutionFromOpenAlexDTO> processAffiliations(JsonNode authorNode) {
        List<InstitutionFromOpenAlexDTO> affiliations = new ArrayList<>();
        
        try {
            // Primero, mapear instituciones por ID (tanto el ID como el nombre)
            HashMap<String, Long> institutionIdMap = new HashMap<>();
            HashMap<String, String> institutionNameMap = new HashMap<>();
            if (authorNode.has("institutions") && authorNode.get("institutions").isArray()) {
                for (JsonNode institution : authorNode.get("institutions")) {
                    if (institution.has("id") && !institution.get("id").isNull()) {
                        String institutionId = institution.get("id").asText();
                        String displayName = institution.has("display_name") && !institution.get("display_name").isNull() ?
                                           institution.get("display_name").asText() : null;
                        String countryCode = institution.has("country_code") && !institution.get("country_code").isNull() ?
                                           institution.get("country_code").asText() : null;
                        
                        if (displayName != null) {
                            Long matchedId = institutionMatchingService.getInstitution(displayName, countryCode);
                            institutionIdMap.put(institutionId, matchedId);
                            institutionNameMap.put(institutionId, displayName);
                        }
                    }
                }
            }
            
            // Procesar afiliaciones
            if (authorNode.has("affiliations") && authorNode.get("affiliations").isArray()) {
                for (JsonNode affiliation : authorNode.get("affiliations")) {
                    InstitutionFromOpenAlexDTO aff = new InstitutionFromOpenAlexDTO();
                    
                    // Raw affiliation string
                    if (affiliation.has("raw_affiliation_string") && !affiliation.get("raw_affiliation_string").isNull()) {
                        aff.setRawAffiliationString(affiliation.get("raw_affiliation_string").asText());
                    }
                    
                    // Institution IDs
                    if (affiliation.has("institution_ids") && affiliation.get("institution_ids").isArray() && 
                        affiliation.get("institution_ids").size() > 0) {
                        String firstInstitutionId = affiliation.get("institution_ids").get(0).asText();
                        Long matchedId = institutionIdMap.get(firstInstitutionId);
                        String institutionName = institutionNameMap.get(firstInstitutionId);
                        aff.setMatchedInstitutionId(matchedId);
                        aff.setFoundInDatabase(matchedId != null);
                        aff.setName(institutionName); // Setear el nombre de la institución
                    } else {
                        aff.setFoundInDatabase(false);
                        // Si no hay institution_ids, usar el raw_affiliation_string como nombre
                        if (aff.getRawAffiliationString() != null) {
                            aff.setName(aff.getRawAffiliationString());
                        }
                    }
                    
                    affiliations.add(aff);
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error processing affiliations: " + e.getMessage());
            e.printStackTrace();
        }
        
        return affiliations;
    }
    
    /**
     * Calcula el progressReport basado en la fecha de publicación
     */
    public Integer calculateProgressReport(String publicationDate) {
        if (publicationDate == null || publicationDate.isEmpty()) {
            return null;
        }
        
        try {
            LocalDate date = LocalDate.parse(publicationDate, DATE_FORMATTER);
            LocalDate cutoff1 = LocalDate.of(2022, 7, 31);
            LocalDate cutoff2 = LocalDate.of(2023, 7, 31);
            LocalDate cutoff3 = LocalDate.of(2024, 7, 31);
            LocalDate cutoff4 = LocalDate.of(2025, 7, 31);
            
            if (date.isBefore(cutoff1) || date.isEqual(cutoff1)) {
                return 1;
            } else if (date.isBefore(cutoff2) || date.isEqual(cutoff2)) {
                return 2;
            } else if (date.isBefore(cutoff3) || date.isEqual(cutoff3)) {
                return 3;
            } else if (date.isBefore(cutoff4) || date.isEqual(cutoff4)) {
                return 4;
            } else {
                return 5;
            }
        } catch (Exception e) {
            System.err.println("Error calculating progressReport: " + e.getMessage());
            return null;
        }
    }
    
    public String getDefaultProjectCode() {
        return defaultProjectCode;
    }

    /**
     * Usa OpenAlex para intentar obtener un PDF para la publicación dada.
     * Si OpenAlex retorna primary_location.pdf_url y el PDF se descarga correctamente,
     * se guarda en el directorio de PDFs y se actualiza el campo linkPDF de la publicación
     * con el formato "PDF:pdfs/{uuid}.pdf".
     *
     * Este método está pensado para ser llamado después de crear la publicación
     * (por ejemplo, en el flujo de importación desde DOI).
     */
    public void attachPdfFromOpenAlexIfAvailable(Publicacion publicacion) {
        if (publicacion == null || publicacion.getDoi() == null || publicacion.getDoi().trim().isEmpty()) {
            return;
        }

        try {
            // Normalizar DOI igual que en las otras llamadas a OpenAlex
            String normalizedDoi = normalizeDoi(publicacion.getDoi());
            String openAlexUrl = OPENALEX_BASE_URL + DOI_PREFIX + normalizedDoi;

            ResponseEntity<String> response = restTemplate.getForEntity(openAlexUrl, String.class);
            System.out.println("openAlexUrl: " + openAlexUrl + " response: " + response.getStatusCode());
            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                return;
            }

            JsonNode json = objectMapper.readTree(response.getBody());
            if (json == null || json.isNull()) {
                return;
            }

            // Navegar a primary_location.pdf_url
            if (!json.has("primary_location") || json.get("primary_location").isNull()) {
                return;
            }

            JsonNode primaryLocation = json.get("primary_location");
            if (!primaryLocation.has("pdf_url") || primaryLocation.get("pdf_url").isNull()) {
                return;
            }

            String pdfUrl = primaryLocation.get("pdf_url").asText(null);
            if (pdfUrl == null || pdfUrl.trim().isEmpty()) {
                return;
            }

            // Descargar y guardar PDF
            String linkPdf = pdfFileService.downloadAndSavePdfFromUrl(pdfUrl);
            if (linkPdf == null || linkPdf.trim().isEmpty()) {
                return;
            }

            // Actualizar publicación en BD
            publicacion.setLinkPDF(linkPdf);
            publicacionRepository.save(publicacion);

        } catch (Exception e) {
            System.err.println("OpenAlexService: Error al adjuntar PDF desde OpenAlex: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Convierte PublicationFromDoiDTO a PublicationPreviewDTO con estados normalizados
     */
    private PublicationPreviewDTO convertToPreviewDTO(PublicationFromDoiDTO data) {
        PublicationPreviewDTO preview = new PublicationPreviewDTO();
        
        // Datos de publicación
        PublicationPreviewDataDTO pubData = new PublicationPreviewDataDTO();
        pubData.setTitle(data.getTitle());
        pubData.setDisplayName(data.getDisplayName());
        pubData.setPublicationDate(data.getPublicationDate());
        pubData.setPublicationYear(data.getPublicationYear());
        pubData.setDoi(data.getDoi());
        pubData.setVolume(data.getVolume());
        pubData.setFirstPage(data.getFirstPage());
        pubData.setLastPage(data.getLastPage());
        pubData.setOpenAlexUrl(data.getOpenAlexUrl());
        pubData.setLinkPDF(data.getLinkPDF());
        preview.setPublication(pubData);
        
        // Journal con estado normalizado
        JournalPreviewDTO journalPreview = new JournalPreviewDTO();
        if (data.getJournalMatch() != null) {
            journalPreview.setName(data.getJournalMatch().getName());
            journalPreview.setIssn(data.getJournalMatch().getIssn());
            if (data.getJournalMatch().getFoundInDatabase() != null && data.getJournalMatch().getFoundInDatabase()) {
                journalPreview.setStatus("matched");
                journalPreview.setMatchId(data.getJournalMatch().getId());
            } else {
                journalPreview.setStatus("new");
            }
        } else {
            journalPreview.setStatus("new");
        }
        preview.setJournal(journalPreview);
        
        // Autores con estados normalizados
        List<AuthorPreviewDTO> authorsPreview = new ArrayList<>();
        if (data.getAuthors() != null) {
            for (AuthorFromOpenAlexDTO author : data.getAuthors()) {
                AuthorPreviewDTO authorPreview = new AuthorPreviewDTO();
                authorPreview.setOpenAlexId(author.getOpenAlexId());
                authorPreview.setName(author.getFullName());
                authorPreview.setOrcid(author.getOrcid());
                authorPreview.setOrder(author.getOrder());
                authorPreview.setIsCorresponding(author.getIsCorresponding());
                authorPreview.setAuthorPosition(author.getAuthorPosition());
                
                // Calcular tipoParticipacionId basado en authorPosition
                // "first" o "last" → 1 (Author principal)
                // "middle" → 2 (Co-author)
                // Si no hay authorPosition, calcular basado en el orden
                Long tipoParticipacionId = null;
                String position = author.getAuthorPosition();
                if (position != null && !position.isEmpty()) {
                    if ("first".equalsIgnoreCase(position) || "last".equalsIgnoreCase(position)) {
                        tipoParticipacionId = 1L; // Author principal
                    } else if ("middle".equalsIgnoreCase(position)) {
                        tipoParticipacionId = 2L; // Co-author
                    }
                } else {
                    // Si no hay authorPosition, calcular basado en el orden
                    // Primer autor o último autor = Author principal (1)
                    // Otros = Co-author (2)
                    int totalAuthors = data.getAuthors() != null ? data.getAuthors().size() : 0;
                    int order = author.getOrder() != null ? author.getOrder() : 0;
                    if (totalAuthors == 1 || order == 1 || order == totalAuthors) {
                        tipoParticipacionId = 1L; // Author principal
                    } else {
                        tipoParticipacionId = 2L; // Co-author
                    }
                }
                authorPreview.setTipoParticipacionId(tipoParticipacionId);
                
                // Mapear estado del matching
                if (author.getResearcherMatch() != null) {
                    ResearcherMatchDTO match = author.getResearcherMatch();
                    String matchStatus = match.getMatchStatus();
                    
                    if ("UNICA".equals(matchStatus)) {
                        authorPreview.setStatus("matched");
                        authorPreview.setMatchId(match.getMatchedId());
                        
                        // Usar datos de BD cuando hay match (no los de OpenAlex)
                        if (match.getMatchedName() != null) {
                            authorPreview.setMatchedName(match.getMatchedName());
                            // El nombre mostrado debe ser el de BD, no el de OpenAlex
                            authorPreview.setName(match.getMatchedName());
                        }
                        
                        // ORCID de BD
                        if (match.getMatchedOrcid() != null) {
                            authorPreview.setMatchedOrcid(match.getMatchedOrcid());
                        }
                        
                        // Estado de sincronización de ORCID
                        if (match.getOrcidSyncStatus() != null) {
                            authorPreview.setOrcidSyncStatus(match.getOrcidSyncStatus());
                            // Por defecto: si falta en BD, ofrecer agregarlo; si hay conflicto, no hacer nada hasta que el usuario decida
                            if ("missing_local".equals(match.getOrcidSyncStatus())) {
                                authorPreview.setOrcidChangeAction("add"); // Por defecto, agregar
                            } else if ("conflict".equals(match.getOrcidSyncStatus())) {
                                authorPreview.setOrcidChangeAction("none"); // Por defecto, no cambiar hasta que el usuario decida
                            } else {
                                authorPreview.setOrcidChangeAction("none");
                            }
                        }
                        
                        // Método de matching
                        if (match.getMatchMethod() != null) {
                            authorPreview.setMatchMethod(match.getMatchMethod());
                        }
                        
                    } else if ("MAS_DE_UNA".equals(matchStatus)) {
                        authorPreview.setStatus("review");
                        authorPreview.setCandidates(match.getCandidates());
                        // Si hay un elegido, también incluirlo
                        if (match.getMatchedId() != null) {
                            authorPreview.setMatchId(match.getMatchedId());
                            if (match.getMatchedName() != null) {
                                authorPreview.setMatchedName(match.getMatchedName());
                            }
                            if (match.getMatchedOrcid() != null) {
                                authorPreview.setMatchedOrcid(match.getMatchedOrcid());
                            }
                            if (match.getOrcidSyncStatus() != null) {
                                authorPreview.setOrcidSyncStatus(match.getOrcidSyncStatus());
                            }
                            if (match.getMatchMethod() != null) {
                                authorPreview.setMatchMethod(match.getMatchMethod());
                            }
                        }
                    } else {
                        authorPreview.setStatus("new");
                    }
                } else {
                    authorPreview.setStatus("new");
                }
                
                // Afiliaciones con estados normalizados
                List<AffiliationPreviewDTO> affiliationsPreview = new ArrayList<>();
                if (author.getAffiliations() != null) {
                    for (InstitutionFromOpenAlexDTO aff : author.getAffiliations()) {
                        AffiliationPreviewDTO affPreview = new AffiliationPreviewDTO();
                        affPreview.setName(aff.getName());
                        affPreview.setRawAffiliationString(aff.getRawAffiliationString());
                        
                        if (aff.getFoundInDatabase() != null && aff.getFoundInDatabase()) {
                            affPreview.setStatus("matched");
                            affPreview.setMatchId(aff.getMatchedInstitutionId());
                        } else {
                            affPreview.setStatus("new");
                        }
                        
                        affiliationsPreview.add(affPreview);
                    }
                }
                authorPreview.setAffiliations(affiliationsPreview);
                
                authorsPreview.add(authorPreview);
            }
        }
        preview.setAuthors(authorsPreview);
        
        return preview;
    }
}

