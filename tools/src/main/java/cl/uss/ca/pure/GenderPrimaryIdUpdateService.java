package cl.uss.ca.pure;

import cl.uss.ca.db.GenderPrimaryIdRepository;
import cl.uss.ca.db.GenderPrimaryIdUpdateResumen;
import cl.uss.ca.pure.json.PersonItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

public class GenderPrimaryIdUpdateService {
    private final PurePersonService personService;
    private final PersonMapper mapper;
    private final GenderPrimaryIdRepository repository;
    private final boolean debug;

    public GenderPrimaryIdUpdateService(PurePersonService personService,
                                        PersonMapper mapper,
                                        GenderPrimaryIdRepository repository,
                                        boolean debug) {
        this.personService = personService;
        this.mapper = mapper;
        this.repository = repository;
        this.debug = debug;
    }

    public GenderPrimaryIdUpdateResumen actualizar() throws Exception {
        System.out.println("Actualizando gender y primaryId desde Pure");
        GenderPrimaryIdUpdateResumen resumen = new GenderPrimaryIdUpdateResumen();
        try (Connection connection = repository.abrirConexion();
             PreparedStatement statement = repository.prepararUpdate(connection)) {
            Integer total = personService.procesarPaginas((items, offset, totalInformado) -> {
                resumen.setPersonasInformadasPorPure(totalInformado);
                actualizarPagina(connection, statement, items, offset, resumen);
            });
            resumen.setPersonasInformadasPorPure(total);
        }
        resumen.imprimir();
        return resumen;
    }

    private void actualizarPagina(Connection connection, PreparedStatement statement,
                                  List<PersonItem> items, int offset,
                                  GenderPrimaryIdUpdateResumen resumen) throws SQLException {
        int actualizadasPagina = 0;
        int noEncontradasPagina = 0;
        try {
            for (PersonItem item : items) {
                int resultado = actualizarPersona(statement, item, resumen);
                if (resultado > 0) {
                    actualizadasPagina++;
                } else if (resultado == 0) {
                    noEncontradasPagina++;
                }
            }
            connection.commit();
            resumen.registrarActualizadas(actualizadasPagina);
            resumen.registrarNoEncontradas(noEncontradasPagina);
        } catch (SQLException ex) {
            connection.rollback();
            resumen.registrarError();
            System.err.println("ERROR SQL en página offset=" + offset
                    + ". Se revirtió la transacción de esa página: " + ex.getMessage());
        }
    }

    /**
     * @return 1 si se actualizó, 0 si uuid no existe en MySQL, -1 si se omitió
     */
    private int actualizarPersona(PreparedStatement statement, PersonItem item,
                                  GenderPrimaryIdUpdateResumen resumen) throws SQLException {
        if (item == null) {
            resumen.registrarError();
            return -1;
        }
        String uuid = LocalizedTexts.trimToNull(item.getUuid());
        String gender = mapper.extraerGender(item);
        String primaryId = mapper.extraerPrimaryId(item);
        resumen.registrarRecuperada(gender, primaryId);

        if (uuid == null) {
            resumen.registrarError();
            System.err.println("WARN Persona Pure sin uuid. pureId=" + item.getPureId());
            return -1;
        }
        if (debug) {
            System.out.println("DEBUG uuid=" + uuid
                    + " gender=" + (gender == null ? "" : gender)
                    + " primaryId=" + (primaryId == null ? "" : primaryId));
        }

        int actualizadas = repository.actualizar(statement, uuid, gender, primaryId);
        if (actualizadas == 0) {
            System.err.println("WARN Persona Pure no encontrada en MySQL uuid=" + uuid
                    + " pureId=" + item.getPureId());
            return 0;
        }
        return 1;
    }
}
