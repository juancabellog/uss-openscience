package cl.uss.ca.events;

import cl.uss.ca.exception.ForbiddenException;
import cl.uss.ca.exception.NotFoundException;
import cl.uss.ca.repository.EventRecord;

public class EventPublicAccessTest {

    public static void main(String[] args) {
        try {
            EventPublicAccess.requireVisible(null);
            throw new AssertionError("missing event");
        } catch (NotFoundException ex) {
            if (!"EVENT_NOT_FOUND".equals(ex.getCode())) {
                throw new AssertionError(ex.getCode());
            }
        }

        EventRecord draft = new EventRecord();
        draft.publicationStatus = "DRAFT";
        try {
            EventPublicAccess.requireVisible(draft);
            throw new AssertionError("draft leaked");
        } catch (ForbiddenException ex) {
            if (!"EVENT_NOT_PUBLISHED".equals(ex.getCode())) {
                throw new AssertionError(ex.getCode());
            }
        }

        for (String status : new String[] {"PUBLISHED", "FINISHED", "CANCELLED"}) {
            EventRecord record = new EventRecord();
            record.publicationStatus = status;
            EventPublicAccess.requireVisible(record);
            if (!EventPublicAccess.isPubliclyVisible(record)) {
                throw new AssertionError(status);
            }
        }
    }
}
