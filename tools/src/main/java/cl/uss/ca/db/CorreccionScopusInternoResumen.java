package cl.uss.ca.db;

public class CorreccionScopusInternoResumen {
    private String modo = "DRY_RUN";
    private int personasCandidatasBd;
    private int scopusDuplicadosDetectados;
    private int getRealizados;
    private int duplicadosConfirmados;
    private int yaCorregidos;
    private int validacionesFallidas;
    private int personasModificadas;
    private int identifiersEliminados;
    private int segundasConservadas;
    private int putExitosos;
    private int putFallidos;
    private int verificacionesOk;
    private int verificacionesFallidas;
    private String archivoAuditoria = "correccion_scopus_duplicados_internos.csv";

    public void setModo(String modo) {
        this.modo = modo == null ? "DRY_RUN" : modo;
    }

    public void setPersonasCandidatasBd(int personasCandidatasBd) {
        this.personasCandidatasBd = personasCandidatasBd;
    }

    public void setScopusDuplicadosDetectados(int scopusDuplicadosDetectados) {
        this.scopusDuplicadosDetectados = scopusDuplicadosDetectados;
    }

    public void registrarGet() {
        getRealizados++;
    }

    public void registrarDuplicadoConfirmado() {
        duplicadosConfirmados++;
    }

    public void registrarYaCorregido() {
        yaCorregidos++;
    }

    public void registrarValidacionFallida() {
        validacionesFallidas++;
    }

    public void registrarPersonaModificada() {
        personasModificadas++;
    }

    public void registrarIdentifiersEliminados(int cantidad) {
        identifiersEliminados += Math.max(0, cantidad);
    }

    public void registrarSegundaConservada() {
        segundasConservadas++;
    }

    public void registrarPutExitoso() {
        putExitosos++;
    }

    public void registrarPutFallido() {
        putFallidos++;
    }

    public void registrarVerificacionOk() {
        verificacionesOk++;
    }

    public void registrarVerificacionFallida() {
        verificacionesFallidas++;
    }

    public void setArchivoAuditoria(String archivoAuditoria) {
        this.archivoAuditoria = archivoAuditoria == null ? "" : archivoAuditoria;
    }

    public void imprimir() {
        System.out.println("================================================");
        System.out.println("CORRECCIÓN SCOPUS DUPLICADOS FINALIZADA");
        System.out.println("================================================");
        System.out.println();
        System.out.println("Modo:                                  " + modo);
        System.out.println();
        System.out.println("Personas candidatas BD:                " + personasCandidatasBd);
        System.out.println("Scopus duplicados detectados:          " + scopusDuplicadosDetectados);
        System.out.println();
        System.out.println("GET Pure realizados:                   " + getRealizados);
        System.out.println();
        System.out.println("Duplicados confirmados:                " + duplicadosConfirmados);
        System.out.println("Ya corregidos:                         " + yaCorregidos);
        System.out.println("Validaciones fallidas:                 " + validacionesFallidas);
        System.out.println();
        System.out.println("Personas modificadas:                  " + personasModificadas);
        System.out.println("Identifiers eliminados:                " + identifiersEliminados);
        System.out.println("Segundas ocurrencias conservadas:       " + segundasConservadas);
        System.out.println();
        System.out.println("PUT exitosos:                          " + putExitosos);
        System.out.println("PUT fallidos:                          " + putFallidos);
        System.out.println();
        System.out.println("Verificaciones posteriores OK:         " + verificacionesOk);
        System.out.println("Verificaciones fallidas:               " + verificacionesFallidas);
        System.out.println();
        System.out.println("Archivo auditoría:");
        System.out.println(archivoAuditoria);
    }
}
