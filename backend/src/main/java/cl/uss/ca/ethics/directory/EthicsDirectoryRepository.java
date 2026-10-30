package cl.uss.ca.ethics.directory;

import cl.uss.ca.ethics.auth.AssignmentStatus;
import cl.uss.ca.ethics.auth.EthicsUserCatalog;
import cl.uss.ca.ethics.auth.RoleAssignment;
import cl.uss.ca.ethics.auth.ScopeType;
import cl.uss.ca.exception.DatabaseException;
import cl.uss.ca.exception.NotFoundException;
import cl.uss.ca.repository.JdbcRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cuentas reales de {@code app_user}. No inserta usuarios ni lee fixtures.
 */
@ApplicationScoped
public class EthicsDirectoryRepository extends JdbcRepository {
    public DirectoryPage search(EthicsUserCatalog.Query query) {
        if (!tableExists("app_user")) {
            throw new DatabaseException("app_user is not available", null);
        }
        String search = EthicsUserCatalog.normalizeSearch(query.search());
        int size = query.size() < 1 ? 25 : Math.min(query.size(), 100);
        int page = Math.max(query.page(), 1);
        Filter filter = filter(search, query);
        long total = count(filter);
        List<EthicsUserCatalog.Account> items = total == 0
                ? List.of()
                : load(filter, size, (page - 1L) * size);
        return new DirectoryPage(items, page, size, total);
    }

    public EthicsUserCatalog.Account require(long userId) {
        EthicsUserCatalog.Account account = find(userId);
        if (account == null) {
            throw new NotFoundException("USER_NOT_FOUND", "User was not found");
        }
        return account;
    }

    public EthicsUserCatalog.Account find(long userId) {
        if (!tableExists("app_user")) {
            throw new DatabaseException("app_user is not available", null);
        }
        Filter filter = new Filter(" WHERE u.deleted_at IS NULL AND u.id = ? ", List.of(userId));
        List<EthicsUserCatalog.Account> items = load(filter, 1, 0);
        return items.isEmpty() ? null : items.get(0);
    }

    public void deactivateEthics(long userId) {
        if (!tableExists("user_application")) {
            return;
        }
        String sql = """
                UPDATE %s
                SET status = 'INACTIVE'
                WHERE user_id = ? AND application_id = 'ETHICS' AND status <> 'INACTIVE'
                """.formatted(qualified("user_application"));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to disable Ethics access", ex);
        }
    }

    public boolean activeReviewer(long userId) {
        if (!tableExists("ethics_role_assignment")) {
            return false;
        }
        String sql = """
                SELECT 1 FROM %s
                WHERE user_id = ? AND role_id = 'REVIEWER' AND status = 'ACTIVE'
                  AND (valid_from IS NULL OR valid_from <= CURRENT_DATE)
                  AND (valid_until IS NULL OR valid_until >= CURRENT_DATE)
                LIMIT 1
                """.formatted(qualified("ethics_role_assignment"));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to read reviewer assignments", ex);
        }
    }

    public RoleAssignment findAssignment(long userId, long assignmentId) {
        if (!tableExists("ethics_role_assignment")) {
            return null;
        }
        String sql = """
                SELECT id, user_id, role_id, scope_type, scope_id, status, valid_from, valid_until
                FROM %s
                WHERE id = ? AND user_id = ?
                """.formatted(qualified("ethics_role_assignment"));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, assignmentId);
            statement.setLong(2, userId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return mapAssignment(rs, ScopeType.parse(rs.getString("scope_type")), AssignmentStatus.parse(rs.getString("status")));
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to read the role assignment", ex);
        }
    }

    /**
     * Otros usuarios con cuenta activa, Ethics activo y ADMINISTRATOR vigente.
     * Si no se puede comprobar, responde 0 para no dejar el sistema sin administrador.
     */
    public long otherActiveAdministrators(long userId) {
        if (!tableExists("app_user") || !tableExists("user_application") || !tableExists("ethics_role_assignment")) {
            return 0;
        }
        String sql = """
                SELECT COUNT(DISTINCT u.id)
                FROM %s u
                JOIN %s ua ON ua.user_id = u.id AND ua.application_id = 'ETHICS' AND ua.status = 'ACTIVE'
                JOIN %s a ON a.user_id = u.id
                  AND a.role_id = 'ADMINISTRATOR'
                  AND a.status = 'ACTIVE'
                  AND (a.valid_from IS NULL OR a.valid_from <= CURRENT_DATE)
                  AND (a.valid_until IS NULL OR a.valid_until >= CURRENT_DATE)
                WHERE u.deleted_at IS NULL AND u.status = 'ACTIVE' AND u.id <> ?
                """.formatted(qualified("app_user"), qualified("user_application"), qualified("ethics_role_assignment"));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to count active Ethics administrators", ex);
        }
    }

    public boolean revokeAssignment(long userId, long assignmentId, Long actorId) {
        if (!tableExists("ethics_role_assignment")) {
            return false;
        }
        StringBuilder set = new StringBuilder("status = 'REVOKED'");
        if (columnExists("ethics_role_assignment", "valid_until")) {
            set.append(", valid_until = CURRENT_DATE");
        }
        boolean modifiedBy = columnExists("ethics_role_assignment", "modified_by");
        if (modifiedBy) {
            set.append(", modified_by = ?");
        }
        String sql = "UPDATE " + qualified("ethics_role_assignment")
                + " SET " + set
                + " WHERE id = ? AND user_id = ? AND status = 'ACTIVE'";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            if (modifiedBy) {
                if (actorId == null) {
                    statement.setNull(index++, java.sql.Types.BIGINT);
                } else {
                    statement.setLong(index++, actorId);
                }
            }
            statement.setLong(index++, assignmentId);
            statement.setLong(index, userId);
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to revoke the role assignment", ex);
        }
    }

    private static RoleAssignment mapAssignment(ResultSet rs, ScopeType scope, AssignmentStatus status) throws SQLException {
        if (scope == null || status == null) {
            return null;
        }
        long userId = rs.getLong("user_id");
        return new RoleAssignment(
                Long.toString(rs.getLong("id")),
                Long.toString(userId),
                rs.getString("role_id"),
                scope,
                rs.getString("scope_id"),
                status,
                rs.getDate("valid_from") == null ? null : rs.getDate("valid_from").toLocalDate(),
                rs.getDate("valid_until") == null ? null : rs.getDate("valid_until").toLocalDate(),
                null,
                null,
                null,
                null
        );
    }

    public void updateLocalNames(long userId, String givenName, String familyName) {
        String given = givenName == null ? "" : givenName.trim();
        String family = familyName == null ? "" : familyName.trim();
        String sql = """
                UPDATE %s
                SET first_name = ?, last_name = ?
                WHERE id = ? AND deleted_at IS NULL
                """.formatted(qualified("app_user"));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, given);
            statement.setString(2, family);
            statement.setLong(3, userId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to update the local profile", ex);
        }
        if (!personJoin() || !columnExists("person", "pure_person_id")) {
            return;
        }
        String personSql = """
                UPDATE %s p
                JOIN %s u ON u.person_id = p.id
                SET p.first_name = ?, p.last_name = ?
                WHERE u.id = ? AND p.pure_person_id IS NULL
                """.formatted(qualified("person"), qualified("app_user"));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(personSql)) {
            statement.setString(1, given);
            statement.setString(2, family);
            statement.setLong(3, userId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to update the local person", ex);
        }
    }

    private Filter filter(String search, EthicsUserCatalog.Query query) {
        StringBuilder where = new StringBuilder(" WHERE u.deleted_at IS NULL ");
        List<Object> params = new ArrayList<>();
        if (search.isEmpty()) {
            where.append(" AND (").append(ethicsRelation()).append(") ");
        } else {
            String like = EthicsUserCatalog.likePattern(search);
            List<String> fields = new ArrayList<>();
            fields.add("LOWER(u.first_name) LIKE ? ESCAPE '\\\\'");
            fields.add("LOWER(u.last_name) LIKE ? ESCAPE '\\\\'");
            fields.add("LOWER(CONCAT(u.first_name, ' ', u.last_name)) LIKE ? ESCAPE '\\\\'");
            fields.add("LOWER(u.email) LIKE ? ESCAPE '\\\\'");
            fields.add("LOWER(u.email_normalized) LIKE ? ESCAPE '\\\\'");
            for (int i = 0; i < 5; i++) {
                params.add(like);
            }
            if (personJoin()) {
                fields.add("LOWER(p.first_name) LIKE ? ESCAPE '\\\\'");
                fields.add("LOWER(p.last_name) LIKE ? ESCAPE '\\\\'");
                fields.add("LOWER(CONCAT(p.first_name, ' ', p.last_name)) LIKE ? ESCAPE '\\\\'");
                params.add(like);
                params.add(like);
                params.add(like);
            }
            if (pureJoin()) {
                fields.add("LOWER(pp.first_name) LIKE ? ESCAPE '\\\\'");
                fields.add("LOWER(pp.last_name) LIKE ? ESCAPE '\\\\'");
                fields.add("LOWER(CONCAT(pp.first_name, ' ', pp.last_name)) LIKE ? ESCAPE '\\\\'");
                fields.add("LOWER(pp.email) LIKE ? ESCAPE '\\\\'");
                params.add(like);
                params.add(like);
                params.add(like);
                params.add(like);
            }
            where.append(" AND (").append(String.join(" OR ", fields)).append(") ");
        }
        String type = query.type() == null ? "ALL" : query.type().trim().toUpperCase();
        if ("INSTITUTIONAL".equals(type) || "EXTERNAL".equals(type)) {
            where.append(" AND (").append(kindExpression()).append(") = ? ");
            params.add(type);
        }
        String status = query.status() == null ? "ALL" : query.status().trim().toUpperCase();
        if ("ACTIVE".equals(status)) {
            where.append(" AND u.status = 'ACTIVE' ");
        } else if ("INACTIVE".equals(status)) {
            where.append(" AND u.status <> 'ACTIVE' ");
        }
        String access = query.ethicsAccess() == null ? "ALL" : query.ethicsAccess().trim().toUpperCase();
        if ("YES".equals(access)) {
            where.append(" AND ").append(ethicsActiveExpression()).append(" ");
        } else if ("NO".equals(access)) {
            where.append(" AND NOT (").append(ethicsActiveExpression()).append(") ");
        }
        return new Filter(where.toString(), params);
    }

    private String ethicsRelation() {
        boolean application = tableExists("user_application");
        boolean roles = tableExists("ethics_role_assignment");
        if (application && roles) {
            return """
                    EXISTS (SELECT 1 FROM %s ua0 WHERE ua0.user_id = u.id AND ua0.application_id = 'ETHICS')
                    OR EXISTS (SELECT 1 FROM %s a0 WHERE a0.user_id = u.id)
                    """.formatted(qualified("user_application"), qualified("ethics_role_assignment"));
        }
        if (application) {
            return "EXISTS (SELECT 1 FROM " + qualified("user_application") + " ua0 WHERE ua0.user_id = u.id AND ua0.application_id = 'ETHICS')";
        }
        if (roles) {
            return "EXISTS (SELECT 1 FROM " + qualified("ethics_role_assignment") + " a0 WHERE a0.user_id = u.id)";
        }
        return "0 = 1";
    }

    private String ethicsActiveExpression() {
        if (!tableExists("user_application")) {
            return "0 = 1";
        }
        return "EXISTS (SELECT 1 FROM " + qualified("user_application")
                + " ua1 WHERE ua1.user_id = u.id AND ua1.application_id = 'ETHICS' AND ua1.status = 'ACTIVE')";
    }

    private String kindExpression() {
        String institutionalDomain = """
                LOWER(SUBSTRING_INDEX(u.email_normalized, '@', -1)) IN ('uss.cl', 'correo.uss.cl', 'docente.uss.cl')
                """;
        if (!personJoin() || !columnExists("person", "person_kind")) {
            return "CASE WHEN " + institutionalDomain + " THEN 'INSTITUTIONAL' ELSE 'EXTERNAL' END";
        }
        return """
                CASE
                    WHEN p.person_kind IN ('INSTITUTIONAL', 'EXTERNAL') THEN p.person_kind
                    WHEN %s THEN 'INSTITUTIONAL'
                    ELSE 'EXTERNAL'
                END
                """.formatted(institutionalDomain);
    }

    private long count(Filter filter) {
        String sql = "SELECT COUNT(*) FROM " + fromClause() + filter.where;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, filter.params);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to count ethics users", ex);
        }
    }

    private List<EthicsUserCatalog.Account> load(Filter filter, int size, long offset) {
        String sql = """
                SELECT u.id, u.first_name, u.last_name, u.email, u.status%s
                FROM %s
                ORDER BY u.last_name, u.first_name, u.id
                LIMIT ? OFFSET ?
                """.formatted(extraColumns(), fromClause() + filter.where);
        List<EthicsUserCatalog.Account> rows = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = bind(statement, filter.params);
            statement.setInt(index++, size);
            statement.setLong(index, offset);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(map(rs));
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list ethics users", ex);
        }
        return attachAssignments(rows);
    }

    private List<EthicsUserCatalog.Account> attachAssignments(List<EthicsUserCatalog.Account> rows) {
        if (rows.isEmpty() || !tableExists("ethics_role_assignment")) {
            return rows;
        }
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) {
                placeholders.append(", ");
            }
            placeholders.append('?');
        }
        String sql = """
                SELECT id, user_id, role_id, scope_type, scope_id, status, valid_from, valid_until
                FROM %s
                WHERE user_id IN (%s)
                """.formatted(qualified("ethics_role_assignment"), placeholders);
        Map<Long, List<RoleAssignment>> byUser = new LinkedHashMap<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < rows.size(); i++) {
                statement.setLong(i + 1, Long.parseLong(rows.get(i).id()));
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ScopeType scope = ScopeType.parse(rs.getString("scope_type"));
                    AssignmentStatus status = AssignmentStatus.parse(rs.getString("status"));
                    RoleAssignment assignment = mapAssignment(rs, scope, status);
                    if (assignment == null) {
                        continue;
                    }
                    long userId = rs.getLong("user_id");
                    byUser.computeIfAbsent(userId, key -> new ArrayList<>()).add(assignment);
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to read ethics roles", ex);
        }
        List<EthicsUserCatalog.Account> withRoles = new ArrayList<>();
        for (EthicsUserCatalog.Account row : rows) {
            List<RoleAssignment> assignments = byUser.getOrDefault(Long.parseLong(row.id()), List.of());
            withRoles.add(new EthicsUserCatalog.Account(
                    row.id(), row.givenName(), row.familyName(), row.secondFamilyName(), row.preferredName(),
                    row.email(), row.accountStatus(), row.personKind(), row.purePersonId(), row.ethicsStatus(),
                    List.copyOf(assignments)
            ));
        }
        return List.copyOf(withRoles);
    }

    private EthicsUserCatalog.Account map(ResultSet rs) throws SQLException {
        String given = rs.getString("first_name");
        String family = rs.getString("last_name");
        String email = rs.getString("email");
        String storedKind = null;
        Long pureId = null;
        String ethicsStatus = null;
        if (personJoin()) {
            storedKind = rs.getString("person_kind");
            if (pureIdColumn()) {
                long rawPure = rs.getLong("pure_person_id");
                if (!rs.wasNull()) {
                    pureId = rawPure;
                }
            }
            if (pureJoin() && pureId != null) {
                String pureGiven = rs.getString("pure_first_name");
                String pureFamily = rs.getString("pure_last_name");
                if (pureGiven != null && !pureGiven.isBlank()) {
                    given = pureGiven;
                }
                if (pureFamily != null && !pureFamily.isBlank()) {
                    family = pureFamily;
                }
                if (columnExists("pure_person", "email")) {
                    String pureEmail = rs.getString("pure_email");
                    if (pureEmail != null && !pureEmail.isBlank()) {
                        email = pureEmail;
                    }
                }
            } else if (storedKind != null) {
                String personGiven = rs.getString("person_first_name");
                String personFamily = rs.getString("person_last_name");
                if (personGiven != null && !personGiven.isBlank()) {
                    given = personGiven;
                }
                if (personFamily != null && !personFamily.isBlank()) {
                    family = personFamily;
                }
            }
        }
        if (tableExists("user_application")) {
            ethicsStatus = rs.getString("ethics_status");
        }
        return new EthicsUserCatalog.Account(
                Long.toString(rs.getLong("id")),
                given,
                family,
                null,
                null,
                email,
                rs.getString("status"),
                EthicsUserCatalog.personKind(storedKind, email),
                pureId,
                ethicsStatus,
                List.of()
        );
    }

    private String extraColumns() {
        StringBuilder columns = new StringBuilder();
        if (personJoin()) {
            columns.append(", p.person_kind, p.first_name AS person_first_name, p.last_name AS person_last_name");
            if (pureIdColumn()) {
                columns.append(", p.pure_person_id");
            }
            if (pureJoin()) {
                columns.append(", pp.first_name AS pure_first_name, pp.last_name AS pure_last_name");
                if (columnExists("pure_person", "email")) {
                    columns.append(", pp.email AS pure_email");
                }
            }
        }
        if (tableExists("user_application")) {
            columns.append(", ua.status AS ethics_status");
        }
        return columns.toString();
    }

    private String fromClause() {
        StringBuilder from = new StringBuilder(qualified("app_user")).append(" u ");
        if (personJoin()) {
            from.append(" LEFT JOIN ").append(qualified("person")).append(" p ON p.id = u.person_id ");
            if (pureJoin()) {
                from.append(" LEFT JOIN ").append(qualified("pure_person")).append(" pp ON pp.id = p.pure_person_id ");
            }
        }
        if (tableExists("user_application")) {
            from.append(" LEFT JOIN ").append(qualified("user_application"))
                    .append(" ua ON ua.user_id = u.id AND ua.application_id = 'ETHICS' ");
        }
        return from.toString();
    }

    private boolean personJoin() {
        return tableExists("person")
                && columnExists("app_user", "person_id")
                && columnExists("person", "person_kind")
                && columnExists("person", "first_name")
                && columnExists("person", "last_name");
    }

    private boolean pureIdColumn() {
        return personJoin() && columnExists("person", "pure_person_id");
    }

    private boolean pureJoin() {
        return pureIdColumn()
                && tableExists("pure_person")
                && columnExists("pure_person", "first_name")
                && columnExists("pure_person", "last_name");
    }

    private static int bind(PreparedStatement statement, List<Object> params) throws SQLException {
        int index = 1;
        for (Object param : params) {
            if (param instanceof Long number) {
                statement.setLong(index++, number);
            } else if (param instanceof Integer number) {
                statement.setInt(index++, number);
            } else {
                statement.setString(index++, String.valueOf(param));
            }
        }
        return index;
    }

    public record DirectoryPage(List<EthicsUserCatalog.Account> items, int page, int size, long total) {
    }

    private record Filter(String where, List<Object> params) {
    }
}
