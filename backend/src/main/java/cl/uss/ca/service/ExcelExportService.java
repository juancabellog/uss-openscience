package cl.uss.ca.service;

import cl.uss.ca.api.dto.CrossrefLinkedOutputDto;
import cl.uss.ca.api.dto.CrossrefWorkSummaryDto;
import cl.uss.ca.api.dto.DspaceItemListDto;
import cl.uss.ca.api.dto.OpenAlexMatchSummaryDto;
import cl.uss.ca.api.dto.DataQualityFindingDto;
import cl.uss.ca.api.dto.DataQualityFindingEvidenceDto;
import cl.uss.ca.api.dto.JournalSummaryDto;
import cl.uss.ca.api.dto.OrganizationSummaryDto;
import cl.uss.ca.api.dto.PersonSummaryDto;
import cl.uss.ca.api.dto.ProjectSummaryDto;
import cl.uss.ca.api.dto.ResearcherIdentityCaseDto;
import cl.uss.ca.api.dto.ResearchOutputSummaryDto;
import cl.uss.ca.api.dto.UnpaywallLinkedOutputDto;
import cl.uss.ca.api.dto.UnpaywallWorkSummaryDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@ApplicationScoped
public class ExcelExportService {
    private static final String XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final DateTimeFormatter FILE_TS =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    public Response exportPeople(List<PersonSummaryDto> rows) {
        String[] headers = {
                "ID PURE", "UUID", "Nombre", "Cargo", "Email", "ORCID",
                "ORCID autenticado", "Scopus IDs", "Número Scopus IDs",
                "Publicaciones", "H-index Scopus (todos los años)", "Citas Scopus (todos los años)",
                "Activo en PURE", "Created By", "Modified By"
        };
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Personas");
            writeHeader(workbook, sheet, headers);
            int rowIndex = 1;
            for (PersonSummaryDto row : rows) {
                Row excelRow = sheet.createRow(rowIndex++);
                int col = 0;
                setNumber(excelRow, col++, row.pureId);
                setText(excelRow, col++, row.uuid);
                setText(excelRow, col++, fullName(row));
                setText(excelRow, col++, row.position);
                setText(excelRow, col++, row.email);
                setText(excelRow, col++, row.orcid);
                setText(excelRow, col++, booleanLabel(row.orcidAuthenticated));
                setText(excelRow, col++, joinScopus(row.scopusIds));
                setNumber(excelRow, col++, row.scopusIdCount);
                setNumber(excelRow, col++, row.publicationCount);
                setNumber(excelRow, col++, row.scopusHindex);
                setNumber(excelRow, col++, row.scopusCitations);
                setText(excelRow, col++, booleanLabel(row.activeInPure));
                setText(excelRow, col++, row.createdBy);
                setText(excelRow, col, row.modifiedBy);
            }
            autosize(sheet, headers.length);
            workbook.write(buffer);
            return attachment(buffer.toByteArray(), "openscience-personas-" + timestamp() + ".xlsx");
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate people Excel", ex);
        }
    }

    public Response exportUnpaywall(List<UnpaywallWorkSummaryDto> rows) {
        String[] headers = {
                "DOI", "ID PURE", "UUID", "Título", "is_oa", "oa_status",
                "host_type", "license", "version", "best URL", "repository copy", "retrieved_at"
        };
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Unpaywall");
            writeHeader(workbook, sheet, headers);
            int rowIndex = 1;
            for (UnpaywallWorkSummaryDto row : rows) {
                if (row.researchOutputs == null || row.researchOutputs.isEmpty()) {
                    rowIndex = writeUnpaywallRow(sheet, rowIndex, row, null);
                } else {
                    for (UnpaywallLinkedOutputDto output : row.researchOutputs) {
                        rowIndex = writeUnpaywallRow(sheet, rowIndex, row, output);
                    }
                }
            }
            autosize(sheet, headers.length);
            workbook.write(buffer);
            return attachment(buffer.toByteArray(), "openscience-unpaywall-" + timestamp() + ".xlsx");
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate Unpaywall Excel", ex);
        }
    }

    public Response exportCrossref(List<CrossrefWorkSummaryDto> rows) {
        String[] headers = {
                "ID PURE", "DOI", "Estado", "Título Crossref", "Título PURE", "Tipo",
                "Container", "ISSN", "Publisher", "Fecha", "ORCID", "Afiliaciones",
                "Funding", "Licencia", "Update/retraction", "Última sync"
        };
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Crossref");
            writeHeader(workbook, sheet, headers);
            int rowIndex = 1;
            for (CrossrefWorkSummaryDto row : rows) {
                if (row.researchOutputs == null || row.researchOutputs.isEmpty()) {
                    rowIndex = writeCrossrefRow(sheet, rowIndex, row, null);
                } else {
                    for (CrossrefLinkedOutputDto output : row.researchOutputs) {
                        rowIndex = writeCrossrefRow(sheet, rowIndex, row, output);
                    }
                }
            }
            autosize(sheet, headers.length);
            workbook.write(buffer);
            return attachment(buffer.toByteArray(), "openscience-crossref-" + timestamp() + ".xlsx");
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate Crossref Excel", ex);
        }
    }

    public Response exportOpenAlex(List<OpenAlexMatchSummaryDto> rows) {
        String[] headers = {
                "ID PURE", "Nombre", "Cargo", "ORCID", "OpenAlex ID", "OpenAlex display name",
                "Publicaciones coincidentes", "Estado", "Método", "Confirmado manualmente", "Última evaluación"
        };
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("OpenAlex");
            writeHeader(workbook, sheet, headers);
            int rowIndex = 1;
            for (OpenAlexMatchSummaryDto row : rows) {
                Row excelRow = sheet.createRow(rowIndex++);
                int col = 0;
                setNumber(excelRow, col++, row.personPureId);
                setText(excelRow, col++, row.personName);
                setText(excelRow, col++, row.personPosition);
                setText(excelRow, col++, row.personOrcid);
                setText(excelRow, col++, row.openalexAuthorId);
                setText(excelRow, col++, row.openalexDisplayName);
                setNumber(excelRow, col++, row.matchingWorkCount);
                setText(excelRow, col++, row.status);
                setText(excelRow, col++, row.matchMethod);
                setText(excelRow, col++, booleanLabel(row.selectedManually));
                setText(excelRow, col, row.lastEvaluatedAt == null ? null : row.lastEvaluatedAt.toString());
            }
            autosize(sheet, headers.length);
            workbook.write(buffer);
            return attachment(buffer.toByteArray(), "openscience-openalex-" + timestamp() + ".xlsx");
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate OpenAlex Excel", ex);
        }
    }

    public Response exportResearchOutputs(List<ResearchOutputSummaryDto> rows) {
        String[] headers = {
                "ID PURE", "UUID", "Título", "Año", "Tipo",
                "Workflow step", "Workflow ES", "Workflow EN",
                "DOI", "Versiones DOI", "Revista",
                "Nº contribuidores", "FWCI", "Año FWCI",
                "Activo en PURE", "Created By", "Modified By"
        };
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Produccion");
            writeHeader(workbook, sheet, headers);
            int rowIndex = 1;
            for (ResearchOutputSummaryDto row : rows) {
                Row excelRow = sheet.createRow(rowIndex++);
                int col = 0;
                setNumber(excelRow, col++, row.pureId);
                setText(excelRow, col++, row.uuid);
                setText(excelRow, col++, row.title);
                setNumber(excelRow, col++, row.year);
                setText(excelRow, col++, row.type);
                setText(excelRow, col++, row.workflowStep);
                setText(excelRow, col++, row.workflowEs);
                setText(excelRow, col++, row.workflowEn);
                setText(excelRow, col++, row.doi);
                setNumber(excelRow, col++, row.doiVersionCount);
                setText(excelRow, col++, row.journal);
                setNumber(excelRow, col++, row.contributorCount);
                if (row.fwci != null) {
                    setText(excelRow, col++, row.fwci.toPlainString());
                } else {
                    setText(excelRow, col++, null);
                }
                setNumber(excelRow, col++, row.fwciYear);
                setText(excelRow, col++, booleanLabel(row.activeInPure));
                setText(excelRow, col++, row.createdBy);
                setText(excelRow, col, row.modifiedBy);
            }
            autosize(sheet, headers.length);
            workbook.write(buffer);
            return attachment(
                    buffer.toByteArray(),
                    "openscience-produccion-cientifica-" + timestamp() + ".xlsx"
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate research-output Excel", ex);
        }
    }

    public Response exportJournals(List<JournalSummaryDto> rows) {
        String[] headers = {
                "ID PURE", "UUID", "Revista", "ISSNs", "Publicaciones asociadas", "Scopus ID", "Tipo",
                "Workflow", "Workflow ES", "Workflow EN",
                "CiteScore (Scopus)", "Año CiteScore", "Cuartil(es)", "Año cuartil",
                "Activo en PURE", "Created By", "Modified By"
        };
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Revistas");
            writeHeader(workbook, sheet, headers);
            int rowIndex = 1;
            for (JournalSummaryDto row : rows) {
                Row excelRow = sheet.createRow(rowIndex++);
                int col = 0;
                setNumber(excelRow, col++, row.pureId);
                setText(excelRow, col++, row.uuid);
                setText(excelRow, col++, row.title);
                setText(excelRow, col++, row.issns == null ? null : String.join("; ", row.issns));
                setNumber(excelRow, col++, row.publicationCount == null ? 0 : row.publicationCount);
                setText(excelRow, col++, row.scopusId);
                setText(excelRow, col++, row.type);
                setText(excelRow, col++, row.workflowStep);
                setText(excelRow, col++, row.workflowEs);
                setText(excelRow, col++, row.workflowEn);
                if (row.citeScore != null) {
                    setText(excelRow, col++, row.citeScore.toPlainString());
                } else {
                    setText(excelRow, col++, null);
                }
                setNumber(excelRow, col++, row.citeScoreYear);
                setText(excelRow, col++, row.quartilesCompact);
                setNumber(excelRow, col++, row.quartileYear);
                setText(excelRow, col++, booleanLabel(row.activeInPure));
                setText(excelRow, col++, row.createdBy);
                setText(excelRow, col, row.modifiedBy);
            }
            autosize(sheet, headers.length);
            workbook.write(buffer);
            return attachment(buffer.toByteArray(), "openscience-revistas-" + timestamp() + ".xlsx");
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate journal Excel", ex);
        }
    }

    public Response exportProjects(List<ProjectSummaryDto> rows) {
        String[] headers = {
                "ID PURE", "UUID", "Título", "Acrónimo", "Project ID", "Referencia financiador",
                "Tipo", "Inicio", "Término", "Investigador(a) Responsable",
                "Organización gestora", "Organización gestora UUID", "Workflow", "Visibilidad", "Activo en PURE", "Portal PURE"
        };
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Proyectos");
            writeHeader(workbook, sheet, headers);
            int rowIndex = 1;
            for (ProjectSummaryDto row : rows) {
                Row excelRow = sheet.createRow(rowIndex++);
                int col = 0;
                setNumber(excelRow, col++, row.pureId);
                setText(excelRow, col++, row.uuid);
                setText(excelRow, col++, row.title);
                setText(excelRow, col++, row.acronym);
                setText(excelRow, col++, row.projectId);
                setText(excelRow, col++, row.funderReference);
                setText(excelRow, col++, row.type);
                setText(excelRow, col++, row.periodStartDate);
                setText(excelRow, col++, row.periodEndDate);
                setText(excelRow, col++, row.principalInvestigatorName);
                setText(excelRow, col++, firstNonBlank(row.managingOrganizationName, row.managingOrganizationNameEs, row.managingOrganizationNameEn));
                setText(excelRow, col++, row.managingOrganizationUuid);
                setText(excelRow, col++, row.workflowStep);
                setText(excelRow, col++, row.visibilityEs != null ? row.visibilityEs : row.visibilityKey);
                setText(excelRow, col++, booleanLabel(row.activeInPure));
                setText(excelRow, col, row.portalUrl);
            }
            autosize(sheet, headers.length);
            workbook.write(buffer);
            return attachment(buffer.toByteArray(), "openscience-proyectos-" + timestamp() + ".xlsx");
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate project Excel", ex);
        }
    }

    public Response exportOrganizations(List<OrganizationSummaryDto> rows) {
        String[] headers = {
                "ID PURE", "UUID", "Nombre ES", "Nombre EN", "Tipo ES", "Tipo EN",
                "Organización superior", "UUID organización superior", "Activo en PURE",
                "Última modificación PURE", "Created By", "Modified By"
        };
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Organizaciones");
            writeHeader(workbook, sheet, headers);
            int rowIndex = 1;
            for (OrganizationSummaryDto row : rows) {
                Row excelRow = sheet.createRow(rowIndex++);
                int col = 0;
                setNumber(excelRow, col++, row.pureId);
                setText(excelRow, col++, row.uuid);
                setText(excelRow, col++, row.nameEs);
                setText(excelRow, col++, row.nameEn);
                setText(excelRow, col++, row.typeEs);
                setText(excelRow, col++, row.typeEn);
                setText(excelRow, col++, firstNonBlank(row.parentName, row.parentNameEs, row.parentNameEn));
                setText(excelRow, col++, row.parentUuid);
                setText(excelRow, col++, booleanLabel(row.activeInPure));
                setText(excelRow, col++, row.modifiedDatePure);
                setText(excelRow, col++, row.createdBy);
                setText(excelRow, col, row.modifiedBy);
            }
            autosize(sheet, headers.length);
            workbook.write(buffer);
            return attachment(buffer.toByteArray(), "openscience-organizaciones-" + timestamp() + ".xlsx");
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate organization Excel", ex);
        }
    }

    public Response exportDspace(List<DspaceItemListDto> rows, List<String[]> filters) {
        String[] headers = {
                "UUID", "Título", "DOI", "DOI normalizado", "DOI compartido", "Handle",
                "Colección", "Comunidad", "Acceso DSpace", "Acceso PURE", "Comparación",
                "PURE", "Research Output UUID", "Archivo", "Última sincronización", "Estado"
        };
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("DSpace");
            writeHeader(workbook, sheet, headers);
            int rowIndex = 1;
            for (DspaceItemListDto row : rows) {
                Row excelRow = sheet.createRow(rowIndex++);
                int col = 0;
                setText(excelRow, col++, row.uuid);
                setText(excelRow, col++, row.name);
                setText(excelRow, col++, row.doi);
                setText(excelRow, col++, row.normalizedDoi);
                setNumber(excelRow, col++, row.sharedDoiCount);
                setText(excelRow, col++, row.handle);
                setText(excelRow, col++, row.collectionName);
                setText(excelRow, col++, row.communityName);
                setText(excelRow, col++, row.dspaceAccessCanonical);
                setText(excelRow, col++, row.pureAccessCanonical);
                setText(excelRow, col++, row.accessComparison);
                setText(excelRow, col++, row.matchStatus);
                setText(excelRow, col++, row.researchOutputUuid);
                setText(excelRow, col++, row.hasOriginalFile ? "Sí" : "No");
                setText(excelRow, col++, row.lastSyncAt == null ? null : row.lastSyncAt.toString());
                setText(excelRow, col, row.lastOutcome);
            }
            autosize(sheet, headers.length);
            writeFilterSheet(workbook, filters);
            workbook.write(buffer);
            return attachment(buffer.toByteArray(), "openscience-dspace-" + timestamp() + ".xlsx");
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate DSpace Excel", ex);
        }
    }

    public Response exportDataQuality(List<DataQualityFindingDto> rows) {
        String[] headers = {
                "Código regla", "Regla", "Entidad", "ID PURE", "UUID", "Registro", "DOI",
                "Campo", "Valor PURE", "Fuente externa", "Valor externo", "Resultado comparación",
                "Problema", "Tipo de acción", "Estado", "Última detección", "Última sync externa"
        };
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Calidad");
            writeHeader(workbook, sheet, headers);
            int rowIndex = 1;
            for (DataQualityFindingDto row : rows) {
                List<DataQualityFindingEvidenceDto> evidences = row.evidences == null || row.evidences.isEmpty()
                        ? java.util.List.of(new DataQualityFindingEvidenceDto())
                        : row.evidences;
                String doi = doiFromEvidenceJson(row.evidenceJson);
                for (DataQualityFindingEvidenceDto evidence : evidences) {
                    Row excelRow = sheet.createRow(rowIndex++);
                    int col = 0;
                    setText(excelRow, col++, row.ruleCode);
                    setText(excelRow, col++, row.ruleName);
                    setText(excelRow, col++, row.entityType);
                    setNumber(excelRow, col++, row.pureId);
                    setText(excelRow, col++, row.recordUuid);
                    setText(excelRow, col++, row.recordLabel);
                    setText(excelRow, col++, doi);
                    setText(excelRow, col++, row.fieldName);
                    setText(excelRow, col++, row.currentValue);
                    setText(excelRow, col++, evidence.source);
                    setText(excelRow, col++, evidence.externalValue);
                    setText(excelRow, col++, evidence.comparisonResult);
                    setText(excelRow, col++, row.summary);
                    setText(excelRow, col++, row.correctionMode);
                    setText(excelRow, col++, row.status);
                    setText(excelRow, col++, row.lastDetectedAt == null ? null : row.lastDetectedAt.toString());
                    setText(excelRow, col, evidence.externalLastSyncAt == null ? null : evidence.externalLastSyncAt.toString());
                }
            }
            autosize(sheet, headers.length);
            workbook.write(buffer);
            return attachment(buffer.toByteArray(), "openscience-calidad-datos-" + timestamp() + ".xlsx");
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate data-quality Excel", ex);
        }
    }

    private static String doiFromEvidenceJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        int start = json.indexOf("\"doi\"");
        if (start < 0) {
            return null;
        }
        int colon = json.indexOf(':', start);
        int quote = json.indexOf('"', colon + 1);
        int end = json.indexOf('"', quote + 1);
        if (quote < 0 || end < 0) {
            return null;
        }
        return json.substring(quote + 1, end);
    }

    public Response exportResearcherIdentityCases(String indicator, List<ResearcherIdentityCaseDto> rows) {
        boolean orcid = "normalizedUssOrcidAffiliation".equals(indicator) || "orcid-affiliation".equals(indicator);
        String[] headers = orcid
                ? new String[] {
                    "ID PURE", "Persona", "Cargo", "ORCID", "Employment ORCID", "Clasificación"
                }
                : new String[] {
                    "ID PURE", "Persona", "Cargo", "Publicaciones evaluadas", "Firmas distintas", "Firma encontrada"
                };
        String sheetName = orcid ? "ORCID" : "Firmas";
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName);
            writeHeader(workbook, sheet, headers);
            int rowIndex = 1;
            for (ResearcherIdentityCaseDto row : rows) {
                Row excelRow = sheet.createRow(rowIndex++);
                int col = 0;
                setNumber(excelRow, col++, row.pureId);
                setText(excelRow, col++, row.fullName);
                setText(excelRow, col++, row.position);
                if (orcid) {
                    setText(excelRow, col++, row.orcid);
                    setText(excelRow, col++, employmentExcel(row));
                    setText(excelRow, col, row.classification);
                } else {
                    setNumber(excelRow, col++, row.evaluatedPublications);
                    setNumber(excelRow, col++, row.distinctSignatureCount);
                    setText(excelRow, col, row.primarySignature);
                }
            }
            autosize(sheet, headers.length);
            workbook.write(buffer);
            return attachment(buffer.toByteArray(), "openscience-identidad-" + timestamp() + ".xlsx");
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate researcher-identity Excel", ex);
        }
    }

    private static int writeUnpaywallRow(
            Sheet sheet,
            int rowIndex,
            UnpaywallWorkSummaryDto row,
            UnpaywallLinkedOutputDto output
    ) {
        Row excelRow = sheet.createRow(rowIndex);
        int col = 0;
        setText(excelRow, col++, row.normalizedDoi != null ? row.normalizedDoi : row.doi);
        setNumber(excelRow, col++, output == null ? null : output.pureId);
        setText(excelRow, col++, output == null ? null : output.uuid);
        setText(excelRow, col++, output == null ? row.title : output.title);
        setText(excelRow, col++, oaFlag(row.isOa));
        setText(excelRow, col++, row.oaStatus);
        setText(excelRow, col++, row.bestOaHostType);
        setText(excelRow, col++, row.bestOaLicense);
        setText(excelRow, col++, row.bestOaVersion);
        setText(excelRow, col++, row.bestOaUrl);
        setText(excelRow, col++, oaFlag(row.hasRepositoryCopy));
        setText(excelRow, col, row.retrievedAt == null ? null : row.retrievedAt.toString());
        return rowIndex + 1;
    }

    private static int writeCrossrefRow(
            Sheet sheet,
            int rowIndex,
            CrossrefWorkSummaryDto row,
            CrossrefLinkedOutputDto output
    ) {
        Row excelRow = sheet.createRow(rowIndex);
        int col = 0;
        setNumber(excelRow, col++, output == null ? null : output.pureId);
        setText(excelRow, col++, row.normalizedDoi != null ? row.normalizedDoi : row.doi);
        setText(excelRow, col++, row.status);
        setText(excelRow, col++, row.title);
        setText(excelRow, col++, output == null ? row.publicationTitle : output.title);
        setText(excelRow, col++, row.workType);
        setText(excelRow, col++, row.containerTitle);
        setText(excelRow, col++, row.issnList);
        setText(excelRow, col++, row.publisher);
        setText(excelRow, col++, row.publishedDate);
        setText(excelRow, col++, oaFlag(row.hasOrcid));
        setText(excelRow, col++, oaFlag(row.hasAffiliation));
        setText(excelRow, col++, oaFlag(row.hasFunding));
        setText(excelRow, col++, oaFlag(row.hasLicense));
        setText(excelRow, col++, row.hasRetraction ? "RETRACTION" : row.hasUpdate ? "UPDATE" : null);
        setText(excelRow, col, row.lastCrossrefSyncAt == null ? null : row.lastCrossrefSyncAt.toString());
        return rowIndex + 1;
    }

    private static void writeFilterSheet(SXSSFWorkbook workbook, List<String[]> filters) {
        if (filters == null || filters.isEmpty()) {
            return;
        }
        Sheet sheet = workbook.createSheet("Filtros aplicados");
        writeHeader(workbook, sheet, new String[] {"Filtro", "Valor"});
        int rowIndex = 1;
        for (String[] pair : filters) {
            if (pair == null || pair.length < 2) {
                continue;
            }
            Row excelRow = sheet.createRow(rowIndex++);
            setText(excelRow, 0, pair[0]);
            setText(excelRow, 1, pair[1]);
        }
        autosize(sheet, 2);
    }

    private static void writeHeader(SXSSFWorkbook workbook, Sheet sheet, String[] headers) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, headers.length - 1));
    }

    private static void setText(Row row, int index, String value) {
        Cell cell = row.createCell(index);
        if (value == null) {
            return;
        }
        cell.setCellValue(protect(value));
    }

    private static void setNumber(Row row, int index, Number value) {
        Cell cell = row.createCell(index);
        if (value == null) {
            return;
        }
        cell.setCellValue(value.doubleValue());
    }

    private static String protect(String value) {
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            return value;
        }
        char first = trimmed.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            return "'" + value;
        }
        return value;
    }

    private static String joinScopus(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return String.join("; ", ids);
    }

    private static String employmentExcel(ResearcherIdentityCaseDto row) {
        if (row.employmentOrganizationName != null && !row.employmentOrganizationName.isBlank()) {
            return row.employmentOrganizationName;
        }
        if (row.employmentOrganizationNames == null || row.employmentOrganizationNames.isEmpty()) {
            return null;
        }
        return String.join(" | ", row.employmentOrganizationNames);
    }

    private static String fullName(PersonSummaryDto row) {
        if (row.fullName != null && !row.fullName.isBlank()) {
            return row.fullName;
        }
        String last = row.lastName == null ? "" : row.lastName.trim();
        String first = row.firstName == null ? "" : row.firstName.trim();
        if (!last.isEmpty() && !first.isEmpty()) {
            return last + ", " + first;
        }
        return (last + " " + first).trim();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String booleanLabel(Boolean value) {
        return Boolean.FALSE.equals(value) ? "No" : "Sí";
    }

    private static String oaFlag(Boolean value) {
        if (value == null) {
            return null;
        }
        return value ? "Sí" : "No";
    }

    private static void autosize(Sheet sheet, int columns) {
        for (int i = 0; i < columns; i++) {
            sheet.setColumnWidth(i, 18 * 256);
        }
        sheet.setColumnWidth(2, 36 * 256);
    }

    private static String timestamp() {
        return ZonedDateTime.now(ZoneId.of("America/Santiago")).format(FILE_TS);
    }

    private static Response attachment(byte[] bytes, String filename) {
        return Response.ok(bytes)
                .type(XLSX)
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .header("Access-Control-Expose-Headers", "Content-Disposition")
                .build();
    }
}
