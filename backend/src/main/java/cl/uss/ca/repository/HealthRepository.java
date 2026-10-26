package cl.uss.ca.repository;

import cl.uss.ca.exception.DatabaseException;
import jakarta.enterprise.context.ApplicationScoped;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@ApplicationScoped
public class HealthRepository extends JdbcRepository {

    public boolean ping() {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1");
             ResultSet rs = statement.executeQuery()) {
            return rs.next();
        } catch (SQLException ex) {
            throw new DatabaseException("Health check failed", ex);
        }
    }
}
