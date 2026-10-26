package cl.uss.ca.events;

public enum EventAffiliationOrigin {
    DIRECTORY_MATCH,
    SELF_DECLARED;

    public static EventAffiliationOrigin fromDirectoryMatch(boolean matched) {
        return matched ? DIRECTORY_MATCH : SELF_DECLARED;
    }
}
