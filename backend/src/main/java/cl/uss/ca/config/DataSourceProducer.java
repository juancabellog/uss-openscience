package cl.uss.ca.config;

import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import javax.sql.DataSource;

@ApplicationScoped
public class DataSourceProducer {

    @Resource(lookup = "jdbc/openscience")
    private DataSource dataSource;

    @Produces
    @ApplicationScoped
    public DataSource dataSource() {
        return dataSource;
    }
}
