package cl.uss.ca.identity;

/** Lectura directa de {@code pure_person}. No declara altas ni actualizaciones. */
public interface PurePersonLookup {
    PurePersonView findById(long purePersonId);
}
