package cl.uss.ca.db;

import cl.uss.ca.openalex.InvestigadorPendiente;
import cl.uss.ca.openalex.PublicacionPendiente;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OrcidPendienteRepository {
    private static final String SQL = """
            SELECT
                p.id AS person_id,
                p.first_name,
                p.last_name,
                z.doi,
                x.title,
                p.cargo AS cargo
            FROM pure_research_output x
            JOIN pure_research_output_contributor y
                ON x.id = y.research_output_id
            JOIN pure_research_output_electronic_version z
                ON x.id = z.research_output_id
            JOIN pure_person p
                ON p.uuid = y.person_uuid
            WHERE 
               (
                  p.orcid IS NULL
                  OR TRIM(p.orcid) = ''
              )
              AND z.doi IS NOT NULL
              AND TRIM(z.doi) <> ''
              and ((cargo='Académico Regular' and p.id > 3460) or (cargo>'Académico Regular'))
            ORDER BY
                p.cargo,
                p.id,
                x.submission_year DESC,
                x.id
            """;

    private final String url;
    private final String user;
    private final String password;

    public OrcidPendienteRepository(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public List<InvestigadorPendiente> buscarInvestigadoresSinOrcid() throws SQLException {
        Map<Long, InvestigadorPendiente> porPersona = new LinkedHashMap<>();
        try (Connection connection = DriverManager.getConnection(url, user, password);
             PreparedStatement statement = connection.prepareStatement(SQL);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                Long personId = rs.getLong(1);
                InvestigadorPendiente investigador = porPersona.get(personId);
                if (investigador == null) {
                    String cargo = leerTexto(rs, 6);
                    investigador = new InvestigadorPendiente(
                            personId,
                            leerTexto(rs, 2),
                            leerTexto(rs, 3),
                            cargo
                    );
                    System.out.println("Persona cargada id=" + personId
                            + " cargo=[" + (cargo == null ? "" : cargo) + "]"
                            + " nombre=" + investigador.getNombreCompleto());
                    porPersona.put(personId, investigador);
                }
                investigador.agregarPublicacion(new PublicacionPendiente(
                        leerTexto(rs, 4),
                        leerTexto(rs, 5)
                ));
            }
        }
        return new ArrayList<>(porPersona.values());
    }

    private static String leerTexto(ResultSet rs, int columna) throws SQLException {
        String value = rs.getString(columna);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
