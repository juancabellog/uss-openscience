package cl.uss.ca;

import cl.uss.ca.events.EventPublicLinks;
import cl.uss.ca.exception.ErrorLog;
import cl.uss.ca.service.EventPublicUrlSettings;
import cl.uss.ca.service.PublicAppUrl;
import cl.uss.ca.service.UserAdminService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class StartupDiagnostics {

    private static final Logger LOG = Logger.getLogger(StartupDiagnostics.class.getName());

    @Resource(lookup = "jdbc/openscience")
    DataSource dataSource;

    @Inject
    UserAdminService userAdminService;

    @Inject
    @ConfigProperty(name = "openscience.frontend.origin", defaultValue = "http://localhost:4200")
    String frontendOrigin;

    @Inject
    @ConfigProperty(name = "openscience.app.publicUrl", defaultValue = "http://localhost:4200")
    String publicUrl;

    @Inject
    EventPublicUrlSettings eventsPublicUrl;

    @PostConstruct
    void logStartup() {
        String appUrl = PublicAppUrl.resolve(System.getenv("APP_PUBLIC_URL"), publicUrl);
        String eventsUrl = eventsPublicUrl.resolve();
        LOG.info("OPENSCIENCE-USS listo. CORS origin=" + frontendOrigin);
        LOG.info("APP public URL (emails)=" + appUrl);
        LOG.info("Events public URL (emails)=" + eventsUrl);
        if (EventPublicLinks.looksDevelopment(eventsUrl) && !eventsPublicUrl.allowLocalMailLinks()) {
            LOG.severe("EVENTS_PUBLIC_URL apunta a un host de desarrollo. Los correos no usarán localhost.");
        }
        LOG.info("API local: http://<hydra>:9080/api/v1/dashboard");
        LOG.info("Si al abrir el Dashboard NO aparece REQUEST GET /api/v1/dashboard, el browser no pega a este Java (sigue en :81/api).");
        probeJdbc();
        LOG.info("Auth bootstrap begin");
        try {
            userAdminService.bootstrapIfNeeded();
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Auth bootstrap skipped: " + ex.getMessage(), ex);
        }
    }

    private void probeJdbc() {
        if (dataSource == null) {
            LOG.severe("JDBC FAIL: DataSource jdbc/openscience no está ligado");
            return;
        }
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT 1")) {
            rs.next();
            DatabaseMetaData meta = connection.getMetaData();
            LOG.info("JDBC OK user=" + meta.getUserName()
                    + " url=" + meta.getURL()
                    + " catalog=" + connection.getCatalog());
        } catch (Exception ex) {
            StringBuilder causes = new StringBuilder();
            for (Throwable t = ex; t != null; t = t.getCause()) {
                causes.append(" | ").append(t.getClass().getSimpleName())
                        .append(": ").append(t.getMessage());
            }
            ErrorLog.unexpected(LOG, "JDBC FAIL:" + causes, ex);
        }
    }
}
