package cl.uss.ca.db;

public class ScopusAuthorCargaResumen {
    private String archivo = "";
    private String affiliationName = "";
    private String affiliationId = "";
    private String archivoAmbiguos = "";
    private int filasCsvLeidas;
    private int autoresEncontradosPorAuthId;
    private int autoresEncontradosPorNombre;
    private int autoresNuevosConAuthId;
    private int autoresNuevosSinAuthId;
    private int autoresNombreAmbiguo;
    private int afiliacionesInsertadas;
    private int afiliacionesYaExistentes;
    private int areasInsertadasAutoresNuevos;
    private int filasIgnoradas;
    private int errores;

    public void setArchivo(String archivo) {
        this.archivo = archivo == null ? "" : archivo;
    }

    public void setAfiliacion(String affiliationName, String affiliationId) {
        this.affiliationName = affiliationName == null ? "" : affiliationName;
        this.affiliationId = affiliationId == null ? "" : affiliationId;
    }

    public void setArchivoAmbiguos(String archivoAmbiguos) {
        this.archivoAmbiguos = archivoAmbiguos == null ? "" : archivoAmbiguos;
    }

    public void registrarFilaLeida() {
        filasCsvLeidas++;
    }

    public void registrarEncontradoPorAuthId() {
        autoresEncontradosPorAuthId++;
    }

    public void registrarEncontradoPorNombre() {
        autoresEncontradosPorNombre++;
    }

    public void registrarNuevoConAuthId() {
        autoresNuevosConAuthId++;
    }

    public void registrarNuevoSinAuthId() {
        autoresNuevosSinAuthId++;
    }

    public void registrarNombreAmbiguo() {
        autoresNombreAmbiguo++;
    }

    public void registrarAfiliacionInsertada() {
        afiliacionesInsertadas++;
    }

    public void registrarAfiliacionExistente() {
        afiliacionesYaExistentes++;
    }

    public void registrarAreasInsertadas(int cantidad) {
        areasInsertadasAutoresNuevos += Math.max(0, cantidad);
    }

    public void registrarFilaIgnorada() {
        filasIgnoradas++;
    }

    public void registrarError() {
        errores++;
    }

    public int autoresProcesados() {
        return autoresEncontradosPorAuthId
                + autoresEncontradosPorNombre
                + autoresNuevosConAuthId
                + autoresNuevosSinAuthId;
    }

    public void imprimir() {
        System.out.println("================================================");
        System.out.println("CARGA SCOPUS AUTHORS FINALIZADA");
        System.out.println("================================================");
        System.out.println();
        System.out.println("Archivo:");
        System.out.println(archivo);
        System.out.println();
        System.out.println("Afiliación:");
        System.out.println(affiliationName);
        System.out.println();
        System.out.println("Affiliation ID:");
        System.out.println(affiliationId);
        System.out.println();
        System.out.println("Filas procesadas:                       " + filasCsvLeidas);
        System.out.println();
        System.out.println("Autores existentes por Auth-ID:         " + autoresEncontradosPorAuthId);
        System.out.println("Autores existentes por nombre:          " + autoresEncontradosPorNombre);
        System.out.println();
        System.out.println("Autores nuevos con Auth-ID:             " + autoresNuevosConAuthId);
        System.out.println("Autores nuevos sin Auth-ID:             " + autoresNuevosSinAuthId);
        System.out.println();
        System.out.println("Nombres ambiguos:                       " + autoresNombreAmbiguo);
        System.out.println();
        System.out.println("Afiliaciones insertadas:                " + afiliacionesInsertadas);
        System.out.println("Afiliaciones ya existentes:             " + afiliacionesYaExistentes);
        System.out.println();
        System.out.println("Áreas insertadas para autores nuevos:   " + areasInsertadasAutoresNuevos);
        System.out.println();
        System.out.println("Errores:                                " + errores);
        if (filasIgnoradas > 0) {
            System.out.println();
            System.out.println("Filas ignoradas:                        " + filasIgnoradas);
        }
        if (!archivoAmbiguos.isBlank()) {
            System.out.println();
            System.out.println("CSV nombres ambiguos:");
            System.out.println(archivoAmbiguos);
        }
    }
}
