package cl.uss.ca.domain;

import java.util.List;

/**
 * Población operacional OPENSCIENCE-USS "Investigadores".
 * No equivale al directorio completo de {@code pure_person}.
 */
public final class ResearcherPositions {

    public static final List<String> VALUES = List.of(
            "Investigador",
            "Docente Investigador",
            "Investigador Postdoctoral"
    );

    public static final String WITHOUT_POSITION_CODE = "SIN_CARGO";

    private ResearcherPositions() {
    }

    public static String inClausePlaceholders() {
        return String.join(", ", VALUES.stream().map(position -> "?").toList());
    }
}
