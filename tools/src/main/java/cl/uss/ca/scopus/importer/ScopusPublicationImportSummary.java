package cl.uss.ca.scopus.importer;

public class ScopusPublicationImportSummary {
    private String archivo = "";
    private boolean dryRun = true;
    private int registrosLeidos;
    private int publicacionesNuevas;
    private int publicacionesActualizadas;
    private int autoresRelacionados;
    private int autoresSinMatch;
    private int afiliaciones;
    private int keywordsAuthor;
    private int keywordsIndex;
    private int warnings;
    private int errores;

    public void setArchivo(String archivo) {
        this.archivo = archivo == null ? "" : archivo;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public void registrarLeido() {
        registrosLeidos++;
    }

    public void registrarNueva() {
        publicacionesNuevas++;
    }

    public void registrarActualizada() {
        publicacionesActualizadas++;
    }

    public void registrarAutores(int relacionados, int sinMatch) {
        autoresRelacionados += Math.max(0, relacionados);
        autoresSinMatch += Math.max(0, sinMatch);
    }

    public void registrarAfiliaciones(int cantidad) {
        afiliaciones += Math.max(0, cantidad);
    }

    public void registrarKeywordsAuthor(int cantidad) {
        keywordsAuthor += Math.max(0, cantidad);
    }

    public void registrarKeywordsIndex(int cantidad) {
        keywordsIndex += Math.max(0, cantidad);
    }

    public void registrarWarning() {
        warnings++;
    }

    public void registrarWarnings(int cantidad) {
        warnings += Math.max(0, cantidad);
    }

    public void registrarError() {
        errores++;
    }

    public int getRegistrosLeidos() {
        return registrosLeidos;
    }

    public void imprimir() {
        System.out.println("================================================");
        System.out.println("CARGA SCOPUS PUBLICATIONS FINALIZADA");
        System.out.println("================================================");
        System.out.println();
        System.out.println("Archivo:");
        System.out.println(archivo);
        System.out.println();
        System.out.println("Registros leídos:                       " + registrosLeidos);
        System.out.println("Publicaciones nuevas:                   " + publicacionesNuevas);
        System.out.println("Publicaciones actualizadas:             " + publicacionesActualizadas);
        System.out.println("Autores relacionados:                   " + autoresRelacionados);
        System.out.println("Autores sin match en scopus_author:     " + autoresSinMatch);
        System.out.println("Afiliaciones:                           " + afiliaciones);
        System.out.println("Keywords AUTHOR:                        " + keywordsAuthor);
        System.out.println("Keywords INDEX:                         " + keywordsIndex);
        System.out.println("Warnings:                               " + warnings);
        System.out.println("Errores:                                " + errores);
        System.out.println("DRY_RUN:                                " + dryRun);
    }
}
