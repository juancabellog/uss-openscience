package cl.uss.ca.orcid.email;

import java.nio.file.Path;

public class ValidacionOrcidEmailResumen {
    private int filasProcesadas;
    private int uuidUnicosConsultados;
    private int emailsEncontrados;
    private int emailsNoEncontrados;
    private int registrosOkSi;
    private int documentosGenerados;
    private int omitidosOkDistintoSi;
    private int omitidosSinEmail;
    private int duplicadosEmailOmitidos;
    private int conflictosOrcid;
    private Path excelGenerado;
    private Path directorioDocumentos;

    public void setFilasProcesadas(int filasProcesadas) {
        this.filasProcesadas = filasProcesadas;
    }

    public void setUuidUnicosConsultados(int uuidUnicosConsultados) {
        this.uuidUnicosConsultados = uuidUnicosConsultados;
    }

    public void registrarEmailEncontrado() {
        emailsEncontrados++;
    }

    public void registrarEmailNoEncontrado() {
        emailsNoEncontrados++;
    }

    public void setRegistrosOkSi(int registrosOkSi) {
        this.registrosOkSi = registrosOkSi;
    }

    public void registrarDocumentoGenerado() {
        documentosGenerados++;
    }

    public void registrarOmitidoOk() {
        omitidosOkDistintoSi++;
    }

    public void registrarOmitidoSinEmail() {
        omitidosSinEmail++;
    }

    public void registrarDuplicadoEmail() {
        duplicadosEmailOmitidos++;
    }

    public void registrarConflicto() {
        conflictosOrcid++;
    }

    public void setExcelGenerado(Path excelGenerado) {
        this.excelGenerado = excelGenerado;
    }

    public void setDirectorioDocumentos(Path directorioDocumentos) {
        this.directorioDocumentos = directorioDocumentos;
    }

    public int getOmitidosOkDistintoSi() {
        return omitidosOkDistintoSi;
    }

    public int getOmitidosSinEmail() {
        return omitidosSinEmail;
    }

    public int getConflictosOrcid() {
        return conflictosOrcid;
    }

    public void imprimirProgreso() {
        System.out.println("Filas leídas: " + filasProcesadas);
        System.out.println();
        System.out.println("Consultando emails...");
        System.out.println();
        System.out.println("Emails encontrados: " + emailsEncontrados);
        System.out.println("Emails no encontrados: " + emailsNoEncontrados);
        System.out.println();
        System.out.println("Generando documentos...");
        System.out.println();
        System.out.println("DOCX generados: " + documentosGenerados);
        System.out.println("Omitidos por Ok != SI: " + omitidosOkDistintoSi);
        System.out.println("Omitidos sin email: " + omitidosSinEmail);
        System.out.println("Conflictos ORCID/email: " + conflictosOrcid);
    }

    public void imprimirFinal() {
        System.out.println("================================================");
        System.out.println("GENERACIÓN VALIDACIÓN ORCID FINALIZADA");
        System.out.println("================================================");
        System.out.println();
        System.out.println("Filas procesadas:                    " + filasProcesadas);
        System.out.println("UUID únicos consultados:             " + uuidUnicosConsultados);
        System.out.println();
        System.out.println("Emails encontrados:                  " + emailsEncontrados);
        System.out.println("Emails no encontrados:               " + emailsNoEncontrados);
        System.out.println();
        System.out.println("Registros Ok = SI:                   " + registrosOkSi);
        System.out.println("Documentos DOCX generados:           " + documentosGenerados);
        System.out.println("Duplicados por email omitidos:       " + duplicadosEmailOmitidos);
        System.out.println("Conflictos ORCID detectados:         " + conflictosOrcid);
        System.out.println();
        System.out.println("Excel generado:");
        System.out.println(excelGenerado == null ? "" : excelGenerado.toAbsolutePath());
        System.out.println();
        System.out.println("Directorio de documentos:");
        System.out.println(directorioDocumentos == null ? "" : directorioDocumentos.toAbsolutePath());
    }
}
