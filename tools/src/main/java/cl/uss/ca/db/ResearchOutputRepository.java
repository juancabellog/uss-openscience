package cl.uss.ca.db;

import cl.uss.ca.pure.ro.ElectronicVersion;
import cl.uss.ca.pure.ro.JournalData;
import cl.uss.ca.pure.ro.KeywordData;
import cl.uss.ca.pure.ro.KeywordGroupData;
import cl.uss.ca.pure.ro.OutputContributor;
import cl.uss.ca.pure.ro.OutputIdentifier;
import cl.uss.ca.pure.ro.OutputLink;
import cl.uss.ca.pure.ro.OutputStatus;
import cl.uss.ca.pure.ro.ResearchOutput;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public class ResearchOutputRepository {
    private static final String UPSERT_OUTPUT_SQL = """
            INSERT INTO pure_research_output (
                pure_id, uuid, type_discriminator, title,
                type_uri, type_es, type_en,
                category_uri, category_es, category_en,
                peer_review,
                language_uri, language_es, language_en,
                submission_year, total_number_of_contributors, managing_organization_uuid,
                pages, volume, journal_number, number_of_pages,
                abstract_es, abstract_en, portal_url,
                visibility_key, visibility_es, visibility_en,
                workflow_step, workflow_es, workflow_en,
                created_date, modified_date, version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                id = LAST_INSERT_ID(id),
                pure_id = VALUES(pure_id),
                type_discriminator = VALUES(type_discriminator),
                title = VALUES(title),
                type_uri = VALUES(type_uri),
                type_es = VALUES(type_es),
                type_en = VALUES(type_en),
                category_uri = VALUES(category_uri),
                category_es = VALUES(category_es),
                category_en = VALUES(category_en),
                peer_review = VALUES(peer_review),
                language_uri = VALUES(language_uri),
                language_es = VALUES(language_es),
                language_en = VALUES(language_en),
                submission_year = VALUES(submission_year),
                total_number_of_contributors = VALUES(total_number_of_contributors),
                managing_organization_uuid = VALUES(managing_organization_uuid),
                pages = VALUES(pages),
                volume = VALUES(volume),
                journal_number = VALUES(journal_number),
                number_of_pages = VALUES(number_of_pages),
                abstract_es = VALUES(abstract_es),
                abstract_en = VALUES(abstract_en),
                portal_url = VALUES(portal_url),
                visibility_key = VALUES(visibility_key),
                visibility_es = VALUES(visibility_es),
                visibility_en = VALUES(visibility_en),
                workflow_step = VALUES(workflow_step),
                workflow_es = VALUES(workflow_es),
                workflow_en = VALUES(workflow_en),
                created_date = VALUES(created_date),
                modified_date = VALUES(modified_date),
                version = VALUES(version)
            """;

    private static final String SELECT_OUTPUT_ID_SQL = "SELECT id FROM pure_research_output WHERE uuid = ?";

    private static final String DELETE_CONTRIBUTOR_ORGS_SQL = """
            DELETE FROM pure_research_output_contributor_organization
            WHERE contributor_id IN (
                SELECT id FROM pure_research_output_contributor WHERE research_output_id = ?
            )
            """;
    private static final String DELETE_KEYWORDS_SQL = """
            DELETE FROM pure_research_output_keyword
            WHERE keyword_group_id IN (
                SELECT id FROM pure_research_output_keyword_group WHERE research_output_id = ?
            )
            """;
    private static final String DELETE_STATUSES_SQL =
            "DELETE FROM pure_research_output_status WHERE research_output_id = ?";
    private static final String DELETE_CONTRIBUTORS_SQL =
            "DELETE FROM pure_research_output_contributor WHERE research_output_id = ?";
    private static final String DELETE_EVERSIONS_SQL =
            "DELETE FROM pure_research_output_electronic_version WHERE research_output_id = ?";
    private static final String DELETE_IDENTIFIERS_SQL =
            "DELETE FROM pure_research_output_identifier WHERE research_output_id = ?";
    private static final String DELETE_LINKS_SQL =
            "DELETE FROM pure_research_output_link WHERE research_output_id = ?";
    private static final String DELETE_RO_JOURNAL_SQL =
            "DELETE FROM pure_research_output_journal WHERE research_output_id = ?";
    private static final String DELETE_EXT_ORGS_SQL =
            "DELETE FROM pure_research_output_external_organization WHERE research_output_id = ?";
    private static final String DELETE_KEYWORD_GROUPS_SQL =
            "DELETE FROM pure_research_output_keyword_group WHERE research_output_id = ?";

    private static final String INSERT_STATUS_SQL = """
            INSERT INTO pure_research_output_status (
                research_output_id, pure_id, current_status,
                status_uri, status_es, status_en,
                publication_year, publication_month, publication_day
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String INSERT_CONTRIBUTOR_SQL = """
            INSERT INTO pure_research_output_contributor (
                research_output_id, pure_id, type_discriminator,
                first_name, last_name,
                role_uri, role_es, role_en,
                person_uuid, external_person_uuid,
                hidden, corresponding_author, association_hidden
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String INSERT_CONTRIBUTOR_ORG_SQL = """
            INSERT INTO pure_research_output_contributor_organization (
                contributor_id, organization_uuid
            ) VALUES (?, ?)
            """;

    private static final String INSERT_EVERSION_SQL = """
            INSERT INTO pure_research_output_electronic_version (
                research_output_id, pure_id, type_discriminator, doi,
                access_type_uri, access_type_es, access_type_en,
                version_type_uri, version_type_es, version_type_en
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String UPSERT_IDENTIFIER_SQL = """
            INSERT INTO pure_research_output_identifier (
                research_output_id, pure_id, type_discriminator, id_source, value
            ) VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                pure_id = VALUES(pure_id),
                type_discriminator = VALUES(type_discriminator)
            """;

    private static final String INSERT_LINK_SQL = """
            INSERT INTO pure_research_output_link (
                research_output_id, pure_id, url,
                description_es, description_en,
                link_type_uri, link_type_es, link_type_en
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String UPSERT_JOURNAL_BY_UUID_SQL = """
            INSERT INTO pure_journal (uuid, title_pure_id, title, issn_pure_id, issn)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                id = LAST_INSERT_ID(id),
                title_pure_id = VALUES(title_pure_id),
                title = VALUES(title),
                issn_pure_id = VALUES(issn_pure_id),
                issn = VALUES(issn)
            """;

    private static final String SELECT_JOURNAL_BY_ISSN_SQL =
            "SELECT id FROM pure_journal WHERE issn = ? LIMIT 1";
    private static final String SELECT_JOURNAL_BY_UUID_SQL =
            "SELECT id FROM pure_journal WHERE uuid = ? LIMIT 1";
    private static final String INSERT_JOURNAL_SQL = """
            INSERT INTO pure_journal (uuid, title_pure_id, title, issn_pure_id, issn)
            VALUES (?, ?, ?, ?, ?)
            """;
    private static final String UPDATE_JOURNAL_SQL = """
            UPDATE pure_journal
            SET title_pure_id = ?, title = ?, issn_pure_id = ?, issn = ?
            WHERE id = ?
            """;

    private static final String INSERT_RO_JOURNAL_SQL = """
            INSERT INTO pure_research_output_journal (research_output_id, journal_id, pure_id)
            VALUES (?, ?, ?)
            """;

    private static final String INSERT_EXT_ORG_SQL = """
            INSERT INTO pure_research_output_external_organization (
                research_output_id, organization_uuid
            ) VALUES (?, ?)
            """;

    private static final String INSERT_KEYWORD_GROUP_SQL = """
            INSERT INTO pure_research_output_keyword_group (
                research_output_id, pure_id, type_discriminator, logical_name, name_es, name_en
            ) VALUES (?, ?, ?, ?, ?, ?)
            """;

    private static final String INSERT_KEYWORD_SQL = """
            INSERT INTO pure_research_output_keyword (
                keyword_group_id, container_pure_id, keyword_type,
                state, origin, keyword_uri, keyword_es, keyword_en,
                locale, free_keyword
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final String url;
    private final String user;
    private final String password;
    private final DataSource dataSource;

    public ResearchOutputRepository(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
        this.dataSource = null;
    }

    public ResearchOutputRepository(DataSource dataSource) {
        this.url = null;
        this.user = null;
        this.password = null;
        this.dataSource = dataSource;
    }

    public Connection abrirConexion() throws SQLException {
        Connection connection = dataSource != null
                ? dataSource.getConnection()
                : DriverManager.getConnection(url, user, password);
        connection.setAutoCommit(false);
        return connection;
    }

    public boolean existsByUuid(Connection connection, String uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_OUTPUT_ID_SQL)) {
            statement.setString(1, uuid);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    public int guardarResearchOutput(Connection connection, ResearchOutput output) throws SQLException {
        Long researchOutputId = upsertResearchOutput(connection, output);
        eliminarHijos(connection, researchOutputId);
        guardarPublicationStatuses(connection, researchOutputId, output.getStatuses());
        guardarContributors(connection, researchOutputId, output.getContributors());
        guardarElectronicVersions(connection, researchOutputId, output.getElectronicVersions());
        guardarIdentifiers(connection, researchOutputId, output.getIdentifiers());
        guardarLinks(connection, researchOutputId, output.getLinks());
        int journals = guardarJournal(connection, researchOutputId, output);
        guardarExternalOrganizations(connection, researchOutputId, output.getExternalOrganizationUuids());
        guardarKeywordGroups(connection, researchOutputId, output.getKeywordGroups());
        return journals;
    }

    Long upsertResearchOutput(Connection connection, ResearchOutput output) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                UPSERT_OUTPUT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            bindResearchOutput(statement, output);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    if (id > 0) {
                        return id;
                    }
                }
            }
        }
        return buscarIdPorUuid(connection, output.getUuid());
    }

    void eliminarHijos(Connection connection, Long researchOutputId) throws SQLException {
        ejecutarDelete(connection, DELETE_CONTRIBUTOR_ORGS_SQL, researchOutputId);
        ejecutarDelete(connection, DELETE_KEYWORDS_SQL, researchOutputId);
        ejecutarDelete(connection, DELETE_STATUSES_SQL, researchOutputId);
        ejecutarDelete(connection, DELETE_CONTRIBUTORS_SQL, researchOutputId);
        ejecutarDelete(connection, DELETE_EVERSIONS_SQL, researchOutputId);
        ejecutarDelete(connection, DELETE_IDENTIFIERS_SQL, researchOutputId);
        ejecutarDelete(connection, DELETE_LINKS_SQL, researchOutputId);
        ejecutarDelete(connection, DELETE_RO_JOURNAL_SQL, researchOutputId);
        ejecutarDelete(connection, DELETE_EXT_ORGS_SQL, researchOutputId);
        ejecutarDelete(connection, DELETE_KEYWORD_GROUPS_SQL, researchOutputId);
    }

    void guardarPublicationStatuses(Connection connection, Long researchOutputId,
                                    List<OutputStatus> statuses) throws SQLException {
        if (statuses == null || statuses.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(INSERT_STATUS_SQL)) {
            for (OutputStatus status : statuses) {
                statement.setLong(1, researchOutputId);
                JdbcValues.setLong(statement, 2, status.getPureId());
                JdbcValues.setNullableBoolean(statement, 3, status.getCurrentStatus());
                statement.setString(4, status.getStatusUri());
                statement.setString(5, status.getStatusEs());
                statement.setString(6, status.getStatusEn());
                JdbcValues.setInt(statement, 7, status.getPublicationYear());
                JdbcValues.setInt(statement, 8, status.getPublicationMonth());
                JdbcValues.setInt(statement, 9, status.getPublicationDay());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    void guardarContributors(Connection connection, Long researchOutputId,
                             List<OutputContributor> contributors) throws SQLException {
        if (contributors == null || contributors.isEmpty()) {
            return;
        }
        for (OutputContributor contributor : contributors) {
            Long contributorId = insertarContributor(connection, researchOutputId, contributor);
            guardarContributorOrganizations(connection, contributorId, contributor.getOrganizationUuids());
        }
    }

    void guardarContributorOrganizations(Connection connection, Long contributorId,
                                         List<String> organizationUuids) throws SQLException {
        if (contributorId == null || organizationUuids == null || organizationUuids.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(INSERT_CONTRIBUTOR_ORG_SQL)) {
            for (String uuid : organizationUuids) {
                if (uuid == null || uuid.isBlank()) {
                    continue;
                }
                statement.setLong(1, contributorId);
                statement.setString(2, uuid);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    void guardarElectronicVersions(Connection connection, Long researchOutputId,
                                   List<ElectronicVersion> versions) throws SQLException {
        if (versions == null || versions.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(INSERT_EVERSION_SQL)) {
            for (ElectronicVersion version : versions) {
                statement.setLong(1, researchOutputId);
                JdbcValues.setLong(statement, 2, version.getPureId());
                statement.setString(3, version.getTypeDiscriminator());
                statement.setString(4, version.getDoi());
                statement.setString(5, version.getAccessTypeUri());
                statement.setString(6, version.getAccessTypeEs());
                statement.setString(7, version.getAccessTypeEn());
                statement.setString(8, version.getVersionTypeUri());
                statement.setString(9, version.getVersionTypeEs());
                statement.setString(10, version.getVersionTypeEn());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    void guardarIdentifiers(Connection connection, Long researchOutputId,
                            List<OutputIdentifier> identifiers) throws SQLException {
        if (identifiers == null || identifiers.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_IDENTIFIER_SQL)) {
            for (OutputIdentifier identifier : identifiers) {
                if (identifier.getValue() == null) {
                    continue;
                }
                statement.setLong(1, researchOutputId);
                JdbcValues.setLong(statement, 2, identifier.getPureId());
                statement.setString(3, identifier.getTypeDiscriminator());
                statement.setString(4, identifier.getIdSource());
                statement.setString(5, identifier.getValue());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    void guardarLinks(Connection connection, Long researchOutputId,
                      List<OutputLink> links) throws SQLException {
        if (links == null || links.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(INSERT_LINK_SQL)) {
            for (OutputLink link : links) {
                statement.setLong(1, researchOutputId);
                JdbcValues.setLong(statement, 2, link.getPureId());
                statement.setString(3, link.getUrl());
                statement.setString(4, link.getDescriptionEs());
                statement.setString(5, link.getDescriptionEn());
                statement.setString(6, link.getLinkTypeUri());
                statement.setString(7, link.getLinkTypeEs());
                statement.setString(8, link.getLinkTypeEn());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    int guardarJournal(Connection connection, Long researchOutputId, ResearchOutput output)
            throws SQLException {
        if (output.getJournal() == null) {
            return 0;
        }
        Long journalId = upsertJournal(connection, output.getJournal());
        try (PreparedStatement statement = connection.prepareStatement(INSERT_RO_JOURNAL_SQL)) {
            statement.setLong(1, researchOutputId);
            statement.setLong(2, journalId);
            JdbcValues.setLong(statement, 3, output.getJournalAssociationPureId());
            statement.executeUpdate();
        }
        return 1;
    }

    void guardarExternalOrganizations(Connection connection, Long researchOutputId,
                                      List<String> organizationUuids) throws SQLException {
        if (organizationUuids == null || organizationUuids.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(INSERT_EXT_ORG_SQL)) {
            for (String uuid : organizationUuids) {
                statement.setLong(1, researchOutputId);
                statement.setString(2, uuid);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    void guardarKeywordGroups(Connection connection, Long researchOutputId,
                              List<KeywordGroupData> groups) throws SQLException {
        if (groups == null || groups.isEmpty()) {
            return;
        }
        for (KeywordGroupData group : groups) {
            Long groupId = insertarKeywordGroup(connection, researchOutputId, group);
            guardarKeywords(connection, groupId, group.getKeywords());
        }
    }

    void guardarKeywords(Connection connection, Long keywordGroupId,
                         List<KeywordData> keywords) throws SQLException {
        if (keywordGroupId == null || keywords == null || keywords.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(INSERT_KEYWORD_SQL)) {
            for (KeywordData keyword : keywords) {
                statement.setLong(1, keywordGroupId);
                JdbcValues.setLong(statement, 2, keyword.getContainerPureId());
                statement.setString(3, keyword.getKeywordType());
                statement.setString(4, keyword.getState());
                statement.setString(5, keyword.getOrigin());
                statement.setString(6, keyword.getKeywordUri());
                statement.setString(7, keyword.getKeywordEs());
                statement.setString(8, keyword.getKeywordEn());
                statement.setString(9, keyword.getLocale());
                statement.setString(10, keyword.getFreeKeyword());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private Long insertarContributor(Connection connection, Long researchOutputId,
                                     OutputContributor contributor) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                INSERT_CONTRIBUTOR_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, researchOutputId);
            JdbcValues.setLong(statement, 2, contributor.getPureId());
            statement.setString(3, contributor.getTypeDiscriminator());
            statement.setString(4, contributor.getFirstName());
            statement.setString(5, contributor.getLastName());
            statement.setString(6, contributor.getRoleUri());
            statement.setString(7, contributor.getRoleEs());
            statement.setString(8, contributor.getRoleEn());
            statement.setString(9, contributor.getPersonUuid());
            statement.setString(10, contributor.getExternalPersonUuid());
            JdbcValues.setNullableBoolean(statement, 11, contributor.getHidden());
            JdbcValues.setNullableBoolean(statement, 12, contributor.getCorrespondingAuthor());
            JdbcValues.setNullableBoolean(statement, 13, contributor.getAssociationHidden());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("No se pudo obtener contributor_id para research_output_id=" + researchOutputId);
    }

    private Long insertarKeywordGroup(Connection connection, Long researchOutputId,
                                      KeywordGroupData group) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                INSERT_KEYWORD_GROUP_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, researchOutputId);
            JdbcValues.setLong(statement, 2, group.getPureId());
            statement.setString(3, group.getTypeDiscriminator());
            statement.setString(4, group.getLogicalName());
            statement.setString(5, group.getNameEs());
            statement.setString(6, group.getNameEn());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("No se pudo obtener keyword_group_id para research_output_id=" + researchOutputId);
    }

    private Long upsertJournal(Connection connection, JournalData journal) throws SQLException {
        if (journal.getUuid() != null) {
            try (PreparedStatement statement = connection.prepareStatement(
                    UPSERT_JOURNAL_BY_UUID_SQL, Statement.RETURN_GENERATED_KEYS)) {
                bindJournal(statement, journal);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next() && keys.getLong(1) > 0) {
                        return keys.getLong(1);
                    }
                }
            }
            Long id = buscarJournalId(connection, SELECT_JOURNAL_BY_UUID_SQL, journal.getUuid());
            if (id != null) {
                return id;
            }
        }
        if (journal.getIssn() != null) {
            Long existing = buscarJournalId(connection, SELECT_JOURNAL_BY_ISSN_SQL, journal.getIssn());
            if (existing != null) {
                try (PreparedStatement statement = connection.prepareStatement(UPDATE_JOURNAL_SQL)) {
                    JdbcValues.setLong(statement, 1, journal.getTitlePureId());
                    statement.setString(2, journal.getTitle());
                    JdbcValues.setLong(statement, 3, journal.getIssnPureId());
                    statement.setString(4, journal.getIssn());
                    statement.setLong(5, existing);
                    statement.executeUpdate();
                }
                return existing;
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                INSERT_JOURNAL_SQL, Statement.RETURN_GENERATED_KEYS)) {
            bindJournal(statement, journal);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("No se pudo persistir pure_journal uuid=" + journal.getUuid());
    }

    private static void bindJournal(PreparedStatement statement, JournalData journal) throws SQLException {
        statement.setString(1, journal.getUuid());
        JdbcValues.setLong(statement, 2, journal.getTitlePureId());
        statement.setString(3, journal.getTitle());
        JdbcValues.setLong(statement, 4, journal.getIssnPureId());
        statement.setString(5, journal.getIssn());
    }

    private static Long buscarJournalId(Connection connection, String sql, String valor) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, valor);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getLong(1);
                }
            }
        }
        return null;
    }

    private static void bindResearchOutput(PreparedStatement statement, ResearchOutput output)
            throws SQLException {
        JdbcValues.setLong(statement, 1, output.getPureId());
        statement.setString(2, output.getUuid());
        statement.setString(3, output.getTypeDiscriminator());
        statement.setString(4, output.getTitle());
        statement.setString(5, output.getTypeUri());
        statement.setString(6, output.getTypeEs());
        statement.setString(7, output.getTypeEn());
        statement.setString(8, output.getCategoryUri());
        statement.setString(9, output.getCategoryEs());
        statement.setString(10, output.getCategoryEn());
        JdbcValues.setNullableBoolean(statement, 11, output.getPeerReview());
        statement.setString(12, output.getLanguageUri());
        statement.setString(13, output.getLanguageEs());
        statement.setString(14, output.getLanguageEn());
        JdbcValues.setInt(statement, 15, output.getSubmissionYear());
        JdbcValues.setInt(statement, 16, output.getTotalNumberOfContributors());
        statement.setString(17, output.getManagingOrganizationUuid());
        statement.setString(18, output.getPages());
        statement.setString(19, output.getVolume());
        statement.setString(20, output.getJournalNumber());
        JdbcValues.setInt(statement, 21, output.getNumberOfPages());
        statement.setString(22, output.getAbstractEs());
        statement.setString(23, output.getAbstractEn());
        statement.setString(24, output.getPortalUrl());
        statement.setString(25, output.getVisibilityKey());
        statement.setString(26, output.getVisibilityEs());
        statement.setString(27, output.getVisibilityEn());
        statement.setString(28, output.getWorkflowStep());
        statement.setString(29, output.getWorkflowEs());
        statement.setString(30, output.getWorkflowEn());
        JdbcValues.setTimestamp(statement, 31, output.getCreatedDate());
        JdbcValues.setTimestamp(statement, 32, output.getModifiedDate());
        statement.setString(33, output.getVersion());
    }

    private static void ejecutarDelete(Connection connection, String sql, Long researchOutputId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, researchOutputId);
            statement.executeUpdate();
        }
    }

    private static Long buscarIdPorUuid(Connection connection, String uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_OUTPUT_ID_SQL)) {
            statement.setString(1, uuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getLong("id");
                }
            }
        }
        throw new SQLException("No se pudo resolver el id interno de pure_research_output uuid=" + uuid);
    }
}
