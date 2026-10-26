package cl.uss.ca.repository;

import cl.uss.ca.external.pure.model.ElectronicVersion;
import cl.uss.ca.external.pure.model.JournalData;
import cl.uss.ca.external.pure.model.KeywordData;
import cl.uss.ca.external.pure.model.KeywordGroupData;
import cl.uss.ca.external.pure.model.OutputContributor;
import cl.uss.ca.external.pure.model.OutputIdentifier;
import cl.uss.ca.external.pure.model.OutputLink;
import cl.uss.ca.external.pure.model.OutputStatus;
import cl.uss.ca.external.pure.model.ResearchOutput;
import cl.uss.ca.sync.diff.AggregateOutcome;
import cl.uss.ca.sync.diff.ResearchOutputSnapshot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ResearchOutputSyncRepository extends JdbcRepository {
    private static final Logger LOG = Logger.getLogger(ResearchOutputSyncRepository.class.getName());
    private static final String UPSERT_OUTPUT_SQL = """
            INSERT INTO """ + " " + qualified("pure_research_output") + """
             (
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
                created_by, modified_by, created_date, modified_date, version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                created_by = VALUES(created_by),
                modified_by = VALUES(modified_by),
                created_date = VALUES(created_date),
                modified_date = VALUES(modified_date),
                version = VALUES(version)
            """;

    private static final String SELECT_OUTPUT_ID_SQL =
            "SELECT id FROM " + qualified("pure_research_output") + " WHERE uuid = ?";

    private static final String DELETE_CONTRIBUTOR_ORGS_SQL = """
            DELETE FROM """ + " " + qualified("pure_research_output_contributor_organization") + """
            
            WHERE contributor_id IN (
                SELECT id FROM """ + " " + qualified("pure_research_output_contributor") + """
             WHERE research_output_id = ?
            )
            """;
    private static final String DELETE_KEYWORDS_SQL = """
            DELETE FROM """ + " " + qualified("pure_research_output_keyword") + """
            
            WHERE keyword_group_id IN (
                SELECT id FROM """ + " " + qualified("pure_research_output_keyword_group") + """
             WHERE research_output_id = ?
            )
            """;
    private static final String DELETE_STATUSES_SQL =
            "DELETE FROM " + qualified("pure_research_output_status") + " WHERE research_output_id = ?";
    private static final String DELETE_CONTRIBUTORS_SQL =
            "DELETE FROM " + qualified("pure_research_output_contributor") + " WHERE research_output_id = ?";
    private static final String DELETE_EVERSIONS_SQL =
            "DELETE FROM " + qualified("pure_research_output_electronic_version") + " WHERE research_output_id = ?";
    private static final String DELETE_IDENTIFIERS_SQL =
            "DELETE FROM " + qualified("pure_research_output_identifier") + " WHERE research_output_id = ?";
    private static final String DELETE_LINKS_SQL =
            "DELETE FROM " + qualified("pure_research_output_link") + " WHERE research_output_id = ?";
    private static final String DELETE_RO_JOURNAL_SQL =
            "DELETE FROM " + qualified("pure_research_output_journal") + " WHERE research_output_id = ?";
    private static final String DELETE_EXT_ORGS_SQL =
            "DELETE FROM " + qualified("pure_research_output_external_organization") + " WHERE research_output_id = ?";
    private static final String DELETE_KEYWORD_GROUPS_SQL =
            "DELETE FROM " + qualified("pure_research_output_keyword_group") + " WHERE research_output_id = ?";

    private static final String INSERT_STATUS_SQL = """
            INSERT INTO """ + " " + qualified("pure_research_output_status") + """
             (
                research_output_id, pure_id, current_status,
                status_uri, status_es, status_en,
                publication_year, publication_month, publication_day
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String INSERT_CONTRIBUTOR_SQL = """
            INSERT INTO """ + " " + qualified("pure_research_output_contributor") + """
             (
                research_output_id, pure_id, type_discriminator,
                first_name, last_name,
                role_uri, role_es, role_en,
                person_uuid, external_person_uuid,
                hidden, corresponding_author, association_hidden
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String INSERT_CONTRIBUTOR_ORG_SQL = """
            INSERT INTO """ + " " + qualified("pure_research_output_contributor_organization") + """
             (
                contributor_id, organization_uuid
            ) VALUES (?, ?)
            """;

    private static final String INSERT_EVERSION_SQL = """
            INSERT INTO """ + " " + qualified("pure_research_output_electronic_version") + """
             (
                research_output_id, pure_id, type_discriminator, doi,
                access_type_uri, access_type_es, access_type_en,
                version_type_uri, version_type_es, version_type_en
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String UPSERT_IDENTIFIER_SQL = """
            INSERT INTO """ + " " + qualified("pure_research_output_identifier") + """
             (
                research_output_id, pure_id, type_discriminator, id_source, value
            ) VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                pure_id = VALUES(pure_id),
                type_discriminator = VALUES(type_discriminator)
            """;

    private static final String INSERT_LINK_SQL = """
            INSERT INTO """ + " " + qualified("pure_research_output_link") + """
             (
                research_output_id, pure_id, url,
                description_es, description_en,
                link_type_uri, link_type_es, link_type_en
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_JOURNAL_BY_UUID_SQL =
            "SELECT id FROM " + qualified("pure_journal") + " WHERE uuid = ? LIMIT 1";

    private static final String INSERT_RO_JOURNAL_SQL = """
            INSERT INTO """ + " " + qualified("pure_research_output_journal") + """
             (research_output_id, journal_id, journal_uuid, pure_id)
            VALUES (?, ?, ?, ?)
            """;

    private static final String INSERT_RO_JOURNAL_LEGACY_SQL = """
            INSERT INTO """ + " " + qualified("pure_research_output_journal") + """
             (research_output_id, journal_id, pure_id)
            VALUES (?, ?, ?)
            """;

    private static final String INSERT_EXT_ORG_SQL = """
            INSERT INTO """ + " " + qualified("pure_research_output_external_organization") + """
             (
                research_output_id, organization_uuid
            ) VALUES (?, ?)
            """;

    private static final String INSERT_KEYWORD_GROUP_SQL = """
            INSERT INTO """ + " " + qualified("pure_research_output_keyword_group") + """
             (
                research_output_id, pure_id, type_discriminator, logical_name, name_es, name_en
            ) VALUES (?, ?, ?, ?, ?, ?)
            """;

    private static final String INSERT_KEYWORD_SQL = """
            INSERT INTO """ + " " + qualified("pure_research_output_keyword") + """
             (
                keyword_group_id, container_pure_id, keyword_type,
                state, origin, keyword_uri, keyword_es, keyword_en,
                locale, free_keyword
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    public Connection begin() throws SQLException {
        Connection connection = connection();
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

    public Map<String, ResearchOutputSnapshot> loadSnapshots(Connection connection, Collection<String> uuids)
            throws SQLException {
        Map<String, ResearchOutputSnapshot> byUuid = new LinkedHashMap<>();
        if (uuids == null || uuids.isEmpty()) {
            return byUuid;
        }
        List<String> values = new ArrayList<>(uuids);
        String sql = """
                SELECT id, pure_id, uuid, type_discriminator, title,
                       type_uri, type_es, type_en,
                       category_uri, category_es, category_en,
                       peer_review, language_uri, language_es, language_en,
                       submission_year, total_number_of_contributors, managing_organization_uuid,
                       pages, volume, journal_number, number_of_pages,
                       abstract_es, abstract_en, portal_url,
                       visibility_key, visibility_es, visibility_en,
                       workflow_step, workflow_es, workflow_en,
                       created_by, modified_by, created_date, modified_date, version
                """ + (columnExists("pure_research_output", "active_in_pure") ? ", active_in_pure" : "") + """
                
                FROM """ + " " + qualified("pure_research_output") + """
                
                WHERE uuid IN (""" + inPlaceholders(values.size()) + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.size(); i++) {
                statement.setString(i + 1, values.get(i));
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ResearchOutputSnapshot snapshot = mapParent(rs);
                    byUuid.put(snapshot.parent.getUuid(), snapshot);
                }
            }
        }
        if (byUuid.isEmpty()) {
            return byUuid;
        }
        List<Long> ids = byUuid.values().stream().map(item -> item.id).toList();
        loadStatuses(connection, ids, byUuid);
        loadContributors(connection, ids, byUuid);
        loadElectronicVersions(connection, ids, byUuid);
        loadIdentifiers(connection, ids, byUuid);
        loadLinks(connection, ids, byUuid);
        loadJournal(connection, ids, byUuid);
        loadExternalOrganizations(connection, ids, byUuid);
        loadKeywordGroups(connection, ids, byUuid);
        return byUuid;
    }

    public Long apply(Connection connection, ResearchOutput output, AggregateOutcome outcome) throws SQLException {
        Long id = outcome.localId;
        if (outcome.persistParent() || id == null) {
            id = upsertResearchOutput(connection, output);
        }
        if (outcome.persistChildren()) {
            eliminarHijos(connection, id);
            guardarPublicationStatuses(connection, id, output.getStatuses());
            guardarContributors(connection, id, output.getContributors());
            guardarElectronicVersions(connection, id, output.getElectronicVersions());
            guardarIdentifiers(connection, id, output.getIdentifiers());
            guardarLinks(connection, id, output.getLinks());
            guardarJournal(connection, id, output);
            guardarExternalOrganizations(connection, id, output.getExternalOrganizationUuids());
            guardarKeywordGroups(connection, id, output.getKeywordGroups());
        }
        return id;
    }

    public int upsert(Connection connection, ResearchOutput output) throws SQLException {
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
                setLong(statement, 2, status.getPureId());
                setNullableBoolean(statement, 3, status.getCurrentStatus());
                statement.setString(4, status.getStatusUri());
                statement.setString(5, status.getStatusEs());
                statement.setString(6, status.getStatusEn());
                setInt(statement, 7, status.getPublicationYear());
                setInt(statement, 8, status.getPublicationMonth());
                setInt(statement, 9, status.getPublicationDay());
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
                setLong(statement, 2, version.getPureId());
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
                setLong(statement, 2, identifier.getPureId());
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
                setLong(statement, 2, link.getPureId());
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
        String journalUuid = firstNonBlank(output.getJournal().getUuid(), null);
        if (journalUuid == null) {
            return 0;
        }
        Long journalId = buscarJournalId(connection, SELECT_JOURNAL_BY_UUID_SQL, journalUuid);
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("researchOutputId=" + researchOutputId
                    + " journalUuid=" + journalUuid
                    + " journalLocalId=" + journalId);
        }
        boolean hasUuidColumn = columnExists("pure_research_output_journal", "journal_uuid");
        String sql = hasUuidColumn ? INSERT_RO_JOURNAL_SQL : INSERT_RO_JOURNAL_LEGACY_SQL;
        if (!hasUuidColumn && journalId == null) {
            return 0;
        }
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, researchOutputId);
            setLong(statement, 2, journalId);
            if (hasUuidColumn) {
                statement.setString(3, journalUuid);
                setLong(statement, 4, output.getJournalAssociationPureId());
            } else {
                setLong(statement, 3, output.getJournalAssociationPureId());
            }
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
                setLong(statement, 2, keyword.getContainerPureId());
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
            setLong(statement, 2, contributor.getPureId());
            statement.setString(3, contributor.getTypeDiscriminator());
            statement.setString(4, contributor.getFirstName());
            statement.setString(5, contributor.getLastName());
            statement.setString(6, contributor.getRoleUri());
            statement.setString(7, contributor.getRoleEs());
            statement.setString(8, contributor.getRoleEn());
            statement.setString(9, contributor.getPersonUuid());
            statement.setString(10, contributor.getExternalPersonUuid());
            setNullableBoolean(statement, 11, contributor.getHidden());
            setNullableBoolean(statement, 12, contributor.getCorrespondingAuthor());
            setNullableBoolean(statement, 13, contributor.getAssociationHidden());
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
            setLong(statement, 2, group.getPureId());
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

    private ResearchOutputSnapshot mapParent(ResultSet rs) throws SQLException {
        ResearchOutputSnapshot snapshot = new ResearchOutputSnapshot();
        snapshot.id = rs.getLong("id");
        ResearchOutput parent = snapshot.parent;
        parent.setPureId(nullableLong(rs, "pure_id"));
        parent.setUuid(rs.getString("uuid"));
        parent.setTypeDiscriminator(rs.getString("type_discriminator"));
        parent.setTitle(rs.getString("title"));
        parent.setTypeUri(rs.getString("type_uri"));
        parent.setTypeEs(rs.getString("type_es"));
        parent.setTypeEn(rs.getString("type_en"));
        parent.setCategoryUri(rs.getString("category_uri"));
        parent.setCategoryEs(rs.getString("category_es"));
        parent.setCategoryEn(rs.getString("category_en"));
        parent.setPeerReview(nullableBoolean(rs, "peer_review"));
        parent.setLanguageUri(rs.getString("language_uri"));
        parent.setLanguageEs(rs.getString("language_es"));
        parent.setLanguageEn(rs.getString("language_en"));
        parent.setSubmissionYear(nullableInt(rs, "submission_year"));
        parent.setTotalNumberOfContributors(nullableInt(rs, "total_number_of_contributors"));
        parent.setManagingOrganizationUuid(rs.getString("managing_organization_uuid"));
        parent.setPages(rs.getString("pages"));
        parent.setVolume(rs.getString("volume"));
        parent.setJournalNumber(rs.getString("journal_number"));
        parent.setNumberOfPages(nullableInt(rs, "number_of_pages"));
        parent.setAbstractEs(rs.getString("abstract_es"));
        parent.setAbstractEn(rs.getString("abstract_en"));
        parent.setPortalUrl(rs.getString("portal_url"));
        parent.setVisibilityKey(rs.getString("visibility_key"));
        parent.setVisibilityEs(rs.getString("visibility_es"));
        parent.setVisibilityEn(rs.getString("visibility_en"));
        parent.setWorkflowStep(rs.getString("workflow_step"));
        parent.setWorkflowEs(rs.getString("workflow_es"));
        parent.setWorkflowEn(rs.getString("workflow_en"));
        parent.setCreatedBy(rs.getString("created_by"));
        parent.setModifiedBy(rs.getString("modified_by"));
        parent.setCreatedDate(offset(rs.getTimestamp("created_date")));
        parent.setModifiedDate(offset(rs.getTimestamp("modified_date")));
        parent.setVersion(rs.getString("version"));
        if (columnExists("pure_research_output", "active_in_pure")) {
            boolean active = rs.getBoolean("active_in_pure");
            snapshot.activeInPure = rs.wasNull() ? Boolean.TRUE : active;
        } else {
            snapshot.activeInPure = Boolean.TRUE;
        }
        return snapshot;
    }

    private void loadStatuses(Connection connection, List<Long> ids, Map<String, ResearchOutputSnapshot> byUuid)
            throws SQLException {
        Map<Long, ResearchOutputSnapshot> byId = byId(byUuid);
        String sql = """
                SELECT research_output_id, pure_id, current_status, status_uri, status_es, status_en,
                       publication_year, publication_month, publication_day
                FROM """ + " " + qualified("pure_research_output_status") + """
                
                WHERE research_output_id IN (""" + inPlaceholders(ids.size()) + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindLongs(statement, ids);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ResearchOutputSnapshot snapshot = byId.get(rs.getLong("research_output_id"));
                    if (snapshot == null) {
                        continue;
                    }
                    OutputStatus status = new OutputStatus();
                    status.setPureId(nullableLong(rs, "pure_id"));
                    status.setCurrentStatus(nullableBoolean(rs, "current_status"));
                    status.setStatusUri(rs.getString("status_uri"));
                    status.setStatusEs(rs.getString("status_es"));
                    status.setStatusEn(rs.getString("status_en"));
                    status.setPublicationYear(nullableInt(rs, "publication_year"));
                    status.setPublicationMonth(nullableInt(rs, "publication_month"));
                    status.setPublicationDay(nullableInt(rs, "publication_day"));
                    snapshot.statuses.add(status);
                }
            }
        }
    }

    private void loadContributors(Connection connection, List<Long> ids, Map<String, ResearchOutputSnapshot> byUuid)
            throws SQLException {
        Map<Long, ResearchOutputSnapshot> byId = byId(byUuid);
        Map<Long, OutputContributor> contributors = new LinkedHashMap<>();
        String sql = """
                SELECT id, research_output_id, pure_id, type_discriminator, first_name, last_name,
                       role_uri, role_es, role_en, person_uuid, external_person_uuid,
                       hidden, corresponding_author, association_hidden
                FROM """ + " " + qualified("pure_research_output_contributor") + """
                
                WHERE research_output_id IN (""" + inPlaceholders(ids.size()) + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindLongs(statement, ids);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ResearchOutputSnapshot snapshot = byId.get(rs.getLong("research_output_id"));
                    if (snapshot == null) {
                        continue;
                    }
                    OutputContributor contributor = new OutputContributor();
                    contributor.setPureId(nullableLong(rs, "pure_id"));
                    contributor.setTypeDiscriminator(rs.getString("type_discriminator"));
                    contributor.setFirstName(rs.getString("first_name"));
                    contributor.setLastName(rs.getString("last_name"));
                    contributor.setRoleUri(rs.getString("role_uri"));
                    contributor.setRoleEs(rs.getString("role_es"));
                    contributor.setRoleEn(rs.getString("role_en"));
                    contributor.setPersonUuid(rs.getString("person_uuid"));
                    contributor.setExternalPersonUuid(rs.getString("external_person_uuid"));
                    contributor.setHidden(nullableBoolean(rs, "hidden"));
                    contributor.setCorrespondingAuthor(nullableBoolean(rs, "corresponding_author"));
                    contributor.setAssociationHidden(nullableBoolean(rs, "association_hidden"));
                    snapshot.contributors.add(contributor);
                    contributors.put(rs.getLong("id"), contributor);
                }
            }
        }
        if (contributors.isEmpty()) {
            return;
        }
        List<Long> contributorIds = new ArrayList<>(contributors.keySet());
        String orgSql = """
                SELECT contributor_id, organization_uuid
                FROM """ + " " + qualified("pure_research_output_contributor_organization") + """
                
                WHERE contributor_id IN (""" + inPlaceholders(contributorIds.size()) + ")";
        try (PreparedStatement statement = connection.prepareStatement(orgSql)) {
            bindLongs(statement, contributorIds);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    OutputContributor contributor = contributors.get(rs.getLong("contributor_id"));
                    if (contributor != null) {
                        contributor.getOrganizationUuids().add(rs.getString("organization_uuid"));
                    }
                }
            }
        }
    }

    private void loadElectronicVersions(Connection connection, List<Long> ids, Map<String, ResearchOutputSnapshot> byUuid)
            throws SQLException {
        Map<Long, ResearchOutputSnapshot> byId = byId(byUuid);
        String sql = """
                SELECT research_output_id, pure_id, type_discriminator, doi,
                       access_type_uri, access_type_es, access_type_en,
                       version_type_uri, version_type_es, version_type_en
                FROM """ + " " + qualified("pure_research_output_electronic_version") + """
                
                WHERE research_output_id IN (""" + inPlaceholders(ids.size()) + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindLongs(statement, ids);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ResearchOutputSnapshot snapshot = byId.get(rs.getLong("research_output_id"));
                    if (snapshot == null) {
                        continue;
                    }
                    ElectronicVersion version = new ElectronicVersion();
                    version.setPureId(nullableLong(rs, "pure_id"));
                    version.setTypeDiscriminator(rs.getString("type_discriminator"));
                    version.setDoi(rs.getString("doi"));
                    version.setAccessTypeUri(rs.getString("access_type_uri"));
                    version.setAccessTypeEs(rs.getString("access_type_es"));
                    version.setAccessTypeEn(rs.getString("access_type_en"));
                    version.setVersionTypeUri(rs.getString("version_type_uri"));
                    version.setVersionTypeEs(rs.getString("version_type_es"));
                    version.setVersionTypeEn(rs.getString("version_type_en"));
                    snapshot.electronicVersions.add(version);
                }
            }
        }
    }

    private void loadIdentifiers(Connection connection, List<Long> ids, Map<String, ResearchOutputSnapshot> byUuid)
            throws SQLException {
        Map<Long, ResearchOutputSnapshot> byId = byId(byUuid);
        String sql = """
                SELECT research_output_id, pure_id, type_discriminator, id_source, value
                FROM """ + " " + qualified("pure_research_output_identifier") + """
                
                WHERE research_output_id IN (""" + inPlaceholders(ids.size()) + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindLongs(statement, ids);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ResearchOutputSnapshot snapshot = byId.get(rs.getLong("research_output_id"));
                    if (snapshot == null) {
                        continue;
                    }
                    OutputIdentifier identifier = new OutputIdentifier();
                    identifier.setPureId(nullableLong(rs, "pure_id"));
                    identifier.setTypeDiscriminator(rs.getString("type_discriminator"));
                    identifier.setIdSource(rs.getString("id_source"));
                    identifier.setValue(rs.getString("value"));
                    snapshot.identifiers.add(identifier);
                }
            }
        }
    }

    private void loadLinks(Connection connection, List<Long> ids, Map<String, ResearchOutputSnapshot> byUuid)
            throws SQLException {
        Map<Long, ResearchOutputSnapshot> byId = byId(byUuid);
        String sql = """
                SELECT research_output_id, pure_id, url, description_es, description_en,
                       link_type_uri, link_type_es, link_type_en
                FROM """ + " " + qualified("pure_research_output_link") + """
                
                WHERE research_output_id IN (""" + inPlaceholders(ids.size()) + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindLongs(statement, ids);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ResearchOutputSnapshot snapshot = byId.get(rs.getLong("research_output_id"));
                    if (snapshot == null) {
                        continue;
                    }
                    OutputLink link = new OutputLink();
                    link.setPureId(nullableLong(rs, "pure_id"));
                    link.setUrl(rs.getString("url"));
                    link.setDescriptionEs(rs.getString("description_es"));
                    link.setDescriptionEn(rs.getString("description_en"));
                    link.setLinkTypeUri(rs.getString("link_type_uri"));
                    link.setLinkTypeEs(rs.getString("link_type_es"));
                    link.setLinkTypeEn(rs.getString("link_type_en"));
                    snapshot.links.add(link);
                }
            }
        }
    }

    private void loadJournal(Connection connection, List<Long> ids, Map<String, ResearchOutputSnapshot> byUuid)
            throws SQLException {
        Map<Long, ResearchOutputSnapshot> byId = byId(byUuid);
        boolean hasUuidColumn = columnExists("pure_research_output_journal", "journal_uuid");
        String sql = """
                SELECT rj.research_output_id, rj.pure_id,"""
                + (hasUuidColumn ? " rj.journal_uuid," : "")
                + """
                 j.uuid AS catalog_uuid
                FROM """ + " " + qualified("pure_research_output_journal") + """
                 rj
                LEFT JOIN """ + " " + qualified("pure_journal") + """
                 j ON j.id = rj.journal_id
                WHERE rj.research_output_id IN (""" + inPlaceholders(ids.size()) + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindLongs(statement, ids);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ResearchOutputSnapshot snapshot = byId.get(rs.getLong("research_output_id"));
                    if (snapshot == null || snapshot.journal != null) {
                        continue;
                    }
                    String uuid = hasUuidColumn
                            ? firstNonBlank(rs.getString("journal_uuid"), rs.getString("catalog_uuid"))
                            : rs.getString("catalog_uuid");
                    if (uuid == null || uuid.isBlank()) {
                        snapshot.journalAssociationPureId = nullableLong(rs, "pure_id");
                        continue;
                    }
                    JournalData journal = new JournalData();
                    journal.setUuid(uuid.trim());
                    snapshot.journal = journal;
                    snapshot.journalAssociationPureId = nullableLong(rs, "pure_id");
                }
            }
        }
    }

    private void loadExternalOrganizations(Connection connection, List<Long> ids, Map<String, ResearchOutputSnapshot> byUuid)
            throws SQLException {
        Map<Long, ResearchOutputSnapshot> byId = byId(byUuid);
        String sql = """
                SELECT research_output_id, organization_uuid
                FROM """ + " " + qualified("pure_research_output_external_organization") + """
                
                WHERE research_output_id IN (""" + inPlaceholders(ids.size()) + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindLongs(statement, ids);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ResearchOutputSnapshot snapshot = byId.get(rs.getLong("research_output_id"));
                    if (snapshot != null) {
                        snapshot.externalOrganizationUuids.add(rs.getString("organization_uuid"));
                    }
                }
            }
        }
    }

    private void loadKeywordGroups(Connection connection, List<Long> ids, Map<String, ResearchOutputSnapshot> byUuid)
            throws SQLException {
        Map<Long, ResearchOutputSnapshot> byId = byId(byUuid);
        Map<Long, KeywordGroupData> groups = new LinkedHashMap<>();
        String sql = """
                SELECT id, research_output_id, pure_id, type_discriminator, logical_name, name_es, name_en
                FROM """ + " " + qualified("pure_research_output_keyword_group") + """
                
                WHERE research_output_id IN (""" + inPlaceholders(ids.size()) + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindLongs(statement, ids);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ResearchOutputSnapshot snapshot = byId.get(rs.getLong("research_output_id"));
                    if (snapshot == null) {
                        continue;
                    }
                    KeywordGroupData group = new KeywordGroupData();
                    group.setPureId(nullableLong(rs, "pure_id"));
                    group.setTypeDiscriminator(rs.getString("type_discriminator"));
                    group.setLogicalName(rs.getString("logical_name"));
                    group.setNameEs(rs.getString("name_es"));
                    group.setNameEn(rs.getString("name_en"));
                    snapshot.keywordGroups.add(group);
                    groups.put(rs.getLong("id"), group);
                }
            }
        }
        if (groups.isEmpty()) {
            return;
        }
        List<Long> groupIds = new ArrayList<>(groups.keySet());
        String keywordSql = """
                SELECT keyword_group_id, container_pure_id, keyword_type, state, origin,
                       keyword_uri, keyword_es, keyword_en, locale, free_keyword
                FROM """ + " " + qualified("pure_research_output_keyword") + """
                
                WHERE keyword_group_id IN (""" + inPlaceholders(groupIds.size()) + ")";
        try (PreparedStatement statement = connection.prepareStatement(keywordSql)) {
            bindLongs(statement, groupIds);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    KeywordGroupData group = groups.get(rs.getLong("keyword_group_id"));
                    if (group == null) {
                        continue;
                    }
                    KeywordData keyword = new KeywordData();
                    keyword.setContainerPureId(nullableLong(rs, "container_pure_id"));
                    keyword.setKeywordType(rs.getString("keyword_type"));
                    keyword.setState(rs.getString("state"));
                    keyword.setOrigin(rs.getString("origin"));
                    keyword.setKeywordUri(rs.getString("keyword_uri"));
                    keyword.setKeywordEs(rs.getString("keyword_es"));
                    keyword.setKeywordEn(rs.getString("keyword_en"));
                    keyword.setLocale(rs.getString("locale"));
                    keyword.setFreeKeyword(rs.getString("free_keyword"));
                    group.getKeywords().add(keyword);
                }
            }
        }
    }

    private static Map<Long, ResearchOutputSnapshot> byId(Map<String, ResearchOutputSnapshot> byUuid) {
        Map<Long, ResearchOutputSnapshot> byId = new LinkedHashMap<>();
        for (ResearchOutputSnapshot snapshot : byUuid.values()) {
            byId.put(snapshot.id, snapshot);
        }
        return byId;
    }

    private static void bindLongs(PreparedStatement statement, List<Long> ids) throws SQLException {
        for (int i = 0; i < ids.size(); i++) {
            statement.setLong(i + 1, ids.get(i));
        }
    }

    private static OffsetDateTime offset(Timestamp value) {
        return value == null ? null : OffsetDateTime.ofInstant(value.toInstant(), ZoneOffset.UTC);
    }

    private static void bindResearchOutput(PreparedStatement statement, ResearchOutput output)
            throws SQLException {
        setLong(statement, 1, output.getPureId());
        statement.setString(2, output.getUuid());
        statement.setString(3, output.getTypeDiscriminator());
        statement.setString(4, output.getTitle());
        statement.setString(5, output.getTypeUri());
        statement.setString(6, output.getTypeEs());
        statement.setString(7, output.getTypeEn());
        statement.setString(8, output.getCategoryUri());
        statement.setString(9, output.getCategoryEs());
        statement.setString(10, output.getCategoryEn());
        setNullableBoolean(statement, 11, output.getPeerReview());
        statement.setString(12, output.getLanguageUri());
        statement.setString(13, output.getLanguageEs());
        statement.setString(14, output.getLanguageEn());
        setInt(statement, 15, output.getSubmissionYear());
        setInt(statement, 16, output.getTotalNumberOfContributors());
        statement.setString(17, output.getManagingOrganizationUuid());
        statement.setString(18, output.getPages());
        statement.setString(19, output.getVolume());
        statement.setString(20, output.getJournalNumber());
        setInt(statement, 21, output.getNumberOfPages());
        statement.setString(22, output.getAbstractEs());
        statement.setString(23, output.getAbstractEn());
        statement.setString(24, output.getPortalUrl());
        statement.setString(25, output.getVisibilityKey());
        statement.setString(26, output.getVisibilityEs());
        statement.setString(27, output.getVisibilityEn());
        statement.setString(28, output.getWorkflowStep());
        statement.setString(29, output.getWorkflowEs());
        statement.setString(30, output.getWorkflowEn());
        statement.setString(31, output.getCreatedBy());
        statement.setString(32, output.getModifiedBy());
        setTimestamp(statement, 33, output.getCreatedDate());
        setTimestamp(statement, 34, output.getModifiedDate());
        statement.setString(35, output.getVersion());
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

    private static String firstNonBlank(String left, String right) {
        if (left != null && !left.isBlank()) {
            return left.trim();
        }
        if (right != null && !right.isBlank()) {
            return right.trim();
        }
        return null;
    }

    private static void setLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    private static void setInt(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private static void setString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null || value.isEmpty()) {
            statement.setNull(index, java.sql.Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    private static void setNullableBoolean(PreparedStatement statement, int index, Boolean value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.TINYINT);
        } else {
            statement.setBoolean(index, value);
        }
    }

    private static void setBoolean(PreparedStatement statement, int index, Boolean value) throws SQLException {
        setNullableBoolean(statement, index, value);
    }

    private static void setTimestamp(PreparedStatement statement, int index, java.time.OffsetDateTime value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.TIMESTAMP);
        } else {
            statement.setTimestamp(index, java.sql.Timestamp.from(value.toInstant()));
        }
    }

}
