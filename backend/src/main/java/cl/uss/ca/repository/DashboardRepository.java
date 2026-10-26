package cl.uss.ca.repository;

import cl.uss.ca.domain.ResearcherPositions;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DashboardRepository extends JdbcRepository {

    public long countPersons() {
        return scalarLong("SELECT COUNT(*) FROM " + qualified("pure_person")
                + " p WHERE " + activeInPurePredicate("pure_person", "p"));
    }

    public long countPersonsWithOrcid() {
        return scalarLong("""
                SELECT COUNT(*)
                FROM %s p
                WHERE orcid IS NOT NULL
                  AND TRIM(orcid) <> ''
                  AND %s
                """.formatted(qualified("pure_person"), activeInPurePredicate("pure_person", "p")));
    }

    /**
     * Población operacional OPENSCIENCE-USS: cargos Investigador,
     * Docente Investigador e Investigador Postdoctoral. No es indicador ANID.
     */
    public long countResearchers() {
        return scalarLong(
                "SELECT COUNT(*) FROM " + qualified("pure_person") + " p WHERE " + researcherUniversePredicate(),
                ResearcherPositions.VALUES.toArray()
        );
    }

    /**
     * Investigadores activos con ORCID informado (no autenticado).
     * Mismo universo que {@link #countResearchers()}.
     */
    public long countResearchersWithOrcid() {
        return scalarLong(
                "SELECT COUNT(*) FROM " + qualified("pure_person") + " p WHERE " + researcherUniversePredicate()
                        + " AND orcid IS NOT NULL AND TRIM(orcid) <> ''",
                ResearcherPositions.VALUES.toArray()
        );
    }

    private String researcherUniversePredicate() {
        return "TRIM(p.cargo) IN (" + ResearcherPositions.inClausePlaceholders() + ")"
                + " AND " + activeInPurePredicate("pure_person", "p");
    }

    public long countOrcidAuthenticated() {
        return scalarLong("""
                SELECT COUNT(*)
                FROM %s p
                WHERE orcid_authenticated = 1
                  AND %s
                """.formatted(qualified("pure_person"), activeInPurePredicate("pure_person", "p")));
    }

    /**
     * Personas distintas con al menos un Scopus ID.
     * El literal identifier_type = "Scopus ID" es el usado por los loaders en tools/.
     */
    public long countPersonsWithScopusId() {
        return scalarLong("""
                SELECT COUNT(DISTINCT p.id)
                FROM %s p
                INNER JOIN %s i
                    ON i.person_id = p.id
                   AND i.identifier_type = ?
                   AND i.identifier_value IS NOT NULL
                   AND TRIM(i.identifier_value) <> ''
                WHERE %s
                """.formatted(
                        qualified("pure_person"),
                        qualified("pure_person_identifiers"),
                        activeInPurePredicate("pure_person", "p")
                ), SCOPUS_IDENTIFIER_TYPE);
    }

    public boolean hasPurePersonTable() {
        return tableExists("pure_person");
    }

    public boolean hasIdentifierTable() {
        return tableExists("pure_person_identifiers");
    }

    public boolean hasScopusAuthorTable() {
        return tableExists("scopus_author");
    }

    public long countScopusAuthors() {
        return scalarLong("SELECT COUNT(*) FROM " + qualified("scopus_author"));
    }
}
