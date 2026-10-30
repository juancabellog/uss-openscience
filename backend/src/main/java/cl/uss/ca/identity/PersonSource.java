package cl.uss.ca.identity;

public enum PersonSource {
    /** {@code pure_person_id} presente. Los vacíos locales se completan desde PURE. */
    PURE,
    /** {@code pure_person_id} nulo. Los datos viven en la persona compartida. */
    LOCAL
}
