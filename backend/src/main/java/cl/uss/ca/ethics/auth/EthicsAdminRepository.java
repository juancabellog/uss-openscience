package cl.uss.ca.ethics.auth;

import cl.uss.ca.exception.DatabaseException;
import cl.uss.ca.repository.JdbcRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Lee y asegura el acceso de Ethics sobre {@code app_user}. No inserta cuentas ni contraseñas.
 * {@code ethics_role_assignment} no tiene {@code application_id}: la tabla ya es de Ethics.
 * No se publica como {@link RoleAssignmentProvider}: ese tipo lo produce {@link EthicsAuthorizationConfig}.
 */
@ApplicationScoped
@Typed(EthicsAdminRepository.class)
public class EthicsAdminRepository extends JdbcRepository implements EthicsAdminDirectory, RoleAssignmentProvider {
    @Override
    public AdminAccount findAccount(String normalizedEmail) {
        if (normalizedEmail == null || !tableExists("app_user")) {
            return null;
        }
        String sql = """
                SELECT id, email
                FROM %s
                WHERE email_normalized = ? AND deleted_at IS NULL
                LIMIT 1
                """.formatted(qualified("app_user"));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizedEmail);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                long id = rs.getLong("id");
                return new AdminAccount(id, rs.getString("email"), personId(id, normalizedEmail), purePersonId(normalizedEmail));
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to find the OpenScience account", ex);
        }
    }

    @Override
    public boolean ensureEthicsActive(long userId) {
        ensureSchema();
        String update = """
                UPDATE %s
                SET status = 'ACTIVE'
                WHERE user_id = ? AND application_id = 'ETHICS' AND status <> 'ACTIVE'
                """.formatted(qualified("user_application"));
        String insert = """
                INSERT INTO %s (user_id, application_id, status)
                SELECT ?, 'ETHICS', 'ACTIVE' FROM DUAL
                WHERE NOT EXISTS (
                    SELECT 1 FROM %s WHERE user_id = ? AND application_id = 'ETHICS'
                )
                """.formatted(qualified("user_application"), qualified("user_application"));
        return write(update, userId) || write(insert, userId, userId);
    }

    @Override
    public boolean ensureStructuralRole(long userId, String roleId) {
        ensureSchema();
        String table = qualified("ethics_role_assignment");
        boolean validUntil = columnExists("ethics_role_assignment", "valid_until");
        boolean validFrom = columnExists("ethics_role_assignment", "valid_from");
        StringBuilder set = new StringBuilder("status = 'ACTIVE'");
        StringBuilder stale = new StringBuilder("status <> 'ACTIVE'");
        if (validUntil) {
            set.append(", valid_until = NULL");
            stale.append(" OR (valid_until IS NOT NULL AND valid_until < CURRENT_DATE)");
        }
        if (validFrom) {
            set.append(", valid_from = CASE WHEN valid_from > CURRENT_DATE THEN NULL ELSE valid_from END");
            stale.append(" OR (valid_from IS NOT NULL AND valid_from > CURRENT_DATE)");
        }
        String update = """
                UPDATE %s
                SET %s
                WHERE user_id = ? AND role_id = ? AND scope_type = 'GLOBAL' AND scope_id = '*'
                  AND (%s)
                """.formatted(table, set, stale);
        String insert = """
                INSERT INTO %s (user_id, role_id, scope_type, scope_id, status)
                SELECT ?, ?, 'GLOBAL', '*', 'ACTIVE' FROM DUAL
                WHERE NOT EXISTS (
                    SELECT 1 FROM %s
                    WHERE user_id = ? AND role_id = ? AND scope_type = 'GLOBAL' AND scope_id = '*'
                )
                """.formatted(table, table);
        return writeRole(update, userId, roleId) || writeRole(insert, userId, roleId, userId, roleId);
    }

    @Override
    public void audit(long userId, String action, String detail) {
        audit(null, userId, action, detail);
    }

    public void audit(Long actorUserId, long targetUserId, String action, String detail) {
        if (!tableExists("app_security_event")) {
            return;
        }
        String sql = """
                INSERT INTO %s (event_type, actor_user_id, target_user_id, target_role_id, detail)
                VALUES (?, ?, ?, NULL, ?)
                """.formatted(qualified("app_security_event"));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, action);
            if (actorUserId == null) {
                statement.setNull(2, java.sql.Types.BIGINT);
            } else {
                statement.setLong(2, actorUserId);
            }
            statement.setLong(3, targetUserId);
            statement.setString(4, detail);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to record the ethics bootstrap event", ex);
        }
    }

    @Override
    public List<RoleAssignment> getAssignmentsForUser(String userId) {
        Long id = numeric(userId);
        if (id == null || !tableExists("ethics_role_assignment")) {
            return List.of();
        }
        String sql = """
                SELECT id, user_id, role_id, scope_type, scope_id, status,
                       valid_from, valid_until, created_by, created_at, modified_by, modified_at
                FROM %s
                WHERE user_id = ?
                """.formatted(qualified("ethics_role_assignment"));
        List<RoleAssignment> rows = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ScopeType scope = ScopeType.parse(rs.getString("scope_type"));
                    AssignmentStatus status = AssignmentStatus.parse(rs.getString("status"));
                    if (scope == null || status == null) {
                        continue;
                    }
                    rows.add(new RoleAssignment(
                            Long.toString(rs.getLong("id")),
                            Long.toString(rs.getLong("user_id")),
                            rs.getString("role_id"),
                            scope,
                            rs.getString("scope_id"),
                            status,
                            rs.getDate("valid_from") == null ? null : rs.getDate("valid_from").toLocalDate(),
                            rs.getDate("valid_until") == null ? null : rs.getDate("valid_until").toLocalDate(),
                            nullableId(rs, "created_by"),
                            nullableTime(rs, "created_at"),
                            nullableId(rs, "modified_by"),
                            nullableTime(rs, "modified_at")
                    ));
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to read ethics role assignments", ex);
        }
        return List.copyOf(rows);
    }

    @Override
    public List<RoleAssignment> getAssignmentsForUserAndContext(String userId, AuthorizationContext context) {
        return getAssignmentsForUser(userId);
    }

    public boolean ethicsApplicationActive(long userId) {
        if (!tableExists("user_application")) {
            return false;
        }
        String sql = """
                SELECT status FROM %s
                WHERE user_id = ? AND application_id = 'ETHICS'
                """.formatted(qualified("user_application"));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() && "ACTIVE".equals(rs.getString("status"));
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to read Ethics access", ex);
        }
    }

    public String ethicsApplicationStatus(long userId) {
        if (!tableExists("user_application")) {
            return null;
        }
        String sql = """
                SELECT status FROM %s
                WHERE user_id = ? AND application_id = 'ETHICS'
                """.formatted(qualified("user_application"));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getString("status") : null;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to read Ethics access", ex);
        }
    }

    public void markEthicsPending(long userId) {
        ensureSchema();
        String status = ethicsApplicationStatus(userId);
        if ("ACTIVE".equals(status) || "PENDING".equals(status)) {
            return;
        }
        if (status == null) {
            write(("""
                    INSERT INTO %s (user_id, application_id, status)
                    VALUES (?, 'ETHICS', 'PENDING')
                    """).formatted(qualified("user_application")), userId);
            return;
        }
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE %s SET status = 'PENDING'
                     WHERE user_id = ? AND application_id = 'ETHICS' AND status <> 'ACTIVE'
                     """.formatted(qualified("user_application")))) {
            statement.setLong(1, userId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to mark Ethics invitation", ex);
        }
    }

    public Long insertInstitutionalPerson(
            Long purePersonId,
            String email,
            String givenName,
            String familyName,
            String campus,
            String faculty,
            String position,
            Long actorId
    ) {
        if (!tableExists("person")) {
            return null;
        }
        String normalized = email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
        Long existing = scalarNullable(
                "SELECT id FROM " + qualified("person") + " WHERE email_normalized = ? LIMIT 1",
                normalized
        );
        if (existing != null) {
            return existing;
        }
        String sql = """
                INSERT INTO %s (
                    pure_person_id, person_kind, first_name, last_name, email, email_normalized,
                    campus, faculty, position_title, created_by
                ) VALUES (?, 'INSTITUTIONAL', ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(qualified("person"));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (purePersonId == null) {
                statement.setNull(1, java.sql.Types.BIGINT);
            } else {
                statement.setLong(1, purePersonId);
            }
            statement.setString(2, givenName);
            statement.setString(3, familyName);
            statement.setString(4, email);
            statement.setString(5, normalized);
            statement.setString(6, campus);
            statement.setString(7, faculty);
            statement.setString(8, position);
            if (actorId == null) {
                statement.setNull(9, java.sql.Types.BIGINT);
            } else {
                statement.setLong(9, actorId);
            }
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : null;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to create the institutional person", ex);
        }
    }

    public void linkPerson(long userId, long personId) {
        if (!columnExists("app_user", "person_id")) {
            return;
        }
        String sql = "UPDATE " + qualified("app_user") + " SET person_id = ? WHERE id = ? AND person_id IS NULL";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, personId);
            statement.setLong(2, userId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to link the person", ex);
        }
    }

    public boolean accountsAvailable() {
        return tableExists("app_user");
    }

    public void ensureSchema() {
        execute("""
                CREATE TABLE IF NOT EXISTS `uss-ca`.`app_application` (
                    code VARCHAR(32) NOT NULL,
                    name_es VARCHAR(128) NOT NULL,
                    name_en VARCHAR(128) NOT NULL,
                    PRIMARY KEY (code)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        execute("""
                INSERT IGNORE INTO `uss-ca`.`app_application` (code, name_es, name_en) VALUES
                    ('OPENSCIENCE', 'OpenScience', 'OpenScience'),
                    ('EVENTOS', 'Eventos', 'Events'),
                    ('ETHICS', 'Ética de proyectos', 'Project ethics')
                """);
        execute("""
                CREATE TABLE IF NOT EXISTS `uss-ca`.`user_application` (
                    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                    user_id BIGINT UNSIGNED NOT NULL,
                    application_id VARCHAR(32) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    created_by BIGINT UNSIGNED NULL,
                    modified_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                    modified_by BIGINT UNSIGNED NULL,
                    PRIMARY KEY (id),
                    UNIQUE KEY uq_user_application (user_id, application_id),
                    KEY idx_user_application_status (application_id, status),
                    CONSTRAINT fk_user_application_user
                        FOREIGN KEY (user_id) REFERENCES `uss-ca`.`app_user` (id),
                    CONSTRAINT fk_user_application_app
                        FOREIGN KEY (application_id) REFERENCES `uss-ca`.`app_application` (code)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        execute("""
                CREATE TABLE IF NOT EXISTS `uss-ca`.`ethics_role_assignment` (
                    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                    user_id BIGINT UNSIGNED NOT NULL,
                    role_id VARCHAR(64) NOT NULL,
                    scope_type VARCHAR(32) NOT NULL,
                    scope_id VARCHAR(64) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    valid_from DATE NULL,
                    valid_until DATE NULL,
                    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    created_by BIGINT UNSIGNED NULL,
                    modified_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                    modified_by BIGINT UNSIGNED NULL,
                    PRIMARY KEY (id),
                    KEY idx_ethics_role_user (user_id, role_id, status),
                    CONSTRAINT fk_ethics_role_user
                        FOREIGN KEY (user_id) REFERENCES `uss-ca`.`app_user` (id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private Long personId(long userId, String email) {
        try {
            return linkedPerson(userId, email);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private Long linkedPerson(long userId, String email) {
        if (columnExists("app_user", "person_id")) {
            Long linked = scalarNullable("SELECT person_id FROM " + qualified("app_user") + " WHERE id = ?", userId);
            if (linked != null) {
                return linked;
            }
        }
        if (!tableExists("person")) {
            return null;
        }
        return scalarNullable("SELECT id FROM " + qualified("person") + " WHERE email_normalized = ? LIMIT 1", email);
    }

    private Long purePersonId(String email) {
        try {
            return linkedPure(email);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private Long linkedPure(String email) {
        if (tableExists("person") && columnExists("person", "pure_person_id")) {
            Long linked = scalarNullable(
                    "SELECT pure_person_id FROM " + qualified("person") + " WHERE email_normalized = ? AND pure_person_id IS NOT NULL LIMIT 1",
                    email
            );
            if (linked != null) {
                return linked;
            }
        }
        if (!tableExists("pure_person")) {
            return null;
        }
        return scalarNullable(
                "SELECT id FROM " + qualified("pure_person") + " WHERE LOWER(TRIM(email)) = ? LIMIT 1",
                email
        );
    }

    private Long scalarNullable(String sql, Object param) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (param instanceof Long value) {
                statement.setLong(1, value);
            } else {
                statement.setString(1, String.valueOf(param));
            }
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next() || rs.getObject(1) == null) {
                    return null;
                }
                return rs.getLong(1);
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to read a related person", ex);
        }
    }

    private boolean write(String sql, long... ids) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < ids.length; i++) {
                statement.setLong(i + 1, ids[i]);
            }
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to enable Ethics access", ex);
        }
    }

    private boolean writeRole(String sql, Object... params) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                Object param = params[i];
                if (param instanceof Long value) {
                    statement.setLong(i + 1, value);
                } else {
                    statement.setString(i + 1, String.valueOf(param));
                }
            }
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to assign an Ethics role", ex);
        }
    }

    private void execute(String sql) {
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to prepare Ethics access tables", ex);
        }
    }

    private static Long numeric(String userId) {
        if (userId == null || userId.isBlank() || !userId.chars().allMatch(Character::isDigit)) {
            return null;
        }
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String nullableId(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : Long.toString(value);
    }

    private static String nullableTime(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant().toString();
    }
}
