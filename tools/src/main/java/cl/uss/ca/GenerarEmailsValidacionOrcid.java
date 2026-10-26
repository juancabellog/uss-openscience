package cl.uss.ca;

import cl.uss.ca.config.AppConfig;
import cl.uss.ca.db.PurePersonEmailRepository;
import cl.uss.ca.orcid.email.ValidacionOrcidEmailService;

import java.nio.file.Path;
import java.util.Properties;

public class GenerarEmailsValidacionOrcid {
    private static final Path EXCEL_ENTRADA = Path.of(
            "/Users/manolocabello/Documents/uss/CA/orcid/Orcid No validado.xlsx");
    private static final Path PLANTILLA_WORD = Path.of(
            "/Users/manolocabello/Documents/uss/CA/orcid/Email para validar orcid 2.docx");
    private static final Path EXCEL_SALIDA = Path.of(
            "/Users/manolocabello/Documents/uss/CA/orcid/Orcid No validado con email.xlsx");
    private static final Path DIRECTORIO_DOCX = Path.of(
            "/Users/manolocabello/Documents/uss/CA/orcid/emails_validacion_orcid");

    public static void main(String[] args) {
        try {
            Path excelEntrada = args.length > 0 ? Path.of(args[0]) : EXCEL_ENTRADA;
            Path plantilla = args.length > 1 ? Path.of(args[1]) : PLANTILLA_WORD;
            Properties config = AppConfig.load();
            ValidacionOrcidEmailService servicio = new ValidacionOrcidEmailService(
                    new PurePersonEmailRepository(),
                    AppConfig.dbUrl(config),
                    AppConfig.dbUser(config),
                    AppConfig.dbPassword(config)
            );
            servicio.procesarExcel(
                    excelEntrada,
                    plantilla,
                    ValidacionOrcidEmailService.destinoExcelSeguro(EXCEL_SALIDA),
                    DIRECTORIO_DOCX
            );
        } catch (Exception ex) {
            System.err.println("Error al generar emails de validación ORCID: " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
