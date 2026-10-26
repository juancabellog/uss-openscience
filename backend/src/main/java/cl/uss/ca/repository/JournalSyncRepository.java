package cl.uss.ca.repository;

import cl.uss.ca.external.pure.model.JournalClassificationData;
import cl.uss.ca.external.pure.model.JournalIdentifierData;
import cl.uss.ca.external.pure.model.JournalIssnData;
import cl.uss.ca.external.pure.model.JournalKeywordData;
import cl.uss.ca.external.pure.model.JournalKeywordGroupData;
import cl.uss.ca.external.pure.model.JournalTitleData;
import cl.uss.ca.external.pure.model.PureJournal;
import cl.uss.ca.sync.diff.AggregateOutcome;
import cl.uss.ca.sync.diff.JournalSnapshot;
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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class JournalSyncRepository extends JdbcRepository {

    public Connection begin() throws SQLException {
        Connection connection = connection();
        connection.setAutoCommit(false);
        return connection;
    }

    public Map<String, JournalSnapshot> loadSnapshots(Connection connection, Collection<String> uuids)
            throws SQLException {
        Map<String, JournalSnapshot> byUuid = new LinkedHashMap<>();
        if (uuids == null || uuids.isEmpty() || !tableExists("pure_journal")) {
            return byUuid;
        }
        List<String> values = new ArrayList<>(uuids);
        String sql = """
                SELECT id, uuid, pure_id, created_by, created_date_pure, modified_by, modified_date_pure,
                       pure_version, publisher_uuid, publisher_system_name,
                       type_uri, type_en, type_es, workflow_step, workflow_description_en, workflow_description_es,
                       system_name
                """ + (columnExists("pure_journal", "active_in_pure") ? ", active_in_pure" : "") + """
                
                FROM """ + " " + qualified("pure_journal") + """
                
                WHERE uuid IN (""" + inPlaceholders(values.size()) + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.size(); i++) {
                statement.setString(i + 1, values.get(i));
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    JournalSnapshot snapshot = mapParent(rs);
                    byUuid.put(snapshot.uuid, snapshot);
                }
            }
        } catch (SQLException ex) {
            if (isUnknownColumn(ex)) {
                return byUuid;
            }
            throw ex;
        }
        if (byUuid.isEmpty()) {
            return byUuid;
        }
        List<Long> ids = byUuid.values().stream().map(item -> item.id).toList();
        if (tableExists("pure_journal_title")) {
            loadTitles(connection, ids, byUuid);
        }
        if (tableExists("pure_journal_issn")) {
            loadIssns(connection, ids, byUuid);
        }
        if (tableExists("pure_journal_identifier")) {
            loadIdentifiers(connection, ids, byUuid);
        }
        if (tableExists("pure_journal_keyword_group")) {
            loadKeywordGroups(connection, ids, byUuid);
        }
        return byUuid;
    }

    public Long apply(Connection connection, PureJournal journal, AggregateOutcome outcome) throws SQLException {
        Long journalId = outcome.localId;
        if (outcome.persistParent() || journalId == null) {
            journalId = upsertJournal(connection, journal);
        }
        if (outcome.persistChildren()) {
            replaceTitles(connection, journalId, journal.getTitles());
            replaceIssns(connection, journalId, journal.getIssns());
            replaceIdentifiers(connection, journalId, journal.getIdentifiers());
            replaceKeywordGroups(connection, journalId, journal.getKeywordGroups());
        }
        return journalId;
    }

    public int resolveResearchOutputReferences(Connection connection) throws SQLException {
        if (!tableExists("pure_research_output_journal")
                || !columnExists("pure_research_output_journal", "journal_uuid")) {
            return 0;
        }
        String sql = """
                UPDATE """ + " " + qualified("pure_research_output_journal") + """
                 rj
                JOIN """ + " " + qualified("pure_journal") + """
                 j ON j.uuid = rj.journal_uuid
                SET rj.journal_id = j.id
                WHERE rj.journal_uuid IS NOT NULL
                  AND (rj.journal_id IS NULL OR rj.journal_id <> j.id)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            return statement.executeUpdate();
        }
    }

    private Long upsertJournal(Connection connection, PureJournal journal) throws SQLException {
        String sql = """
                INSERT INTO """ + " " + qualified("pure_journal") + """
                 (
                    uuid, pure_id, title_pure_id, title, issn_pure_id, issn,
                    created_by, created_date_pure, modified_by, modified_date_pure, pure_version,
                    publisher_uuid, publisher_system_name,
                    type_uri, type_en, type_es,
                    workflow_step, workflow_description_en, workflow_description_es, system_name
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    id = LAST_INSERT_ID(id),
                    pure_id = VALUES(pure_id),
                    title_pure_id = VALUES(title_pure_id),
                    title = VALUES(title),
                    issn_pure_id = VALUES(issn_pure_id),
                    issn = VALUES(issn),
                    created_by = VALUES(created_by),
                    created_date_pure = VALUES(created_date_pure),
                    modified_by = VALUES(modified_by),
                    modified_date_pure = VALUES(modified_date_pure),
                    pure_version = VALUES(pure_version),
                    publisher_uuid = VALUES(publisher_uuid),
                    publisher_system_name = VALUES(publisher_system_name),
                    type_uri = VALUES(type_uri),
                    type_en = VALUES(type_en),
                    type_es = VALUES(type_es),
                    workflow_step = VALUES(workflow_step),
                    workflow_description_en = VALUES(workflow_description_en),
                    workflow_description_es = VALUES(workflow_description_es),
                    system_name = VALUES(system_name)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, journal.getUuid());
            setLong(statement, 2, journal.getPureId());
            JournalTitleData firstTitle = journal.getTitles().isEmpty() ? null : journal.getTitles().get(0);
            JournalIssnData firstIssn = journal.getIssns().isEmpty() ? null : journal.getIssns().get(0);
            setLong(statement, 3, firstTitle == null ? null : firstTitle.getPureId());
            statement.setString(4, firstTitle == null ? null : firstTitle.getTitle());
            setLong(statement, 5, firstIssn == null ? null : firstIssn.getPureId());
            statement.setString(6, firstIssn == null ? null : firstIssn.getIssn());
            statement.setString(7, journal.getCreatedBy());
            setTimestamp(statement, 8, journal.getCreatedDatePure());
            statement.setString(9, journal.getModifiedBy());
            setTimestamp(statement, 10, journal.getModifiedDatePure());
            statement.setString(11, journal.getVersion());
            statement.setString(12, journal.getPublisherUuid());
            statement.setString(13, journal.getPublisherSystemName());
            statement.setString(14, journal.getTypeUri());
            statement.setString(15, journal.getTypeEn());
            statement.setString(16, journal.getTypeEs());
            statement.setString(17, journal.getWorkflowStep());
            statement.setString(18, journal.getWorkflowDescriptionEn());
            statement.setString(19, journal.getWorkflowDescriptionEs());
            statement.setString(20, journal.getSystemName());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next() && keys.getLong(1) > 0) {
                    return keys.getLong(1);
                }
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM " + qualified("pure_journal") + " WHERE uuid = ?")) {
            statement.setString(1, journal.getUuid());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new SQLException("Unable to persist journal uuid=" + journal.getUuid());
    }

    private void replaceTitles(Connection connection, Long journalId, List<JournalTitleData> titles)
            throws SQLException {
        if (!tableExists("pure_journal_title")) {
            return;
        }
        executeDelete(connection, "pure_journal_title", journalId);
        if (titles == null || titles.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO """ + " " + qualified("pure_journal_title") + """
                 (journal_id, pure_id, title) VALUES (?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (JournalTitleData title : titles) {
                statement.setLong(1, journalId);
                setLong(statement, 2, title.getPureId());
                statement.setString(3, title.getTitle());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void replaceIssns(Connection connection, Long journalId, List<JournalIssnData> issns)
            throws SQLException {
        if (!tableExists("pure_journal_issn")) {
            return;
        }
        executeDelete(connection, "pure_journal_issn", journalId);
        if (issns == null || issns.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO """ + " " + qualified("pure_journal_issn") + """
                 (journal_id, pure_id, issn) VALUES (?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (JournalIssnData issn : issns) {
                statement.setLong(1, journalId);
                setLong(statement, 2, issn.getPureId());
                statement.setString(3, issn.getIssn());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void replaceIdentifiers(Connection connection, Long journalId, List<JournalIdentifierData> identifiers)
            throws SQLException {
        if (!tableExists("pure_journal_identifier")) {
            return;
        }
        executeDelete(connection, "pure_journal_identifier", journalId);
        if (identifiers == null || identifiers.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO """ + " " + qualified("pure_journal_identifier") + """
                 (journal_id, pure_id, type_discriminator, id_source, identifier_value, type_uri, type_en, type_es)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (JournalIdentifierData identifier : identifiers) {
                statement.setLong(1, journalId);
                setLong(statement, 2, identifier.getPureId());
                statement.setString(3, identifier.getTypeDiscriminator());
                statement.setString(4, identifier.getIdSource());
                statement.setString(5, identifier.getValue());
                statement.setString(6, identifier.getTypeUri());
                statement.setString(7, identifier.getTypeEn());
                statement.setString(8, identifier.getTypeEs());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void replaceKeywordGroups(
            Connection connection,
            Long journalId,
            List<JournalKeywordGroupData> groups
    ) throws SQLException {
        if (!tableExists("pure_journal_keyword_group")) {
            return;
        }
        if (tableExists("pure_journal_classification")) {
            executeDeleteByParent(connection, "pure_journal_classification", "pure_journal_keyword_group", journalId);
        }
        if (tableExists("pure_journal_keyword")) {
            executeDeleteByParent(connection, "pure_journal_keyword", "pure_journal_keyword_group", journalId);
        }
        executeDelete(connection, "pure_journal_keyword_group", journalId);
        if (groups == null || groups.isEmpty()) {
            return;
        }
        for (JournalKeywordGroupData group : groups) {
            Long groupId = insertGroup(connection, journalId, group);
            insertClassifications(connection, groupId, group.getClassifications());
            insertKeywords(connection, groupId, group.getKeywords());
        }
    }

    private Long insertGroup(Connection connection, Long journalId, JournalKeywordGroupData group)
            throws SQLException {
        String sql = """
                INSERT INTO """ + " " + qualified("pure_journal_keyword_group") + """
                 (journal_id, pure_id, type_discriminator, logical_name, name_en, name_es)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, journalId);
            setLong(statement, 2, group.getPureId());
            statement.setString(3, group.getTypeDiscriminator());
            statement.setString(4, group.getLogicalName());
            statement.setString(5, group.getNameEn());
            statement.setString(6, group.getNameEs());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("Unable to persist journal keyword group");
    }

    private void insertClassifications(
            Connection connection,
            Long groupId,
            List<JournalClassificationData> classifications
    ) throws SQLException {
        if (!tableExists("pure_journal_classification") || classifications == null || classifications.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO """ + " " + qualified("pure_journal_classification") + """
                 (keyword_group_id, uri, term_en, term_es) VALUES (?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (JournalClassificationData item : classifications) {
                statement.setLong(1, groupId);
                statement.setString(2, item.getUri());
                statement.setString(3, item.getTermEn());
                statement.setString(4, item.getTermEs());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertKeywords(Connection connection, Long groupId, List<JournalKeywordData> keywords)
            throws SQLException {
        if (!tableExists("pure_journal_keyword") || keywords == null || keywords.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO """ + " " + qualified("pure_journal_keyword") + """
                 (keyword_group_id, pure_id, state, origin, keyword_uri, keyword_en, keyword_es)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (JournalKeywordData item : keywords) {
                statement.setLong(1, groupId);
                setLong(statement, 2, item.getPureId());
                statement.setString(3, item.getState());
                statement.setString(4, item.getOrigin());
                statement.setString(5, item.getKeywordUri());
                statement.setString(6, item.getKeywordEn());
                statement.setString(7, item.getKeywordEs());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void loadTitles(Connection connection, List<Long> ids, Map<String, JournalSnapshot> byUuid)
            throws SQLException {
        Map<Long, JournalSnapshot> byId = byId(byUuid);
        String sql = """
                SELECT journal_id, pure_id, title
                FROM """ + " " + qualified("pure_journal_title") + """
                
                WHERE journal_id IN (""" + inPlaceholders(ids.size()) + ") ORDER BY id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindLongs(statement, ids);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    JournalSnapshot snapshot = byId.get(rs.getLong("journal_id"));
                    if (snapshot == null) {
                        continue;
                    }
                    JournalTitleData title = new JournalTitleData();
                    title.setPureId(nullableLong(rs, "pure_id"));
                    title.setTitle(rs.getString("title"));
                    snapshot.titles.add(title);
                }
            }
        }
    }

    private void loadIssns(Connection connection, List<Long> ids, Map<String, JournalSnapshot> byUuid)
            throws SQLException {
        Map<Long, JournalSnapshot> byId = byId(byUuid);
        String sql = """
                SELECT journal_id, pure_id, issn
                FROM """ + " " + qualified("pure_journal_issn") + """
                
                WHERE journal_id IN (""" + inPlaceholders(ids.size()) + ") ORDER BY id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindLongs(statement, ids);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    JournalSnapshot snapshot = byId.get(rs.getLong("journal_id"));
                    if (snapshot == null) {
                        continue;
                    }
                    JournalIssnData issn = new JournalIssnData();
                    issn.setPureId(nullableLong(rs, "pure_id"));
                    issn.setIssn(rs.getString("issn"));
                    snapshot.issns.add(issn);
                }
            }
        }
    }

    private void loadIdentifiers(Connection connection, List<Long> ids, Map<String, JournalSnapshot> byUuid)
            throws SQLException {
        Map<Long, JournalSnapshot> byId = byId(byUuid);
        String sql = """
                SELECT journal_id, pure_id, type_discriminator, id_source, identifier_value, type_uri, type_en, type_es
                FROM """ + " " + qualified("pure_journal_identifier") + """
                
                WHERE journal_id IN (""" + inPlaceholders(ids.size()) + ") ORDER BY id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindLongs(statement, ids);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    JournalSnapshot snapshot = byId.get(rs.getLong("journal_id"));
                    if (snapshot == null) {
                        continue;
                    }
                    JournalIdentifierData identifier = new JournalIdentifierData();
                    identifier.setPureId(nullableLong(rs, "pure_id"));
                    identifier.setTypeDiscriminator(rs.getString("type_discriminator"));
                    identifier.setIdSource(rs.getString("id_source"));
                    identifier.setValue(rs.getString("identifier_value"));
                    identifier.setTypeUri(rs.getString("type_uri"));
                    identifier.setTypeEn(rs.getString("type_en"));
                    identifier.setTypeEs(rs.getString("type_es"));
                    snapshot.identifiers.add(identifier);
                }
            }
        }
    }

    private void loadKeywordGroups(Connection connection, List<Long> ids, Map<String, JournalSnapshot> byUuid)
            throws SQLException {
        Map<Long, JournalSnapshot> byId = byId(byUuid);
        Map<Long, JournalKeywordGroupData> groups = new LinkedHashMap<>();
        String sql = """
                SELECT id, journal_id, pure_id, type_discriminator, logical_name, name_en, name_es
                FROM """ + " " + qualified("pure_journal_keyword_group") + """
                
                WHERE journal_id IN (""" + inPlaceholders(ids.size()) + ") ORDER BY id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindLongs(statement, ids);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    JournalSnapshot snapshot = byId.get(rs.getLong("journal_id"));
                    if (snapshot == null) {
                        continue;
                    }
                    JournalKeywordGroupData group = new JournalKeywordGroupData();
                    group.setPureId(nullableLong(rs, "pure_id"));
                    group.setTypeDiscriminator(rs.getString("type_discriminator"));
                    group.setLogicalName(rs.getString("logical_name"));
                    group.setNameEn(rs.getString("name_en"));
                    group.setNameEs(rs.getString("name_es"));
                    snapshot.keywordGroups.add(group);
                    groups.put(rs.getLong("id"), group);
                }
            }
        }
        if (groups.isEmpty()) {
            return;
        }
        List<Long> groupIds = new ArrayList<>(groups.keySet());
        if (tableExists("pure_journal_classification")) {
            String classSql = """
                    SELECT keyword_group_id, uri, term_en, term_es
                    FROM """ + " " + qualified("pure_journal_classification") + """
                    
                    WHERE keyword_group_id IN (""" + inPlaceholders(groupIds.size()) + ")";
            try (PreparedStatement statement = connection.prepareStatement(classSql)) {
                bindLongs(statement, groupIds);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        JournalKeywordGroupData group = groups.get(rs.getLong("keyword_group_id"));
                        if (group == null) {
                            continue;
                        }
                        JournalClassificationData item = new JournalClassificationData();
                        item.setUri(rs.getString("uri"));
                        item.setTermEn(rs.getString("term_en"));
                        item.setTermEs(rs.getString("term_es"));
                        group.getClassifications().add(item);
                    }
                }
            }
        }
        if (tableExists("pure_journal_keyword")) {
            String keywordSql = """
                    SELECT keyword_group_id, pure_id, state, origin, keyword_uri, keyword_en, keyword_es
                    FROM """ + " " + qualified("pure_journal_keyword") + """
                    
                    WHERE keyword_group_id IN (""" + inPlaceholders(groupIds.size()) + ")";
            try (PreparedStatement statement = connection.prepareStatement(keywordSql)) {
                bindLongs(statement, groupIds);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        JournalKeywordGroupData group = groups.get(rs.getLong("keyword_group_id"));
                        if (group == null) {
                            continue;
                        }
                        JournalKeywordData item = new JournalKeywordData();
                        item.setPureId(nullableLong(rs, "pure_id"));
                        item.setState(rs.getString("state"));
                        item.setOrigin(rs.getString("origin"));
                        item.setKeywordUri(rs.getString("keyword_uri"));
                        item.setKeywordEn(rs.getString("keyword_en"));
                        item.setKeywordEs(rs.getString("keyword_es"));
                        group.getKeywords().add(item);
                    }
                }
            }
        }
    }

    private JournalSnapshot mapParent(ResultSet rs) throws SQLException {
        JournalSnapshot snapshot = new JournalSnapshot();
        snapshot.id = rs.getLong("id");
        snapshot.uuid = rs.getString("uuid");
        snapshot.pureId = nullableLong(rs, "pure_id");
        snapshot.createdBy = nullableString(rs, "created_by");
        snapshot.createdDatePure = offset(rs.getTimestamp("created_date_pure"));
        snapshot.modifiedBy = nullableString(rs, "modified_by");
        snapshot.modifiedDatePure = offset(rs.getTimestamp("modified_date_pure"));
        snapshot.version = nullableString(rs, "pure_version");
        snapshot.publisherUuid = nullableString(rs, "publisher_uuid");
        snapshot.publisherSystemName = nullableString(rs, "publisher_system_name");
        snapshot.typeUri = nullableString(rs, "type_uri");
        snapshot.typeEn = nullableString(rs, "type_en");
        snapshot.typeEs = nullableString(rs, "type_es");
        snapshot.workflowStep = nullableString(rs, "workflow_step");
        snapshot.workflowDescriptionEn = nullableString(rs, "workflow_description_en");
        snapshot.workflowDescriptionEs = nullableString(rs, "workflow_description_es");
        snapshot.systemName = nullableString(rs, "system_name");
        if (hasColumn(rs, "active_in_pure")) {
            snapshot.activeInPure = nullableBoolean(rs, "active_in_pure");
        }
        return snapshot;
    }

    private void executeDelete(Connection connection, String table, Long journalId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM " + qualified(table) + " WHERE journal_id = ?")) {
            statement.setLong(1, journalId);
            statement.executeUpdate();
        }
    }

    private void executeDeleteByParent(Connection connection, String child, String parent, Long journalId)
            throws SQLException {
        String sql = "DELETE FROM " + qualified(child)
                + " WHERE keyword_group_id IN (SELECT id FROM " + qualified(parent) + " WHERE journal_id = ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, journalId);
            statement.executeUpdate();
        }
    }

    private static Map<Long, JournalSnapshot> byId(Map<String, JournalSnapshot> byUuid) {
        Map<Long, JournalSnapshot> byId = new LinkedHashMap<>();
        for (JournalSnapshot snapshot : byUuid.values()) {
            byId.put(snapshot.id, snapshot);
        }
        return byId;
    }

    private static void bindLongs(PreparedStatement statement, List<Long> ids) throws SQLException {
        for (int i = 0; i < ids.size(); i++) {
            statement.setLong(i + 1, ids.get(i));
        }
    }

    private static void setLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    private static void setTimestamp(PreparedStatement statement, int index, OffsetDateTime value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP);
        } else {
            statement.setTimestamp(index, Timestamp.from(value.toInstant()));
        }
    }

    private static OffsetDateTime offset(Timestamp value) {
        return value == null ? null : OffsetDateTime.ofInstant(value.toInstant(), ZoneOffset.UTC);
    }

    private static boolean hasColumn(ResultSet rs, String column) {
        try {
            rs.findColumn(column);
            return true;
        } catch (SQLException ex) {
            return false;
        }
    }

    private static boolean isUnknownColumn(SQLException ex) {
        return ex.getMessage() != null && ex.getMessage().toLowerCase().contains("unknown column");
    }
}
