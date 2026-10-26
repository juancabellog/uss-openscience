package cl.uss.ca.events;

import cl.uss.ca.exception.ForbiddenException;
import cl.uss.ca.exception.NotFoundException;
import cl.uss.ca.repository.EventRecord;

/**
 * Public catalog/detail visibility. Registration still requires PUBLISHED + open window.
 */
public final class EventPublicAccess {
    private EventPublicAccess() {
    }

    public static void requireVisible(EventRecord record) {
        if (record == null) {
            throw new NotFoundException("EVENT_NOT_FOUND", "Event not found");
        }
        EventPublicationStatus status = EventPublicationStatus.parse(record.publicationStatus);
        if (status == null || status == EventPublicationStatus.DRAFT) {
            throw new ForbiddenException("EVENT_NOT_PUBLISHED", "Event is not published");
        }
    }

    public static boolean isPubliclyVisible(EventRecord record) {
        if (record == null) {
            return false;
        }
        EventPublicationStatus status = EventPublicationStatus.parse(record.publicationStatus);
        return status != null && status != EventPublicationStatus.DRAFT;
    }
}
