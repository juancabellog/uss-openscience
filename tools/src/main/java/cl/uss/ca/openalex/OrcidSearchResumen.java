package cl.uss.ca.openalex;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class OrcidSearchResumen {
    private int investigadoresEvaluados;
    private int orcidEncontrados;
    private int orcidNoEncontrados;
    private int publicacionesDisponibles;
    private int doiConsultados;
    private int doiOmitidosDuplicado;
    private int doiNoEncontradosOpenAlex;
    private int erroresOpenAlex;
    private Path excelGenerado;
    private final List<OrcidEncontrado> encontrados = new ArrayList<>();

    public void registrarPersonaEncontrada(OrcidEncontrado encontrado) {
        investigadoresEvaluados++;
        orcidEncontrados++;
        if (encontrado != null) {
            encontrados.add(encontrado);
            actualizarExcel();
        }
    }

    public void registrarPersonaNoEncontrada(Long personId, String nombreCompleto) {
        investigadoresEvaluados++;
        orcidNoEncontrados++;
    }

    public void agregarPublicaciones(int cantidad) {
        publicacionesDisponibles += cantidad;
    }

    public void registrarDoiConsultado() {
        doiConsultados++;
    }

    public void registrarDoiDuplicado() {
        doiOmitidosDuplicado++;
    }

    public void registrarDoiNoEncontrado() {
        doiNoEncontradosOpenAlex++;
    }

    public void registrarErrorOpenAlex() {
        erroresOpenAlex++;
    }

    public List<OrcidEncontrado> getEncontrados() {
        return encontrados;
    }

    public Path getExcelGenerado() {
        return excelGenerado;
    }

    public void escribirExcel(Path destino) throws IOException {
        Path objetivo = destino == null
                ? Path.of("orcid-encontrados.xlsx").toAbsolutePath()
                : destino.toAbsolutePath();
        excelGenerado = new OrcidExcelWriter().escribir(encontrados, objetivo);
    }

    public void actualizarExcel() {
        Path destino = excelGenerado != null
                ? excelGenerado
                : Path.of("orcid-encontrados.xlsx").toAbsolutePath();
        try {
            escribirExcel(destino);
        } catch (IOException ex) {
            System.err.println("WARN no se pudo actualizar el Excel de ORCID encontrados: " + ex.getMessage());
        }
    }

    public void imprimir() {
        System.out.println("========================================");
        System.out.println("BÚSQUEDA ORCID OPENALEX FINALIZADA");
        System.out.println("========================================");
        System.out.println();
        System.out.println("Investigadores evaluados:        " + investigadoresEvaluados);
        System.out.println("ORCID encontrados:               " + orcidEncontrados);
        System.out.println("ORCID no encontrados:            " + orcidNoEncontrados);
        System.out.println();
        System.out.println("Publicaciones disponibles:       " + publicacionesDisponibles);
        System.out.println("DOI consultados en OpenAlex:     " + doiConsultados);
        System.out.println("DOI omitidos por duplicado:      " + doiOmitidosDuplicado);
        System.out.println("DOI no encontrados en OpenAlex:  " + doiNoEncontradosOpenAlex);
        System.out.println("Errores OpenAlex:                " + erroresOpenAlex);
        System.out.println();
        System.out.println("Porcentaje ORCID encontrado:");
        System.out.println(porcentaje() + " %");
        if (excelGenerado != null) {
            System.out.println();
            System.out.println("Excel generado:");
            System.out.println(excelGenerado.toAbsolutePath());
        }
        System.out.println();
        System.out.println("id\torcid\tCargo\tnombre completo");
        for (OrcidEncontrado encontrado : encontrados) {
            String id = encontrado.getPersonId() == null ? "" : String.valueOf(encontrado.getPersonId());
            String orcid = encontrado.getOrcid() == null ? "" : encontrado.getOrcid();
            String cargo = encontrado.getCargo() == null ? "" : encontrado.getCargo();
            String nombre = encontrado.getNombrePersona() == null ? "" : encontrado.getNombrePersona();
            System.out.println(id + "\t" + orcid + "\t" + cargo + "\t" + nombre);
        }
    }

    private String porcentaje() {
        if (investigadoresEvaluados == 0) {
            return "0.00";
        }
        double valor = 100.0 * orcidEncontrados / investigadoresEvaluados;
        return String.format(java.util.Locale.ROOT, "%.2f", valor);
    }
}
