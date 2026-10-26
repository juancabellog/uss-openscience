package cl.uss.ca.orcid;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class OrcidValidationResumen {
    private int filasProcesadas;
    private int personasUnicas;
    private int okSi;
    private int okNo;
    private int okError;
    private int afiliacionSi;
    private int afiliacionNo;
    private int afiliacionError;
    private int personasConDoiComun;
    private int personasSinDoiComun;
    private int doiBdAnalizados;
    private int doiOrcidAnalizados;
    private int doiCoincidentes;
    private int orcidUnicosConsultados;
    private int respuestasDesdeCache;
    private int erroresHttp;
    private Path archivoGenerado;
    private final Set<String> personasVistas = new HashSet<>();

    public void registrarFila(OrcidFilaValidada fila) {
        filasProcesadas++;
        if (fila == null) {
            return;
        }
        contarOk(fila.getOk());
        contarAfiliacion(fila.getAfiliacion());
        String clavePersona = clavePersona(fila);
        if (!personasVistas.add(clavePersona)) {
            return;
        }
        personasUnicas++;
        doiBdAnalizados += fila.getCantidadDoisBd();
        doiOrcidAnalizados += fila.getCantidadDoisOrcid();
        doiCoincidentes += fila.getCantidadDoisComunes();
        if (fila.getCantidadDoisComunes() > 0) {
            personasConDoiComun++;
        } else {
            personasSinDoiComun++;
        }
    }

    public void registrarConsultaHttp() {
        orcidUnicosConsultados++;
    }

    public void registrarCache() {
        respuestasDesdeCache++;
    }

    public void registrarErrorHttp() {
        erroresHttp++;
    }

    public void setArchivoGenerado(Path archivoGenerado) {
        this.archivoGenerado = archivoGenerado;
    }

    public void imprimir() {
        System.out.println("========================================");
        System.out.println("VALIDACIÓN ORCID FINALIZADA");
        System.out.println("========================================");
        System.out.println();
        System.out.println("Filas procesadas:                    " + filasProcesadas);
        System.out.println("Personas únicas:                     " + personasUnicas);
        System.out.println();
        System.out.println("Nombre coherente:");
        System.out.println("  SI:                                " + okSi);
        System.out.println("  NO:                                " + okNo);
        System.out.println("  ERROR:                             " + okError);
        System.out.println();
        System.out.println("Afiliación USS:");
        System.out.println("  SI:                                " + afiliacionSi);
        System.out.println("  NO:                                " + afiliacionNo);
        System.out.println("  ERROR:                             " + afiliacionError);
        System.out.println();
        System.out.println("Personas con DOI en común:           " + personasConDoiComun);
        System.out.println("Personas sin DOI en común:           " + personasSinDoiComun);
        System.out.println();
        System.out.println("DOI BD analizados:                   " + doiBdAnalizados);
        System.out.println("DOI ORCID analizados:                " + doiOrcidAnalizados);
        System.out.println("DOI coincidentes:                    " + doiCoincidentes);
        System.out.println();
        System.out.println("ORCID únicos consultados:            " + orcidUnicosConsultados);
        System.out.println("Respuestas desde cache:              " + respuestasDesdeCache);
        System.out.println("Errores HTTP:                        " + erroresHttp);
        System.out.println();
        System.out.println("Archivo generado:");
        System.out.println(archivoGenerado == null ? "" : archivoGenerado.toAbsolutePath());
    }

    private static String clavePersona(OrcidFilaValidada fila) {
        String uuid = fila.getUuid();
        if (uuid != null && !uuid.isBlank()) {
            return "uuid:" + uuid;
        }
        String id = fila.getOrigen() == null ? "" : fila.getOrigen().getId();
        if (id != null && !id.isBlank()) {
            return "id:" + id;
        }
        int numero = fila.getOrigen() == null ? 0 : fila.getOrigen().getNumeroFilaExcel();
        return "fila:" + numero;
    }

    private void contarOk(String ok) {
        if ("SI".equals(ok)) {
            okSi++;
        } else if ("ERROR".equals(ok)) {
            okError++;
        } else {
            okNo++;
        }
    }

    private void contarAfiliacion(String afiliacion) {
        if ("SI".equals(afiliacion)) {
            afiliacionSi++;
        } else if ("ERROR".equals(afiliacion)) {
            afiliacionError++;
        } else {
            afiliacionNo++;
        }
    }
}
