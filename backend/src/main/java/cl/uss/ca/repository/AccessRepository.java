package cl.uss.ca.repository;

import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.auth.AppModule;
import cl.uss.ca.auth.AppPermission;
import cl.uss.ca.auth.EffectivePermission;
import cl.uss.ca.auth.EmailNormalizer;
import cl.uss.ca.auth.UserDeletionPolicy;
import cl.uss.ca.auth.UserStatus;
import cl.uss.ca.exception.DatabaseException;
import cl.uss.ca.exception.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class AccessRepository extends JdbcRepository {
    private static final String USER = "app_user";
    private static final String ROLE = "app_role";
    private static final String USER_ROLE = "app_user_role";
    private static final String ROLE_PERM = "app_role_permission";
    private static final String TOKEN = "app_account_token";
    private static final String SESSION = "app_session";
    private static final String EVENT = "app_security_event";
    private static final Set<String> USER_SORTS = Set.of("name", "email", "status", "lastLoginAt", "createdAt");
    private static final Set<String> ROLE_SORTS = Set.of("name", "code", "active", "userCount");

    public boolean available() {
        return tableExists(USER) && tableExists(ROLE) && tableExists(USER_ROLE)
                && tableExists(ROLE_PERM) && tableExists(TOKEN) && tableExists(SESSION);
    }

    public void assertAvailable() {
        if (!available()) {
            throw new ValidationException(
                    "AUTH_TABLES_UNAVAILABLE",
                    "Access tables are required. Execute proposed-auth.sql"
            );
        }
    }

    public long countUsers() {
        return scalarLong("SELECT COUNT(*) FROM " + qualified(USER));
    }

    public AccessUserRecord findUserById(long id) {
        return loadUser("id = ?", id);
    }

    public Map<Long, AccessUserRecord> findUsersByIds(List<Long> ids) {
        Map<Long, AccessUserRecord> byId = new LinkedHashMap<>();
        if (ids == null || ids.isEmpty() || !tableExists(USER)) {
            return byId;
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>(ids);
        String sql = "SELECT * FROM " + qualified(USER) + " WHERE id IN (" + inPlaceholders(unique.size()) + ")";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (Long id : unique) {
                statement.setLong(index++, id);
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    AccessUserRecord user = mapUser(rs);
                    byId.put(user.id, user);
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load users by id", ex);
        }
        return byId;
    }

    public AccessUserRecord findUserByEmail(String email) {
        String normalized = EmailNormalizer.normalize(email);
        if (normalized == null) {
            return null;
        }
        return loadUser("email_normalized = ?", normalized);
    }

    public Set<EffectivePermission> effectivePermissions(long userId) {
        String sql = """
                SELECT rp.module_code, rp.permission_code
                FROM %s ur
                JOIN %s r ON r.id = ur.role_id AND r.active = 1
                JOIN %s rp ON rp.role_id = r.id
                WHERE ur.user_id = ?
                """.formatted(qualified(USER_ROLE), qualified(ROLE), qualified(ROLE_PERM));
        Set<EffectivePermission> granted = new LinkedHashSet<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    AppModule module = AppModule.from(rs.getString(1));
                    AppPermission permission = AppPermission.from(rs.getString(2));
                    if (module != null && permission != null) {
                        granted.add(new EffectivePermission(module, permission));
                        if (permission == AppPermission.ADMINISTER) {
                            granted.add(new EffectivePermission(module, AppPermission.CONSULT));
                        }
                    }
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load effective permissions", ex);
        }
        return granted;
    }

    public boolean hasPermission(long userId, AppModule module, AppPermission permission) {
        if (module == null || permission == null) {
            return false;
        }
        Set<EffectivePermission> granted = effectivePermissions(userId);
        return granted.contains(new EffectivePermission(module, permission))
                || (permission == AppPermission.CONSULT
                && granted.contains(new EffectivePermission(module, AppPermission.ADMINISTER)));
    }

    public long countActiveAdministrators() {
        String sql = """
                SELECT COUNT(DISTINCT u.id)
                FROM %s u
                JOIN %s ur ON ur.user_id = u.id
                JOIN %s r ON r.id = ur.role_id AND r.active = 1
                JOIN %s rp ON rp.role_id = r.id
                WHERE u.status = 'ACTIVE'
                  AND rp.module_code = 'ADMINISTRATION'
                  AND rp.permission_code = 'ADMINISTER'
                  %s
                """.formatted(
                qualified(USER),
                qualified(USER_ROLE),
                qualified(ROLE),
                qualified(ROLE_PERM),
                deletionColumnAvailable() ? "AND u.deleted_at IS NULL" : ""
        );
        return scalarLong(sql);
    }

    public boolean wouldRemainAdministrator(long userId, List<Long> nextRoleIds) {
        if (nextRoleIds == null || nextRoleIds.isEmpty()) {
            return false;
        }
        String sql = """
                SELECT COUNT(*)
                FROM %s r
                JOIN %s rp ON rp.role_id = r.id
                WHERE r.active = 1
                  AND rp.module_code = 'ADMINISTRATION'
                  AND rp.permission_code = 'ADMINISTER'
                  AND r.id IN (%s)
                """.formatted(qualified(ROLE), qualified(ROLE_PERM), inPlaceholders(nextRoleIds.size()));
        return scalarLong(sql, nextRoleIds.toArray()) > 0;
    }

    public boolean roleGrantsAdmin(long roleId) {
        String sql = """
                SELECT COUNT(*) FROM %s
                WHERE role_id = ? AND module_code = 'ADMINISTRATION' AND permission_code = 'ADMINISTER'
                """.formatted(qualified(ROLE_PERM));
        return scalarLong(sql, roleId) > 0;
    }

    public PageResponse<AccessUserRecord> listUsers(
            String search, String status, Long roleId, String sort, String direction, int page, int size
    ) {
        List<String> clauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (search != null && !search.isBlank()) {
            String like = likeContains(search.trim());
            clauses.add("(u.first_name LIKE ? ESCAPE '!' OR u.last_name LIKE ? ESCAPE '!' OR u.email LIKE ? ESCAPE '!')");
            params.add(like);
            params.add(like);
            params.add(like);
        }
        String statusFilter = status == null ? "" : status.trim().toUpperCase();
        if (UserDeletionPolicy.DISPLAY_STATUS.equals(statusFilter)) {
            if (deletionColumnAvailable()) {
                clauses.add("u.deleted_at IS NOT NULL");
            } else {
                clauses.add("1 = 0");
            }
        } else {
            if (deletionColumnAvailable()) {
                clauses.add("u.deleted_at IS NULL");
            }
            if (!statusFilter.isBlank()) {
                clauses.add("u.status = ?");
                params.add(statusFilter);
            }
        }
        if (roleId != null) {
            clauses.add("EXISTS (SELECT 1 FROM " + qualified(USER_ROLE) + " ur WHERE ur.user_id = u.id AND ur.role_id = ?)");
            params.add(roleId);
        }
        String where = clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
        long total = scalarLong("SELECT COUNT(*) FROM " + qualified(USER) + " u" + where, params.toArray());
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        String sql = "SELECT u.* FROM " + qualified(USER) + " u" + where
                + " ORDER BY " + userOrder(sort, direction) + " LIMIT ? OFFSET ?";
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(safeSize);
        pageParams.add((safePage - 1) * safeSize);
        List<AccessUserRecord> items = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, pageParams.toArray());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    items.add(mapUser(rs));
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list users", ex);
        }
        attachRoles(items);
        return PageResponse.of(items, safePage - 1, safeSize, total);
    }

    public long insertUser(AccessUserRecord user) {
        String sql = """
                INSERT INTO %s (email, email_normalized, first_name, last_name, password_hash, status,
                    auth_provider, preferred_language, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(qualified(USER));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, user.email);
            statement.setString(2, user.emailNormalized);
            statement.setString(3, user.firstName);
            statement.setString(4, user.lastName);
            statement.setString(5, user.passwordHash);
            statement.setString(6, user.status);
            statement.setString(7, user.authProvider == null ? "LOCAL" : user.authProvider);
            statement.setString(8, user.preferredLanguage == null ? "es" : user.preferredLanguage);
            if (user.createdBy == null) {
                statement.setNull(9, Types.BIGINT);
            } else {
                statement.setLong(9, user.createdBy);
            }
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("app_user insert did not return id");
                }
                return keys.getLong(1);
            }
        } catch (SQLException ex) {
            if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("duplicate")) {
                throw new cl.uss.ca.exception.ConflictException("EMAIL_EXISTS", "A user with this email already exists");
            }
            throw new DatabaseException("Failed to create user", ex);
        }
    }

    public void updateUser(long id, String firstName, String lastName, String preferredLanguage) {
        String sql = "UPDATE " + qualified(USER) + " SET first_name = ?, last_name = ?, preferred_language = ? WHERE id = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, firstName);
            statement.setString(2, lastName);
            statement.setString(3, preferredLanguage);
            statement.setLong(4, id);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to update user", ex);
        }
    }

    public void updateStatus(long id, UserStatus status) {
        String sql = "UPDATE " + qualified(USER) + " SET status = ?, locked_until = NULL, failed_login_count = 0 WHERE id = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            statement.setLong(2, id);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to update user status", ex);
        }
    }

    public void activate(long id, String passwordHash) {
        String sql = """
                UPDATE %s SET password_hash = ?, status = 'ACTIVE', activated_at = NOW(6),
                    failed_login_count = 0, locked_until = NULL
                WHERE id = ?
                """.formatted(qualified(USER));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, passwordHash);
            statement.setLong(2, id);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to activate user", ex);
        }
    }

    public void updatePassword(long id, String passwordHash) {
        String sql = "UPDATE " + qualified(USER) + " SET password_hash = ?, failed_login_count = 0, locked_until = NULL WHERE id = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, passwordHash);
            statement.setLong(2, id);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to update password", ex);
        }
    }

    public void recordLoginSuccess(long id) {
        String sql = "UPDATE " + qualified(USER)
                + " SET last_login_at = NOW(6), failed_login_count = 0, locked_until = NULL, status = 'ACTIVE' WHERE id = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to record login", ex);
        }
    }

    public void recordLoginFailure(long id, int threshold, int lockMinutes) {
        AccessUserRecord user = findUserById(id);
        if (user == null) {
            return;
        }
        int next = user.failedLoginCount + 1;
        boolean lock = threshold > 0 && next >= threshold;
        String sql = lock
                ? "UPDATE " + qualified(USER) + " SET failed_login_count = ?, status = 'LOCKED', locked_until = DATE_ADD(NOW(6), INTERVAL ? MINUTE) WHERE id = ?"
                : "UPDATE " + qualified(USER) + " SET failed_login_count = ? WHERE id = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, next);
            if (lock) {
                statement.setInt(2, Math.max(1, lockMinutes));
                statement.setLong(3, id);
            } else {
                statement.setLong(2, id);
            }
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to record failed login", ex);
        }
    }

    public void unlockIfExpired(AccessUserRecord user) {
        if (user == null || !"LOCKED".equals(user.status) || user.lockedUntil == null) {
            return;
        }
        if (user.lockedUntil.isAfter(OffsetDateTime.now(ZoneOffset.UTC))) {
            return;
        }
        updateStatus(user.id, UserStatus.ACTIVE);
        user.status = UserStatus.ACTIVE.name();
        user.lockedUntil = null;
        user.failedLoginCount = 0;
    }

    public void addUserRole(long userId, long roleId) {
        String sql = "INSERT IGNORE INTO " + qualified(USER_ROLE) + " (user_id, role_id) VALUES (?, ?)";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setLong(2, roleId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to assign role", ex);
        }
    }

    public void replaceUserRoles(long userId, List<Long> roleIds) {
        try (Connection connection = connection()) {
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM " + qualified(USER_ROLE) + " WHERE user_id = ?")) {
                delete.setLong(1, userId);
                delete.executeUpdate();
            }
            if (roleIds == null || roleIds.isEmpty()) {
                return;
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO " + qualified(USER_ROLE) + " (user_id, role_id) VALUES (?, ?)")) {
                for (Long roleId : roleIds) {
                    if (roleId == null) {
                        continue;
                    }
                    insert.setLong(1, userId);
                    insert.setLong(2, roleId);
                    insert.addBatch();
                }
                insert.executeBatch();
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to assign roles", ex);
        }
    }

    public List<AccessRoleRecord> listRoles(boolean includeInactive) {
        String sql = """
                SELECT r.*,
                       (SELECT COUNT(*) FROM %s ur WHERE ur.role_id = r.id) AS user_count
                FROM %s r
                %s
                ORDER BY r.name
                """.formatted(
                qualified(USER_ROLE),
                qualified(ROLE),
                includeInactive ? "" : "WHERE r.active = 1"
        );
        List<AccessRoleRecord> items = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                items.add(mapRole(rs));
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list roles", ex);
        }
        attachGrants(items);
        return items;
    }

    public AccessRoleRecord findRole(long id) {
        String sql = """
                SELECT r.*,
                       (SELECT COUNT(*) FROM %s ur WHERE ur.role_id = r.id) AS user_count
                FROM %s r WHERE r.id = ?
                """.formatted(qualified(USER_ROLE), qualified(ROLE));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                AccessRoleRecord role = mapRole(rs);
                attachGrants(List.of(role));
                return role;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load role", ex);
        }
    }

    public AccessRoleRecord findRoleByCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String sql = """
                SELECT r.*,
                       (SELECT COUNT(*) FROM %s ur WHERE ur.role_id = r.id) AS user_count
                FROM %s r WHERE r.code = ?
                """.formatted(qualified(USER_ROLE), qualified(ROLE));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code.trim().toUpperCase());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                AccessRoleRecord role = mapRole(rs);
                attachGrants(List.of(role));
                return role;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load role by code", ex);
        }
    }

    public long insertRole(AccessRoleRecord role) {
        String sql = "INSERT INTO " + qualified(ROLE) + " (code, name, description, active, system_role) VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, role.code);
            statement.setString(2, role.name);
            statement.setString(3, role.description);
            statement.setBoolean(4, role.active);
            statement.setBoolean(5, role.systemRole);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("app_role insert did not return id");
                }
                return keys.getLong(1);
            }
        } catch (SQLException ex) {
            if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("duplicate")) {
                throw new cl.uss.ca.exception.ConflictException("ROLE_CODE_EXISTS", "A role with this code already exists");
            }
            throw new DatabaseException("Failed to create role", ex);
        }
    }

    public void updateRole(long id, String name, String description, boolean active) {
        String sql = "UPDATE " + qualified(ROLE) + " SET name = ?, description = ?, active = ? WHERE id = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setString(2, description);
            statement.setBoolean(3, active);
            statement.setLong(4, id);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to update role", ex);
        }
    }

    public void replaceRolePermissions(long roleId, List<AccessPermissionGrant> grants) {
        try (Connection connection = connection()) {
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM " + qualified(ROLE_PERM) + " WHERE role_id = ?")) {
                delete.setLong(1, roleId);
                delete.executeUpdate();
            }
            if (grants == null || grants.isEmpty()) {
                return;
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO " + qualified(ROLE_PERM) + " (role_id, module_code, permission_code) VALUES (?, ?, ?)")) {
                for (AccessPermissionGrant grant : grants) {
                    insert.setLong(1, roleId);
                    insert.setString(2, grant.moduleCode);
                    insert.setString(3, grant.permissionCode);
                    insert.addBatch();
                }
                insert.executeBatch();
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to update role permissions", ex);
        }
    }

    public void insertToken(long userId, String purpose, String tokenHash, OffsetDateTime expiresAt) {
        String sql = "INSERT INTO " + qualified(TOKEN) + " (user_id, purpose, token_hash, expires_at) VALUES (?, ?, ?, ?)";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setString(2, purpose);
            statement.setString(3, tokenHash);
            statement.setTimestamp(4, Timestamp.from(expiresAt.toInstant()));
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to store account token", ex);
        }
    }

    public void invalidateTokens(long userId, String purpose) {
        String sql = "UPDATE " + qualified(TOKEN)
                + " SET used_at = NOW(6) WHERE user_id = ? AND purpose = ? AND used_at IS NULL";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setString(2, purpose);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to invalidate tokens", ex);
        }
    }

    public TokenRecord findValidToken(String tokenHash, String purpose) {
        String sql = """
                SELECT id, user_id, purpose, expires_at, used_at
                FROM %s
                WHERE token_hash = ? AND purpose = ?
                """.formatted(qualified(TOKEN));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tokenHash);
            statement.setString(2, purpose);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                TokenRecord token = new TokenRecord();
                token.id = rs.getLong("id");
                token.userId = rs.getLong("user_id");
                token.purpose = rs.getString("purpose");
                token.expiresAt = timestamp(rs, "expires_at");
                token.usedAt = timestamp(rs, "used_at");
                return token;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load account token", ex);
        }
    }

    public void markTokenUsed(long id) {
        String sql = "UPDATE " + qualified(TOKEN) + " SET used_at = NOW(6) WHERE id = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to consume token", ex);
        }
    }

    public long insertSession(long userId, String tokenHash, OffsetDateTime expiresAt) {
        String sql = "INSERT INTO " + qualified(SESSION) + " (user_id, token_hash, expires_at) VALUES (?, ?, ?)";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, userId);
            statement.setString(2, tokenHash);
            statement.setTimestamp(3, Timestamp.from(expiresAt.toInstant()));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to create session", ex);
        }
    }

    public SessionRecord findSession(String tokenHash) {
        String sql = """
                SELECT id, user_id, expires_at, revoked_at
                FROM %s WHERE token_hash = ?
                """.formatted(qualified(SESSION));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tokenHash);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                SessionRecord session = new SessionRecord();
                session.id = rs.getLong("id");
                session.userId = rs.getLong("user_id");
                session.expiresAt = timestamp(rs, "expires_at");
                session.revokedAt = timestamp(rs, "revoked_at");
                return session;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load session", ex);
        }
    }

    public void touchSession(long id) {
        String sql = "UPDATE " + qualified(SESSION) + " SET last_seen_at = NOW(6) WHERE id = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to touch session", ex);
        }
    }

    public void revokeSession(String tokenHash) {
        String sql = "UPDATE " + qualified(SESSION) + " SET revoked_at = NOW(6) WHERE token_hash = ? AND revoked_at IS NULL";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tokenHash);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to revoke session", ex);
        }
    }

    public void revokeUserSessions(long userId) {
        String sql = "UPDATE " + qualified(SESSION) + " SET revoked_at = NOW(6) WHERE user_id = ? AND revoked_at IS NULL";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to revoke user sessions", ex);
        }
    }

    public boolean deletionColumnAvailable() {
        return columnExists(USER, "deleted_at") && columnExists(USER, "deleted_by");
    }

    public void assertDeletionReady() {
        if (!deletionColumnAvailable()) {
            throw new ValidationException(
                    "AUTH_MIGRATION_REQUIRED",
                    "Faltan columnas de eliminación. Ejecute backend/sql/proposed-auth-user-deletion.sql"
            );
        }
    }

    public boolean hasHistoricalActivity(AccessUserRecord user) {
        if (user == null) {
            return false;
        }
        if (user.lastLoginAt != null) {
            return true;
        }
        if (hasActorHistory(user.id)) {
            return true;
        }
        if (createdOtherUsers(user.id)) {
            return true;
        }
        return referencedAsOperator(String.valueOf(user.id));
    }

    public String historicalReason(AccessUserRecord user) {
        if (user == null) {
            return UserDeletionPolicy.MODE_PHYSICAL;
        }
        if (user.lastLoginAt != null) {
            return "LOGIN";
        }
        if (hasActorHistory(user.id)) {
            return "SECURITY_EVENT";
        }
        if (createdOtherUsers(user.id)) {
            return "CREATED_USERS";
        }
        if (referencedAsOperator(String.valueOf(user.id))) {
            return "OPERATIONAL_HISTORY";
        }
        return UserDeletionPolicy.MODE_PHYSICAL;
    }

    public void softDeleteUser(long userId, Long actorId, String emailSnapshot, String reason) {
        assertDeletionReady();
        Connection connection = null;
        boolean previous = true;
        try {
            connection = connection();
            previous = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE " + qualified(USER) + " SET deleted_at = NOW(6), deleted_by = ? WHERE id = ? AND deleted_at IS NULL")) {
                setLong(update, 1, actorId);
                update.setLong(2, userId);
                if (update.executeUpdate() != 1) {
                    throw new ValidationException(UserDeletionPolicy.ALREADY_DELETED, "The user is already deleted");
                }
            }
            try (PreparedStatement sessions = connection.prepareStatement(
                    "UPDATE " + qualified(SESSION) + " SET revoked_at = NOW(6) WHERE user_id = ? AND revoked_at IS NULL")) {
                sessions.setLong(1, userId);
                sessions.executeUpdate();
            }
            try (PreparedStatement tokens = connection.prepareStatement(
                    "UPDATE " + qualified(TOKEN) + " SET used_at = NOW(6) WHERE user_id = ? AND used_at IS NULL")) {
                tokens.setLong(1, userId);
                tokens.executeUpdate();
            }
            insertEvent(
                    connection,
                    UserDeletionPolicy.EVENT_DELETED,
                    actorId,
                    userId,
                    null,
                    UserDeletionPolicy.eventDetail(UserDeletionPolicy.MODE_SOFT, emailSnapshot, reason)
            );
            connection.commit();
        } catch (ValidationException ex) {
            rollbackQuietly(connection);
            throw ex;
        } catch (SQLException ex) {
            rollbackQuietly(connection);
            throw new DatabaseException("Failed to soft-delete user", ex);
        } finally {
            restoreAutoCommit(connection, previous);
            closeQuietly(connection);
        }
    }

    public void physicallyDeleteUser(long userId, Long actorId, String emailSnapshot, String reason) {
        assertDeletionReady();
        Connection connection = null;
        boolean previous = true;
        try {
            connection = connection();
            previous = connection.getAutoCommit();
            connection.setAutoCommit(false);
            insertEvent(
                    connection,
                    UserDeletionPolicy.EVENT_DELETED,
                    actorId,
                    userId,
                    null,
                    UserDeletionPolicy.eventDetail(UserDeletionPolicy.MODE_PHYSICAL, emailSnapshot, reason)
            );
            try (PreparedStatement sessions = connection.prepareStatement(
                    "DELETE FROM " + qualified(SESSION) + " WHERE user_id = ?")) {
                sessions.setLong(1, userId);
                sessions.executeUpdate();
            }
            try (PreparedStatement tokens = connection.prepareStatement(
                    "DELETE FROM " + qualified(TOKEN) + " WHERE user_id = ?")) {
                tokens.setLong(1, userId);
                tokens.executeUpdate();
            }
            try (PreparedStatement roles = connection.prepareStatement(
                    "DELETE FROM " + qualified(USER_ROLE) + " WHERE user_id = ?")) {
                roles.setLong(1, userId);
                roles.executeUpdate();
            }
            try (PreparedStatement user = connection.prepareStatement(
                    "DELETE FROM " + qualified(USER) + " WHERE id = ?")) {
                user.setLong(1, userId);
                if (user.executeUpdate() != 1) {
                    throw new ValidationException("USER_NOT_FOUND", "User was not found");
                }
            }
            connection.commit();
        } catch (ValidationException ex) {
            rollbackQuietly(connection);
            throw ex;
        } catch (SQLException ex) {
            rollbackQuietly(connection);
            throw new DatabaseException("Failed to delete user", ex);
        } finally {
            restoreAutoCommit(connection, previous);
            closeQuietly(connection);
        }
    }

    public void recordEvent(String type, Long actorId, Long targetUserId, Long targetRoleId, String detail) {
        if (!tableExists(EVENT)) {
            return;
        }
        try (Connection connection = connection()) {
            insertEvent(connection, type, actorId, targetUserId, targetRoleId, detail);
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to record security event", ex);
        }
    }

    private void insertEvent(
            Connection connection,
            String type,
            Long actorId,
            Long targetUserId,
            Long targetRoleId,
            String detail
    ) throws SQLException {
        if (!tableExists(EVENT)) {
            return;
        }
        String sql = """
                INSERT INTO %s (event_type, actor_user_id, target_user_id, target_role_id, detail)
                VALUES (?, ?, ?, ?, ?)
                """.formatted(qualified(EVENT));
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type);
            setLong(statement, 2, actorId);
            setLong(statement, 3, targetUserId);
            setLong(statement, 4, targetRoleId);
            statement.setString(5, detail);
            statement.executeUpdate();
        }
    }

    private boolean hasActorHistory(long userId) {
        if (!tableExists(EVENT)) {
            return false;
        }
        String sql = """
                SELECT event_type, actor_user_id
                FROM %s
                WHERE actor_user_id = ?
                """.formatted(qualified(EVENT));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    if (UserDeletionPolicy.actorEventIsHistorical(
                            rs.getString("event_type"),
                            nullableLong(rs, "actor_user_id")
                    )) {
                        return true;
                    }
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to inspect security events", ex);
        }
        return false;
    }

    private boolean createdOtherUsers(long userId) {
        if (!columnExists(USER, "created_by")) {
            return false;
        }
        return scalarLong(
                "SELECT COUNT(*) FROM " + qualified(USER) + " WHERE created_by = ? AND id <> ?",
                userId,
                userId
        ) > 0;
    }

    private boolean referencedAsOperator(String userKey) {
        return operatorMatch("sync_execution", "started_by", userKey)
                || operatorMatch("data_quality_execution", "started_by", userKey)
                || operatorMatch("orcid_discovery_execution", "started_by", userKey)
                || operatorMatch("pure_correction_audit", "performed_by", userKey);
    }

    private boolean operatorMatch(String table, String column, String userKey) {
        if (!tableExists(table) || !columnExists(table, column) || userKey == null || userKey.isBlank()) {
            return false;
        }
        return scalarLong(
                "SELECT COUNT(*) FROM " + qualified(table) + " WHERE `" + column + "` = ?",
                userKey
        ) > 0;
    }

    private AccessUserRecord loadUser(String predicate, Object param) {
        String sql = "SELECT * FROM " + qualified(USER) + " WHERE " + predicate;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, param);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                AccessUserRecord user = mapUser(rs);
                attachRoles(List.of(user));
                return user;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load user", ex);
        }
    }

    private void attachRoles(List<AccessUserRecord> users) {
        if (users.isEmpty()) {
            return;
        }
        Map<Long, AccessUserRecord> byId = new LinkedHashMap<>();
        for (AccessUserRecord user : users) {
            byId.put(user.id, user);
        }
        String sql = """
                SELECT ur.user_id, r.id, r.code, r.name, r.description, r.active, r.system_role
                FROM %s ur
                JOIN %s r ON r.id = ur.role_id
                WHERE ur.user_id IN (%s)
                ORDER BY r.name
                """.formatted(qualified(USER_ROLE), qualified(ROLE), inPlaceholders(byId.size()));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int i = 1;
            for (Long id : byId.keySet()) {
                statement.setLong(i++, id);
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    AccessUserRecord user = byId.get(rs.getLong("user_id"));
                    if (user == null) {
                        continue;
                    }
                    AccessRoleRecord role = new AccessRoleRecord();
                    role.id = rs.getLong("id");
                    role.code = rs.getString("code");
                    role.name = rs.getString("name");
                    role.description = rs.getString("description");
                    role.active = rs.getBoolean("active");
                    role.systemRole = rs.getBoolean("system_role");
                    user.roles.add(role);
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load user roles", ex);
        }
    }

    private void attachGrants(List<AccessRoleRecord> roles) {
        if (roles.isEmpty()) {
            return;
        }
        Map<Long, AccessRoleRecord> byId = new LinkedHashMap<>();
        for (AccessRoleRecord role : roles) {
            byId.put(role.id, role);
        }
        String sql = "SELECT role_id, module_code, permission_code FROM " + qualified(ROLE_PERM)
                + " WHERE role_id IN (" + inPlaceholders(byId.size()) + ")";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int i = 1;
            for (Long id : byId.keySet()) {
                statement.setLong(i++, id);
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    AccessRoleRecord role = byId.get(rs.getLong("role_id"));
                    if (role == null) {
                        continue;
                    }
                    AccessPermissionGrant grant = new AccessPermissionGrant();
                    grant.moduleCode = rs.getString("module_code");
                    grant.permissionCode = rs.getString("permission_code");
                    role.grants.add(grant);
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load role permissions", ex);
        }
    }

    private AccessUserRecord mapUser(ResultSet rs) throws SQLException {
        AccessUserRecord user = new AccessUserRecord();
        user.id = rs.getLong("id");
        user.email = nullableString(rs, "email");
        user.emailNormalized = nullableString(rs, "email_normalized");
        user.firstName = nullableString(rs, "first_name");
        user.lastName = nullableString(rs, "last_name");
        user.passwordHash = rs.getString("password_hash");
        user.status = nullableString(rs, "status");
        user.authProvider = nullableString(rs, "auth_provider");
        user.preferredLanguage = nullableString(rs, "preferred_language");
        user.failedLoginCount = rs.getInt("failed_login_count");
        user.lockedUntil = timestamp(rs, "locked_until");
        user.lastLoginAt = timestamp(rs, "last_login_at");
        user.activatedAt = timestamp(rs, "activated_at");
        user.createdAt = timestamp(rs, "created_at");
        user.updatedAt = timestamp(rs, "updated_at");
        user.createdBy = nullableLong(rs, "created_by");
        if (deletionColumnAvailable()) {
            user.deletedAt = timestamp(rs, "deleted_at");
            user.deletedBy = nullableLong(rs, "deleted_by");
        }
        return user;
    }

    private AccessRoleRecord mapRole(ResultSet rs) throws SQLException {
        AccessRoleRecord role = new AccessRoleRecord();
        role.id = rs.getLong("id");
        role.code = nullableString(rs, "code");
        role.name = nullableString(rs, "name");
        role.description = nullableString(rs, "description");
        role.active = rs.getBoolean("active");
        role.systemRole = rs.getBoolean("system_role");
        role.userCount = rs.getLong("user_count");
        return role;
    }

    private String userOrder(String sort, String direction) {
        String field = sort == null ? "createdAt" : sort;
        if (!USER_SORTS.contains(field)) {
            field = "createdAt";
        }
        boolean desc = direction == null || "desc".equalsIgnoreCase(direction);
        String expression = switch (field) {
            case "name" -> "u.last_name";
            case "email" -> "u.email_normalized";
            case "status" -> "u.status";
            case "lastLoginAt" -> "u.last_login_at";
            default -> "u.created_at";
        };
        return orderByNullsLast(expression, desc);
    }

    private static void setLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    private static OffsetDateTime timestamp(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant().atOffset(ZoneOffset.UTC);
    }

    public static class TokenRecord {
        public long id;
        public long userId;
        public String purpose;
        public OffsetDateTime expiresAt;
        public OffsetDateTime usedAt;
    }

    public static class SessionRecord {
        public long id;
        public long userId;
        public OffsetDateTime expiresAt;
        public OffsetDateTime revokedAt;
    }
}
