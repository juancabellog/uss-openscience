package cl.uss.ca.events;

import java.time.LocalDateTime;

public final class EventRegistrationPolicyTest {
    private EventRegistrationPolicyTest() {
    }

    public static void main(String[] args) {
        LocalDateTime now = LocalDateTime.of(2026, 10, 20, 12, 0);
        assert EventRegistrationPolicy.isOpen(
                EventPublicationStatus.PUBLISHED,
                now,
                now.minusHours(1),
                now.plusHours(1)
        );
        assert !EventRegistrationPolicy.isOpen(EventPublicationStatus.DRAFT, now, null, null);
        assert !EventRegistrationPolicy.isOpen(EventPublicationStatus.FINISHED, now, null, null);
        assert !EventRegistrationPolicy.isOpen(EventPublicationStatus.PUBLISHED, now, now.plusDays(1), null);
        assert EventRegistrationPolicy.hasCapacity(null, 50);
        assert EventRegistrationPolicy.hasCapacity(10, 9);
        assert !EventRegistrationPolicy.hasCapacity(10, 10);
        assert Integer.valueOf(0).equals(EventRegistrationPolicy.remainingCapacity(3, 3));
        assert EventAffiliationOrigin.DIRECTORY_MATCH == EventAffiliationOrigin.fromDirectoryMatch(true);
        assert EventAffiliationOrigin.SELF_DECLARED == EventAffiliationOrigin.fromDirectoryMatch(false);
        assert EventAffiliationOrigin.SELF_DECLARED != EventAffiliationOrigin.DIRECTORY_MATCH;
        System.out.println("EventRegistrationPolicyTest OK");
    }
}
