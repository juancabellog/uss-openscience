package cl.uss.ca.identity;

import java.util.Set;

/**
 * Mismos valores que el formulario de proyecto (campus, facultad y tipo de cargo).
 * No hay catálogo de carrera en {@code person}; ese dato no se pide aquí.
 */
public final class InstitutionalCatalog {
    public static final Set<String> CAMPUSES = Set.of(
            "Santiago - Bellavista",
            "Santiago - Los Leones",
            "Santiago - Ciudad Universitaria (Huechuraba)",
            "Concepción - Tres Pascualas",
            "Concepción - Paicaví",
            "Valdivia - Sede",
            "Valdivia - Centro de Estudios Científicos",
            "Patagonia"
    );
    public static final Set<String> FACULTIES = Set.of(
            "Arquitectura, Arte y Diseño",
            "Ciencias",
            "Ciencias de la Rehabilitación y Calidad de Vida",
            "Ciencias para el cuidado de la Salud",
            "Derecho y Ciencias Sociales",
            "Economía, Negocios y Gobierno",
            "Educación",
            "Ingeniería",
            "Medicina",
            "Medicina Veterinaria",
            "Odontología"
    );
    public static final Set<String> POSITIONS = Set.of(
            "Estudiante de pregrado",
            "Estudiante de magister",
            "Estudiante de doctorado",
            "Postdoctorante",
            "Asistente de Investigación",
            "Personal Técnico",
            "Docente",
            "Co-Investigador",
            "Investigador",
            "Director Unidad Administrativa"
    );

    private InstitutionalCatalog() {
    }

    public static boolean accepts(String campus, String faculty, String position) {
        return CAMPUSES.contains(campus) && FACULTIES.contains(faculty) && POSITIONS.contains(position);
    }
}
