package cl.uss.ca.scopus;

import java.nio.file.Path;

public class CorreccionScopusOpciones {
    public static final String ARCHIVO_AUDITORIA = "correccion_scopus_pure.csv";
    private static final int ERRORES_PUT_CONSECUTIVOS_MAX = 5;

    private boolean execute;
    private Integer limit;
    private Long personId;
    private String uuid;
    private Path csvSalida = Path.of(ARCHIVO_AUDITORIA);

    public static CorreccionScopusOpciones parse(String[] args) {
        return parse(args, Path.of(ARCHIVO_AUDITORIA));
    }

    public static CorreccionScopusOpciones parse(String[] args, Path csvDefault) {
        CorreccionScopusOpciones opciones = new CorreccionScopusOpciones();
        if (csvDefault != null) {
            opciones.csvSalida = csvDefault;
        }
        if (args == null) {
            return opciones;
        }
        for (String arg : args) {
            if (arg == null || arg.isBlank()) {
                continue;
            }
            if ("--execute".equals(arg)) {
                opciones.execute = true;
            } else if (arg.startsWith("--limit=")) {
                opciones.limit = parseEntero(arg.substring("--limit=".length()), "--limit");
            } else if (arg.startsWith("--person-id=")) {
                long valor = parseLong(arg.substring("--person-id=".length()), "--person-id");
                opciones.personId = valor;
            } else if (arg.startsWith("--uuid=")) {
                opciones.uuid = arg.substring("--uuid=".length()).trim();
            } else if (arg.startsWith("--out=")) {
                opciones.csvSalida = Path.of(arg.substring("--out=".length()).trim());
            } else if (arg.startsWith("--")) {
                System.err.println("WARN Argumento no reconocido: " + arg);
            }
        }
        return opciones;
    }

    public boolean isExecute() {
        return execute;
    }

    public Integer getLimit() {
        return limit;
    }

    public Long getPersonId() {
        return personId;
    }

    public String getUuid() {
        return uuid;
    }

    public Path getCsvSalida() {
        return csvSalida;
    }

    public int getErroresPutConsecutivosMax() {
        return ERRORES_PUT_CONSECUTIVOS_MAX;
    }

    public String modoTexto() {
        return execute ? "EXECUTE" : "DRY RUN";
    }

    public String modoResumen() {
        return execute ? "EXECUTE" : "DRY_RUN";
    }

    private static Integer parseEntero(String valor, String nombre) {
        try {
            int n = Integer.parseInt(valor.trim());
            if (n < 0) {
                throw new IllegalArgumentException(nombre + " no puede ser negativo");
            }
            return n;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Valor inválido para " + nombre + ": " + valor);
        }
    }

    private static long parseLong(String valor, String nombre) {
        try {
            return Long.parseLong(valor.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Valor inválido para " + nombre + ": " + valor);
        }
    }
}
