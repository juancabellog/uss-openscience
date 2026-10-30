package cl.uss.ca.identity;

/**
 * Institucional no es lo mismo que tener {@code purePersonId}.
 * Una cuenta USS puede existir antes de aparecer en PURE.
 * Externo, en Ethics, solo describe a un revisor creado por Administración.
 */
public enum PersonKind {
    INSTITUTIONAL,
    EXTERNAL
}
