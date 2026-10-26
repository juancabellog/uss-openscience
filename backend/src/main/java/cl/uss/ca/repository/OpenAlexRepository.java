package cl.uss.ca.repository;

import cl.uss.ca.api.dto.OpenAlexAuthorQuery;
import cl.uss.ca.api.dto.OpenAlexAuthorSummaryDto;
import cl.uss.ca.api.dto.OpenAlexAuthorshipDto;
import cl.uss.ca.api.dto.OpenAlexFiltersDto;
import cl.uss.ca.api.dto.OpenAlexInstitutionDto;
import cl.uss.ca.api.dto.OpenAlexLinkedOutputDto;
import cl.uss.ca.api.dto.OpenAlexMatchDetailDto;
import cl.uss.ca.api.dto.OpenAlexMatchQuery;
import cl.uss.ca.api.dto.OpenAlexMatchSummaryDto;
import cl.uss.ca.api.dto.OpenAlexMatchWorkDto;
import cl.uss.ca.api.dto.OpenAlexMatchWorkEvidenceDto;
import cl.uss.ca.api.dto.OpenAlexPersonIdentityDto;
import cl.uss.ca.api.dto.OpenAlexTopicDto;
import cl.uss.ca.api.dto.OpenAlexWorkDetailDto;
import cl.uss.ca.api.dto.OpenAlexWorkQuery;
import cl.uss.ca.api.dto.OpenAlexWorkSummaryDto;
import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.domain.AuthorNameMatcher;
import cl.uss.ca.domain.DoiNormalizer;
import cl.uss.ca.domain.MysqlDuplicateKey;
import cl.uss.ca.domain.OpenAlexId;
import cl.uss.ca.domain.OpenAlexMatchWorkPlanner;
import cl.uss.ca.domain.OpenAlexMatchWorkPaging;
import cl.uss.ca.domain.OpenAlexPersonMatcher;
import cl.uss.ca.domain.OrcidCandidateMatcher;
import cl.uss.ca.domain.OpenAlexWorkIdentity;
import cl.uss.ca.exception.DatabaseException;
import cl.uss.ca.exception.ValidationException;
import cl.uss.ca.external.openalex.OpenAlexAuthorRecord;
import cl.uss.ca.external.openalex.OpenAlexAuthorshipRecord;
import cl.uss.ca.external.openalex.OpenAlexFetchResult;
import cl.uss.ca.external.openalex.OpenAlexInstitutionRecord;
import cl.uss.ca.external.openalex.OpenAlexStatus;
import cl.uss.ca.external.openalex.OpenAlexTopicRecord;
import cl.uss.ca.external.openalex.OpenAlexWorkRecord;
import cl.uss.ca.external.openalex.OpenAlexWorkSnapshot;
import cl.uss.ca.sync.OpenAlexAuthorRecovery;
import cl.uss.ca.sync.SyncRunState;
import cl.uss.ca.sync.WorkSyncState;
import cl.uss.ca.sync.diff.SyncChange;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class OpenAlexRepository extends JdbcRepository {
    private static final String WORK = "openalex_work";
    private static final String AUTHOR = "openalex_author";
    private static final String AUTHORSHIP = "openalex_authorship";
    private static final String INST = "openalex_authorship_institution";
    private static final String TOPIC = "openalex_work_topic";
    private static final String LINK = "openalex_research_output";
    private static final String MATCH = "openalex_person_match";
    private static final String MATCH_WORK = "openalex_person_match_work";
    private static final String MATCH_WORK_EVIDENCE = "openalex_person_match_work_evidence";
    private static final String STATS = "openalex_sync_stats";
    private static final String DOI_TYPE = "DoiElectronicVersion";
    private static final Set<String> WORK_SORTS = Set.of("doi", "title", "year", "type", "cited", "status", "syncedAt");
    private static final Set<String> AUTHOR_SORTS = Set.of("name", "orcid", "works", "cited", "syncedAt");
    private static final Set<String> MATCH_SORTS = Set.of("person", "openalex", "works", "status", "evaluated");

    public boolean available() {
        return tableExists(WORK)
                && tableExists(AUTHOR)
                && tableExists(AUTHORSHIP)
                && tableExists(MATCH)
                && columnExists(WORK, "last_outcome")
                && columnExists(AUTHOR, "last_outcome");
    }

    public void assertAvailable() {
        if (!available()) {
            throw new ValidationException(
                    "OPENALEX_TABLES_UNAVAILABLE",
                    "openalex_work tables are required before synchronizing OpenAlex"
            );
        }
    }

    public List<OpenAlexDoiLink> listDoiLinks(boolean activeOnly) {
        if (!tableExists("pure_research_output_electronic_version") || !tableExists("pure_research_output")) {
            return List.of();
        }
        String active = activeOnly ? " AND " + activeInPurePredicate("pure_research_output", "o").trim() : "";
        String sql = """
                SELECT ev.id AS electronic_version_id, ev.research_output_id, ev.doi,
                       o.uuid, o.title, o.pure_id, COALESCE(o.active_in_pure, 1) AS active_in_pure
                FROM %s ev
                JOIN %s o ON o.id = ev.research_output_id
                WHERE ev.type_discriminator = ?
                  AND ev.doi IS NOT NULL AND TRIM(ev.doi) <> ''
                  %s
                ORDER BY ev.research_output_id, ev.id
                """.formatted(
                qualified("pure_research_output_electronic_version"),
                qualified("pure_research_output"),
                active
        );
        List<OpenAlexDoiLink> links = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, DOI_TYPE);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    OpenAlexDoiLink link = new OpenAlexDoiLink();
                    link.electronicVersionId = nullableLong(rs, "electronic_version_id");
                    link.researchOutputId = rs.getLong("research_output_id");
                    link.doi = nullableString(rs, "doi");
                    link.uuid = nullableString(rs, "uuid");
                    link.title = nullableString(rs, "title");
                    link.pureId = nullableLong(rs, "pure_id");
                    link.activeInPure = rs.getBoolean("active_in_pure");
                    links.add(link);
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list OpenAlex DOI universe", ex);
        }
        return links;
    }

    public StoredWork findByNormalizedDoi(String normalizedDoi) {
        if (!available() || normalizedDoi == null) {
            return null;
        }
        try (Connection connection = connection()) {
            return findByNormalizedDoi(connection, normalizedDoi);
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load OpenAlex work", ex);
        }
    }

    public Map<String, WorkSyncState> loadWorkStates() {
        Map<String, WorkSyncState> items = new HashMap<>();
        if (!tableExists(WORK)) {
            return items;
        }
        String sql = "SELECT normalized_doi, last_outcome, last_sync_at, needs_refresh FROM " + qualified(WORK);
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                WorkSyncState state = new WorkSyncState();
                state.present = true;
                state.status = nullableString(rs, "last_outcome");
                state.lastSyncAt = timestamp(rs, "last_sync_at");
                state.needsRefresh = rs.getBoolean("needs_refresh");
                items.put(rs.getString("normalized_doi"), state);
            }
            indexLinkedLookupDois(connection, items);
            return items;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load OpenAlex work states", ex);
        }
    }

    public Set<String> loadMappedNormalizedDois() {
        Set<String> items = new HashSet<>();
        if (!tableExists(WORK) || !tableExists(LINK)) {
            return items;
        }
        String sql = """
                SELECT DISTINCT w.normalized_doi
                FROM %s w
                INNER JOIN %s m ON m.openalex_work_id = w.id
                WHERE COALESCE(m.active_in_pure, 1) = 1
                """.formatted(qualified(WORK), qualified(LINK));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                items.add(rs.getString(1));
            }
            addLinkedLookupDois(connection, items);
            return items;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load mapped OpenAlex DOIs", ex);
        }
    }

    public Map<String, WorkSyncState> loadAuthorStates() {
        Map<String, WorkSyncState> items = new HashMap<>();
        if (!tableExists(AUTHOR)) {
            return items;
        }
        String sql = "SELECT openalex_author_id, last_outcome, last_sync_at, needs_refresh FROM " + qualified(AUTHOR);
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                WorkSyncState state = new WorkSyncState();
                state.present = true;
                state.status = nullableString(rs, "last_outcome");
                state.lastSyncAt = timestamp(rs, "last_sync_at");
                state.needsRefresh = rs.getBoolean("needs_refresh");
                state.mapped = true;
                items.put(rs.getString("openalex_author_id"), state);
            }
            return items;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load OpenAlex author states", ex);
        }
    }

    public OpenAlexReconcileResult reconcile(
            String normalizedDoi,
            String displayDoi,
            OpenAlexFetchResult fetch,
            List<OpenAlexDoiLink> links,
            String syncId
    ) {
        assertAvailable();
        OpenAlexReconcileResult result = new OpenAlexReconcileResult();
        try (Connection connection = connection()) {
            boolean previous = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                OpenAlexWorkRecord incoming = fetch.work == null ? new OpenAlexWorkRecord() : fetch.work;
                String oaId = OpenAlexId.normalize(incoming.openalexWorkId);
                String canonicalDoi = fetch.found()
                        ? OpenAlexWorkIdentity.canonicalNormalizedDoi(normalizedDoi, incoming)
                        : normalizedDoi;
                String canonicalDisplay = fetch.found()
                        ? OpenAlexWorkIdentity.displayDoi(displayDoi, incoming)
                        : displayDoi;
                StoredWork byOa = oaId == null ? null : findByOpenAlexWorkId(connection, oaId);
                StoredWork byCanonical = canonicalDoi == null ? null : findByNormalizedDoi(connection, canonicalDoi);
                StoredWork byLookup = normalizedDoi == null || normalizedDoi.equals(canonicalDoi)
                        ? byCanonical
                        : findByNormalizedDoi(connection, normalizedDoi);
                OpenAlexWorkIdentity.Decision decision = OpenAlexWorkIdentity.decide(
                        snapshot(byOa),
                        snapshot(byCanonical),
                        snapshot(byLookup),
                        oaId
                );
                String parentSnap = OpenAlexWorkSnapshot.parent(incoming, fetch.status);
                String childSnap = OpenAlexWorkSnapshot.authorships(incoming);
                StoredWork stored = storedOf(decision, byOa, byCanonical, byLookup);
                long workId;
                boolean created = false;
                if (decision.action == OpenAlexWorkIdentity.Decision.Action.CONFLICT) {
                    workId = stored.id;
                    markDoiOaConflict(connection, workId, fetch, syncId, stored.openalexWorkId, oaId);
                    result.kind = "UNCHANGED";
                    result.changes.add(SyncChange.updated(
                            "PARENT",
                            OpenAlexWorkIdentity.DOI_OA_ID_CONFLICT,
                            stored.openalexWorkId,
                            oaId
                    ).on("OPENALEX", workId, null, pureUuid(links)).key(normalizedDoi));
                    result.workId = workId;
                    connection.commit();
                    return result;
                }
                if (decision.action == OpenAlexWorkIdentity.Decision.Action.INSERT || stored == null) {
                    try {
                        workId = insertWork(
                                connection,
                                canonicalDoi,
                                canonicalDisplay,
                                incoming,
                                fetch,
                                syncId,
                                parentSnap,
                                childSnap
                        );
                        created = true;
                        result.kind = "CREATED";
                        result.changes.add(SyncChange.created()
                                .on("OPENALEX", workId, null, pureUuid(links))
                                .key(normalizedDoi));
                    } catch (SQLException ex) {
                        StoredWork raced = recoverDuplicate(connection, ex, oaId, canonicalDoi);
                        if (raced == null) {
                            throw ex;
                        }
                        stored = raced;
                        workId = raced.id;
                    }
                } else {
                    workId = stored.id;
                }
                if (!created) {
                    boolean parentChanged = stored == null
                            || !OpenAlexWorkSnapshot.same(stored.parentSnapshot, parentSnap);
                    boolean childChanged = stored == null
                            || !OpenAlexWorkSnapshot.same(stored.authorshipSnapshot, childSnap);
                    if (parentChanged || childChanged) {
                        updateWork(connection, workId, canonicalDisplay, incoming, fetch, syncId, parentSnap, childSnap);
                        result.kind = "UPDATED";
                        addFieldChanges(result, stored, incoming, fetch, workId, canonicalDoi);
                    } else {
                        touch(connection, workId, fetch, syncId);
                        if (result.kind == null || "UNCHANGED".equals(result.kind)) {
                            result.kind = "UNCHANGED";
                        }
                    }
                    if (parentChanged || childChanged) {
                        replaceChildren(connection, workId, incoming);
                    }
                } else {
                    replaceChildren(connection, workId, incoming);
                }
                result.workId = workId;
                reconcileLinks(connection, workId, links);
                connection.commit();
                return result;
            } catch (SQLException ex) {
                rollbackQuietly(connection);
                throw ex;
            } finally {
                restoreAutoCommit(connection, previous);
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to reconcile OpenAlex work", ex);
        }
    }

    public boolean upsertAuthor(OpenAlexFetchResult fetch, String syncId) {
        if (fetch == null || fetch.author == null || fetch.author.openalexAuthorId == null) {
            return false;
        }
        OpenAlexAuthorRecord author = fetch.author;
        String snap = OpenAlexWorkSnapshot.author(author);
        String sql = """
                INSERT INTO %s (
                    openalex_author_id, display_name, orcid, works_count, cited_by_count, openalex_updated_date,
                    last_outcome, last_http_status, last_error_code, last_sync_execution_id, last_sync_at,
                    needs_refresh, parent_snapshot
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(6), 0, ?)
                ON DUPLICATE KEY UPDATE
                    display_name = VALUES(display_name),
                    orcid = VALUES(orcid),
                    works_count = VALUES(works_count),
                    cited_by_count = VALUES(cited_by_count),
                    openalex_updated_date = VALUES(openalex_updated_date),
                    last_outcome = VALUES(last_outcome),
                    last_http_status = VALUES(last_http_status),
                    last_error_code = VALUES(last_error_code),
                    last_sync_execution_id = VALUES(last_sync_execution_id),
                    last_sync_at = NOW(6),
                    needs_refresh = 0,
                    parent_snapshot = VALUES(parent_snapshot)
                """.formatted(qualified(AUTHOR));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, author.openalexAuthorId);
            statement.setString(2, author.displayName);
            statement.setString(3, author.orcid);
            setNullableInt(statement, 4, author.worksCount);
            setNullableInt(statement, 5, author.citedByCount);
            statement.setString(6, author.updatedDate);
            statement.setString(7, fetch.status);
            setNullableInt(statement, 8, fetch.httpStatus == 0 ? null : fetch.httpStatus);
            statement.setString(9, fetch.errorCode);
            bindSyncId(statement, 10, AUTHOR, "last_sync_execution_id", syncId);
            statement.setString(11, snap);
            int rows = statement.executeUpdate();
            return rows > 0;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to upsert OpenAlex author", ex);
        }
    }

    /**
     * All author stubs that still need an Author fetch.
     * Does not filter by last_sync_execution_id, created_at, or a source FULL.
     */
    public List<String> listPendingAuthors() {
        List<String> items = new ArrayList<>();
        if (!tableExists(AUTHOR)) {
            return items;
        }
        String sql = """
                SELECT openalex_author_id
                FROM %s
                WHERE needs_refresh = 1
                  AND openalex_author_id IS NOT NULL
                ORDER BY openalex_author_id
                """.formatted(qualified(AUTHOR));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                items.add(rs.getString("openalex_author_id"));
            }
            return items;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list pending OpenAlex authors", ex);
        }
    }

    public long countPendingAuthors() {
        if (!tableExists(AUTHOR)) {
            return 0;
        }
        String sql = "SELECT COUNT(*) FROM " + qualified(AUTHOR) + " WHERE needs_refresh = 1";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to count pending OpenAlex authors", ex);
        }
    }

    public List<String> listPendingAuthorsForWorksOf(String sourceSyncId, OffsetDateTime sourceStartedAt) {
        return listPendingAuthors();
    }

    public long countPendingAuthorsForWorksOf(String sourceSyncId, OffsetDateTime sourceStartedAt) {
        return countPendingAuthors();
    }

    public void ensureAuthorStub(String openalexAuthorId, String displayName, String orcid) {
        if (openalexAuthorId == null) {
            return;
        }
        String sql = """
                INSERT IGNORE INTO %s (openalex_author_id, display_name, orcid, last_outcome, needs_refresh)
                VALUES (?, ?, ?, NULL, 1)
                """.formatted(qualified(AUTHOR));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, openalexAuthorId);
            statement.setString(2, displayName);
            statement.setString(3, orcid);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to stub OpenAlex author", ex);
        }
    }

    public List<OpenAlexAuthorshipRecord> authorshipsOf(long workId) {
        List<OpenAlexAuthorshipRecord> items = new ArrayList<>();
        String sql = "SELECT * FROM " + qualified(AUTHORSHIP) + " WHERE openalex_work_id = ? ORDER BY source_order, id";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, workId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    OpenAlexAuthorshipRecord row = new OpenAlexAuthorshipRecord();
                    row.id = rs.getLong("id");
                    row.sourceOrder = rs.getInt("source_order");
                    row.authorPosition = nullableString(rs, "author_position");
                    row.corresponding = rs.getBoolean("is_corresponding");
                    row.rawAuthorName = nullableString(rs, "raw_author_name");
                    row.rawAffiliationString = nullableString(rs, "raw_affiliation_string");
                    row.openalexAuthorId = nullableString(rs, "openalex_author_id");
                    row.authorDisplayName = nullableString(rs, "author_display_name");
                    row.authorOrcid = nullableString(rs, "author_orcid");
                    row.observedOrcid = nullableString(rs, "observed_orcid");
                    items.add(row);
                }
            }
            for (OpenAlexAuthorshipRecord row : items) {
                // institutions loaded later per authorship when matching needs ROR
            }
            loadInstitutions(connection, workId, items);
            return items;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load OpenAlex authorships", ex);
        }
    }

    public List<MatchContributor> contributorsForOutputs(List<Long> researchOutputIds) {
        if (researchOutputIds == null || researchOutputIds.isEmpty()) {
            return List.of();
        }
        String placeholders = inPlaceholders(researchOutputIds.size());
        String sql = """
                SELECT c.id AS contributor_id, c.research_output_id, c.first_name, c.last_name, c.person_uuid,
                       p.id AS person_id, p.first_name AS person_first, p.last_name AS person_last,
                       p.orcid, p.cargo, p.pure_id, p.uuid,
                       ROW_NUMBER() OVER (PARTITION BY c.research_output_id ORDER BY c.id) AS contributor_position,
                       COUNT(*) OVER (PARTITION BY c.research_output_id) AS contributor_count
                FROM %s c
                JOIN %s p ON p.uuid = c.person_uuid
                WHERE c.research_output_id IN (%s)
                  AND c.person_uuid IS NOT NULL AND TRIM(c.person_uuid) <> ''
                ORDER BY c.research_output_id, c.id
                """.formatted(
                qualified("pure_research_output_contributor"),
                qualified("pure_person"),
                placeholders
        );
        List<MatchContributor> items = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (Long id : researchOutputIds) {
                statement.setLong(index++, id);
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    MatchContributor row = new MatchContributor();
                    row.contributorId = rs.getLong("contributor_id");
                    row.researchOutputId = rs.getLong("research_output_id");
                    row.firstName = nullableString(rs, "first_name");
                    row.lastName = nullableString(rs, "last_name");
                    row.personUuid = nullableString(rs, "person_uuid");
                    row.personId = rs.getLong("person_id");
                    row.personFirst = nullableString(rs, "person_first");
                    row.personLast = nullableString(rs, "person_last");
                    row.orcid = nullableString(rs, "orcid");
                    row.position = nullableString(rs, "cargo");
                    row.pureId = nullableLong(rs, "pure_id");
                    row.contributorPosition = nullableInt(rs, "contributor_position");
                    row.contributorCount = nullableInt(rs, "contributor_count");
                    items.add(row);
                }
            }
            return items;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load PURE contributors for OpenAlex matching", ex);
        }
    }

    public void upsertMatch(
            OpenAlexPersonMatcher.PersonInput person,
            String openalexAuthorId,
            OpenAlexPersonMatcher.MatchDecision decision,
            String evidenceJson
    ) {
        if (person == null || openalexAuthorId == null || decision == null) {
            return;
        }
        StoredMatch existing = findMatch(person.personId, openalexAuthorId);
        String status = decision.status;
        boolean selected = false;
        boolean selectedManually = false;
        boolean reviewRequested = false;
        if (existing != null) {
            if (OpenAlexPersonMatcher.STATUS_REJECTED.equals(existing.status)) {
                status = OpenAlexPersonMatcher.STATUS_REJECTED;
                reviewRequested = OpenAlexPersonMatcher.STATUS_STRONG.equals(decision.status)
                        || OpenAlexPersonMatcher.STATUS_CONFLICT.equals(decision.status);
            } else if (OpenAlexPersonMatcher.STATUS_CONFIRMED.equals(existing.status)) {
                status = OpenAlexPersonMatcher.STATUS_CONFIRMED;
                selected = existing.selected;
                selectedManually = existing.selectedManually;
            }
        }
        String sql = """
                INSERT INTO %s (
                    person_id, openalex_author_id, status, match_method, selected, selected_manually,
                    matching_work_count, orcid_match, uss_affiliation, review_requested, evidence_json,
                    last_evaluated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), NOW(6))
                ON DUPLICATE KEY UPDATE
                    status = VALUES(status),
                    match_method = VALUES(match_method),
                    selected = VALUES(selected),
                    selected_manually = VALUES(selected_manually),
                    matching_work_count = VALUES(matching_work_count),
                    orcid_match = VALUES(orcid_match),
                    uss_affiliation = VALUES(uss_affiliation),
                    review_requested = VALUES(review_requested),
                    evidence_json = VALUES(evidence_json),
                    last_evaluated_at = NOW(6)
                """.formatted(qualified(MATCH));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, person.personId);
            statement.setString(2, openalexAuthorId);
            statement.setString(3, status);
            statement.setString(4, decision.matchMethod);
            statement.setBoolean(5, selected);
            statement.setBoolean(6, selectedManually);
            statement.setInt(7, decision.matchingWorkCount);
            statement.setBoolean(8, decision.orcidMatch);
            statement.setBoolean(9, decision.ussAffiliation);
            statement.setBoolean(10, reviewRequested);
            statement.setString(11, evidenceJson);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to upsert OpenAlex person match", ex);
        }
        StoredMatch saved = findMatch(person.personId, openalexAuthorId);
        if (saved != null) {
            replaceMatchWorks(saved.id, decision.works);
        }
    }

    public int markConflicts() {
        if (!tableExists(MATCH)) {
            return 0;
        }
        String personSql = """
                UPDATE %s m
                JOIN (
                    SELECT person_id
                    FROM %s
                    WHERE status IN ('STRONG','MANUAL_REVIEW','CONFIRMED') AND matching_work_count >= 1
                    GROUP BY person_id
                    HAVING COUNT(*) > 1
                ) x ON x.person_id = m.person_id
                SET m.status = 'CONFLICT'
                WHERE m.status IN ('STRONG','MANUAL_REVIEW')
                """.formatted(qualified(MATCH), qualified(MATCH));
        String authorSql = """
                UPDATE %s m
                JOIN (
                    SELECT openalex_author_id
                    FROM %s
                    WHERE status IN ('STRONG','MANUAL_REVIEW','CONFIRMED') AND matching_work_count >= 1
                    GROUP BY openalex_author_id
                    HAVING COUNT(*) > 1
                ) x ON x.openalex_author_id = m.openalex_author_id
                SET m.status = 'CONFLICT'
                WHERE m.status IN ('STRONG','MANUAL_REVIEW')
                """.formatted(qualified(MATCH), qualified(MATCH));
        try (Connection connection = connection();
             PreparedStatement person = connection.prepareStatement(personSql);
             PreparedStatement author = connection.prepareStatement(authorSql)) {
            return person.executeUpdate() + author.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to mark OpenAlex match conflicts", ex);
        }
    }

    public Long findConfirmedPersonIdForAuthor(String openalexAuthorId, Long excludeMatchId) {
        if (!tableExists(MATCH) || openalexAuthorId == null || openalexAuthorId.isBlank()) {
            return null;
        }
        String sql = "SELECT person_id FROM " + qualified(MATCH)
                + " WHERE openalex_author_id = ? AND status = 'CONFIRMED'"
                + (excludeMatchId == null ? "" : " AND id <> ?")
                + " LIMIT 1";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, openalexAuthorId);
            if (excludeMatchId != null) {
                statement.setLong(2, excludeMatchId);
            }
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load confirmed OpenAlex author", ex);
        }
    }

    public OpenAlexMatchDetailDto confirm(long matchId, String user) {
        OpenAlexMatchDetailDto current = findMatchDetail(matchId);
        if (current == null) {
            return null;
        }
        String clear = """
                UPDATE %s SET selected = 0,
                    status = CASE WHEN status = 'CONFIRMED' THEN 'MANUAL_REVIEW' ELSE status END
                WHERE person_id = ? AND id <> ?
                """.formatted(qualified(MATCH));
        String sql = """
                UPDATE %s SET status = 'CONFIRMED', selected = 1, selected_manually = 1,
                    confirmed_at = NOW(6), confirmed_by = ?, rejected_at = NULL, rejected_by = NULL
                WHERE id = ?
                """.formatted(qualified(MATCH));
        try (Connection connection = connection();
             PreparedStatement other = connection.prepareStatement(clear);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            other.setLong(1, current.personId);
            other.setLong(2, matchId);
            other.executeUpdate();
            statement.setString(1, user);
            statement.setLong(2, matchId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to confirm OpenAlex match", ex);
        }
        return findMatchDetail(matchId);
    }

    public OpenAlexMatchDetailDto reject(long matchId, String user) {
        String sql = """
                UPDATE %s SET status = 'REJECTED', selected = 0, selected_manually = 0,
                    rejected_at = NOW(6), rejected_by = ?, review_requested = 0
                WHERE id = ?
                """.formatted(qualified(MATCH));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, user);
            statement.setLong(2, matchId);
            if (statement.executeUpdate() == 0) {
                return null;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to reject OpenAlex match", ex);
        }
        return findMatchDetail(matchId);
    }

    public void saveRunStats(SyncRunState run) {
        if (!tableExists(STATS) || run == null || run.syncId == null) {
            return;
        }
        Long id = parseNumericId(run.syncId);
        if (id == null) {
            return;
        }
        boolean withPhase = columnExists(STATS, "phase");
        long upserted = OpenAlexAuthorRecovery.authorsUpserted(
                run.authorsCreated.get(), run.authorsUpdated.get()
        );
        String sql = withPhase
                ? """
                INSERT INTO %s (sync_execution_id, found_count, not_found_count, invalid_count, authors_upserted,
                                matches_strong, matches_review, phase, authors_total, authors_processed,
                                authors_created, authors_updated, source_sync_execution_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    found_count = VALUES(found_count),
                    not_found_count = VALUES(not_found_count),
                    invalid_count = VALUES(invalid_count),
                    authors_upserted = VALUES(authors_upserted),
                    matches_strong = VALUES(matches_strong),
                    matches_review = VALUES(matches_review),
                    phase = VALUES(phase),
                    authors_total = VALUES(authors_total),
                    authors_processed = VALUES(authors_processed),
                    authors_created = VALUES(authors_created),
                    authors_updated = VALUES(authors_updated),
                    source_sync_execution_id = VALUES(source_sync_execution_id)
                """.formatted(qualified(STATS))
                : """
                INSERT INTO %s (sync_execution_id, found_count, not_found_count, invalid_count, authors_upserted,
                                matches_strong, matches_review)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    found_count = VALUES(found_count),
                    not_found_count = VALUES(not_found_count),
                    invalid_count = VALUES(invalid_count),
                    authors_upserted = VALUES(authors_upserted),
                    matches_strong = VALUES(matches_strong),
                    matches_review = VALUES(matches_review)
                """.formatted(qualified(STATS));
        long strong = countStatusMatches(OpenAlexPersonMatcher.STATUS_STRONG);
        long review = countStatusMatches(OpenAlexPersonMatcher.STATUS_MANUAL_REVIEW);
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.setLong(2, run.oa.get());
            statement.setLong(3, run.notFound.get());
            statement.setLong(4, run.invalid.get());
            statement.setLong(5, upserted);
            statement.setLong(6, strong);
            statement.setLong(7, review);
            if (withPhase) {
                statement.setString(8, run.phase);
                if (run.authorsTotal == null) {
                    statement.setNull(9, Types.BIGINT);
                } else {
                    statement.setLong(9, run.authorsTotal);
                }
                statement.setLong(10, run.authorsProcessed.get());
                statement.setLong(11, run.authorsCreated.get());
                statement.setLong(12, run.authorsUpdated.get());
                Long sourceId = parseNumericId(run.sourceSyncExecutionId);
                if (sourceId == null) {
                    statement.setNull(13, Types.BIGINT);
                } else {
                    statement.setLong(13, sourceId);
                }
            }
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to persist OpenAlex sync stats", ex);
        }
    }

    public RunExtras findRunStats(String syncId) {
        if (!tableExists(STATS) || syncId == null) {
            return null;
        }
        Long id = parseNumericId(syncId);
        if (id == null) {
            return null;
        }
        boolean withPhase = columnExists(STATS, "phase");
        String sql = "SELECT found_count, not_found_count, invalid_count"
                + (withPhase
                ? ", phase, authors_total, authors_processed, authors_created, authors_updated, source_sync_execution_id"
                : "")
                + " FROM " + qualified(STATS) + " WHERE sync_execution_id = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                RunExtras extras = new RunExtras();
                extras.found = rs.getLong(1);
                extras.notFound = rs.getLong(2);
                extras.invalid = rs.getLong(3);
                if (withPhase) {
                    extras.phase = nullableString(rs, "phase");
                    long authorsTotal = rs.getLong("authors_total");
                    extras.authorsTotal = rs.wasNull() ? null : (int) authorsTotal;
                    extras.authorsProcessed = rs.getLong("authors_processed");
                    extras.authorsCreated = rs.getLong("authors_created");
                    extras.authorsUpdated = rs.getLong("authors_updated");
                    long source = rs.getLong("source_sync_execution_id");
                    extras.sourceSyncExecutionId = rs.wasNull() ? null : String.valueOf(source);
                }
                return extras;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load OpenAlex sync stats", ex);
        }
    }

    public long countUniqueActiveDois() {
        return listDoiLinks(true).stream()
                .map(link -> cl.uss.ca.domain.DoiNormalizer.normalize(link.doi))
                .filter(value -> value != null)
                .distinct()
                .count();
    }

    public long countOutcome(String outcome) {
        if (!tableExists(WORK) || !columnExists(WORK, "last_outcome") || outcome == null) {
            return 0;
        }
        return scalarLong("SELECT COUNT(*) FROM " + qualified(WORK) + " WHERE last_outcome = ?", outcome);
    }

    public long countAuthors() {
        return scalarLong("SELECT COUNT(*) FROM " + qualified(AUTHOR));
    }

    public long countStatusMatches(String status) {
        if (!tableExists(MATCH)) {
            return 0;
        }
        return scalarLong("SELECT COUNT(*) FROM " + qualified(MATCH) + " WHERE status = ?", status);
    }

    public long countLinkedPersons() {
        if (!tableExists(MATCH)) {
            return 0;
        }
        return scalarLong("SELECT COUNT(DISTINCT person_id) FROM " + qualified(MATCH)
                + " WHERE status IN ('STRONG','CONFIRMED')");
    }

    public long countEvaluablePersons() {
        if (!tableExists("pure_person") || !tableExists("pure_research_output_contributor")) {
            return 0;
        }
        String sql = """
                SELECT COUNT(DISTINCT p.id)
                FROM %s p
                JOIN %s c ON c.person_uuid = p.uuid
                JOIN %s ev ON ev.research_output_id = c.research_output_id
                WHERE ev.type_discriminator = ? AND ev.doi IS NOT NULL AND TRIM(ev.doi) <> ''
                """.formatted(
                qualified("pure_person"),
                qualified("pure_research_output_contributor"),
                qualified("pure_research_output_electronic_version")
        );
        return scalarLong(sql, DOI_TYPE);
    }

    public long countConfirmedPersons() {
        if (!tableExists(MATCH)) {
            return 0;
        }
        return scalarLong("SELECT COUNT(DISTINCT person_id) FROM " + qualified(MATCH) + " WHERE status = 'CONFIRMED'");
    }

    public PageResponse<OpenAlexWorkSummaryDto> listWorks(OpenAlexWorkQuery query) {
        assertAvailable();
        FilterSql filter = workFilter(query);
        String order = workOrder(query);
        int page = Math.max(1, query.page);
        int size = Math.min(200, Math.max(1, query.pageSize));
        long total = scalarLong("SELECT COUNT(*) FROM " + qualified(WORK) + " w " + filter.sql, filter.params.toArray());
        String sql = """
                SELECT w.*,
                       (SELECT COUNT(*) FROM %s m WHERE m.openalex_work_id = w.id) AS research_output_count,
                       (SELECT o.title FROM %s m JOIN %s o ON o.id = m.research_output_id
                         WHERE m.openalex_work_id = w.id ORDER BY o.id LIMIT 1) AS publication_title
                FROM %s w
                %s
                ORDER BY %s, w.id
                LIMIT ? OFFSET ?
                """.formatted(qualified(LINK), qualified(LINK), qualified("pure_research_output"),
                qualified(WORK), filter.sql, order);
        List<OpenAlexWorkSummaryDto> items = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (Object param : filter.params) {
                statement.setObject(index++, param);
            }
            statement.setInt(index++, size);
            statement.setInt(index, (page - 1) * size);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    items.add(mapWorkSummary(rs));
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list OpenAlex works", ex);
        }
        return PageResponse.of(items, page - 1, size, total);
    }

    public OpenAlexWorkDetailDto findWorkDetail(long id) {
        assertAvailable();
        String sql = "SELECT * FROM " + qualified(WORK) + " WHERE id = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                OpenAlexWorkDetailDto dto = mapWorkDetail(rs);
                dto.authorships.addAll(loadAuthorshipDtos(connection, id));
                dto.topics.addAll(loadTopics(connection, id));
                dto.researchOutputs.addAll(loadLinks(connection, id));
                dto.researchOutputCount = dto.researchOutputs.size();
                if (!dto.researchOutputs.isEmpty()) {
                    dto.publicationTitle = dto.researchOutputs.get(0).title;
                }
                return dto;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load OpenAlex work detail", ex);
        }
    }

    public PageResponse<OpenAlexAuthorSummaryDto> listAuthors(OpenAlexAuthorQuery query) {
        assertAvailable();
        FilterSql filter = authorFilter(query);
        String order = authorOrder(query);
        int page = Math.max(1, query.page);
        int size = Math.min(200, Math.max(1, query.pageSize));
        long total = scalarLong("SELECT COUNT(*) FROM " + qualified(AUTHOR) + " a " + filter.sql, filter.params.toArray());
        String sql = """
                SELECT a.*,
                       (SELECT COUNT(*) FROM %s m WHERE m.openalex_author_id = a.openalex_author_id) AS linked_person_count
                FROM %s a
                %s
                ORDER BY %s, a.id
                LIMIT ? OFFSET ?
                """.formatted(qualified(MATCH), qualified(AUTHOR), filter.sql, order);
        List<OpenAlexAuthorSummaryDto> items = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (Object param : filter.params) {
                statement.setObject(index++, param);
            }
            statement.setInt(index++, size);
            statement.setInt(index, (page - 1) * size);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    items.add(mapAuthor(rs));
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list OpenAlex authors", ex);
        }
        return PageResponse.of(items, page - 1, size, total);
    }

    public PageResponse<OpenAlexMatchSummaryDto> listMatches(OpenAlexMatchQuery query) {
        assertAvailable();
        FilterSql filter = matchFilter(query);
        String order = matchOrder(query);
        int page = Math.max(1, query.page);
        int size = Math.min(200, Math.max(1, query.pageSize));
        String from = """
                FROM %s m
                JOIN %s p ON p.id = m.person_id
                LEFT JOIN %s a ON a.openalex_author_id = m.openalex_author_id
                """.formatted(qualified(MATCH), qualified("pure_person"), qualified(AUTHOR));
        long total = scalarLong("SELECT COUNT(*) " + from + filter.sql, filter.params.toArray());
        String sql = """
                SELECT m.*, p.pure_id, p.uuid, p.first_name, p.last_name, p.orcid AS person_orcid, p.cargo,
                       a.display_name AS author_display_name, a.orcid AS author_orcid
                """ + from + filter.sql + " ORDER BY " + order + ", m.id LIMIT ? OFFSET ?";
        List<OpenAlexMatchSummaryDto> items = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (Object param : filter.params) {
                statement.setObject(index++, param);
            }
            statement.setInt(index++, size);
            statement.setInt(index, (page - 1) * size);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    items.add(mapMatch(rs));
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list OpenAlex matches", ex);
        }
        return PageResponse.of(items, page - 1, size, total);
    }

    public List<OpenAlexMatchSummaryDto> listMatchesForExport(OpenAlexMatchQuery query) {
        OpenAlexMatchQuery copy = query;
        copy.page = 1;
        copy.pageSize = 20_000;
        return listMatches(copy).items;
    }

    public OpenAlexMatchDetailDto findMatchDetail(long id) {
        return findMatchDetail(id, 1, OpenAlexMatchWorkPaging.DEFAULT_SIZE, null);
    }

    public OpenAlexMatchDetailDto findMatchDetail(long id, Integer workPage, Integer workSize, String workSearch) {
        if (!available()) {
            return null;
        }
        int page = OpenAlexMatchWorkPaging.page(workPage);
        int size = OpenAlexMatchWorkPaging.size(workSize);
        String search = OpenAlexMatchWorkPaging.search(workSearch);
        String sql = """
                SELECT m.*, p.pure_id, p.uuid, p.first_name, p.last_name, p.orcid AS person_orcid, p.cargo,
                       a.display_name AS author_display_name, a.orcid AS author_orcid
                FROM %s m
                JOIN %s p ON p.id = m.person_id
                LEFT JOIN %s a ON a.openalex_author_id = m.openalex_author_id
                WHERE m.id = ?
                """.formatted(qualified(MATCH), qualified("pure_person"), qualified(AUTHOR));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                OpenAlexMatchDetailDto dto = new OpenAlexMatchDetailDto();
                copyMatch(mapMatch(rs), dto);
                dto.workPage = page;
                dto.workSize = size;
                dto.worksTotal = countMatchWorks(connection, id, search);
                dto.works.addAll(loadMatchWorks(connection, id, page, size, search));
                dto.conclusion = conclusion(dto);
                return dto;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load OpenAlex match detail", ex);
        }
    }

    public OpenAlexFiltersDto filters() {
        assertAvailable();
        OpenAlexFiltersDto dto = new OpenAlexFiltersDto();
        dto.statuses.addAll(distinct(WORK, "last_outcome"));
        dto.matchStatuses.addAll(distinct(MATCH, "status"));
        dto.workTypes.addAll(distinct(WORK, "work_type"));
        if (tableExists("pure_person")) {
            dto.positions.addAll(distinct("pure_person", "cargo"));
        }
        return dto;
    }

    public OpenAlexPersonIdentityDto identityForPerson(long personId) {
        if (!available()) {
            return null;
        }
        String sql = """
                SELECT m.openalex_author_id, m.status, m.matching_work_count, m.selected, m.selected_manually,
                       a.display_name
                FROM %s m
                LEFT JOIN %s a ON a.openalex_author_id = m.openalex_author_id
                WHERE m.person_id = ?
                ORDER BY m.selected DESC, m.status = 'CONFIRMED' DESC, m.matching_work_count DESC, m.id
                LIMIT 1
                """.formatted(qualified(MATCH), qualified(AUTHOR));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, personId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                OpenAlexPersonIdentityDto dto = new OpenAlexPersonIdentityDto();
                dto.openalexAuthorId = nullableString(rs, "openalex_author_id");
                dto.displayName = nullableString(rs, "display_name");
                dto.status = nullableString(rs, "status");
                dto.matchingWorkCount = nullableInt(rs, "matching_work_count");
                dto.confirmed = "CONFIRMED".equals(dto.status);
                dto.openalexUrl = OpenAlexId.url(dto.openalexAuthorId);
                return dto;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load OpenAlex identity", ex);
        }
    }

    public List<QualityRow> listQualityRows(Long personId) {
        if (!available()) {
            return List.of();
        }
        String extra = personId == null ? "" : " AND p.id = ?";
        String sql = """
                SELECT p.id AS person_id, p.pure_id, p.uuid, p.orcid AS person_orcid,
                       m.openalex_author_id, m.status, m.matching_work_count, m.orcid_match, a.orcid AS author_orcid
                FROM %s p
                LEFT JOIN %s m ON m.person_id = p.id
                LEFT JOIN %s a ON a.openalex_author_id = m.openalex_author_id
                WHERE 1=1 %s
                """.formatted(qualified("pure_person"), qualified(MATCH), qualified(AUTHOR), extra);
        List<QualityRow> items = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (personId != null) {
                statement.setLong(1, personId);
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    QualityRow row = new QualityRow();
                    row.personId = rs.getLong("person_id");
                    row.pureId = nullableLong(rs, "pure_id");
                    row.uuid = nullableString(rs, "uuid");
                    row.personOrcid = nullableString(rs, "person_orcid");
                    row.openalexAuthorId = nullableString(rs, "openalex_author_id");
                    row.status = nullableString(rs, "status");
                    row.matchingWorkCount = nullableInt(rs, "matching_work_count");
                    row.orcidMatch = rs.getBoolean("orcid_match");
                    row.authorOrcid = nullableString(rs, "author_orcid");
                    items.add(row);
                }
            }
            return items;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load OpenAlex quality rows", ex);
        }
    }

    public List<String> authorIdsForPerson(long personId) {
        List<String> items = new ArrayList<>();
        if (!tableExists(MATCH)) {
            return items;
        }
        String sql = "SELECT openalex_author_id FROM " + qualified(MATCH)
                + " WHERE person_id = ? AND status IN ('STRONG','MANUAL_REVIEW','CONFIRMED','CONFLICT')";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, personId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    items.add(rs.getString(1));
                }
            }
            return items;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load OpenAlex authors for person", ex);
        }
    }

    public List<Long> personIdsForAuthor(String openalexAuthorId) {
        List<Long> items = new ArrayList<>();
        if (!tableExists(MATCH) || openalexAuthorId == null) {
            return items;
        }
        String sql = "SELECT person_id FROM " + qualified(MATCH)
                + " WHERE openalex_author_id = ? AND status IN ('STRONG','MANUAL_REVIEW','CONFIRMED','CONFLICT')";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, openalexAuthorId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    items.add(rs.getLong(1));
                }
            }
            return items;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load persons for OpenAlex author", ex);
        }
    }

    private void indexLinkedLookupDois(Connection connection, Map<String, WorkSyncState> items) throws SQLException {
        if (!tableExists(LINK) || !tableExists("pure_research_output_electronic_version")) {
            return;
        }
        String sql = """
                SELECT ev.doi, w.last_outcome, w.last_sync_at, w.needs_refresh
                FROM %s w
                JOIN %s m ON m.openalex_work_id = w.id
                JOIN %s ev ON ev.id = m.electronic_version_id
                WHERE ev.doi IS NOT NULL AND TRIM(ev.doi) <> ''
                """.formatted(
                qualified(WORK),
                qualified(LINK),
                qualified("pure_research_output_electronic_version")
        );
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                String normalized = DoiNormalizer.normalize(rs.getString("doi"));
                if (normalized == null || items.containsKey(normalized)) {
                    continue;
                }
                WorkSyncState state = new WorkSyncState();
                state.present = true;
                state.status = nullableString(rs, "last_outcome");
                state.lastSyncAt = timestamp(rs, "last_sync_at");
                state.needsRefresh = rs.getBoolean("needs_refresh");
                items.put(normalized.toLowerCase(Locale.ROOT), state);
            }
        }
    }

    private void addLinkedLookupDois(Connection connection, Set<String> items) throws SQLException {
        if (!tableExists("pure_research_output_electronic_version")) {
            return;
        }
        String sql = """
                SELECT ev.doi
                FROM %s m
                JOIN %s ev ON ev.id = m.electronic_version_id
                WHERE COALESCE(m.active_in_pure, 1) = 1
                  AND ev.doi IS NOT NULL AND TRIM(ev.doi) <> ''
                """.formatted(qualified(LINK), qualified("pure_research_output_electronic_version"));
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                String normalized = DoiNormalizer.normalize(rs.getString(1));
                if (normalized != null) {
                    items.add(normalized.toLowerCase(Locale.ROOT));
                }
            }
        }
    }

    private StoredWork findByOpenAlexWorkId(Connection connection, String openalexWorkId) throws SQLException {
        String sql = "SELECT * FROM " + qualified(WORK) + " WHERE openalex_work_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, openalexWorkId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? mapStored(rs) : null;
            }
        }
    }

    private static OpenAlexWorkIdentity.Snapshot snapshot(StoredWork stored) {
        if (stored == null) {
            return null;
        }
        return OpenAlexWorkIdentity.Snapshot.of(stored.id, stored.openalexWorkId, stored.normalizedDoi);
    }

    private static StoredWork storedOf(
            OpenAlexWorkIdentity.Decision decision,
            StoredWork byOa,
            StoredWork byCanonical,
            StoredWork byLookup
    ) {
        if (decision != null && decision.existing != null) {
            long id = decision.existing.id;
            if (byOa != null && byOa.id == id) {
                return byOa;
            }
            if (byCanonical != null && byCanonical.id == id) {
                return byCanonical;
            }
            if (byLookup != null && byLookup.id == id) {
                return byLookup;
            }
        }
        if (byOa != null) {
            return byOa;
        }
        if (byCanonical != null) {
            return byCanonical;
        }
        return byLookup;
    }

    private StoredWork recoverDuplicate(
            Connection connection,
            SQLException ex,
            String openalexWorkId,
            String canonicalDoi
    ) throws SQLException {
        if (MysqlDuplicateKey.of(ex, MysqlDuplicateKey.OPENALEX_WORK_OA_ID) && openalexWorkId != null) {
            return findByOpenAlexWorkId(connection, openalexWorkId);
        }
        if (MysqlDuplicateKey.of(ex, MysqlDuplicateKey.OPENALEX_WORK_DOI) && canonicalDoi != null) {
            return findByNormalizedDoi(connection, canonicalDoi);
        }
        return null;
    }

    private void markDoiOaConflict(
            Connection connection,
            long workId,
            OpenAlexFetchResult fetch,
            String syncId,
            String storedOpenAlexId,
            String incomingOpenAlexId
    ) throws SQLException {
        String sql = """
                UPDATE %s SET last_error_code = ?, last_error_message = ?, last_http_status = ?,
                    last_sync_execution_id = ?, last_sync_at = NOW(6)
                WHERE id = ?
                """.formatted(qualified(WORK));
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, OpenAlexWorkIdentity.DOI_OA_ID_CONFLICT);
            statement.setString(2, "Lookup DOI resolved to " + incomingOpenAlexId
                    + " but stored work is " + storedOpenAlexId);
            setNullableInt(statement, 3, fetch.httpStatus == 0 ? null : fetch.httpStatus);
            bindSyncId(statement, 4, WORK, "last_sync_execution_id", syncId);
            statement.setLong(5, workId);
            statement.executeUpdate();
        }
    }

    private static String pureUuid(List<OpenAlexDoiLink> links) {
        if (links == null) {
            return null;
        }
        for (OpenAlexDoiLink link : links) {
            if (link != null && link.uuid != null && !link.uuid.isBlank()) {
                return link.uuid.trim();
            }
        }
        return null;
    }

    private StoredWork findByNormalizedDoi(Connection connection, String normalizedDoi) throws SQLException {
        String sql = "SELECT * FROM " + qualified(WORK) + " WHERE normalized_doi = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizedDoi);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? mapStored(rs) : null;
            }
        }
    }

    private long insertWork(
            Connection connection,
            String normalizedDoi,
            String displayDoi,
            OpenAlexWorkRecord work,
            OpenAlexFetchResult fetch,
            String syncId,
            String parentSnap,
            String childSnap
    ) throws SQLException {
        String sql = """
                INSERT INTO %s (
                    openalex_work_id, doi, normalized_doi, display_name, publication_year, publication_date, work_type,
                    cited_by_count, referenced_works_count, is_oa, oa_status, language, primary_source_id,
                    primary_source_display_name, primary_source_issn_l, primary_topic_id, primary_topic_display_name,
                    primary_topic_score, domain_id, domain_display_name, field_id, field_display_name, subfield_id,
                    subfield_display_name, authorships_count, authorships_truncated, openalex_updated_date,
                    last_outcome, last_http_status, last_error_code, last_error_message, last_sync_execution_id,
                    last_sync_at, last_change_detected_at, needs_refresh, parent_snapshot, authorship_snapshot,
                    raw_json, raw_json_truncated, raw_json_original_size
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NOW(6),NOW(6),0,?,?,?,?,?)
                """.formatted(qualified(WORK));
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindWork(statement, 1, work, fetch, normalizedDoi, displayDoi, syncId, parentSnap, childSnap);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("openalex_work insert did not return id");
                }
                return keys.getLong(1);
            }
        }
    }

    private void updateWork(
            Connection connection,
            long id,
            String displayDoi,
            OpenAlexWorkRecord work,
            OpenAlexFetchResult fetch,
            String syncId,
            String parentSnap,
            String childSnap
    ) throws SQLException {
        String sql = """
                UPDATE %s SET
                    openalex_work_id=?, doi=?, display_name=?, publication_year=?, publication_date=?, work_type=?,
                    cited_by_count=?, referenced_works_count=?, is_oa=?, oa_status=?, language=?, primary_source_id=?,
                    primary_source_display_name=?, primary_source_issn_l=?, primary_topic_id=?, primary_topic_display_name=?,
                    primary_topic_score=?, domain_id=?, domain_display_name=?, field_id=?, field_display_name=?,
                    subfield_id=?, subfield_display_name=?, authorships_count=?, authorships_truncated=?,
                    openalex_updated_date=?, last_outcome=?, last_http_status=?, last_error_code=?, last_error_message=?,
                    last_sync_execution_id=?, last_sync_at=NOW(6), last_change_detected_at=NOW(6), needs_refresh=0,
                    parent_snapshot=?, authorship_snapshot=?, raw_json=?, raw_json_truncated=?, raw_json_original_size=?
                WHERE id=?
                """.formatted(qualified(WORK));
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = bindWork(statement, 1, work, fetch, null, displayDoi, syncId, parentSnap, childSnap);
            statement.setLong(index, id);
            statement.executeUpdate();
        }
    }

    private void touch(Connection connection, long id, OpenAlexFetchResult fetch, String syncId) throws SQLException {
        String sql = """
                UPDATE %s SET last_sync_at = NOW(6), last_http_status = ?, last_error_code = ?,
                    last_error_message = ?, last_sync_execution_id = ?, last_outcome = ?, needs_refresh = 0
                WHERE id = ?
                """.formatted(qualified(WORK));
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            setNullableInt(statement, 1, fetch.httpStatus == 0 ? null : fetch.httpStatus);
            statement.setString(2, fetch.errorCode);
            statement.setString(3, fetch.errorMessage);
            bindSyncId(statement, 4, WORK, "last_sync_execution_id", syncId);
            statement.setString(5, fetch.status);
            statement.setLong(6, id);
            statement.executeUpdate();
        }
    }

    private int bindWork(
            PreparedStatement statement,
            int start,
            OpenAlexWorkRecord work,
            OpenAlexFetchResult fetch,
            String normalizedDoi,
            String displayDoi,
            String syncId,
            String parentSnap,
            String childSnap
    ) throws SQLException {
        int i = start;
        OpenAlexTopicRecord primary = work.topics.stream().filter(item -> item.primary).findFirst().orElse(null);
        statement.setString(i++, work.openalexWorkId);
        statement.setString(i++, displayDoi);
        if (normalizedDoi != null) {
            statement.setString(i++, normalizedDoi);
        }
        statement.setString(i++, work.displayName);
        setNullableInt(statement, i++, work.publicationYear);
        statement.setString(i++, work.publicationDate);
        statement.setString(i++, work.workType);
        setNullableInt(statement, i++, work.citedByCount);
        setNullableInt(statement, i++, work.referencedWorksCount);
        if (work.isOa == null) {
            statement.setNull(i++, Types.BOOLEAN);
        } else {
            statement.setBoolean(i++, work.isOa);
        }
        statement.setString(i++, work.oaStatus);
        statement.setString(i++, work.language);
        statement.setString(i++, work.primarySourceId);
        statement.setString(i++, work.primarySourceDisplayName);
        statement.setString(i++, work.primarySourceIssnL);
        statement.setString(i++, primary == null ? null : primary.topicId);
        statement.setString(i++, primary == null ? null : primary.displayName);
        if (primary == null || primary.score == null) {
            statement.setNull(i++, Types.DECIMAL);
        } else {
            statement.setBigDecimal(i++, java.math.BigDecimal.valueOf(primary.score));
        }
        statement.setString(i++, primary == null ? null : primary.domainId);
        statement.setString(i++, primary == null ? null : primary.domainDisplayName);
        statement.setString(i++, primary == null ? null : primary.fieldId);
        statement.setString(i++, primary == null ? null : primary.fieldDisplayName);
        statement.setString(i++, primary == null ? null : primary.subfieldId);
        statement.setString(i++, primary == null ? null : primary.subfieldDisplayName);
        setNullableInt(statement, i++, work.authorships.size());
        statement.setBoolean(i++, work.authorshipsTruncated());
        statement.setString(i++, work.updatedDate);
        statement.setString(i++, fetch.status);
        setNullableInt(statement, i++, fetch.httpStatus == 0 ? null : fetch.httpStatus);
        statement.setString(i++, fetch.errorCode);
        statement.setString(i++, fetch.errorMessage);
        bindSyncId(statement, i++, WORK, "last_sync_execution_id", syncId);
        statement.setString(i++, parentSnap);
        statement.setString(i++, childSnap);
        statement.setString(i++, work.rawJson);
        statement.setBoolean(i++, work.rawJsonTruncated);
        setNullableInt(statement, i++, work.rawJsonOriginalSize);
        return i;
    }

    private void replaceChildren(Connection connection, long workId, OpenAlexWorkRecord work) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM " + qualified(AUTHORSHIP) + " WHERE openalex_work_id = ?")) {
            delete.setLong(1, workId);
            delete.executeUpdate();
        }
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM " + qualified(TOPIC) + " WHERE openalex_work_id = ?")) {
            delete.setLong(1, workId);
            delete.executeUpdate();
        }
        if (work.authorships.isEmpty() && work.topics.isEmpty()) {
            return;
        }
        String authorshipSql = """
                INSERT INTO %s (
                    openalex_work_id, source_order, author_position, is_corresponding, raw_author_name,
                    raw_affiliation_string, openalex_author_id, author_display_name, author_orcid, observed_orcid
                ) VALUES (?,?,?,?,?,?,?,?,?,?)
                """.formatted(qualified(AUTHORSHIP));
        String instSql = """
                INSERT INTO %s (authorship_id, source_order, openalex_institution_id, display_name, ror, country_code, type)
                VALUES (?,?,?,?,?,?,?)
                """.formatted(qualified(INST));
        try (PreparedStatement authorship = connection.prepareStatement(authorshipSql, Statement.RETURN_GENERATED_KEYS);
             PreparedStatement inst = connection.prepareStatement(instSql)) {
            for (OpenAlexAuthorshipRecord row : work.authorships) {
                authorship.setLong(1, workId);
                authorship.setInt(2, row.sourceOrder);
                authorship.setString(3, row.authorPosition);
                authorship.setBoolean(4, row.corresponding);
                authorship.setString(5, row.rawAuthorName);
                authorship.setString(6, row.rawAffiliationString);
                authorship.setString(7, row.openalexAuthorId);
                authorship.setString(8, row.authorDisplayName);
                authorship.setString(9, row.authorOrcid);
                authorship.setString(10, row.observedOrcid);
                authorship.executeUpdate();
                long authorshipId;
                try (ResultSet keys = authorship.getGeneratedKeys()) {
                    if (!keys.next()) {
                        continue;
                    }
                    authorshipId = keys.getLong(1);
                }
                for (OpenAlexInstitutionRecord institution : row.institutions) {
                    inst.setLong(1, authorshipId);
                    inst.setInt(2, institution.sourceOrder);
                    inst.setString(3, institution.openalexInstitutionId);
                    inst.setString(4, institution.displayName);
                    inst.setString(5, institution.ror);
                    inst.setString(6, institution.countryCode);
                    inst.setString(7, institution.type);
                    inst.addBatch();
                }
            }
            inst.executeBatch();
        }
        String topicSql = """
                INSERT INTO %s (
                    openalex_work_id, topic_id, display_name, score, is_primary, domain_id, domain_display_name,
                    field_id, field_display_name, subfield_id, subfield_display_name
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """.formatted(qualified(TOPIC));
        try (PreparedStatement topic = connection.prepareStatement(topicSql)) {
            for (OpenAlexTopicRecord row : work.topics) {
                if (row.topicId == null) {
                    continue;
                }
                topic.setLong(1, workId);
                topic.setString(2, row.topicId);
                topic.setString(3, row.displayName);
                if (row.score == null) {
                    topic.setNull(4, Types.DECIMAL);
                } else {
                    topic.setBigDecimal(4, java.math.BigDecimal.valueOf(row.score));
                }
                topic.setBoolean(5, row.primary);
                topic.setString(6, row.domainId);
                topic.setString(7, row.domainDisplayName);
                topic.setString(8, row.fieldId);
                topic.setString(9, row.fieldDisplayName);
                topic.setString(10, row.subfieldId);
                topic.setString(11, row.subfieldDisplayName);
                topic.addBatch();
            }
            topic.executeBatch();
        }
    }

    private void reconcileLinks(Connection connection, long workId, List<OpenAlexDoiLink> links) throws SQLException {
        Map<String, Long> existing = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, research_output_id, electronic_version_id FROM " + qualified(LINK)
                        + " WHERE openalex_work_id = ?")) {
            statement.setLong(1, workId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    existing.put(linkKey(rs.getLong("research_output_id"), nullableLong(rs, "electronic_version_id")),
                            rs.getLong("id"));
                }
            }
        }
        Set<String> incoming = new HashSet<>();
        if (links != null) {
            for (OpenAlexDoiLink link : links) {
                String key = linkKey(link.researchOutputId, link.electronicVersionId);
                incoming.add(key);
                if (existing.containsKey(key)) {
                    continue;
                }
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO " + qualified(LINK)
                                + " (openalex_work_id, research_output_id, electronic_version_id, active_in_pure) VALUES (?,?,?,?)")) {
                    insert.setLong(1, workId);
                    insert.setLong(2, link.researchOutputId);
                    if (link.electronicVersionId == null) {
                        insert.setNull(3, Types.BIGINT);
                    } else {
                        insert.setLong(3, link.electronicVersionId);
                    }
                    insert.setBoolean(4, link.activeInPure);
                    insert.executeUpdate();
                }
            }
        }
        for (Map.Entry<String, Long> entry : existing.entrySet()) {
            if (!incoming.contains(entry.getKey())) {
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM " + qualified(LINK) + " WHERE id = ?")) {
                    delete.setLong(1, entry.getValue());
                    delete.executeUpdate();
                }
            }
        }
    }

    private void addFieldChanges(
            OpenAlexReconcileResult result,
            StoredWork stored,
            OpenAlexWorkRecord incoming,
            OpenAlexFetchResult fetch,
            long workId,
            String doi
    ) {
        if (!OpenAlexWorkSnapshot.same(stored.authorshipSnapshot, OpenAlexWorkSnapshot.authorships(incoming))) {
            result.changes.add(SyncChange.updated("CHILD", "AUTHORSHIP_CHANGED", stored.authorshipSnapshot,
                    OpenAlexWorkSnapshot.authorships(incoming)).on("OPENALEX", workId, null, doi));
        }
        if (stored.citedByCount != null && incoming.citedByCount != null
                && !stored.citedByCount.equals(incoming.citedByCount)) {
            result.changes.add(SyncChange.updated("PARENT", "CITATION_COUNT_CHANGED",
                    String.valueOf(stored.citedByCount), String.valueOf(incoming.citedByCount))
                    .on("OPENALEX", workId, null, doi));
        }
        if (!OpenAlexStatus.FOUND.equals(fetch.status) && stored.lastOutcome != null
                && !stored.lastOutcome.equals(fetch.status)) {
            result.changes.add(SyncChange.updated("PARENT", "OUTCOME_CHANGED", stored.lastOutcome, fetch.status)
                    .on("OPENALEX", workId, null, doi));
        }
    }

    private StoredMatch findMatch(long personId, String openalexAuthorId) {
        String sql = "SELECT id, status, selected, selected_manually FROM " + qualified(MATCH)
                + " WHERE person_id = ? AND openalex_author_id = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, personId);
            statement.setString(2, openalexAuthorId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                StoredMatch match = new StoredMatch();
                match.id = rs.getLong("id");
                match.status = nullableString(rs, "status");
                match.selected = rs.getBoolean("selected");
                match.selectedManually = rs.getBoolean("selected_manually");
                return match;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load OpenAlex match", ex);
        }
    }

    private void replaceMatchWorks(long matchId, List<OpenAlexPersonMatcher.WorkEvidence> works) {
        List<OpenAlexMatchWorkPlanner.MatchWorkGroup> groups = OpenAlexMatchWorkPlanner.groupByResearchOutput(works);
        boolean writeEvidence = tableExists(MATCH_WORK_EVIDENCE);
        try (Connection connection = connection()) {
            boolean previous = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                for (OpenAlexMatchWorkPlanner.MatchWorkGroup group : groups) {
                    long matchWorkId = upsertMatchWork(connection, matchId, group);
                    if (writeEvidence && matchWorkId > 0) {
                        replaceMatchWorkEvidences(connection, matchWorkId, group.evidences);
                    }
                }
                recountMatchingWorks(connection, matchId);
                connection.commit();
            } catch (SQLException ex) {
                rollbackQuietly(connection);
                throw ex;
            } finally {
                restoreAutoCommit(connection, previous);
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to replace OpenAlex match works", ex);
        }
    }

    /**
     * Removes auto-generated match_work rows for this DOI/person set that the
     * current pass did not re-select. Does not delete CONFIRMED/REJECTED matches
     * and does not wipe other DOIs of a kept match.
     */
    public void pruneStaleMatchWorks(
            Collection<Long> personIds,
            Collection<Long> outputIds,
            Set<String> keptPersonAuthorKeys
    ) {
        if (personIds == null || personIds.isEmpty() || outputIds == null || outputIds.isEmpty()) {
            return;
        }
        List<Long> persons = new ArrayList<>(personIds);
        List<Long> outputs = new ArrayList<>(outputIds);
        Set<String> kept = keptPersonAuthorKeys == null ? Set.of() : keptPersonAuthorKeys;
        StringBuilder sql = new StringBuilder("""
                DELETE mw FROM %s mw
                INNER JOIN %s m ON m.id = mw.match_id
                WHERE mw.research_output_id IN (%s)
                  AND m.person_id IN (%s)
                  AND m.status IN ('STRONG','MANUAL_REVIEW','CONFLICT')
                """.formatted(
                qualified(MATCH_WORK),
                qualified(MATCH),
                inPlaceholders(outputs.size()),
                inPlaceholders(persons.size())
        ));
        List<String> keptPairs = new ArrayList<>();
        for (String key : kept) {
            if (key != null && key.contains("|")) {
                keptPairs.add(key);
            }
        }
        if (!keptPairs.isEmpty()) {
            sql.append(" AND CONCAT(m.person_id, '|', m.openalex_author_id) NOT IN (")
                    .append(inPlaceholders(keptPairs.size()))
                    .append(")");
        }
        try (Connection connection = connection()) {
            boolean previous = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                    int index = 1;
                    for (Long outputId : outputs) {
                        statement.setLong(index++, outputId);
                    }
                    for (Long personId : persons) {
                        statement.setLong(index++, personId);
                    }
                    for (String key : keptPairs) {
                        statement.setString(index++, key);
                    }
                    statement.executeUpdate();
                }
                String emptyMatches = """
                        DELETE FROM %s
                        WHERE person_id IN (%s)
                          AND status IN ('STRONG','MANUAL_REVIEW','CONFLICT')
                          AND NOT EXISTS (
                              SELECT 1 FROM %s w WHERE w.match_id = %s.id
                          )
                        """.formatted(
                        qualified(MATCH),
                        inPlaceholders(persons.size()),
                        qualified(MATCH_WORK),
                        qualified(MATCH)
                );
                try (PreparedStatement statement = connection.prepareStatement(emptyMatches)) {
                    int index = 1;
                    for (Long personId : persons) {
                        statement.setLong(index++, personId);
                    }
                    statement.executeUpdate();
                }
                String recount = """
                        UPDATE %s m
                        SET matching_work_count = (
                            SELECT COUNT(*) FROM %s w WHERE w.match_id = m.id
                        )
                        WHERE m.person_id IN (%s)
                        """.formatted(qualified(MATCH), qualified(MATCH_WORK), inPlaceholders(persons.size()));
                try (PreparedStatement statement = connection.prepareStatement(recount)) {
                    int index = 1;
                    for (Long personId : persons) {
                        statement.setLong(index++, personId);
                    }
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (SQLException ex) {
                rollbackQuietly(connection);
                throw ex;
            } finally {
                restoreAutoCommit(connection, previous);
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to prune OpenAlex match works", ex);
        }
    }

    private long upsertMatchWork(
            Connection connection,
            long matchId,
            OpenAlexMatchWorkPlanner.MatchWorkGroup group
    ) throws SQLException {
        OpenAlexPersonMatcher.WorkEvidence primary = group.primary;
        if (primary == null || group.researchOutputId == null) {
            return 0L;
        }
        String sql = """
                INSERT INTO %s (
                    match_id, research_output_id, normalized_doi, openalex_work_oa_id, contributor_id,
                    contributor_first_name, contributor_last_name, contributor_position, contributor_count,
                    raw_author_name, author_position, source_order, name_compatible, name_partition_different,
                    position_code, orcid_pure, orcid_openalex, uss_affiliation, affiliation_display, ror, reason_codes
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE
                    normalized_doi = VALUES(normalized_doi),
                    openalex_work_oa_id = VALUES(openalex_work_oa_id),
                    contributor_id = VALUES(contributor_id),
                    contributor_first_name = VALUES(contributor_first_name),
                    contributor_last_name = VALUES(contributor_last_name),
                    contributor_position = VALUES(contributor_position),
                    contributor_count = VALUES(contributor_count),
                    raw_author_name = VALUES(raw_author_name),
                    author_position = VALUES(author_position),
                    source_order = VALUES(source_order),
                    name_compatible = VALUES(name_compatible),
                    name_partition_different = VALUES(name_partition_different),
                    position_code = VALUES(position_code),
                    orcid_pure = VALUES(orcid_pure),
                    orcid_openalex = VALUES(orcid_openalex),
                    uss_affiliation = VALUES(uss_affiliation),
                    affiliation_display = VALUES(affiliation_display),
                    ror = VALUES(ror),
                    reason_codes = VALUES(reason_codes)
                """.formatted(qualified(MATCH_WORK));
        try (PreparedStatement insert = connection.prepareStatement(sql)) {
            bindMatchWork(insert, matchId, primary);
            insert.executeUpdate();
        }
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT id FROM " + qualified(MATCH_WORK) + " WHERE match_id = ? AND research_output_id = ?")) {
            select.setLong(1, matchId);
            select.setLong(2, group.researchOutputId);
            try (ResultSet rs = select.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    private void bindMatchWork(
            PreparedStatement insert,
            long matchId,
            OpenAlexPersonMatcher.WorkEvidence work
    ) throws SQLException {
        insert.setLong(1, matchId);
        insert.setLong(2, work.researchOutputId);
        insert.setString(3, work.normalizedDoi);
        insert.setString(4, work.openalexWorkOaId);
        if (work.contributorId == null) {
            insert.setNull(5, Types.BIGINT);
        } else {
            insert.setLong(5, work.contributorId);
        }
        insert.setString(6, work.contributorFirst);
        insert.setString(7, work.contributorLast);
        setNullableInt(insert, 8, work.contributorPosition);
        setNullableInt(insert, 9, work.contributorCount);
        insert.setString(10, work.rawAuthorName);
        insert.setString(11, work.authorPosition);
        setNullableInt(insert, 12, work.sourceOrder);
        insert.setBoolean(13, Boolean.TRUE.equals(work.nameCompatible));
        insert.setBoolean(14, Boolean.TRUE.equals(work.namePartitionDifferent));
        insert.setString(15, work.positionCode);
        insert.setString(16, work.orcidPure);
        insert.setString(17, work.orcidOpenAlex);
        insert.setBoolean(18, work.ussAffiliation);
        insert.setString(19, work.affiliationDisplay);
        insert.setString(20, work.ror);
        insert.setString(21, String.join(",", work.reasonCodes));
    }

    private void replaceMatchWorkEvidences(
            Connection connection,
            long matchWorkId,
            List<OpenAlexPersonMatcher.WorkEvidence> evidences
    ) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM " + qualified(MATCH_WORK_EVIDENCE) + " WHERE match_work_id = ?")) {
            delete.setLong(1, matchWorkId);
            delete.executeUpdate();
        }
        if (evidences == null || evidences.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO %s (
                    match_work_id, authorship_id, source_order, raw_author_name, author_display_name,
                    openalex_author_id, author_position, affiliation_display, ror, name_compatible,
                    name_partition_different, position_code, orcid_pure, orcid_openalex, uss_affiliation,
                    reason_codes
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """.formatted(qualified(MATCH_WORK_EVIDENCE));
        try (PreparedStatement insert = connection.prepareStatement(sql)) {
            for (OpenAlexPersonMatcher.WorkEvidence evidence : evidences) {
                insert.setLong(1, matchWorkId);
                if (evidence.authorshipId == null) {
                    insert.setNull(2, Types.BIGINT);
                } else {
                    insert.setLong(2, evidence.authorshipId);
                }
                insert.setInt(3, evidence.sourceOrder == null ? 0 : evidence.sourceOrder);
                insert.setString(4, evidence.rawAuthorName);
                insert.setString(5, evidence.authorDisplayName);
                insert.setString(6, evidence.openAlexAuthorId);
                insert.setString(7, evidence.authorPosition);
                insert.setString(8, evidence.affiliationDisplay);
                insert.setString(9, evidence.ror);
                insert.setBoolean(10, Boolean.TRUE.equals(evidence.nameCompatible));
                insert.setBoolean(11, Boolean.TRUE.equals(evidence.namePartitionDifferent));
                insert.setString(12, evidence.positionCode);
                insert.setString(13, evidence.orcidPure);
                insert.setString(14, evidence.orcidOpenAlex);
                insert.setBoolean(15, evidence.ussAffiliation);
                insert.setString(16, String.join(",", evidence.reasonCodes));
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private void recountMatchingWorks(Connection connection, long matchId) throws SQLException {
        String sql = """
                UPDATE %s SET matching_work_count = (
                    SELECT COUNT(*) FROM %s WHERE match_id = ?
                ) WHERE id = ?
                """.formatted(qualified(MATCH), qualified(MATCH_WORK));
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, matchId);
            statement.setLong(2, matchId);
            statement.executeUpdate();
        }
    }

    private void loadInstitutions(Connection connection, long workId, List<OpenAlexAuthorshipRecord> authorships)
            throws SQLException {
        Map<Integer, OpenAlexAuthorshipRecord> byOrder = new HashMap<>();
        for (OpenAlexAuthorshipRecord item : authorships) {
            byOrder.put(item.sourceOrder, item);
        }
        String sql = """
                SELECT a.source_order AS authorship_order, i.*
                FROM %s i
                JOIN %s a ON a.id = i.authorship_id
                WHERE a.openalex_work_id = ?
                ORDER BY i.source_order, i.id
                """.formatted(qualified(INST), qualified(AUTHORSHIP));
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, workId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    OpenAlexAuthorshipRecord authorship = byOrder.get(rs.getInt("authorship_order"));
                    if (authorship == null) {
                        continue;
                    }
                    OpenAlexInstitutionRecord institution = new OpenAlexInstitutionRecord();
                    institution.sourceOrder = rs.getInt("source_order");
                    institution.openalexInstitutionId = nullableString(rs, "openalex_institution_id");
                    institution.displayName = nullableString(rs, "display_name");
                    institution.ror = nullableString(rs, "ror");
                    institution.countryCode = nullableString(rs, "country_code");
                    institution.type = nullableString(rs, "type");
                    authorship.institutions.add(institution);
                }
            }
        }
    }

    private List<OpenAlexAuthorshipDto> loadAuthorshipDtos(Connection connection, long workId) throws SQLException {
        List<OpenAlexAuthorshipRecord> rows = new ArrayList<>();
        String sql = "SELECT * FROM " + qualified(AUTHORSHIP) + " WHERE openalex_work_id = ? ORDER BY source_order, id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, workId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    OpenAlexAuthorshipRecord row = new OpenAlexAuthorshipRecord();
                    row.sourceOrder = rs.getInt("source_order");
                    row.authorPosition = nullableString(rs, "author_position");
                    row.corresponding = rs.getBoolean("is_corresponding");
                    row.rawAuthorName = nullableString(rs, "raw_author_name");
                    row.rawAffiliationString = nullableString(rs, "raw_affiliation_string");
                    row.openalexAuthorId = nullableString(rs, "openalex_author_id");
                    row.authorDisplayName = nullableString(rs, "author_display_name");
                    row.authorOrcid = nullableString(rs, "author_orcid");
                    row.observedOrcid = nullableString(rs, "observed_orcid");
                    rows.add(row);
                }
            }
        }
        loadInstitutions(connection, workId, rows);
        List<OpenAlexAuthorshipDto> items = new ArrayList<>();
        for (OpenAlexAuthorshipRecord row : rows) {
            OpenAlexAuthorshipDto dto = new OpenAlexAuthorshipDto();
            dto.sourceOrder = row.sourceOrder;
            dto.authorPosition = row.authorPosition;
            dto.corresponding = row.corresponding;
            dto.rawAuthorName = row.rawAuthorName;
            dto.rawAffiliationString = row.rawAffiliationString;
            dto.openalexAuthorId = row.openalexAuthorId;
            dto.authorDisplayName = row.authorDisplayName;
            dto.authorOrcid = row.authorOrcid;
            dto.observedOrcid = row.observedOrcid;
            dto.openalexUrl = OpenAlexId.url(row.openalexAuthorId);
            for (OpenAlexInstitutionRecord institution : row.institutions) {
                OpenAlexInstitutionDto item = new OpenAlexInstitutionDto();
                item.sourceOrder = institution.sourceOrder;
                item.openalexInstitutionId = institution.openalexInstitutionId;
                item.displayName = institution.displayName;
                item.ror = institution.ror;
                item.countryCode = institution.countryCode;
                item.type = institution.type;
                dto.institutions.add(item);
            }
            items.add(dto);
        }
        return items;
    }

    private List<OpenAlexTopicDto> loadTopics(Connection connection, long workId) throws SQLException {
        List<OpenAlexTopicDto> items = new ArrayList<>();
        String sql = "SELECT * FROM " + qualified(TOPIC) + " WHERE openalex_work_id = ? ORDER BY is_primary DESC, id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, workId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    OpenAlexTopicDto dto = new OpenAlexTopicDto();
                    dto.topicId = nullableString(rs, "topic_id");
                    dto.displayName = nullableString(rs, "display_name");
                    dto.score = rs.getBigDecimal("score") == null ? null : rs.getBigDecimal("score").doubleValue();
                    dto.primary = rs.getBoolean("is_primary");
                    dto.domainDisplayName = nullableString(rs, "domain_display_name");
                    dto.fieldDisplayName = nullableString(rs, "field_display_name");
                    dto.subfieldDisplayName = nullableString(rs, "subfield_display_name");
                    items.add(dto);
                }
            }
        }
        return items;
    }

    private List<OpenAlexLinkedOutputDto> loadLinks(Connection connection, long workId) throws SQLException {
        List<OpenAlexLinkedOutputDto> items = new ArrayList<>();
        String sql = """
                SELECT m.research_output_id, m.electronic_version_id, m.active_in_pure, o.pure_id, o.uuid, o.title
                FROM %s m JOIN %s o ON o.id = m.research_output_id
                WHERE m.openalex_work_id = ?
                """.formatted(qualified(LINK), qualified("pure_research_output"));
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, workId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    OpenAlexLinkedOutputDto dto = new OpenAlexLinkedOutputDto();
                    dto.researchOutputId = rs.getLong("research_output_id");
                    dto.electronicVersionId = nullableLong(rs, "electronic_version_id");
                    dto.activeInPure = rs.getBoolean("active_in_pure");
                    dto.pureId = nullableLong(rs, "pure_id");
                    dto.uuid = nullableString(rs, "uuid");
                    dto.title = nullableString(rs, "title");
                    items.add(dto);
                }
            }
        }
        return items;
    }

    private int countMatchWorks(Connection connection, long matchId, String search) throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM %s w
                LEFT JOIN %s o ON o.id = w.research_output_id
                WHERE w.match_id = ?
                """.formatted(qualified(MATCH_WORK), qualified("pure_research_output"))
                + matchWorkSearchSql(search);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindMatchWorkSearch(statement, matchId, search);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private List<OpenAlexMatchWorkDto> loadMatchWorks(
            Connection connection,
            long matchId,
            int page,
            int size,
            String search
    ) throws SQLException {
        List<OpenAlexMatchWorkDto> items = new ArrayList<>();
        String sql = """
                SELECT w.*, o.title
                FROM %s w
                LEFT JOIN %s o ON o.id = w.research_output_id
                WHERE w.match_id = ?
                """.formatted(qualified(MATCH_WORK), qualified("pure_research_output"))
                + matchWorkSearchSql(search)
                + " ORDER BY w.id LIMIT ? OFFSET ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = bindMatchWorkSearch(statement, matchId, search);
            statement.setInt(index++, size);
            statement.setInt(index, OpenAlexMatchWorkPaging.offset(page, size));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    OpenAlexMatchWorkDto dto = new OpenAlexMatchWorkDto();
                    dto.id = rs.getLong("id");
                    dto.researchOutputId = nullableLong(rs, "research_output_id");
                    dto.normalizedDoi = nullableString(rs, "normalized_doi");
                    dto.openalexWorkId = nullableString(rs, "openalex_work_oa_id");
                    dto.title = nullableString(rs, "title");
                    dto.contributorFirstName = nullableString(rs, "contributor_first_name");
                    dto.contributorLastName = nullableString(rs, "contributor_last_name");
                    dto.contributorPosition = nullableInt(rs, "contributor_position");
                    dto.contributorCount = nullableInt(rs, "contributor_count");
                    dto.rawAuthorName = nullableString(rs, "raw_author_name");
                    dto.authorPosition = nullableString(rs, "author_position");
                    dto.sourceOrder = nullableInt(rs, "source_order");
                    dto.nameCompatible = nullableBoolean(rs, "name_compatible");
                    dto.namePartitionDifferent = nullableBoolean(rs, "name_partition_different");
                    dto.positionCode = nullableString(rs, "position_code");
                    dto.orcidPure = nullableString(rs, "orcid_pure");
                    dto.orcidOpenAlex = nullableString(rs, "orcid_openalex");
                    dto.ussAffiliation = rs.getBoolean("uss_affiliation");
                    dto.affiliationDisplay = nullableString(rs, "affiliation_display");
                    dto.ror = nullableString(rs, "ror");
                    dto.reasonCodes = nullableString(rs, "reason_codes");
                    dto.reasonCodeList.addAll(OrcidCandidateMatcher.splitReasonCodes(dto.reasonCodes));
                    items.add(dto);
                }
            }
        }
        loadMatchWorkEvidences(connection, items);
        return items;
    }

    private static String matchWorkSearchSql(String search) {
        if (search == null) {
            return "";
        }
        return " AND (o.title LIKE ? ESCAPE '!' OR w.normalized_doi LIKE ? ESCAPE '!' OR w.openalex_work_oa_id LIKE ? ESCAPE '!')";
    }

    private int bindMatchWorkSearch(PreparedStatement statement, long matchId, String search) throws SQLException {
        int index = 1;
        statement.setLong(index++, matchId);
        if (search != null) {
            String like = likeContains(search);
            statement.setString(index++, like);
            statement.setString(index++, like);
            statement.setString(index++, like);
        }
        return index;
    }

    private void loadMatchWorkEvidences(Connection connection, List<OpenAlexMatchWorkDto> works) throws SQLException {
        if (works == null || works.isEmpty() || !tableExists(MATCH_WORK_EVIDENCE)) {
            return;
        }
        Map<Long, OpenAlexMatchWorkDto> byId = new LinkedHashMap<>();
        for (OpenAlexMatchWorkDto work : works) {
            if (work.id != null) {
                byId.put(work.id, work);
            }
        }
        if (byId.isEmpty()) {
            return;
        }
        List<Long> ids = new ArrayList<>(byId.keySet());
        String sql = """
                SELECT * FROM %s
                WHERE match_work_id IN (%s)
                ORDER BY source_order, id
                """.formatted(qualified(MATCH_WORK_EVIDENCE), inPlaceholders(ids.size()));
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (Long id : ids) {
                statement.setLong(index++, id);
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    OpenAlexMatchWorkDto work = byId.get(rs.getLong("match_work_id"));
                    if (work == null) {
                        continue;
                    }
                    OpenAlexMatchWorkEvidenceDto dto = new OpenAlexMatchWorkEvidenceDto();
                    dto.id = rs.getLong("id");
                    dto.authorshipId = nullableLong(rs, "authorship_id");
                    dto.sourceOrder = rs.getInt("source_order");
                    dto.rawAuthorName = nullableString(rs, "raw_author_name");
                    dto.authorDisplayName = nullableString(rs, "author_display_name");
                    dto.openalexAuthorId = nullableString(rs, "openalex_author_id");
                    dto.authorPosition = nullableString(rs, "author_position");
                    dto.affiliationDisplay = nullableString(rs, "affiliation_display");
                    dto.ror = nullableString(rs, "ror");
                    dto.nameCompatible = rs.getBoolean("name_compatible");
                    dto.namePartitionDifferent = rs.getBoolean("name_partition_different");
                    dto.positionCode = nullableString(rs, "position_code");
                    dto.orcidPure = nullableString(rs, "orcid_pure");
                    dto.orcidOpenAlex = nullableString(rs, "orcid_openalex");
                    dto.ussAffiliation = rs.getBoolean("uss_affiliation");
                    dto.reasonCodes = nullableString(rs, "reason_codes");
                    dto.reasonCodeList.addAll(OrcidCandidateMatcher.splitReasonCodes(dto.reasonCodes));
                    work.evidences.add(dto);
                }
            }
        }
    }

    private FilterSql workFilter(OpenAlexWorkQuery query) {
        FilterSql filter = new FilterSql();
        List<String> clauses = new ArrayList<>();
        if (query.search != null && !query.search.isBlank()) {
            clauses.add("(w.normalized_doi LIKE ? ESCAPE '!' OR w.display_name LIKE ? ESCAPE '!')");
            String like = likeContains(query.search.trim());
            filter.params.add(like);
            filter.params.add(like);
        }
        if (query.status != null && !query.status.isBlank()) {
            clauses.add("w.last_outcome = ?");
            filter.params.add(query.status.trim().toUpperCase(Locale.ROOT));
        }
        if (query.workType != null && !query.workType.isBlank()) {
            clauses.add("w.work_type = ?");
            filter.params.add(query.workType.trim());
        }
        filter.sql = clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
        return filter;
    }

    private String workOrder(OpenAlexWorkQuery query) {
        String sort = query.sort == null || !WORK_SORTS.contains(query.sort) ? "syncedAt" : query.sort;
        boolean desc = query.direction == null || "desc".equalsIgnoreCase(query.direction);
        String expression = switch (sort) {
            case "doi" -> "w.normalized_doi";
            case "title" -> "w.display_name";
            case "year" -> "w.publication_year";
            case "type" -> "w.work_type";
            case "cited" -> "w.cited_by_count";
            case "status" -> "w.last_outcome";
            default -> "w.last_sync_at";
        };
        return orderByNullsLast(expression, desc);
    }

    private FilterSql authorFilter(OpenAlexAuthorQuery query) {
        FilterSql filter = new FilterSql();
        if (query.search != null && !query.search.isBlank()) {
            filter.sql = " WHERE (a.openalex_author_id LIKE ? ESCAPE '!' OR a.display_name LIKE ? ESCAPE '!' OR a.orcid LIKE ? ESCAPE '!')";
            String like = likeContains(query.search.trim());
            filter.params.add(like);
            filter.params.add(like);
            filter.params.add(like);
        }
        return filter;
    }

    private String authorOrder(OpenAlexAuthorQuery query) {
        String sort = query.sort == null || !AUTHOR_SORTS.contains(query.sort) ? "syncedAt" : query.sort;
        boolean desc = query.direction == null || "desc".equalsIgnoreCase(query.direction);
        String expression = switch (sort) {
            case "name" -> "a.display_name";
            case "orcid" -> "a.orcid";
            case "works" -> "a.works_count";
            case "cited" -> "a.cited_by_count";
            default -> "a.last_sync_at";
        };
        return orderByNullsLast(expression, desc);
    }

    private FilterSql matchFilter(OpenAlexMatchQuery query) {
        FilterSql filter = new FilterSql();
        List<String> clauses = new ArrayList<>();
        if (query.search != null && !query.search.isBlank()) {
            clauses.add("(p.first_name LIKE ? ESCAPE '!' OR p.last_name LIKE ? ESCAPE '!' OR m.openalex_author_id LIKE ? ESCAPE '!')");
            String like = likeContains(query.search.trim());
            filter.params.add(like);
            filter.params.add(like);
            filter.params.add(like);
        }
        if (query.status != null && !query.status.isBlank()) {
            clauses.add("m.status = ?");
            filter.params.add(query.status.trim().toUpperCase(Locale.ROOT));
        }
        if (query.position != null && !query.position.isBlank()) {
            clauses.add("p.cargo = ?");
            filter.params.add(query.position.trim());
        }
        if (Boolean.TRUE.equals(query.hasOrcid)) {
            clauses.add("p.orcid IS NOT NULL AND TRIM(p.orcid) <> ''");
        } else if (Boolean.FALSE.equals(query.hasOrcid)) {
            clauses.add("(p.orcid IS NULL OR TRIM(p.orcid) = '')");
        }
        if (query.ussAffiliation != null) {
            clauses.add("m.uss_affiliation = ?");
            filter.params.add(query.ussAffiliation);
        }
        if (query.minWorks != null && query.minWorks > 0) {
            clauses.add("m.matching_work_count >= ?");
            filter.params.add(query.minWorks);
        }
        filter.sql = clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
        return filter;
    }

    private String matchOrder(OpenAlexMatchQuery query) {
        String sort = query.sort == null || !MATCH_SORTS.contains(query.sort) ? "works" : query.sort;
        boolean desc = query.direction == null || "desc".equalsIgnoreCase(query.direction);
        String expression = switch (sort) {
            case "person" -> "p.last_name";
            case "openalex" -> "m.openalex_author_id";
            case "status" -> "m.status";
            case "evaluated" -> "m.last_evaluated_at";
            default -> "m.matching_work_count";
        };
        return orderByNullsLast(expression, desc);
    }

    private List<String> distinct(String table, String column) {
        List<String> items = new ArrayList<>();
        if (!tableExists(table) || !columnExists(table, column)) {
            return items;
        }
        String sql = "SELECT DISTINCT `" + column + "` FROM " + qualified(table)
                + " WHERE `" + column + "` IS NOT NULL AND TRIM(`" + column + "`) <> '' ORDER BY 1";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                items.add(rs.getString(1));
            }
            return items;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load distinct " + column, ex);
        }
    }

    private OpenAlexWorkSummaryDto mapWorkSummary(ResultSet rs) throws SQLException {
        OpenAlexWorkSummaryDto dto = new OpenAlexWorkSummaryDto();
        dto.id = rs.getLong("id");
        dto.openalexWorkId = nullableString(rs, "openalex_work_id");
        dto.doi = nullableString(rs, "doi");
        dto.normalizedDoi = nullableString(rs, "normalized_doi");
        dto.displayName = nullableString(rs, "display_name");
        dto.publicationYear = nullableInt(rs, "publication_year");
        dto.workType = nullableString(rs, "work_type");
        dto.citedByCount = nullableInt(rs, "cited_by_count");
        dto.authorshipsCount = nullableInt(rs, "authorships_count");
        dto.authorshipsTruncated = rs.getBoolean("authorships_truncated");
        dto.rawJsonTruncated = booleanColumn(rs, "raw_json_truncated");
        dto.lastOutcome = nullableString(rs, "last_outcome");
        dto.lastSyncAt = timestamp(rs, "last_sync_at");
        dto.publicationTitle = nullableString(rs, "publication_title");
        dto.researchOutputCount = rs.getInt("research_output_count");
        return dto;
    }

    private OpenAlexWorkDetailDto mapWorkDetail(ResultSet rs) throws SQLException {
        OpenAlexWorkDetailDto dto = new OpenAlexWorkDetailDto();
        dto.id = rs.getLong("id");
        dto.openalexWorkId = nullableString(rs, "openalex_work_id");
        dto.doi = nullableString(rs, "doi");
        dto.normalizedDoi = nullableString(rs, "normalized_doi");
        dto.displayName = nullableString(rs, "display_name");
        dto.publicationYear = nullableInt(rs, "publication_year");
        dto.publicationDate = nullableString(rs, "publication_date");
        dto.workType = nullableString(rs, "work_type");
        dto.citedByCount = nullableInt(rs, "cited_by_count");
        dto.referencedWorksCount = nullableInt(rs, "referenced_works_count");
        dto.isOa = nullableBoolean(rs, "is_oa");
        dto.oaStatus = nullableString(rs, "oa_status");
        dto.language = nullableString(rs, "language");
        dto.primarySourceDisplayName = nullableString(rs, "primary_source_display_name");
        dto.primaryTopicDisplayName = nullableString(rs, "primary_topic_display_name");
        dto.domainDisplayName = nullableString(rs, "domain_display_name");
        dto.fieldDisplayName = nullableString(rs, "field_display_name");
        dto.subfieldDisplayName = nullableString(rs, "subfield_display_name");
        dto.authorshipsCount = nullableInt(rs, "authorships_count");
        dto.authorshipsTruncated = rs.getBoolean("authorships_truncated");
        dto.rawJsonTruncated = booleanColumn(rs, "raw_json_truncated");
        dto.lastOutcome = nullableString(rs, "last_outcome");
        dto.lastSyncAt = timestamp(rs, "last_sync_at");
        dto.openalexUrl = OpenAlexId.url(dto.openalexWorkId);
        return dto;
    }

    private OpenAlexAuthorSummaryDto mapAuthor(ResultSet rs) throws SQLException {
        OpenAlexAuthorSummaryDto dto = new OpenAlexAuthorSummaryDto();
        dto.id = rs.getLong("id");
        dto.openalexAuthorId = nullableString(rs, "openalex_author_id");
        dto.displayName = nullableString(rs, "display_name");
        dto.orcid = nullableString(rs, "orcid");
        dto.worksCount = nullableInt(rs, "works_count");
        dto.citedByCount = nullableInt(rs, "cited_by_count");
        dto.lastOutcome = nullableString(rs, "last_outcome");
        dto.lastSyncAt = timestamp(rs, "last_sync_at");
        dto.openalexUrl = OpenAlexId.url(dto.openalexAuthorId);
        dto.linkedPersonCount = rs.getInt("linked_person_count");
        return dto;
    }

    private OpenAlexMatchSummaryDto mapMatch(ResultSet rs) throws SQLException {
        OpenAlexMatchSummaryDto dto = new OpenAlexMatchSummaryDto();
        dto.id = rs.getLong("id");
        dto.personId = rs.getLong("person_id");
        dto.personPureId = nullableLong(rs, "pure_id");
        dto.personUuid = nullableString(rs, "uuid");
        dto.personName = AuthorNameMatcher.fullName(nullableString(rs, "first_name"), nullableString(rs, "last_name"));
        dto.personPosition = nullableString(rs, "cargo");
        dto.personOrcid = nullableString(rs, "person_orcid");
        dto.openalexAuthorId = nullableString(rs, "openalex_author_id");
        dto.openalexDisplayName = nullableString(rs, "author_display_name");
        dto.openalexOrcid = nullableString(rs, "author_orcid");
        dto.openalexUrl = OpenAlexId.url(dto.openalexAuthorId);
        dto.status = nullableString(rs, "status");
        dto.matchMethod = nullableString(rs, "match_method");
        dto.selected = rs.getBoolean("selected");
        dto.selectedManually = rs.getBoolean("selected_manually");
        dto.matchingWorkCount = rs.getInt("matching_work_count");
        dto.orcidMatch = rs.getBoolean("orcid_match");
        dto.ussAffiliation = rs.getBoolean("uss_affiliation");
        dto.reviewRequested = rs.getBoolean("review_requested");
        dto.lastEvaluatedAt = timestamp(rs, "last_evaluated_at");
        dto.confirmedAt = timestamp(rs, "confirmed_at");
        dto.confirmedBy = nullableString(rs, "confirmed_by");
        return dto;
    }

    private static void copyMatch(OpenAlexMatchSummaryDto source, OpenAlexMatchDetailDto target) {
        target.id = source.id;
        target.personId = source.personId;
        target.personPureId = source.personPureId;
        target.personUuid = source.personUuid;
        target.personName = source.personName;
        target.personPosition = source.personPosition;
        target.personOrcid = source.personOrcid;
        target.openalexAuthorId = source.openalexAuthorId;
        target.openalexDisplayName = source.openalexDisplayName;
        target.openalexOrcid = source.openalexOrcid;
        target.openalexUrl = source.openalexUrl;
        target.status = source.status;
        target.matchMethod = source.matchMethod;
        target.selected = source.selected;
        target.selectedManually = source.selectedManually;
        target.matchingWorkCount = source.matchingWorkCount;
        target.orcidMatch = source.orcidMatch;
        target.ussAffiliation = source.ussAffiliation;
        target.reviewRequested = source.reviewRequested;
        target.lastEvaluatedAt = source.lastEvaluatedAt;
        target.confirmedAt = source.confirmedAt;
        target.confirmedBy = source.confirmedBy;
        target.identityConfirmed = source.identityConfirmed;
        target.orcidComparison = source.orcidComparison;
        target.orcidSyncAvailable = source.orcidSyncAvailable;
        target.orcidSyncBlockedReason = source.orcidSyncBlockedReason;
    }

    private static String conclusion(OpenAlexMatchDetailDto dto) {
        if ("CONFIRMED".equals(dto.status)) {
            return "CONFIRMED";
        }
        if ("REJECTED".equals(dto.status)) {
            return "REJECTED";
        }
        if ("CONFLICT".equals(dto.status)) {
            return "CONFLICT";
        }
        if (dto.orcidMatch && dto.matchingWorkCount >= 1) {
            return "STRONG";
        }
        if (dto.matchingWorkCount >= 2) {
            return "MULTI_WORK";
        }
        if (dto.matchingWorkCount >= 1) {
            return "SAME_DOI";
        }
        return dto.status;
    }

    private StoredWork mapStored(ResultSet rs) throws SQLException {
        StoredWork stored = new StoredWork();
        stored.id = rs.getLong("id");
        stored.openalexWorkId = nullableString(rs, "openalex_work_id");
        stored.normalizedDoi = nullableString(rs, "normalized_doi");
        stored.lastOutcome = nullableString(rs, "last_outcome");
        stored.parentSnapshot = nullableString(rs, "parent_snapshot");
        stored.authorshipSnapshot = nullableString(rs, "authorship_snapshot");
        stored.citedByCount = nullableInt(rs, "cited_by_count");
        stored.lastSyncAt = timestamp(rs, "last_sync_at");
        return stored;
    }

    private static OffsetDateTime timestamp(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant().atOffset(ZoneOffset.UTC);
    }

    private static String linkKey(long researchOutputId, Long electronicVersionId) {
        return researchOutputId + ":" + (electronicVersionId == null ? "0" : electronicVersionId);
    }

    private static void setNullableInt(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private static boolean booleanColumn(ResultSet rs, String column) {
        try {
            return rs.getBoolean(column);
        } catch (SQLException ex) {
            return false;
        }
    }

    public static class StoredWork {
        public long id;
        public String openalexWorkId;
        public String normalizedDoi;
        public String lastOutcome;
        public String parentSnapshot;
        public String authorshipSnapshot;
        public Integer citedByCount;
        public OffsetDateTime lastSyncAt;
    }

    public static class StoredMatch {
        public long id;
        public String status;
        public boolean selected;
        public boolean selectedManually;
    }

    public static class RunExtras {
        public long found;
        public long notFound;
        public long invalid;
        public String phase;
        public Integer authorsTotal;
        public Long authorsProcessed;
        public Long authorsCreated;
        public Long authorsUpdated;
        public String sourceSyncExecutionId;
    }

    public static class QualityRow {
        public long personId;
        public Long pureId;
        public String uuid;
        public String personOrcid;
        public String openalexAuthorId;
        public String status;
        public Integer matchingWorkCount;
        public boolean orcidMatch;
        public String authorOrcid;
    }

    public static class MatchContributor {
        public long contributorId;
        public long researchOutputId;
        public String firstName;
        public String lastName;
        public String personUuid;
        public long personId;
        public String personFirst;
        public String personLast;
        public String orcid;
        public String position;
        public Long pureId;
        public Integer contributorPosition;
        public Integer contributorCount;
    }

    private static final class FilterSql {
        private String sql = "";
        private final List<Object> params = new ArrayList<>();
    }
}
