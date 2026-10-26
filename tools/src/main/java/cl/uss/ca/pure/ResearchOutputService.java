package cl.uss.ca.pure;

import cl.uss.ca.db.ResearchOutputCargaResumen;
import cl.uss.ca.db.ResearchOutputRepository;
import cl.uss.ca.pure.json.ResearchOutputItem;
import cl.uss.ca.pure.json.ResearchOutputListResponse;
import cl.uss.ca.pure.ro.ResearchOutput;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class ResearchOutputService {
    public static final int PAGE_SIZE = 1000;

    private final PureApiClient client;
    private final ResearchOutputMapper mapper;
    private final ResearchOutputRepository repository;

    public ResearchOutputService(PureApiClient client, ResearchOutputMapper mapper,
                                 ResearchOutputRepository repository) {
        this.client = client;
        this.mapper = mapper;
        this.repository = repository;
    }

    public ResearchOutputCargaResumen cargarTodosLosResearchOutputs()
            throws IOException, InterruptedException, SQLException {
        ResearchOutputCargaResumen resumen = new ResearchOutputCargaResumen();
        Integer totalInformado = null;
        int offset = 0;

        try (Connection connection = repository.abrirConexion()) {
            while (true) {
                System.out.println("Consultando Research Outputs Pure: offset=" + offset + " size=" + PAGE_SIZE);
                ResearchOutputListResponse pagina = client.obtenerPaginaResearchOutputs(offset, PAGE_SIZE);
                List<ResearchOutputItem> items = pagina.getItems() == null ? List.of() : pagina.getItems();

                if (totalInformado == null) {
                    totalInformado = pagina.getCount();
                    resumen.setInformadosPorPure(totalInformado);
                    System.out.println("Recibidos: " + items.size());
                    System.out.println("Total informado por Pure: " + totalInformado);
                } else {
                    System.out.println("Recibidos: " + items.size());
                }

                for (ResearchOutputItem item : items) {
                    resumen.registrarRecuperado();
                    procesarResearchOutput(connection, item, resumen);
                }

                if (items.isEmpty()) {
                    break;
                }
                offset += PAGE_SIZE;
                if (totalInformado != null && offset >= totalInformado) {
                    break;
                }
            }
        }

        validarConteo(totalInformado, resumen.getRecuperados());
        resumen.imprimir();
        return resumen;
    }

    private void procesarResearchOutput(Connection connection, ResearchOutputItem item,
                                        ResearchOutputCargaResumen resumen) throws SQLException {
        ResearchOutput output = mapper.mapear(item);
        if (output == null || output.getUuid() == null) {
            resumen.registrarError();
            System.err.println("ERROR Research Output sin uuid. pureId="
                    + (item == null ? null : item.getPureId()));
            return;
        }
        try {
            int journals = repository.guardarResearchOutput(connection, output);
            connection.commit();
            resumen.registrarOk(output, journals);
        } catch (Exception ex) {
            connection.rollback();
            resumen.registrarError();
            System.err.println("ERROR persistiendo Research Output uuid=" + output.getUuid()
                    + " pureId=" + output.getPureId() + ": " + ex.getMessage());
        }
    }

    private void validarConteo(Integer totalInformado, int recuperados) {
        if (totalInformado != null && recuperados != totalInformado) {
            System.err.println("Pure informó " + totalInformado
                    + " Research Outputs pero se recuperaron " + recuperados + ".");
        }
    }
}
