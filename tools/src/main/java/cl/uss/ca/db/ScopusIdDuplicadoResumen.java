package cl.uss.ca.db;

public class ScopusIdDuplicadoResumen {
    private int scopusIdsRepetidos;
    private int gruposPatronUnoVsMultiples;
    private int personasPosiblementeMalAsignadas;
    private int casosTodosConUnId;
    private int casosTodosConMultiplesIds;
    private int filasCsv;
    private String archivoGenerado = "";

    public void setScopusIdsRepetidos(int scopusIdsRepetidos) {
        this.scopusIdsRepetidos = scopusIdsRepetidos;
    }

    public void registrarGrupoPatron() {
        gruposPatronUnoVsMultiples++;
    }

    public void setPersonasPosiblementeMalAsignadas(int personasPosiblementeMalAsignadas) {
        this.personasPosiblementeMalAsignadas = Math.max(0, personasPosiblementeMalAsignadas);
    }

    public void registrarTodosConUnId() {
        casosTodosConUnId++;
    }

    public void registrarTodosConMultiplesIds() {
        casosTodosConMultiplesIds++;
    }

    public void setFilasCsv(int filasCsv) {
        this.filasCsv = filasCsv;
    }

    public void setArchivoGenerado(String archivoGenerado) {
        this.archivoGenerado = archivoGenerado == null ? "" : archivoGenerado;
    }

    public int getScopusIdsRepetidos() {
        return scopusIdsRepetidos;
    }

    public void imprimir() {
        System.out.println("================================================");
        System.out.println("ANÁLISIS SCOPUS ID FINALIZADO");
        System.out.println("================================================");
        System.out.println();
        System.out.println("Scopus ID repetidos entre personas:             " + scopusIdsRepetidos);
        System.out.println();
        System.out.println("Grupos con patrón 1 ID vs múltiples IDs:         " + gruposPatronUnoVsMultiples);
        System.out.println();
        System.out.println("Personas posiblemente mal asignadas:             " + personasPosiblementeMalAsignadas);
        System.out.println("Filas en el CSV:                                 " + filasCsv);
        System.out.println();
        System.out.println("Casos ambiguos:");
        System.out.println("todos con 1 ID:                                  " + casosTodosConUnId);
        System.out.println("todos con múltiples IDs:                         " + casosTodosConMultiplesIds);
        System.out.println();
        System.out.println("Archivo generado:");
        System.out.println(archivoGenerado.isEmpty() ? "scopus_ids_posiblemente_mal_asignados.csv" : archivoGenerado);
    }
}
