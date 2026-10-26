package cl.uss.ca.scopus.importer;

import java.nio.file.Path;

public class ScopusPublicationImportOptions {
    private Path archivo;
    private boolean dryRun = true;
    private Integer limit;

    public static ScopusPublicationImportOptions parse(String[] args) {
        ScopusPublicationImportOptions opciones = new ScopusPublicationImportOptions();
        if (args == null) {
            return opciones;
        }
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg == null || arg.isBlank()) {
                continue;
            }
            if (arg.startsWith("--file=")) {
                i = asignarArchivo(opciones, args, i, arg.substring("--file=".length()));
            } else if ("--file".equals(arg)) {
                i = asignarArchivo(opciones, args, i + 1, exigirValor(args, i + 1, "--file"));
            } else if (arg.startsWith("--dry-run=")) {
                opciones.dryRun = parseBoolean(arg.substring("--dry-run=".length()), "--dry-run");
            } else if ("--dry-run".equals(arg)) {
                opciones.dryRun = true;
            } else if (arg.startsWith("--limit=")) {
                opciones.limit = parseEntero(arg.substring("--limit=".length()), "--limit");
            } else if ("--limit".equals(arg)) {
                opciones.limit = parseEntero(exigirValor(args, ++i, "--limit"), "--limit");
            } else if (arg.startsWith("--")) {
                throw new IllegalArgumentException("Argumento no reconocido: " + arg);
            } else if (opciones.archivo == null) {
                i = asignarArchivo(opciones, args, i, arg);
            } else {
                throw new IllegalArgumentException("Argumento no reconocido: " + arg);
            }
        }
        return opciones;
    }

    public Path getArchivo() {
        return archivo;
    }

    public void setArchivo(Path archivo) {
        this.archivo = archivo;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public Integer getLimit() {
        return limit;
    }

    public void validar() {
        if (archivo == null) {
            throw new IllegalArgumentException("""
                    Debe indicar el archivo CSV de Scopus.

                    Maven parte exec.args por espacios; use --file= y, si el nombre tiene espacio,
                    deje la ruta en scopus.publication.csv o en SCOPUS_PUBLICATION_CSV.

                    Ejemplo:
                    mvn compile exec:java -Dexec.mainClass=cl.uss.ca.CargarPublicacionesScopusCsv -Dexec.args="--dry-run=true --limit=10"
                    """);
        }
        if (limit != null && limit < 1) {
            throw new IllegalArgumentException("--limit debe ser mayor que 0");
        }
    }

    private static int asignarArchivo(ScopusPublicationImportOptions opciones,
                                      String[] args,
                                      int indice,
                                      String primerFragmento) {
        if (primerFragmento == null || primerFragmento.isBlank()) {
            throw new IllegalArgumentException("Falta el valor para --file");
        }
        StringBuilder ruta = new StringBuilder(primerFragmento.trim());
        int i = indice;
        while (i + 1 < args.length && esFragmentoDeRuta(args[i + 1])) {
            ruta.append(' ').append(args[++i].trim());
        }
        opciones.archivo = Path.of(ruta.toString());
        return i;
    }

    private static boolean esFragmentoDeRuta(String arg) {
        return arg != null && !arg.isBlank() && !arg.startsWith("--");
    }

    private static String exigirValor(String[] args, int indice, String nombre) {
        if (indice >= args.length || args[indice] == null || args[indice].isBlank()
                || args[indice].startsWith("--")) {
            throw new IllegalArgumentException("Falta el valor para " + nombre);
        }
        return args[indice].trim();
    }

    private static boolean parseBoolean(String valor, String nombre) {
        if (valor == null) {
            throw new IllegalArgumentException("Falta el valor para " + nombre);
        }
        String normalizado = valor.trim();
        if ("true".equalsIgnoreCase(normalizado) || "false".equalsIgnoreCase(normalizado)) {
            return Boolean.parseBoolean(normalizado);
        }
        throw new IllegalArgumentException("Valor inválido para " + nombre + ": " + valor);
    }

    private static Integer parseEntero(String valor, String nombre) {
        try {
            return Integer.parseInt(valor.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Valor inválido para " + nombre + ": " + valor);
        }
    }
}
