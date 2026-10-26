package cl.uss.ca.repository;

import cl.uss.ca.events.EventOrganizingUnits;
import cl.uss.ca.exception.DatabaseException;
import jakarta.enterprise.context.ApplicationScoped;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class EventOrganizingUnitRepository extends JdbcRepository {

    public List<EventOrganizingUnits.Unit> listActive() {
        if (!tableExists("event_organizing_unit")) {
            return EventOrganizingUnits.seed();
        }
        String sql = "SELECT code, name_es, name_en, active, sort_order FROM "
                + qualified("event_organizing_unit")
                + " WHERE active = 1 ORDER BY sort_order ASC, code ASC";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<EventOrganizingUnits.Unit> items = new ArrayList<>();
            while (rs.next()) {
                items.add(new EventOrganizingUnits.Unit(
                        rs.getString("code"),
                        rs.getString("name_es"),
                        rs.getString("name_en"),
                        rs.getBoolean("active"),
                        rs.getInt("sort_order")
                ));
            }
            return items.isEmpty() ? EventOrganizingUnits.seed() : List.copyOf(items);
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list organizing units", ex);
        }
    }
}
