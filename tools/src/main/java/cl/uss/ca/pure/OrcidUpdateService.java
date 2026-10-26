package cl.uss.ca.pure;

import cl.uss.ca.db.OrcidCambio;
import cl.uss.ca.db.OrcidUpdateRepository;
import cl.uss.ca.db.OrcidUpdateResumen;
import cl.uss.ca.openalex.OpenAlexService;
import cl.uss.ca.pure.json.PersonItem;
import cl.uss.ca.pure.json.PersonName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class OrcidUpdateService {
    private final PurePersonService personService;
    private final OrcidUpdateRepository repository;

    public OrcidUpdateService(PurePersonService personService, OrcidUpdateRepository repository) {
        this.personService = personService;
        this.repository = repository;
    }

    public OrcidUpdateResumen actualizar() throws Exception {
        System.out.println("Actualizando orcid desde Pure");
        OrcidUpdateResumen resumen = new OrcidUpdateResumen();
        try (Connection connection = repository.abrirConexion();
             PreparedStatement select = repository.prepararSelect(connection);
             PreparedStatement update = repository.prepararUpdate(connection)) {
            Integer total = personService.procesarPaginas((items, offset, totalInformado) -> {
                resumen.setPersonasInformadasPorPure(totalInformado);
                actualizarPagina(connection, select, update, items, offset, resumen);
            });
            resumen.setPersonasInformadasPorPure(total);
        }
        resumen.imprimir();
        return resumen;
    }

    private void actualizarPagina(Connection connection, PreparedStatement select,
                                  PreparedStatement update, List<PersonItem> items,
                                  int offset, OrcidUpdateResumen resumen) throws SQLException {
        int noEncontradasPagina = 0;
        int encontradasPagina = 0;
        int sinCambioPagina = 0;
        List<OrcidCambio> cambiosPagina = new ArrayList<>();
        try {
            for (PersonItem item : items) {
                ResultadoPersona resultado = actualizarPersona(select, update, item, resumen);
                if (resultado.codigo() == 0) {
                    noEncontradasPagina++;
                } else if (resultado.codigo() == 1) {
                    encontradasPagina++;
                    if (resultado.cambio() != null) {
                        cambiosPagina.add(resultado.cambio());
                    } else {
                        sinCambioPagina++;
                    }
                }
            }
            connection.commit();
            resumen.registrarNoEncontradas(noEncontradasPagina);
            resumen.registrarEncontradas(encontradasPagina);
            resumen.registrarSinCambio(sinCambioPagina);
            for (OrcidCambio cambio : cambiosPagina) {
                resumen.registrarCambio(cambio);
                System.out.println("ORCID cambiado uuid=" + cambio.getUuid()
                        + " nombre=" + cambio.getNombre()
                        + " anterior=[" + cambio.getOrcidAnterior() + "]"
                        + " nuevo=[" + cambio.getOrcidNuevo() + "]");
            }
        } catch (SQLException ex) {
            connection.rollback();
            resumen.registrarError();
            System.err.println("ERROR SQL en página offset=" + offset
                    + ". Se revirtió la transacción de esa página: " + ex.getMessage());
        }
    }

    private ResultadoPersona actualizarPersona(PreparedStatement select, PreparedStatement update,
                                               PersonItem item, OrcidUpdateResumen resumen)
            throws SQLException {
        if (item == null) {
            resumen.registrarError();
            return ResultadoPersona.omitida();
        }
        resumen.registrarRecuperada();
        String uuid = LocalizedTexts.trimToNull(item.getUuid());
        if (uuid == null) {
            resumen.registrarError();
            System.err.println("WARN Persona Pure sin uuid. pureId=" + item.getPureId());
            return ResultadoPersona.omitida();
        }

        String orcidBd = repository.buscarOrcid(select, uuid);
        if (orcidBd == null) {
            System.err.println("WARN Persona Pure no encontrada en MySQL uuid=" + uuid
                    + " pureId=" + item.getPureId());
            return ResultadoPersona.noEncontrada();
        }

        String orcidPure = LocalizedTexts.trimToNull(item.getOrcid());
        if (mismoOrcid(orcidBd, orcidPure)) {
            return ResultadoPersona.sinCambio();
        }

        repository.actualizarOrcid(update, uuid, orcidPure);
        return ResultadoPersona.cambio(new OrcidCambio(
                uuid, nombre(item), orcidBd, orcidPure == null ? "" : orcidPure));
    }

    private record ResultadoPersona(int codigo, OrcidCambio cambio) {
        static ResultadoPersona omitida() {
            return new ResultadoPersona(-1, null);
        }

        static ResultadoPersona noEncontrada() {
            return new ResultadoPersona(0, null);
        }

        static ResultadoPersona sinCambio() {
            return new ResultadoPersona(1, null);
        }

        static ResultadoPersona cambio(OrcidCambio cambio) {
            return new ResultadoPersona(1, cambio);
        }
    }

    private static boolean mismoOrcid(String anterior, String nuevo) {
        String a = OpenAlexService.normalizarOrcid(anterior);
        String b = OpenAlexService.normalizarOrcid(nuevo);
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.equalsIgnoreCase(b);
    }

    private static String nombre(PersonItem item) {
        PersonName name = item.getName();
        if (name == null) {
            return "";
        }
        String first = name.getFirstName() == null ? "" : name.getFirstName().trim();
        String last = name.getLastName() == null ? "" : name.getLastName().trim();
        return (first + " " + last).trim();
    }
}
