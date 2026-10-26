package cl.uss.ca.events;

import java.time.LocalDateTime;

public final class EventChallengeStateTest {
    private EventChallengeStateTest() {
    }

    public static void main(String[] args) {
        LocalDateTime now = LocalDateTime.of(2026, 10, 21, 12, 0);
        assert EventChallengeState.classify(null, 1, now.plusMinutes(30), null, now) == EventChallengeState.INVALID;
        assert EventChallengeState.classify(2L, 1, now.plusMinutes(30), null, now) == EventChallengeState.INVALID;
        assert EventChallengeState.classify(1L, 1, now.plusMinutes(30), now.minusMinutes(1), now) == EventChallengeState.USED;
        assert EventChallengeState.classify(1L, 1, now.minusMinutes(1), null, now) == EventChallengeState.EXPIRED;
        assert EventChallengeState.classify(1L, 1, now, null, now) == EventChallengeState.EXPIRED;
        assert EventChallengeState.classify(1L, 1, now.plusMinutes(30), null, now) == EventChallengeState.USABLE;
        assert "EVENT_TOKEN_EXPIRED".equals(EventChallengeState.EXPIRED.errorCode());
        assert "EVENT_TOKEN_USED".equals(EventChallengeState.USED.errorCode());
        assert "EVENT_TOKEN_INVALID".equals(EventChallengeState.INVALID.errorCode());
        assert !EventChallengeState.USED.errorMessage().toLowerCase().contains("expir");
        System.out.println("EventChallengeStateTest OK");
    }
}
