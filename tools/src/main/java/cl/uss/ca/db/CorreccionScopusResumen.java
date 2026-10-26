package cl.uss.ca.db;

public class CorreccionScopusResumen {
    private String modo = "DRY_RUN";
    private int scopusIdsCompartidos;
    private int gruposCorregibles;
    private int personasCandidatas;
    private int getRealizados;
    private int validacionesCorrectas;
    private int validacionesRechazadas;
    private int putRealizados;
    private int putExitosos;
    private int putConError;
    private int verificacionesOk;
    private int verificacionesError;
    private String archivoAuditoria = "correccion_scopus_pure.csv";

    public void setModo(String modo) {
        this.modo = modo == null ? "DRY_RUN" : modo;
    }

    public void setScopusIdsCompartidos(int scopusIdsCompartidos) {
        this.scopusIdsCompartidos = scopusIdsCompartidos;
    }

    public void setGruposCorregibles(int gruposCorregibles) {
        this.gruposCorregibles = gruposCorregibles;
    }

    public void setPersonasCandidatas(int personasCandidatas) {
        this.personasCandidatas = personasCandidatas;
    }

    public void registrarGet() {
        getRealizados++;
    }

    public void registrarValidacionCorrecta() {
        validacionesCorrectas++;
    }

    public void registrarValidacionRechazada() {
        validacionesRechazadas++;
    }

    public void registrarPut() {
        putRealizados++;
    }

    public void registrarPutExitoso() {
        putExitosos++;
    }

    public void registrarPutError() {
        putConError++;
    }

    public void registrarVerificacionOk() {
        verificacionesOk++;
    }

    public void registrarVerificacionError() {
        verificacionesError++;
    }

    public void setArchivoAuditoria(String archivoAuditoria) {
        this.archivoAuditoria = archivoAuditoria == null ? "" : archivoAuditoria;
    }

    public void imprimir() {
        System.out.println("================================================");
        System.out.println("CORRECCIÓN SCOPUS PURE FINALIZADA");
        System.out.println("================================================");
        System.out.println();
        System.out.println("Modo:                              " + modo);
        System.out.println();
        System.out.println("Scopus ID compartidos:             " + scopusIdsCompartidos);
        System.out.println("Grupos corregibles:                " + gruposCorregibles);
        System.out.println("Personas candidatas:               " + personasCandidatas);
        System.out.println();
        System.out.println("GET Pure realizados:               " + getRealizados);
        System.out.println("Validaciones correctas:            " + validacionesCorrectas);
        System.out.println("Validaciones rechazadas:           " + validacionesRechazadas);
        System.out.println();
        System.out.println("PUT realizados:                    " + putRealizados);
        System.out.println("PUT exitosos:                      " + putExitosos);
        System.out.println("PUT con error:                     " + putConError);
        System.out.println();
        System.out.println("Verificaciones posteriores OK:     " + verificacionesOk);
        System.out.println("Verificaciones posteriores ERROR:  " + verificacionesError);
        System.out.println();
        System.out.println("Archivo auditoría:");
        System.out.println(archivoAuditoria);
    }
}
