package cl.uss.ca.db;

public class PersonIdentifierCargaResumen {
    private int personasRecuperadas;
    private int personasEncontradasEnBd;
    private int personasNoEncontradas;
    private int identifiersProcesados;
    private int identifiersInsertados;
    private int primaryIds;
    private int classifiedIds;
    private int otrosTipos;
    private int errores;

    public void registrarPersonaRecuperada() {
        personasRecuperadas++;
    }

    public void registrarPersonaEncontrada() {
        personasEncontradasEnBd++;
    }

    public void registrarPersonaNoEncontrada() {
        personasNoEncontradas++;
    }

    public void registrarIdentifierProcesado() {
        identifiersProcesados++;
    }

    public void registrarIdentifierInsertado(String typeDiscriminator) {
        identifiersInsertados++;
        registrarTipo(typeDiscriminator);
    }

    public void registrarError() {
        errores++;
    }

    public void imprimir() {
        System.out.println("================================================");
        System.out.println("CARGA PERSON IDENTIFIERS FINALIZADA");
        System.out.println("================================================");
        System.out.println();
        System.out.println("Personas recuperadas:                 " + personasRecuperadas);
        System.out.println("Personas encontradas en BD:           " + personasEncontradasEnBd);
        System.out.println("Personas no encontradas:              " + personasNoEncontradas);
        System.out.println();
        System.out.println("Identifiers procesados:               " + identifiersProcesados);
        System.out.println("Identifiers insertados:               " + identifiersInsertados);
        System.out.println();
        System.out.println("PrimaryId:                            " + primaryIds);
        System.out.println("ClassifiedId:                         " + classifiedIds);
        System.out.println("Otros tipos:                          " + otrosTipos);
        System.out.println();
        System.out.println("Errores:                              " + errores);
    }

    private void registrarTipo(String typeDiscriminator) {
        if ("PrimaryId".equals(typeDiscriminator)) {
            primaryIds++;
        } else if ("ClassifiedId".equals(typeDiscriminator)) {
            classifiedIds++;
        } else {
            otrosTipos++;
        }
    }
}
