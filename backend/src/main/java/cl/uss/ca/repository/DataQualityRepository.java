package cl.uss.ca.repository;

import cl.uss.ca.api.dto.DataQualityExecutionDto;
import cl.uss.ca.api.dto.DataQualityFindingDto;
import cl.uss.ca.api.dto.DataQualityFindingEvidenceDto;
import cl.uss.ca.api.dto.DataQualityRuleCountDto;
import cl.uss.ca.api.dto.DataQualityRuleDto;
import cl.uss.ca.api.dto.DataQualityStatsDto;
import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.exception.DatabaseException;
import cl.uss.ca.exception.ExceptionDetail;
import cl.uss.ca.exception.NotFoundException;
import cl.uss.ca.quality.DataQualityCandidate;
import cl.uss.ca.quality.DataQualityCatalog;
import cl.uss.ca.quality.consistency.AuthorEvidence;
import cl.uss.ca.quality.consistency.ExternalEvidence;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@ApplicationScoped
public class DataQualityRepository extends JdbcRepository {
    private final ObjectMapper evidenceMapper = new ObjectMapper();

    public boolean available() {
        return tableExists("data_quality_rule")
                && tableExists("data_quality_execution")
                && tableExists("data_quality_finding");
    }

    public boolean tableExistsPublic(String table) {
        return tableExists(table);
    }

    public boolean columnExistsPublic(String table, String column) {
        return columnExists(table, column);
    }

    public String activePredicatePublic(String table, String alias) {
        return activeInPurePredicate(table, alias);
    }

    public String qualifiedPublic(String table) {
        return qualified(table);
    }

    public void ensureCatalog() {
        if (!available()) {
            return;
        }
        String sql = """
                INSERT INTO """ + " " + qualified("data_quality_rule") + """
                
                (code, source_family, entity_type, field_name, name, description, rule_text, alert_text,
                 bulk_procedure, category, correction_mode, enabled)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                ON DUPLICATE KEY UPDATE
                    source_family = VALUES(source_family),
                    entity_type = VALUES(entity_type),
                    field_name = VALUES(field_name),
                    name = VALUES(name),
                    description = VALUES(description),
                    rule_text = VALUES(rule_text),
                    alert_text = VALUES(alert_text),
                    bulk_procedure = VALUES(bulk_procedure),
                    category = VALUES(category),
                    correction_mode = VALUES(correction_mode)
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (DataQualityCatalog.RuleDef rule : DataQualityCatalog.RULES) {
                statement.setString(1, rule.code());
                statement.setString(2, rule.sourceFamily());
                statement.setString(3, rule.entityType());
                statement.setString(4, rule.fieldName());
                statement.setString(5, rule.name());
                statement.setString(6, rule.description());
                statement.setString(7, rule.ruleText());
                statement.setString(8, rule.alertText());
                statement.setString(9, rule.bulkProcedure());
                statement.setString(10, rule.category());
                statement.setString(11, rule.correctionMode());
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to upsert data-quality catalog", ex);
        }
    }

    public boolean hasRunning() {
        return scalarLong(
                "SELECT COUNT(*) FROM " + qualified("data_quality_execution")
                        + " WHERE status IN ('PENDING','RUNNING')"
        ) > 0;
    }

    public long createExecution(String triggerType, String scope, String entityType, Long localRecordId,
            Long syncExecutionId, String startedBy, int totalRecords) {
        String sql = """
                INSERT INTO """ + " " + qualified("data_quality_execution") + """
                
                (status, trigger_type, scope, entity_type, local_record_id, sync_execution_id,
                 started_at, total_records, started_by)
                VALUES ('RUNNING', ?, ?, ?, ?, ?, NOW(6), ?, ?)
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, triggerType);
            statement.setString(2, scope);
            statement.setString(3, entityType);
            setLong(statement, 4, localRecordId);
            setLong(statement, 5, syncExecutionId);
            statement.setInt(6, totalRecords);
            statement.setString(7, clip(startedBy, 128));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("data_quality_execution did not return a generated id");
                }
                return keys.getLong(1);
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to create data-quality execution", ex);
        }
    }

    public void heartbeat(long executionId, int evaluated, int findings, int errors) {
        String sql = """
                UPDATE """ + " " + qualified("data_quality_execution") + """
                
                SET evaluated_records = ?, finding_count = ?, error_count = ?
                WHERE id = ?
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, evaluated);
            statement.setInt(2, findings);
            statement.setInt(3, errors);
            statement.setLong(4, executionId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to heartbeat data-quality execution", ex);
        }
    }

    public void finish(long executionId, String status, int evaluated, int findings, int resolved, int errors,
            String errorCode, String errorMessage) {
        String sql = """
                UPDATE """ + " " + qualified("data_quality_execution") + """
                
                SET status = ?, finished_at = NOW(6), evaluated_records = ?, finding_count = ?,
                    resolved_count = ?, error_count = ?, error_code = ?, error_message = ?
                WHERE id = ?
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setInt(2, evaluated);
            statement.setInt(3, findings);
            statement.setInt(4, resolved);
            statement.setInt(5, errors);
            statement.setString(6, clip(errorCode, 100));
            statement.setString(7, clip(errorMessage, 1000));
            statement.setLong(8, executionId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to finish data-quality execution", ex);
        }
    }

    public DataQualityExecutionDto findExecution(long id) {
        List<DataQualityExecutionDto> items = selectExecutions(
                "SELECT * FROM " + qualified("data_quality_execution") + " WHERE id = ?", id);
        if (items.isEmpty()) {
            throw new NotFoundException("DATA_QUALITY_EXECUTION_NOT_FOUND", "Validation run was not found");
        }
        return items.get(0);
    }

    public DataQualityExecutionDto findLatest() {
        List<DataQualityExecutionDto> items = selectExecutions(
                "SELECT * FROM " + qualified("data_quality_execution") + " ORDER BY id DESC LIMIT 1");
        return items.isEmpty() ? null : items.get(0);
    }

    public List<DataQualityExecutionDto> history(int limit) {
        return selectExecutions(
                "SELECT * FROM " + qualified("data_quality_execution") + " ORDER BY id DESC LIMIT ?",
                Math.max(1, Math.min(limit, 50))
        );
    }

    public Long ruleId(String code) {
        String sql = "SELECT id FROM " + qualified("data_quality_rule") + " WHERE code = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new NotFoundException("DATA_QUALITY_RULE_NOT_FOUND", "Rule was not found: " + code);
                }
                return rs.getLong(1);
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load data-quality rule id", ex);
        }
    }

    public void upsertFinding(long executionId, long ruleId, DataQualityCandidate candidate) {
        String sql = """
                INSERT INTO """ + " " + qualified("data_quality_finding") + """
                
                (rule_id, execution_id, finding_key, entity_type, local_record_id, pure_id, record_uuid,
                 field_name, issue_kind, correction_mode, status, summary, current_value, suggested_value,
                 evidence_json, first_detected_at, last_detected_at, last_seen_execution_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', ?, ?, ?, ?, NOW(6), NOW(6), ?)
                ON DUPLICATE KEY UPDATE
                    execution_id = IF(status = 'NOT_APPLICABLE', execution_id, VALUES(execution_id)),
                    last_detected_at = NOW(6),
                    last_seen_execution_id = VALUES(last_seen_execution_id),
                    summary = VALUES(summary),
                    current_value = VALUES(current_value),
                    suggested_value = VALUES(suggested_value),
                    evidence_json = VALUES(evidence_json),
                    issue_kind = VALUES(issue_kind),
                    correction_mode = VALUES(correction_mode),
                    status = CASE
                        WHEN status = 'NOT_APPLICABLE' THEN status
                        WHEN status = 'RESOLVED' THEN 'OPEN'
                        ELSE status
                    END,
                    resolved_at = IF(status = 'NOT_APPLICABLE', resolved_at, NULL),
                    resolved_by = IF(status = 'NOT_APPLICABLE', resolved_by, NULL),
                    resolution_note = IF(status = 'NOT_APPLICABLE', resolution_note, NULL)
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, ruleId);
            statement.setLong(2, executionId);
            statement.setString(3, clip(candidate.findingKey, 191));
            statement.setString(4, candidate.entityType);
            statement.setLong(5, candidate.localRecordId);
            setLong(statement, 6, candidate.pureId);
            statement.setString(7, clip(candidate.recordUuid, 36));
            statement.setString(8, clip(candidate.fieldName, 128));
            statement.setString(9, clip(candidate.issueKind, 64));
            statement.setString(10, clip(candidate.correctionMode, 32));
            statement.setString(11, clip(candidate.summary, 500));
            statement.setString(12, candidate.currentValue);
            statement.setString(13, candidate.suggestedValue);
            statement.setString(14, candidate.evidenceJson);
            statement.setLong(15, executionId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to upsert data-quality finding: " + ExceptionDetail.describe(ex), ex);
        }
        if (candidate.evidences != null) {
            replaceEvidence(candidate.findingKey, candidate.evidences);
        }
    }

    public void replaceEvidence(String findingKey, List<ExternalEvidence> evidences) {
        if (!tableExists("data_quality_finding_evidence")) {
            return;
        }
        long findingId = scalarLong(
                "SELECT id FROM " + qualified("data_quality_finding") + " WHERE finding_key = ?",
                clip(findingKey, 191)
        );
        if (findingId < 1) {
            return;
        }
        String deleteSql = "DELETE FROM " + qualified("data_quality_finding_evidence") + " WHERE finding_id = ?";
        String insertSql = """
                INSERT INTO """ + " " + qualified("data_quality_finding_evidence") + """
                
                (finding_id, source, source_record_id, source_field, external_value, normalized_external_value,
                 comparison_result, external_last_sync_at, metadata_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = connection();
             PreparedStatement delete = connection.prepareStatement(deleteSql);
             PreparedStatement insert = connection.prepareStatement(insertSql)) {
            delete.setLong(1, findingId);
            delete.executeUpdate();
            if (evidences == null || evidences.isEmpty()) {
                return;
            }
            for (ExternalEvidence item : evidences) {
                insert.setLong(1, findingId);
                insert.setString(2, clip(item.source, 32));
                insert.setString(3, clip(item.sourceRecordId, 64));
                insert.setString(4, clip(item.field == null ? null : item.field.fieldName(), 64));
                insert.setString(5, item.originalValue);
                insert.setString(6, clip(item.normalizedValue, 500));
                insert.setString(7, clip(item.result == null ? null : item.result.name(), 32));
                if (item.lastExternalSyncAt == null) {
                    insert.setNull(8, Types.TIMESTAMP);
                } else {
                    insert.setTimestamp(8, Timestamp.from(item.lastExternalSyncAt.toInstant()));
                }
                insert.setString(9, evidenceMetadata(item));
                insert.addBatch();
            }
            insert.executeBatch();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to replace data-quality evidence: " + ExceptionDetail.describe(ex), ex);
        }
    }

    private String evidenceMetadata(ExternalEvidence item) {
        ObjectNode node = evidenceMapper.createObjectNode();
        if (item.completeAuthorList != null) {
            node.put("completeAuthorList", item.completeAuthorList);
        }
        if (item.locatorKind != null) {
            node.put("locatorKind", item.locatorKind.name());
        }
        if (!item.authors.isEmpty()) {
            ArrayNode authors = node.putArray("authors");
            for (AuthorEvidence author : item.authors) {
                ObjectNode row = authors.addObject();
                row.put("fullName", author.fullName == null ? "" : author.fullName);
                row.put("matchStatus", author.matchStatus == null ? "" : author.matchStatus);
                if (author.position != null) {
                    row.put("position", author.position);
                }
            }
        }
        return node.isEmpty() ? null : node.toString();
    }

    public int resolveMissing(long executionId, String entityType, Long localRecordId, List<String> ruleCodes) {
        if (ruleCodes == null || ruleCodes.isEmpty()) {
            return 0;
        }
        StringBuilder sql = new StringBuilder("""
                UPDATE """).append(" ").append(qualified("data_quality_finding")).append("""
                
                SET status = 'RESOLVED',
                    resolved_at = NOW(6),
                    resolved_by = 'AUTO',
                    resolution_note = 'AUTO_RESOLVED'
                WHERE status IN ('OPEN','IN_REVIEW')
                  AND (last_seen_execution_id IS NULL OR last_seen_execution_id <> ?)
                  AND rule_id IN (
                      SELECT id FROM """).append(" ").append(qualified("data_quality_rule")).append("""
                      
                      WHERE code IN (""");
        sql.append(inPlaceholders(ruleCodes.size())).append("))");
        if (entityType != null && localRecordId != null) {
            sql.append(" AND entity_type = ? AND local_record_id = ?");
        }
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int index = 1;
            statement.setLong(index++, executionId);
            for (String code : ruleCodes) {
                statement.setString(index++, code);
            }
            if (entityType != null && localRecordId != null) {
                statement.setString(index++, entityType);
                statement.setLong(index, localRecordId);
            }
            return statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to auto-resolve disappeared findings", ex);
        }
    }

    public void markResolved(long findingId, String resolvedBy, String note) {
        updateFindingStatus(findingId, "RESOLVED", resolvedBy, note);
    }

    public void markInReview(long findingId, String resolvedBy) {
        String sql = """
                UPDATE """ + " " + qualified("data_quality_finding") + """
                
                SET status = 'IN_REVIEW'
                WHERE id = ? AND status IN ('OPEN','IN_REVIEW')
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, findingId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to mark finding in review", ex);
        }
    }

    public void markNotApplicable(long findingId, String resolvedBy, String justification) {
        updateFindingStatus(findingId, "NOT_APPLICABLE", resolvedBy, justification);
    }

    public DataQualityFindingDto requireFinding(long id) {
        FindingQuery query = new FindingQuery();
        query.findingId = id;
        query.page = 0;
        query.size = 1;
        List<DataQualityFindingDto> items = listFindings(query).items;
        if (items.isEmpty()) {
            throw new NotFoundException("DATA_QUALITY_FINDING_NOT_FOUND", "Finding was not found");
        }
        return items.get(0);
    }

    public PageResponse<DataQualityFindingDto> listFindings(FindingQuery query) {
        Where where = buildWhere(query);
        String order = sortSql(query.sort, query.direction);
        String sql = findingSelect() + where.sql
                + " ORDER BY " + order + " LIMIT ? OFFSET ?";
        List<Object> params = new ArrayList<>(where.params);
        int size = Math.max(1, Math.min(query.size, 100));
        int page = Math.max(query.page, 0);
        params.add(size);
        params.add(page * size);
        List<DataQualityFindingDto> items = selectFindings(sql, params.toArray());
        attachEvidence(items);
        long total = scalarLong("SELECT COUNT(*) " + findingFrom() + where.sql, where.params.toArray());
        return PageResponse.of(items, page, size, total);
    }

    public List<DataQualityFindingDto> exportFindings(FindingQuery query) {
        Where where = buildWhere(query);
        String order = sortSql(query.sort, query.direction);
        List<DataQualityFindingDto> items = selectFindings(
                findingSelect() + where.sql + " ORDER BY " + order + " LIMIT 50000",
                where.params.toArray());
        attachEvidence(items);
        return items;
    }

    public DataQualityStatsDto stats(FindingQuery query) {
        FindingQuery openQuery = copy(query);
        openQuery.status = "OPEN";
        Where where = buildWhere(openQuery);
        DataQualityStatsDto dto = new DataQualityStatsDto();
        dto.openFindings = scalarLong("SELECT COUNT(*) " + findingFrom() + where.sql, where.params.toArray());
        dto.affectedRecords = scalarLong(
                "SELECT COUNT(DISTINCT CONCAT(f.entity_type, ':', f.local_record_id)) "
                        + findingFrom() + where.sql,
                where.params.toArray()
        );
        dto.correctionAvailable = scalarLong(
                "SELECT COUNT(*) " + findingFrom() + where.sql + " AND f.correction_mode = 'SAFE_SUGGESTION'",
                where.params.toArray()
        );
        dto.manualReview = scalarLong(
                "SELECT COUNT(*) " + findingFrom() + where.sql + " AND f.correction_mode = 'MANUAL_REVIEW'",
                where.params.toArray()
        );
        dto.viewOnly = scalarLong(
                "SELECT COUNT(*) " + findingFrom() + where.sql + " AND f.correction_mode = 'VIEW_ONLY'",
                where.params.toArray()
        );
        dto.rulesWithFindings = scalarLong(
                "SELECT COUNT(DISTINCT f.rule_id) " + findingFrom() + where.sql,
                where.params.toArray()
        );
        dto.ruleCounts = ruleCounts(query);
        return dto;
    }

    public List<DataQualityRuleDto> listRules() {
        String sql = """
                SELECT r.*, (
                    SELECT COUNT(*) FROM """ + " " + qualified("data_quality_finding") + """
                     f
                    WHERE f.rule_id = r.id AND f.status = 'OPEN'
                ) AS open_count
                FROM """ + " " + qualified("data_quality_rule") + """
                 r
                ORDER BY r.code
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<DataQualityRuleDto> items = new ArrayList<>();
            while (rs.next()) {
                items.add(mapRule(rs));
            }
            return items;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list data-quality rules", ex);
        }
    }

    public long countOpenFindings() {
        return scalarLong(
                "SELECT COUNT(*) FROM " + qualified("data_quality_finding") + " WHERE status = 'OPEN'"
        );
    }

    public int countOutputs() {
        return (int) scalarLong(
                "SELECT COUNT(*) FROM " + qualified("pure_research_output") + " o WHERE "
                        + activeInPurePredicate("pure_research_output", "o")
        );
    }

    public int countPeople() {
        return (int) scalarLong(
                "SELECT COUNT(*) FROM " + qualified("pure_person") + " p WHERE "
                        + activeInPurePredicate("pure_person", "p")
        );
    }

    public <T> List<T> select(String sql, RowMapper<T> mapper, Object... params) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet rs = statement.executeQuery()) {
                List<T> items = new ArrayList<>();
                while (rs.next()) {
                    items.add(mapper.map(rs));
                }
                return items;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Data-quality query failed: " + ExceptionDetail.describe(ex), ex);
        }
    }

    public static class FindingQuery {
        public String search;
        public String category;
        public String ruleCode;
        public String entityType;
        public String correctionMode;
        public String status;
        public String sort;
        public String direction;
        public int page;
        public int size = 25;
        public Long findingId;
        public String source;
    }

    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    private List<DataQualityRuleCountDto> ruleCounts(FindingQuery query) {
        FindingQuery copy = copy(query);
        copy.ruleCode = null;
        Where where = buildWhere(copy);
        String extra = where.sql.isBlank() ? "" : where.sql.replaceFirst("WHERE", "AND");
        String sql = "SELECT r.code, r.name, r.source_family, (SELECT COUNT(*) FROM "
                + qualified("data_quality_finding") + " f WHERE f.rule_id = r.id"
                + extra + ") AS cnt FROM "
                + qualified("data_quality_rule") + " r ORDER BY r.code";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, where.params.toArray());
            try (ResultSet rs = statement.executeQuery()) {
                List<DataQualityRuleCountDto> items = new ArrayList<>();
                while (rs.next()) {
                    DataQualityRuleCountDto dto = new DataQualityRuleCountDto();
                    dto.code = rs.getString("code");
                    dto.name = rs.getString("name");
                    dto.sourceFamily = rs.getString("source_family");
                    dto.count = rs.getLong("cnt");
                    items.add(dto);
                }
                String source = DataQualityCatalog.normalizeSource(query.source);
                if (DataQualityCatalog.isRuleSourceFamily(source)) {
                    items.removeIf(item -> !source.equalsIgnoreCase(item.sourceFamily));
                } else if (source != null) {
                    items.removeIf(item -> item.count <= 0);
                }
                return items;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load data-quality rule counts", ex);
        }
    }

    private String findingSelect() {
        return """
                SELECT f.*, r.code AS rule_code, r.name AS rule_name, r.category, r.description,
                       r.rule_text, r.alert_text,
                       CASE f.entity_type
                         WHEN 'RESEARCH_OUTPUT' THEN (
                            SELECT o.title FROM """ + " " + qualified("pure_research_output") + """
                             o WHERE o.id = f.local_record_id
                         )
                         WHEN 'PERSON' THEN (
                            SELECT TRIM(CONCAT(COALESCE(p.last_name, ''),
                                IF(p.last_name IS NULL OR p.last_name = '', '', ', '),
                                COALESCE(p.first_name, '')))
                            FROM """ + " " + qualified("pure_person") + """
                             p WHERE p.id = f.local_record_id
                         )
                         ELSE NULL
                       END AS record_label
                """ + findingFrom();
    }

    private String findingFrom() {
        return " FROM " + qualified("data_quality_finding") + " f JOIN "
                + qualified("data_quality_rule") + " r ON r.id = f.rule_id ";
    }

    private Where buildWhere(FindingQuery query) {
        List<String> clauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (query.findingId != null) {
            clauses.add("f.id = ?");
            params.add(query.findingId);
        }
        if (notBlank(query.category)) {
            clauses.add("r.category = ?");
            params.add(query.category);
        }
        if (notBlank(query.ruleCode)) {
            clauses.add("r.code = ?");
            params.add(query.ruleCode);
        }
        if (notBlank(query.entityType)) {
            clauses.add("f.entity_type = ?");
            params.add(query.entityType);
        }
        if (notBlank(query.correctionMode)) {
            clauses.add("f.correction_mode = ?");
            params.add(query.correctionMode);
        }
        if (notBlank(query.status) && !"ALL".equalsIgnoreCase(query.status)) {
            clauses.add("f.status = ?");
            params.add(query.status.toUpperCase(Locale.ROOT));
        }
        String source = DataQualityCatalog.normalizeSource(query.source);
        if (DataQualityCatalog.isRuleSourceFamily(source)) {
            clauses.add("r.source_family = ?");
            params.add(source);
        } else if (source != null && tableExists("data_quality_finding_evidence")) {
            clauses.add("""
                    EXISTS (
                        SELECT 1 FROM """ + " " + qualified("data_quality_finding_evidence") + """
                         e
                        WHERE e.finding_id = f.id AND e.source = ?
                    )
                    """);
            params.add(source);
        }
        if (notBlank(query.search)) {
            clauses.add("""
                    (r.code LIKE ? ESCAPE '!'
                     OR r.name LIKE ? ESCAPE '!'
                     OR f.summary LIKE ? ESCAPE '!'
                     OR COALESCE(f.current_value,'') LIKE ? ESCAPE '!'
                     OR COALESCE(f.record_uuid,'') LIKE ? ESCAPE '!'
                     OR CAST(f.pure_id AS CHAR) LIKE ? ESCAPE '!')
                    """);
            String like = likeContains(query.search.trim());
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        Where where = new Where();
        where.sql = clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
        where.params = params;
        return where;
    }

    private String sortSql(String sort, String direction) {
        boolean desc = sort != null && "desc".equalsIgnoreCase(direction);
        String expression = switch (sort == null ? "" : sort) {
            case "rule", "ruleCode" -> "r.code";
            case "record", "recordLabel" -> "record_label";
            case "entity", "entityType" -> "f.entity_type";
            case "field", "fieldName" -> "f.field_name";
            case "problem", "summary" -> "f.summary";
            case "action", "correctionMode" -> "f.correction_mode";
            case "status" -> "f.status";
            case "detected", "lastDetectedAt" -> "f.last_detected_at";
            default -> "f.last_detected_at";
        };
        if ("f.last_detected_at".equals(expression) && (sort == null || sort.isBlank())) {
            desc = true;
        }
        return orderByNullsLast(expression, desc) + ", f.id ASC";
    }

    private List<DataQualityFindingDto> selectFindings(String sql, Object... params) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet rs = statement.executeQuery()) {
                List<DataQualityFindingDto> items = new ArrayList<>();
                while (rs.next()) {
                    items.add(mapFinding(rs));
                }
                return items;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list data-quality findings", ex);
        }
    }

    private List<DataQualityExecutionDto> selectExecutions(String sql, Object... params) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet rs = statement.executeQuery()) {
                List<DataQualityExecutionDto> items = new ArrayList<>();
                while (rs.next()) {
                    items.add(mapExecution(rs));
                }
                return items;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load data-quality executions", ex);
        }
    }

    private DataQualityFindingDto mapFinding(ResultSet rs) throws SQLException {
        DataQualityFindingDto dto = new DataQualityFindingDto();
        dto.id = rs.getLong("id");
        dto.ruleId = rs.getLong("rule_id");
        dto.ruleCode = rs.getString("rule_code");
        dto.ruleName = rs.getString("rule_name");
        dto.category = rs.getString("category");
        dto.entityType = rs.getString("entity_type");
        dto.localRecordId = rs.getLong("local_record_id");
        dto.pureId = nullableLong(rs, "pure_id");
        dto.recordUuid = nullableString(rs, "record_uuid");
        dto.recordLabel = nullableString(rs, "record_label");
        dto.fieldName = nullableString(rs, "field_name");
        dto.issueKind = rs.getString("issue_kind");
        dto.correctionMode = rs.getString("correction_mode");
        dto.status = rs.getString("status");
        dto.summary = rs.getString("summary");
        dto.currentValue = rs.getString("current_value");
        dto.suggestedValue = rs.getString("suggested_value");
        dto.evidenceJson = rs.getString("evidence_json");
        dto.description = rs.getString("description");
        dto.ruleText = rs.getString("rule_text");
        dto.alertText = rs.getString("alert_text");
        dto.firstDetectedAt = offset(rs.getTimestamp("first_detected_at"));
        dto.lastDetectedAt = offset(rs.getTimestamp("last_detected_at"));
        dto.resolvedAt = offset(rs.getTimestamp("resolved_at"));
        dto.resolvedBy = nullableString(rs, "resolved_by");
        dto.resolutionNote = nullableString(rs, "resolution_note");
        dto.recordRoute = "PERSON".equals(dto.entityType)
                ? "/people/" + dto.localRecordId
                : "/research-output/" + dto.localRecordId;
        return dto;
    }

    private DataQualityExecutionDto mapExecution(ResultSet rs) throws SQLException {
        DataQualityExecutionDto dto = new DataQualityExecutionDto();
        dto.id = rs.getLong("id");
        dto.status = rs.getString("status");
        dto.triggerType = rs.getString("trigger_type");
        dto.scope = rs.getString("scope");
        dto.entityType = nullableString(rs, "entity_type");
        dto.localRecordId = nullableLong(rs, "local_record_id");
        dto.syncExecutionId = nullableLong(rs, "sync_execution_id");
        dto.startedAt = offset(rs.getTimestamp("started_at"));
        dto.finishedAt = offset(rs.getTimestamp("finished_at"));
        dto.totalRecords = rs.getInt("total_records");
        dto.evaluatedRecords = rs.getInt("evaluated_records");
        dto.findingCount = rs.getInt("finding_count");
        dto.resolvedCount = rs.getInt("resolved_count");
        dto.errorCount = rs.getInt("error_count");
        dto.startedBy = nullableString(rs, "started_by");
        dto.errorCode = nullableString(rs, "error_code");
        dto.errorMessage = nullableString(rs, "error_message");
        if (dto.startedAt != null && dto.finishedAt != null) {
            dto.durationSeconds = (int) Math.max(0,
                    dto.finishedAt.toEpochSecond() - dto.startedAt.toEpochSecond());
        }
        return dto;
    }

    private DataQualityRuleDto mapRule(ResultSet rs) throws SQLException {
        DataQualityRuleDto dto = new DataQualityRuleDto();
        dto.id = rs.getLong("id");
        dto.code = rs.getString("code");
        dto.sourceFamily = rs.getString("source_family");
        dto.entityType = rs.getString("entity_type");
        dto.fieldName = rs.getString("field_name");
        dto.name = rs.getString("name");
        dto.description = rs.getString("description");
        dto.ruleText = rs.getString("rule_text");
        dto.alertText = rs.getString("alert_text");
        dto.bulkProcedure = rs.getString("bulk_procedure");
        dto.category = rs.getString("category");
        dto.correctionMode = rs.getString("correction_mode");
        dto.enabled = rs.getBoolean("enabled");
        dto.openFindingCount = rs.getLong("open_count");
        return dto;
    }

    private void updateFindingStatus(long findingId, String status, String resolvedBy, String note) {
        String sql = """
                UPDATE """ + " " + qualified("data_quality_finding") + """
                
                SET status = ?, resolved_at = NOW(6), resolved_by = ?, resolution_note = ?
                WHERE id = ?
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setString(2, clip(resolvedBy, 128));
            statement.setString(3, clip(note, 1000));
            statement.setLong(4, findingId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to update finding status", ex);
        }
    }

    private static FindingQuery copy(FindingQuery query) {
        FindingQuery copy = new FindingQuery();
        copy.search = query.search;
        copy.category = query.category;
        copy.ruleCode = query.ruleCode;
        copy.entityType = query.entityType;
        copy.correctionMode = query.correctionMode;
        copy.status = query.status;
        copy.sort = query.sort;
        copy.direction = query.direction;
        copy.page = query.page;
        copy.size = query.size;
        copy.findingId = query.findingId;
        copy.source = query.source;
        return copy;
    }

    private void attachEvidence(List<DataQualityFindingDto> items) {
        if (items == null || items.isEmpty() || !tableExists("data_quality_finding_evidence")) {
            return;
        }
        Map<Long, DataQualityFindingDto> byId = new LinkedHashMap<>();
        for (DataQualityFindingDto item : items) {
            byId.put(item.id, item);
        }
        List<Long> ids = new ArrayList<>(byId.keySet());
        int from = 0;
        while (from < ids.size()) {
            int to = Math.min(from + 200, ids.size());
            List<Long> chunk = ids.subList(from, to);
            String sql = """
                    SELECT id, finding_id, source, source_record_id, source_field, external_value,
                           normalized_external_value, comparison_result, external_last_sync_at, metadata_json
                    FROM """ + " " + qualified("data_quality_finding_evidence") + """
                    
                    WHERE finding_id IN (""" + inPlaceholders(chunk.size()) + ") ORDER BY id";
            try (Connection connection = connection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < chunk.size(); i++) {
                    statement.setLong(i + 1, chunk.get(i));
                }
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        DataQualityFindingDto finding = byId.get(rs.getLong("finding_id"));
                        if (finding == null) {
                            continue;
                        }
                        DataQualityFindingEvidenceDto row = new DataQualityFindingEvidenceDto();
                        row.id = rs.getLong("id");
                        row.source = nullableString(rs, "source");
                        row.sourceRecordId = nullableString(rs, "source_record_id");
                        row.sourceField = nullableString(rs, "source_field");
                        row.externalValue = rs.getString("external_value");
                        row.normalizedExternalValue = nullableString(rs, "normalized_external_value");
                        row.comparisonResult = nullableString(rs, "comparison_result");
                        row.externalLastSyncAt = offset(rs.getTimestamp("external_last_sync_at"));
                        row.metadataJson = rs.getString("metadata_json");
                        finding.evidences.add(row);
                    }
                }
            } catch (SQLException ex) {
                throw new DatabaseException("Failed to load data-quality evidence", ex);
            }
            from = to;
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static void setLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static OffsetDateTime offset(Timestamp value) {
        return value == null ? null : OffsetDateTime.ofInstant(value.toInstant(), ZoneOffset.UTC);
    }

    private static final class Where {
        String sql = "";
        List<Object> params = new ArrayList<>();
    }
}
