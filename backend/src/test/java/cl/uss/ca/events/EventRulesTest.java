package cl.uss.ca.events;

import cl.uss.ca.api.dto.EventWriteDto;

import java.util.List;

public final class EventRulesTest {
    private EventRulesTest() {
    }

    public static void main(String[] args) {
        draftOnlyNeedsTitle();
        whitespaceTitleRejected();
        publishEmptyFails();
        publishValidPasses();
        dateWindowValidations();
        modalityPlaceRules();
        capacityAndBlankRows();
        organizingUnitRules();
        System.out.println("EventRulesTest OK");
    }

    private static void draftOnlyNeedsTitle() {
        EventWriteDto body = new EventWriteDto();
        body.title = "  Jornada de ciencia abierta  ";
        List<EventFieldIssue> issues = EventRules.collect(body, false);
        assert issues.isEmpty() : issues;
        assert "Jornada de ciencia abierta".equals(EventRules.requireTitle(body.title));
    }

    private static void whitespaceTitleRejected() {
        EventWriteDto body = new EventWriteDto();
        body.title = "   ";
        List<EventFieldIssue> issues = EventRules.collect(body, false);
        assert issues.size() == 1 : issues;
        assert "EVENT_TITLE_REQUIRED".equals(issues.get(0).code());
        assert "title".equals(issues.get(0).field());
        assert "El nombre del evento es obligatorio".equals(issues.get(0).message());
    }

    private static void publishEmptyFails() {
        EventWriteDto body = new EventWriteDto();
        body.title = "Seminario";
        List<EventFieldIssue> issues = EventRules.collect(body, true);
        assert hasCode(issues, "EVENT_DESCRIPTION_REQUIRED") : issues;
        assert hasCode(issues, "EVENT_TYPE_REQUIRED") : issues;
        assert hasCode(issues, "EVENT_MODALITY_REQUIRED") : issues;
        assert hasCode(issues, "EVENT_START_REQUIRED") : issues;
        assert hasCode(issues, "EVENT_END_REQUIRED") : issues;
        assert hasCode(issues, "EVENT_TIMEZONE_REQUIRED") : issues;
        assert hasCode(issues, "EVENT_UNIT_REQUIRED") : issues;
        assert hasCode(issues, "EVENT_REGISTRATION_OPEN_REQUIRED") : issues;
        assert hasCode(issues, "EVENT_REGISTRATION_CLOSE_REQUIRED") : issues;
    }

    private static void publishValidPasses() {
        List<EventFieldIssue> issues = EventRules.collect(validPublish(), true);
        assert issues.isEmpty() : issues;
    }

    private static void dateWindowValidations() {
        EventWriteDto body = validPublish();
        body.endsAt = "2026-03-01T09:00:00";
        assert hasCode(EventRules.collect(body, true), "EVENT_ENDS_BEFORE_START");

        body = validPublish();
        body.registrationClosesAt = "2026-02-01T09:00:00";
        assert hasCode(EventRules.collect(body, true), "EVENT_REGISTRATION_WINDOW_INVALID");

        body = validPublish();
        body.registrationClosesAt = "2026-03-01T12:00:00";
        assert hasCode(EventRules.collect(body, true), "EVENT_REGISTRATION_AFTER_START");

        body.allowRegistrationDuringEvent = true;
        assert EventRules.collect(body, true).isEmpty() : EventRules.collect(body, true);
    }

    private static void modalityPlaceRules() {
        EventWriteDto presential = validPublish();
        presential.locationName = null;
        presential.campusName = null;
        assert hasCode(EventRules.collect(presential, true), "EVENT_PLACE_REQUIRED");

        EventWriteDto virtual = validPublish();
        virtual.modality = "VIRTUAL";
        virtual.locationName = null;
        virtual.campusName = null;
        virtual.virtualAccess = null;
        assert hasCode(EventRules.collect(virtual, true), "EVENT_VIRTUAL_ACCESS_REQUIRED");

        EventWriteDto hybrid = validPublish();
        hybrid.modality = "HYBRID";
        hybrid.virtualAccess = null;
        assert hasCode(EventRules.collect(hybrid, true), "EVENT_VIRTUAL_ACCESS_REQUIRED");

        hybrid.virtualAccess = "https://meet.uss.cl/evento";
        hybrid.locationName = null;
        hybrid.campusName = null;
        assert hasCode(EventRules.collect(hybrid, true), "EVENT_PLACE_REQUIRED");

        hybrid.campusName = "Sede Santiago";
        assert EventRules.collect(hybrid, true).isEmpty() : EventRules.collect(hybrid, true);
    }

    private static void capacityAndBlankRows() {
        EventWriteDto body = validPublish();
        body.capacity = 0;
        assert hasCode(EventRules.collect(body, false), "EVENT_CAPACITY_INVALID");
        body.capacity = 25;
        body.blankSignatureRows = 0;
        assert EventRules.collect(body, false).isEmpty();
        body.blankSignatureRows = 10;
        assert EventRules.collect(body, false).isEmpty();
        body.blankSignatureRows = -1;
        assert hasCode(EventRules.collect(body, false), "EVENT_BLANK_ROWS_INVALID");
        assert EventRules.blankRows(null) == EventRules.DEFAULT_BLANK_ROWS;
    }

    private static EventWriteDto validPublish() {
        EventWriteDto body = new EventWriteDto();
        body.title = "Seminario de ciencia abierta";
        body.description = "Programa y objetivos del evento.";
        body.eventType = "SEMINAR";
        body.modality = "PRESENTIAL";
        body.startsAt = "2026-03-01T10:00:00";
        body.endsAt = "2026-03-01T13:00:00";
        body.timezone = EventRules.DEFAULT_TIMEZONE;
        body.organizingUnitCode = "CIENCIA_ABIERTA";
        body.locationName = "Aula Magna";
        body.campusName = "Sede Santiago";
        body.registrationOpensAt = "2026-02-01T10:00:00";
        body.registrationClosesAt = "2026-02-28T18:00:00";
        body.blankSignatureRows = 10;
        return body;
    }

    private static void organizingUnitRules() {
        EventWriteDto otherEmpty = validPublish();
        otherEmpty.organizingUnitCode = "OTHER";
        otherEmpty.organizingUnit = null;
        assert hasCode(EventRules.collect(otherEmpty, true), "EVENT_UNIT_OTHER_REQUIRED");
        assert EventRules.collect(otherEmpty, false).isEmpty();

        otherEmpty.organizingUnit = "Facultad de Medicina";
        assert EventRules.collect(otherEmpty, true).isEmpty();

        EventWriteDto invalid = validPublish();
        invalid.organizingUnitCode = "NO_EXISTE";
        assert hasCode(EventRules.collect(invalid, false), "EVENT_UNIT_INVALID");

        EventWriteDto leftover = validPublish();
        leftover.organizingUnitCode = "DISEI";
        leftover.organizingUnit = "texto anterior de Otro";
        assert EventRules.collect(leftover, true).isEmpty();
        EventOrganizingUnits.Resolved resolved = EventOrganizingUnits.resolve(leftover.organizingUnitCode, leftover.organizingUnit);
        assert "DISEI".equals(resolved.storedText);
    }

    private static boolean hasCode(List<EventFieldIssue> issues, String code) {
        return issues.stream().anyMatch((issue) -> code.equals(issue.code()));
    }
}
