package cl.uss.ca.pure;

import cl.uss.ca.db.PersonIdentifierCargaResumen;
import cl.uss.ca.db.PersonIdentifierRepository;
import cl.uss.ca.pure.json.IdentifierItem;
import cl.uss.ca.pure.json.PersonItem;
import cl.uss.ca.pure.json.PersonListResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PersonIdentifierLoadService {
    private final PureApiClient client;
    private final PersonIdentifierMapper mapper;
    private final PersonIdentifierRepository repository;
    private final boolean debug;

    public PersonIdentifierLoadService(PureApiClient client,
                                       PersonIdentifierMapper mapper,
                                       PersonIdentifierRepository repository,
                                       boolean debug) {
        this.client = client;
        this.mapper = mapper;
        this.repository = repository;
        this.debug = debug;
    }

    public PersonIdentifierCargaResumen cargar() throws IOException, InterruptedException, SQLException {
        System.out.println("Cargando identifiers de personas desde Pure");
        PersonIdentifierCargaResumen resumen = new PersonIdentifierCargaResumen();
        Map<String, Long> personIdPorUuid = new HashMap<>();
        Integer totalInformado = null;
        int offset = 0;

        try (Connection connection = repository.abrirConexion();
             PreparedStatement select = repository.prepararSelectPersona(connection);
             PreparedStatement delete = repository.prepararDelete(connection);
             PreparedStatement insert = repository.prepararInsert(connection)) {
            while (true) {
                System.out.println("Consultando Personas Pure:");
                System.out.println("offset=" + offset);
                System.out.println("size=" + PurePersonService.PAGE_SIZE);
                PersonListResponse pagina = client.obtenerPaginaPersonas(offset, PurePersonService.PAGE_SIZE);
                List<PersonItem> items = pagina.getItems() == null ? List.of() : pagina.getItems();

                if (totalInformado == null) {
                    totalInformado = pagina.getCount();
                    System.out.println();
                    System.out.println("Recibidos: " + items.size());
                    if (totalInformado != null) {
                        System.out.println("Total informado por Pure: " + totalInformado);
                    }
                } else {
                    System.out.println();
                    System.out.println("Recibidos: " + items.size());
                }

                for (PersonItem item : items) {
                    sincronizarPersona(connection, select, delete, insert, item, personIdPorUuid, resumen);
                }

                if (items.isEmpty()) {
                    break;
                }
                offset += PurePersonService.PAGE_SIZE;
                if (totalInformado != null && offset >= totalInformado) {
                    break;
                }
            }
        }

        resumen.imprimir();
        return resumen;
    }

    private void sincronizarPersona(Connection connection,
                                    PreparedStatement select,
                                    PreparedStatement delete,
                                    PreparedStatement insert,
                                    PersonItem item,
                                    Map<String, Long> personIdPorUuid,
                                    PersonIdentifierCargaResumen resumen) {
        if (item == null) {
            resumen.registrarError();
            return;
        }
        resumen.registrarPersonaRecuperada();
        String uuid = LocalizedTexts.trimToNull(item.getUuid());
        if (uuid == null) {
            resumen.registrarError();
            System.err.println("WARN Persona Pure sin uuid. pureId=" + item.getPureId());
            return;
        }

        try {
            Long personId = resolverPersonId(select, uuid, personIdPorUuid);
            if (personId == null) {
                resumen.registrarPersonaNoEncontrada();
                System.err.println("WARN Persona no encontrada en pure_person:");
                System.err.println("uuid=" + uuid);
                System.err.println("pureId=" + item.getPureId());
                return;
            }
            resumen.registrarPersonaEncontrada();
            List<PersonIdentifier> identifiers = mapearIdentifiers(item, uuid, resumen);
            repository.eliminarPorPersonId(delete, personId);
            for (PersonIdentifier identifier : identifiers) {
                repository.insertar(insert, personId, identifier);
                resumen.registrarIdentifierInsertado(identifier.getTypeDiscriminator());
                if (debug) {
                    System.out.println("DEBUG Persona uuid=" + uuid);
                    System.out.println("identifier=" + identifier.getTypeDiscriminator());
                    System.out.println("value=" + identifier.getValue());
                }
            }
            connection.commit();
        } catch (SQLException ex) {
            resumen.registrarError();
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                System.err.println("WARN Error al revertir transacción uuid=" + uuid
                        + ": " + rollbackEx.getMessage());
            }
            System.err.println("ERROR SQL sincronizando identifiers uuid=" + uuid
                    + " pureId=" + item.getPureId() + ": " + ex.getMessage());
        }
    }

    private Long resolverPersonId(PreparedStatement select, String uuid,
                                  Map<String, Long> personIdPorUuid) throws SQLException {
        if (personIdPorUuid.containsKey(uuid)) {
            return personIdPorUuid.get(uuid);
        }
        Long personId = repository.buscarIdPorUuid(select, uuid);
        personIdPorUuid.put(uuid, personId);
        return personId;
    }

    private List<PersonIdentifier> mapearIdentifiers(PersonItem item, String uuid,
                                                     PersonIdentifierCargaResumen resumen) {
        List<PersonIdentifier> resultado = new ArrayList<>();
        List<IdentifierItem> items = item.getIdentifiers() == null ? List.of() : item.getIdentifiers();
        for (IdentifierItem identifierItem : items) {
            if (identifierItem == null) {
                continue;
            }
            resumen.registrarIdentifierProcesado();
            PersonIdentifier identifier = mapper.mapear(identifierItem);
            if (identifier == null) {
                registrarIdentifierOmitido(uuid, identifierItem);
                continue;
            }
            resultado.add(identifier);
        }
        return resultado;
    }

    private void registrarIdentifierOmitido(String uuid, IdentifierItem item) {
        String valor = mapper.valor(item);
        String discriminator = mapper.typeDiscriminator(item);
        if (valor == null) {
            System.err.println("WARN Identifier sin value ni id. uuid=" + uuid
                    + " typeDiscriminator=" + (discriminator == null ? "" : discriminator)
                    + " pureId=" + item.getPureId());
            return;
        }
        if (discriminator == null) {
            System.err.println("WARN Identifier sin typeDiscriminator. uuid=" + uuid
                    + " value=" + valor);
        }
    }
}
