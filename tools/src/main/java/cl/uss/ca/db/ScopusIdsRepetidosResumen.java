package cl.uss.ca.db;

public class ScopusIdsRepetidosResumen {
    private int scopusIdsRepetidos;
    private int personasInvolucradas;
    private int parejasGeneradas;
    private int personasConUnScopusId;
    private int personasConVariosScopusIds;
    private String excelGenerado = "";

    public void setScopusIdsRepetidos(int scopusIdsRepetidos) {
        this.scopusIdsRepetidos = scopusIdsRepetidos;
    }

    public void setPersonasInvolucradas(int personasInvolucradas) {
        this.personasInvolucradas = personasInvolucradas;
    }

    public void setParejasGeneradas(int parejasGeneradas) {
        this.parejasGeneradas = parejasGeneradas;
    }

    public void setPersonasConUnScopusId(int personasConUnScopusId) {
        this.personasConUnScopusId = personasConUnScopusId;
    }

    public void setPersonasConVariosScopusIds(int personasConVariosScopusIds) {
        this.personasConVariosScopusIds = personasConVariosScopusIds;
    }

    public void setExcelGenerado(String excelGenerado) {
        this.excelGenerado = excelGenerado == null ? "" : excelGenerado;
    }

    public void imprimir() {
        System.out.println("================================================");
        System.out.println("REPORTE SCOPUS ID REPETIDOS");
        System.out.println("================================================");
        System.out.println();
        System.out.println("Scopus ID repetidos:                " + scopusIdsRepetidos);
        System.out.println("Personas involucradas:              " + personasInvolucradas);
        System.out.println("Parejas generadas:                  " + parejasGeneradas);
        System.out.println();
        System.out.println("Personas con 1 Scopus ID:           " + personasConUnScopusId);
        System.out.println("Personas con >1 Scopus ID:          " + personasConVariosScopusIds);
        System.out.println();
        System.out.println("Excel generado:");
        System.out.println(excelGenerado);
    }
}
