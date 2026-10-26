package cl.uss.ca.pure;

import cl.uss.ca.pure.json.PersonItem;
import cl.uss.ca.pure.json.PersonListResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PurePersonService {
    public static final int PAGE_SIZE = 1000;

    private final PureApiClient client;
    private final PersonMapper mapper;

    public PurePersonService(PureApiClient client, PersonMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    public List<PurePerson> obtenerTodasLasPersonas() throws IOException, InterruptedException {
        Map<String, PurePerson> personasPorUuid = new LinkedHashMap<>();
        Integer totalInformado = null;
        int offset = 0;

        while (true) {
            System.out.println("Consultando personas Pure: offset=" + offset + " size=" + PAGE_SIZE);
            PersonListResponse pagina = client.obtenerPaginaPersonas(offset, PAGE_SIZE);
            List<PersonItem> items = pagina.getItems() == null ? List.of() : pagina.getItems();

            if (totalInformado == null) {
                totalInformado = pagina.getCount();
                System.out.println("Recibidos: " + items.size());
                System.out.println("Total informado por Pure: " + totalInformado);
            } else {
                System.out.println("Recibidos: " + items.size());
            }

            for (PersonItem item : items) {
                PurePerson persona = mapper.mapearPersona(item);
                if (persona == null || persona.getUuid() == null) {
                    System.err.println("Se omitió un item de Pure sin uuid.");
                    continue;
                }
                personasPorUuid.putIfAbsent(persona.getUuid(), persona);
            }

            if (items.isEmpty()) {
                break;
            }
            offset += PAGE_SIZE;
            if (totalInformado != null && offset >= totalInformado) {
                break;
            }
        }

        List<PurePerson> personas = new ArrayList<>(personasPorUuid.values());
        validarConteo(totalInformado, personas.size());
        return personas;
    }

    @FunctionalInterface
    public interface PersonPageProcessor {
        void procesar(List<PersonItem> items, int offset, Integer totalInformado) throws Exception;
    }

    public Integer procesarPaginas(PersonPageProcessor processor)
            throws IOException, InterruptedException {
        Integer totalInformado = null;
        int offset = 0;
        try {
            while (true) {
                System.out.println("Consultando personas Pure: offset=" + offset + " size=" + PAGE_SIZE);
                PersonListResponse pagina = client.obtenerPaginaPersonas(offset, PAGE_SIZE);
                List<PersonItem> items = pagina.getItems() == null ? List.of() : pagina.getItems();

                if (totalInformado == null) {
                    totalInformado = pagina.getCount();
                    System.out.println("Recibidos: " + items.size());
                    System.out.println("Total informado por Pure: " + totalInformado);
                } else {
                    System.out.println("Recibidos: " + items.size());
                }

                processor.procesar(items, offset, totalInformado);

                if (items.isEmpty()) {
                    break;
                }
                offset += PAGE_SIZE;
                if (totalInformado != null && offset >= totalInformado) {
                    break;
                }
            }
        } catch (IOException | InterruptedException | RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IOException("Error procesando página Pure offset=" + offset, ex);
        }
        return totalInformado;
    }

    private void validarConteo(Integer totalInformado, int recuperadas) {
        if (totalInformado != null && recuperadas != totalInformado) {
            System.err.println("Pure informó " + totalInformado + " personas pero se recuperaron "
                    + recuperadas + ".");
        }
    }
}
