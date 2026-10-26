package cl.uss.ca.service;

import cl.uss.ca.api.dto.EventAdminRegistrationDto;
import cl.uss.ca.api.dto.EventArtifactDto;
import cl.uss.ca.api.dto.EventAttendanceDto;
import cl.uss.ca.api.dto.EventAttendanceImportDto;
import cl.uss.ca.api.dto.EventAttendanceImportRequestDto;
import cl.uss.ca.api.dto.EventAttendanceImportRowDto;
import cl.uss.ca.api.dto.EventAttendanceWriteDto;
import cl.uss.ca.api.dto.EventDocumentDto;
import cl.uss.ca.api.dto.EventIndicatorsDto;
import cl.uss.ca.api.dto.EventRegistrationReceiptDto;
import cl.uss.ca.events.EventAttendancePolicy;
import cl.uss.ca.events.EventLanguage;
import cl.uss.ca.events.EventAttendanceStatus;
import cl.uss.ca.events.EventDateTimes;
import cl.uss.ca.events.EventFileStore;
import cl.uss.ca.events.EventMinutesPdf;
import cl.uss.ca.events.EventOrganizingUnits;
import cl.uss.ca.events.EventPublicationStatus;
import cl.uss.ca.events.EventRules;
import cl.uss.ca.exception.ConflictException;
import cl.uss.ca.exception.ValidationException;
import cl.uss.ca.repository.EventOpsRecord;
import cl.uss.ca.repository.EventOpsRepository;
import cl.uss.ca.repository.EventRecord;
import cl.uss.ca.repository.EventRepository;
import cl.uss.ca.repository.EventSignupRecord;
import cl.uss.ca.repository.EventSignupRepository;
import cl.uss.ca.security.CurrentUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@ApplicationScoped
public class EventOpsService {

    @Inject
    EventService eventService;

    @Inject
    EventRegistrationService registrationService;

    @Inject
    EventRepository eventRepository;

    @Inject
    EventSignupRepository signupRepository;

    @Inject
    EventOpsRepository opsRepository;

    @Inject
    EventFileStore fileStore;

    public EventArtifactDto generateMinutes(long eventId, Integer blankRows, CurrentUser user) {
        return generateMinutes(eventId, blankRows, EventLanguage.ES, user);
    }

    public EventArtifactDto generateMinutes(long eventId, Integer blankRows, String language, CurrentUser user) {
        requireArtifacts();
        EventRecord event = eventRepository.requireById(eventId);
        assertOpen(eventId);
        String lang = EventLanguage.normalize(language);
        int blanks = EventAttendancePolicy.blankRows(blankRows != null ? blankRows : event.blankSignatureRows);
        List<EventSignupRecord> registrations = signupRepository.listByEvent(eventId).stream()
                .filter(item -> "CONFIRMED".equals(item.status))
                .toList();
        EventMinutesPdf.MinutesDocument document = new EventMinutesPdf.MinutesDocument();
        document.title = EventLanguage.isEnglish(lang) && event.titleEn != null && !event.titleEn.isBlank()
                ? event.titleEn
                : event.title;
        document.language = lang;
        document.when = formatWindow(event.startsAt, event.endsAt, lang);
        document.place = place(event);
        document.unit = EventOrganizingUnits.display(
                event.organizingUnitCode,
                event.organizingUnit,
                lang
        );
        document.identifier = event.publicSlug;
        document.version = opsRepository.nextArtifactVersion(eventId);
        List<EventOpsRecord.ArtifactRow> storedRows = new ArrayList<>();
        int number = 1;
        for (EventSignupRecord registration : registrations) {
            EventMinutesPdf.Row row = new EventMinutesPdf.Row();
            row.number = number;
            row.name = registration.displayName;
            row.faculty = registration.facultyOrInstitution;
            row.position = registration.positionTitle;
            row.email = registration.emailNormalized;
            row.publicCode = registration.publicCode;
            row.registrationId = registration.id;
            document.rows.add(row);
            storedRows.add(artifactRow(number, registration, false));
            number++;
        }
        for (int i = 0; i < blanks; i++) {
            EventMinutesPdf.Row row = new EventMinutesPdf.Row();
            row.number = number;
            row.blank = true;
            document.rows.add(row);
            storedRows.add(artifactRow(number, null, true));
            number++;
        }
        byte[] pdf = EventMinutesPdf.render(document, lang);
        EventFileStore.Stored stored = fileStore.save(pdf, filename(event, document.version, lang));
        EventOpsRecord.Artifact artifact = new EventOpsRecord.Artifact();
        artifact.eventId = eventId;
        artifact.versionNo = document.version;
        artifact.kind = "MINUTES_PDF";
        artifact.originalFilename = filename(event, document.version, lang);
        artifact.storedName = stored.storedName();
        artifact.sha256 = stored.sha256();
        artifact.byteSize = stored.byteSize();
        artifact.blankRows = blanks;
        artifact.generatedBy = actor(user);
        long id = opsRepository.insertArtifact(artifact, storedRows);
        return toArtifact(opsRepository.requireArtifact(eventId, id));
    }

    public List<EventArtifactDto> listMinutes(long eventId) {
        eventRepository.requireById(eventId);
        if (!opsRepository.artifactsAvailable()) {
            return List.of();
        }
        return opsRepository.listArtifacts(eventId).stream().map(this::toArtifact).toList();
    }

    public byte[] minutesBytes(long eventId, long artifactId) {
        requireArtifacts();
        EventOpsRecord.Artifact artifact = opsRepository.requireArtifact(eventId, artifactId);
        return fileStore.read(artifact.storedName);
    }

    public EventOpsRecord.Artifact minutesMeta(long eventId, long artifactId) {
        requireArtifacts();
        return opsRepository.requireArtifact(eventId, artifactId);
    }

    public EventDocumentDto uploadDocument(long eventId, byte[] body, String filename, String kind, String note, CurrentUser user) {
        requireDocuments();
        eventRepository.requireById(eventId);
        assertOpen(eventId);
        String normalizedKind = EventAttendancePolicy.requireDocumentKind(kind);
        if (!EventAttendancePolicy.allowedEvidence(filename, normalizedKind)) {
            throw new ValidationException(
                    "EVENT_DOCUMENT_TYPE_INVALID",
                    "Use PDF para actas firmadas, o JPG/PNG/WebP para fotografías"
            );
        }
        EventFileStore.Stored stored = fileStore.save(body, filename);
        EventOpsRecord.Document document = new EventOpsRecord.Document();
        document.eventId = eventId;
        document.kind = normalizedKind;
        document.originalFilename = filename == null ? stored.storedName() : filename;
        document.storedName = stored.storedName();
        document.contentType = EventAttendancePolicy.contentType(filename, null);
        document.sha256 = stored.sha256();
        document.byteSize = stored.byteSize();
        document.note = EventRules.optionalText(note, 512, "EVENT_NOTE_TOO_LONG");
        document.uploadedBy = actor(user);
        long id = opsRepository.insertDocument(document);
        return toDocument(opsRepository.requireDocument(eventId, id));
    }

    public List<EventDocumentDto> listDocuments(long eventId) {
        eventRepository.requireById(eventId);
        if (!opsRepository.documentsAvailable()) {
            return List.of();
        }
        return opsRepository.listDocuments(eventId).stream().map(this::toDocument).toList();
    }

    public byte[] documentBytes(long eventId, long documentId) {
        requireDocuments();
        return fileStore.read(opsRepository.requireDocument(eventId, documentId).storedName);
    }

    public EventOpsRecord.Document documentMeta(long eventId, long documentId) {
        requireDocuments();
        return opsRepository.requireDocument(eventId, documentId);
    }

    public List<EventAttendanceDto> listAttendance(long eventId) {
        EventRecord event = eventRepository.requireById(eventId);
        List<EventSignupRecord> registrations = signupRepository.listByEvent(eventId);
        List<EventOpsRecord.Attendance> stored = opsRepository.attendanceAvailable()
                ? opsRepository.listAttendance(eventId)
                : List.of();
        List<EventAttendanceDto> items = new ArrayList<>();
        for (EventSignupRecord registration : registrations) {
            EventOpsRecord.Attendance attendance = stored.stream()
                    .filter(item -> item.registrationId == registration.id)
                    .findFirst()
                    .orElse(null);
            items.add(toAttendance(event.id, registration, attendance));
        }
        return items;
    }

    public EventAttendanceDto setAttendance(long eventId, EventAttendanceWriteDto body, CurrentUser user) {
        requireAttendance();
        eventRepository.requireById(eventId);
        assertOpen(eventId);
        EventSignupRecord registration = resolveRegistration(eventId, body);
        EventAttendanceStatus status = EventAttendancePolicy.requireStatus(body == null ? null : body.status);
        EventOpsRecord.Attendance attendance = new EventOpsRecord.Attendance();
        attendance.eventId = eventId;
        attendance.registrationId = registration.id;
        attendance.status = status.name();
        attendance.source = "MANUAL";
        attendance.notes = EventRules.optionalText(body.notes, 512, "EVENT_NOTE_TOO_LONG");
        attendance.confirmedBy = actor(user);
        attendance.confirmedAt = LocalDateTime.now();
        opsRepository.upsertAttendance(attendance, actor(user), "MANUAL");
        return toAttendance(eventId, registration, opsRepository.findAttendance(eventId, registration.id));
    }

    public EventRegistrationReceiptDto walkIn(long eventId, EventAdminRegistrationDto body, CurrentUser user) {
        requireAttendance();
        eventRepository.requireById(eventId);
        assertOpen(eventId);
        if (body == null) {
            body = new EventAdminRegistrationDto();
        }
        body.walkIn = true;
        EventRegistrationReceiptDto receipt = registrationService.adminRegister(eventId, body, user);
        EventSignupRecord registration = signupRepository.findByPublicCode(eventId, receipt.publicCode);
        if (registration != null) {
            EventOpsRecord.Attendance attendance = new EventOpsRecord.Attendance();
            attendance.eventId = eventId;
            attendance.registrationId = registration.id;
            attendance.status = EventAttendanceStatus.ASISTIO.name();
            attendance.source = "WALK_IN";
            attendance.walkIn = true;
            attendance.confirmedBy = actor(user);
            attendance.confirmedAt = LocalDateTime.now();
            opsRepository.upsertAttendance(attendance, actor(user), "WALK_IN");
        }
        return receipt;
    }

    public EventAttendanceImportDto previewImport(long eventId, EventAttendanceImportRequestDto body, CurrentUser user) {
        requireAttendance();
        eventRepository.requireById(eventId);
        assertOpen(eventId);
        if (body == null || body.rows.isEmpty()) {
            throw new ValidationException("EVENT_IMPORT_EMPTY", "La importación no contiene filas");
        }
        EventOpsRecord.Artifact artifact = body.artifactVersion == null
                ? null
                : opsRepository.findArtifactByVersion(eventId, body.artifactVersion);
        EventOpsRecord.Import imported = new EventOpsRecord.Import();
        imported.eventId = eventId;
        imported.artifactId = artifact == null ? null : artifact.id;
        imported.source = source(body.source);
        imported.status = "PREVIEW";
        imported.createdBy = actor(user);
        List<EventOpsRecord.ImportRow> rows = new ArrayList<>();
        int index = 1;
        for (EventAttendanceImportRowDto incoming : body.rows) {
            EventOpsRecord.ImportRow row = new EventOpsRecord.ImportRow();
            row.rowNumber = incoming.rowNumber == null ? index : incoming.rowNumber;
            row.registrationCode = EventRules.trimToNull(incoming.registrationCode);
            row.artifactVersion = incoming.artifactVersion == null ? body.artifactVersion : incoming.artifactVersion;
            EventAttendanceStatus status = EventAttendancePolicy.requireStatus(incoming.proposedStatus);
            row.proposedStatus = status.name();
            row.displayName = EventRules.trimToNull(incoming.displayName);
            row.notes = EventRules.optionalText(incoming.notes, 512, "EVENT_NOTE_TOO_LONG");
            EventSignupRecord match = signupRepository.findByPublicCode(eventId, row.registrationCode);
            int matches = signupRepository.countByPublicCode(eventId, row.registrationCode);
            row.matchStatus = EventAttendancePolicy.matchStatus(matches);
            row.matchedRegistrationId = match == null ? null : match.id;
            if (row.displayName == null && match != null) {
                row.displayName = match.displayName;
            }
            rows.add(row);
            index++;
        }
        long id = opsRepository.insertImport(imported, rows);
        return toImport(opsRepository.requireImport(eventId, id), rows);
    }

    public EventAttendanceImportDto previewFile(long eventId, byte[] body, String filename, Integer artifactVersion, CurrentUser user) {
        EventAttendanceImportRequestDto request = parseFile(body, filename);
        if (artifactVersion != null) {
            request.artifactVersion = artifactVersion;
        }
        request.source = "MANUAL_FILE";
        EventAttendanceImportDto preview = previewImport(eventId, request, user);
        if (body != null && body.length > 0) {
            EventFileStore.Stored stored = fileStore.save(body, filename);
            // metadata already stored without file; acceptable for preview
            preview.filename = filename;
            preview.source = "MANUAL_FILE";
            stored.sha256();
        }
        return preview;
    }

    public EventAttendanceImportDto confirmImport(long eventId, long importId, CurrentUser user) {
        requireAttendance();
        eventRepository.requireById(eventId);
        assertOpen(eventId);
        EventOpsRecord.Import imported = opsRepository.requireImport(eventId, importId);
        if (!"PREVIEW".equals(imported.status)) {
            throw new ConflictException("EVENT_IMPORT_NOT_PREVIEW", "Solo se confirma una vista previa pendiente");
        }
        List<EventOpsRecord.ImportRow> rows = opsRepository.listImportRows(importId);
        LocalDateTime now = LocalDateTime.now();
        for (EventOpsRecord.ImportRow row : rows) {
            if (!"MATCHED".equals(row.matchStatus) || row.matchedRegistrationId == null) {
                continue;
            }
            EventOpsRecord.Attendance attendance = new EventOpsRecord.Attendance();
            attendance.eventId = eventId;
            attendance.registrationId = row.matchedRegistrationId;
            attendance.status = row.proposedStatus;
            attendance.source = "IMPORT";
            attendance.artifactId = imported.artifactId;
            attendance.artifactRowNumber = row.rowNumber;
            attendance.notes = row.notes;
            attendance.confirmedBy = actor(user);
            attendance.confirmedAt = now;
            opsRepository.upsertAttendance(attendance, actor(user), "IMPORT");
        }
        opsRepository.confirmImport(importId);
        return toImport(opsRepository.requireImport(eventId, importId), opsRepository.listImportRows(importId));
    }

    public void closeDocumentation(long eventId, CurrentUser user) {
        eventRepository.requireById(eventId);
        opsRepository.closeDocumentation(eventId);
        eventService.finish(eventId, user);
    }

    public EventIndicatorsDto indicators(String fromRaw, String toRaw) {
        LocalDateTime from = EventDateTimes.parse(fromRaw);
        LocalDateTime to = EventDateTimes.parse(toRaw);
        EventOpsRepository.EventIndicatorTotals totals = opsRepository.totals(from, to);
        EventIndicatorsDto dto = new EventIndicatorsDto();
        dto.totalEvents = totals.totalEvents;
        dto.publishedEvents = totals.publishedEvents;
        dto.advanceRegistrations = totals.advanceRegistrations;
        dto.effectiveAttendees = totals.effectiveAttendees;
        dto.walkIns = totals.walkIns;
        dto.confirmedAbsences = totals.confirmedAbsences;
        dto.uniquePeople = totals.uniquePeople;
        dto.recurringPeople = totals.recurringPeople;
        dto.pureLinked = totals.pureLinked;
        dto.attendanceTablesAvailable = opsRepository.attendanceAvailable();
        dto.from = EventDateTimes.format(from);
        dto.to = EventDateTimes.format(to);
        int registeredPresent = Math.max(0, totals.effectiveAttendees - totals.walkIns);
        if (totals.advanceRegistrations > 0) {
            dto.registeredAttendanceRate = Math.round((registeredPresent * 1000.0) / totals.advanceRegistrations) / 10.0;
        }
        opsRepository.groupBy("faculty_or_institution", from, to).forEach(item -> {
            EventIndicatorsDto.EventNameCountDto row = new EventIndicatorsDto.EventNameCountDto();
            row.label = item.label();
            row.total = item.total();
            dto.byFaculty.add(row);
        });
        opsRepository.groupBy("position_title", from, to).forEach(item -> {
            EventIndicatorsDto.EventNameCountDto row = new EventIndicatorsDto.EventNameCountDto();
            row.label = item.label();
            row.total = item.total();
            dto.byPosition.add(row);
        });
        return dto;
    }

    public EventAttendanceImportRequestDto parseFile(byte[] body, String filename) {
        if (body == null || body.length == 0) {
            throw new ValidationException("EVENT_IMPORT_EMPTY", "La importación no contiene filas");
        }
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            return parseWorkbook(body);
        }
        return parseCsv(new String(body, StandardCharsets.UTF_8));
    }

    private EventAttendanceImportRequestDto parseWorkbook(byte[] body) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(body))) {
            Sheet sheet = workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
            if (sheet == null) {
                throw new ValidationException("EVENT_IMPORT_EMPTY", "La importación no contiene filas");
            }
            DataFormatter formatter = new DataFormatter();
            EventAttendanceImportRequestDto request = new EventAttendanceImportRequestDto();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                EventAttendanceImportRowDto dto = new EventAttendanceImportRowDto();
                dto.rowNumber = i;
                dto.registrationCode = text(row.getCell(0), formatter);
                dto.artifactVersion = integer(text(row.getCell(1), formatter));
                dto.proposedStatus = text(row.getCell(2), formatter);
                dto.displayName = text(row.getCell(3), formatter);
                dto.notes = text(row.getCell(4), formatter);
                if (dto.registrationCode != null || dto.proposedStatus != null) {
                    request.rows.add(dto);
                }
            }
            return request;
        } catch (ValidationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ValidationException("EVENT_IMPORT_INVALID", "No fue posible leer el archivo de importación");
        }
    }

    private EventAttendanceImportRequestDto parseCsv(String raw) {
        EventAttendanceImportRequestDto request = new EventAttendanceImportRequestDto();
        String[] lines = raw.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                continue;
            }
            String[] cols = lines[i].split(";", -1);
            if (cols.length < 3) {
                cols = lines[i].split(",", -1);
            }
            EventAttendanceImportRowDto dto = new EventAttendanceImportRowDto();
            dto.rowNumber = i;
            dto.registrationCode = cols.length > 0 ? EventRules.trimToNull(cols[0]) : null;
            dto.artifactVersion = cols.length > 1 ? integer(cols[1]) : null;
            dto.proposedStatus = cols.length > 2 ? EventRules.trimToNull(cols[2]) : null;
            dto.displayName = cols.length > 3 ? EventRules.trimToNull(cols[3]) : null;
            dto.notes = cols.length > 4 ? EventRules.trimToNull(cols[4]) : null;
            request.rows.add(dto);
        }
        return request;
    }

    private EventSignupRecord resolveRegistration(long eventId, EventAttendanceWriteDto body) {
        if (body != null && body.registrationId != null) {
            return signupRepository.listByEvent(eventId).stream()
                    .filter(item -> item.id == body.registrationId)
                    .findFirst()
                    .orElseThrow(() -> new ValidationException("EVENT_REGISTRATION_NOT_FOUND", "Inscripción no encontrada"));
        }
        EventSignupRecord record = signupRepository.findByPublicCode(eventId, body == null ? null : body.publicCode);
        if (record == null) {
            throw new ValidationException("EVENT_REGISTRATION_NOT_FOUND", "Inscripción no encontrada");
        }
        return record;
    }

    private void requireArtifacts() {
        eventService.requireTables();
        if (!opsRepository.artifactsAvailable()) {
            throw new ValidationException(
                    "EVENTS_OPS_TABLES_MISSING",
                    "Faltan las tablas de actas. Ejecute backend/sql/proposed-events-phase3-6.sql"
            );
        }
    }

    private void requireDocuments() {
        eventService.requireTables();
        if (!opsRepository.documentsAvailable()) {
            throw new ValidationException(
                    "EVENTS_OPS_TABLES_MISSING",
                    "Faltan las tablas de evidencias. Ejecute backend/sql/proposed-events-phase3-6.sql"
            );
        }
    }

    private void requireAttendance() {
        eventService.requireTables();
        if (!opsRepository.attendanceAvailable()) {
            throw new ValidationException(
                    "EVENTS_OPS_TABLES_MISSING",
                    "Faltan las tablas de asistencia. Ejecute backend/sql/proposed-events-phase3-6.sql"
            );
        }
    }

    private void assertOpen(long eventId) {
        if (opsRepository.documentationClosed(eventId)) {
            throw new ConflictException("EVENT_DOCUMENTATION_CLOSED", "El expediente del evento ya está cerrado");
        }
    }

    private static EventOpsRecord.ArtifactRow artifactRow(int number, EventSignupRecord registration, boolean blank) {
        EventOpsRecord.ArtifactRow row = new EventOpsRecord.ArtifactRow();
        row.rowNumber = number;
        row.blankRow = blank;
        if (registration != null) {
            row.registrationId = registration.id;
            row.publicCode = registration.publicCode;
            row.displayName = registration.displayName;
            row.facultyOrInstitution = registration.facultyOrInstitution;
            row.positionTitle = registration.positionTitle;
            row.emailNormalized = registration.emailNormalized;
        }
        return row;
    }

    private EventArtifactDto toArtifact(EventOpsRecord.Artifact artifact) {
        EventArtifactDto dto = new EventArtifactDto();
        dto.id = artifact.id;
        dto.versionNo = artifact.versionNo;
        dto.filename = artifact.originalFilename;
        dto.sha256 = artifact.sha256;
        dto.byteSize = artifact.byteSize;
        dto.blankRows = artifact.blankRows;
        dto.generatedBy = artifact.generatedBy;
        dto.generatedAt = EventDateTimes.format(artifact.generatedAt);
        dto.language = languageFromFilename(artifact.originalFilename);
        dto.downloadPath = "/events/admin/" + artifact.eventId + "/minutes/" + artifact.id;
        return dto;
    }

    private EventDocumentDto toDocument(EventOpsRecord.Document document) {
        EventDocumentDto dto = new EventDocumentDto();
        dto.id = document.id;
        dto.kind = document.kind;
        dto.filename = document.originalFilename;
        dto.contentType = document.contentType;
        dto.sha256 = document.sha256;
        dto.byteSize = document.byteSize;
        dto.note = document.note;
        dto.uploadedBy = document.uploadedBy;
        dto.uploadedAt = EventDateTimes.format(document.uploadedAt);
        dto.downloadPath = "/events/admin/" + document.eventId + "/documents/" + document.id;
        return dto;
    }

    private EventAttendanceDto toAttendance(long eventId, EventSignupRecord registration, EventOpsRecord.Attendance attendance) {
        EventAttendanceDto dto = new EventAttendanceDto();
        dto.registrationId = registration.id;
        dto.publicCode = registration.publicCode;
        dto.seqNo = registration.seqNo;
        dto.displayName = registration.displayName;
        dto.email = registration.emailNormalized;
        dto.facultyOrInstitution = registration.facultyOrInstitution;
        dto.positionTitle = registration.positionTitle;
        if (attendance == null) {
            dto.status = EventAttendanceStatus.PENDIENTE.name();
            dto.statusLabel = EventAttendanceStatus.PENDIENTE.labelEs();
            dto.source = null;
            dto.walkIn = false;
            return dto;
        }
        dto.id = attendance.id;
        EventAttendanceStatus status = EventAttendanceStatus.parse(attendance.status);
        dto.status = attendance.status;
        dto.statusLabel = status == null ? attendance.status : status.labelEs();
        dto.source = attendance.source;
        dto.walkIn = attendance.walkIn;
        dto.notes = attendance.notes;
        dto.confirmedBy = attendance.confirmedBy;
        dto.confirmedAt = EventDateTimes.format(attendance.confirmedAt);
        return dto;
    }

    private EventAttendanceImportDto toImport(EventOpsRecord.Import imported, List<EventOpsRecord.ImportRow> rows) {
        EventAttendanceImportDto dto = new EventAttendanceImportDto();
        dto.id = imported.id;
        dto.source = imported.source;
        dto.status = imported.status;
        dto.filename = imported.originalFilename;
        dto.createdBy = imported.createdBy;
        dto.createdAt = EventDateTimes.format(imported.createdAt);
        for (EventOpsRecord.ImportRow row : rows) {
            EventAttendanceImportRowDto item = new EventAttendanceImportRowDto();
            item.rowNumber = row.rowNumber;
            item.registrationCode = row.registrationCode;
            item.artifactVersion = row.artifactVersion;
            item.proposedStatus = row.proposedStatus;
            item.matchStatus = row.matchStatus;
            item.matchedRegistrationId = row.matchedRegistrationId;
            item.displayName = row.displayName;
            item.notes = row.notes;
            dto.rows.add(item);
            if ("MATCHED".equals(row.matchStatus)) {
                dto.matched++;
            } else if ("AMBIGUOUS".equals(row.matchStatus)) {
                dto.ambiguous++;
            } else {
                dto.unmatched++;
            }
        }
        return dto;
    }

    private static String filename(EventRecord event, int version, String language) {
        String slug = event.publicSlug == null ? "evento" : event.publicSlug;
        String lang = EventLanguage.normalize(language);
        String prefix = EventLanguage.isEnglish(lang) ? "attendance-sheet-" : "acta-";
        return prefix + slug + "-v" + version + "-" + lang + ".pdf";
    }

    private static String languageFromFilename(String filename) {
        if (filename != null && filename.endsWith("-en.pdf")) {
            return EventLanguage.EN;
        }
        return EventLanguage.ES;
    }

    private static String formatWindow(LocalDateTime starts, LocalDateTime ends, String language) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
        if (starts == null) {
            return "-";
        }
        if (ends == null) {
            return starts.format(fmt);
        }
        String separator = EventLanguage.isEnglish(language) ? " to " : " a ";
        return starts.format(fmt) + separator + ends.format(fmt);
    }

    private static String place(EventRecord event) {
        if (event.locationName != null && event.campusName != null) {
            return event.locationName + ", " + event.campusName;
        }
        return event.locationName != null ? event.locationName : event.campusName;
    }

    private static String actor(CurrentUser user) {
        return user == null ? null : user.email;
    }

    private static String source(String raw) {
        if (raw == null || raw.isBlank()) {
            return "CHATGPT";
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if ("CHATGPT".equals(value) || "MANUAL_FILE".equals(value) || "OLLAMA_READY".equals(value)) {
            return value;
        }
        return "CHATGPT";
    }

    private static String text(Cell cell, DataFormatter formatter) {
        return cell == null ? null : EventRules.trimToNull(formatter.formatCellValue(cell));
    }

    private static Integer integer(String raw) {
        String value = EventRules.trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
