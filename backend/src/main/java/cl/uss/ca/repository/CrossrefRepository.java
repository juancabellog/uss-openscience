package cl.uss.ca.repository;

import cl.uss.ca.api.dto.CrossrefAffiliationDto;
import cl.uss.ca.api.dto.CrossrefAuthorDto;
import cl.uss.ca.api.dto.CrossrefFiltersDto;
import cl.uss.ca.api.dto.CrossrefFunderDto;
import cl.uss.ca.api.dto.CrossrefIssnDto;
import cl.uss.ca.api.dto.CrossrefLicenseDto;
import cl.uss.ca.api.dto.CrossrefLinkedOutputDto;
import cl.uss.ca.api.dto.CrossrefQuery;
import cl.uss.ca.api.dto.CrossrefRelationDto;
import cl.uss.ca.api.dto.CrossrefWorkDetailDto;
import cl.uss.ca.api.dto.CrossrefWorkSummaryDto;
import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.exception.DatabaseException;
import cl.uss.ca.exception.ValidationException;
import cl.uss.ca.external.crossref.CrossrefAffiliationRecord;
import cl.uss.ca.external.crossref.CrossrefAuthorRecord;
import cl.uss.ca.external.crossref.CrossrefFetchResult;
import cl.uss.ca.external.crossref.CrossrefFunderRecord;
import cl.uss.ca.external.crossref.CrossrefIssnRecord;
import cl.uss.ca.external.crossref.CrossrefLicenseRecord;
import cl.uss.ca.external.crossref.CrossrefRelationRecord;
import cl.uss.ca.external.crossref.CrossrefStatus;
import cl.uss.ca.external.crossref.CrossrefMapper;
import cl.uss.ca.external.crossref.CrossrefWorkRecord;
import cl.uss.ca.external.crossref.CrossrefWorkSnapshot;
import cl.uss.ca.quality.CrossrefComparison;
import cl.uss.ca.sync.SyncRunState;
import cl.uss.ca.sync.WorkSyncState;
import cl.uss.ca.sync.diff.SyncChange;
import jakarta.enterprise.context.ApplicationScoped;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class CrossrefRepository extends JdbcRepository {
    private static final String WORK = "crossref_work";
    private static final String AUTHOR = "crossref_author";
    private static final String AFF = "crossref_author_affiliation";
    private static final String ISSN = "crossref_issn";
    private static final String FUNDER = "crossref_funder";
    private static final String LICENSE = "crossref_license";
    private static final String RELATION = "crossref_relation";
    private static final String LINK = "crossref_research_output";
    private static final String STATS = "crossref_sync_stats";
    private static final String DOI_TYPE = "DoiElectronicVersion";
    private static final Set<String> SORTS = Set.of(
            "doi", "pureTitle", "title", "type", "container", "publishedDate", "status", "syncedAt"
    );

    public boolean available() {
        return tableExists(WORK) && tableExists(LINK) && tableExists(AUTHOR);
    }

    public void assertAvailable() {
        if (!available()) {
            throw new ValidationException(
                    "CROSSREF_TABLES_UNAVAILABLE",
                    "crossref_work tables are required before synchronizing Crossref"
            );
        }
    }

    public List<CrossrefDoiLink> listDoiLinks(boolean activeOnly) {
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
        List<CrossrefDoiLink> links = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, DOI_TYPE);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    CrossrefDoiLink link = new CrossrefDoiLink();
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
            throw new DatabaseException("Failed to list Crossref DOI universe", ex);
        }
        return links;
    }

    public StoredWork findByNormalizedDoi(String normalizedDoi) {
        if (!available() || normalizedDoi == null) {
            return null;
        }
        String sql = "SELECT * FROM " + qualified(WORK) + " WHERE normalized_doi = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizedDoi);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? mapStored(rs) : null;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load Crossref work", ex);
        }
    }

    public boolean isFresh(StoredWork stored, int refreshDays) {
        if (stored == null || refreshDays <= 0 || stored.lastSyncAt == null) {
            return false;
        }
        if (!CrossrefStatus.remoteOutcome(stored.status)) {
            return false;
        }
        return stored.lastSyncAt.isAfter(OffsetDateTime.now().minusDays(refreshDays));
    }

    public Map<String, WorkSyncState> loadWorkStates() {
        Map<String, WorkSyncState> items = new HashMap<>();
        if (!tableExists(WORK)) {
            return items;
        }
        boolean refresh = columnExists(WORK, "needs_refresh");
        String sql = "SELECT normalized_doi, status, last_crossref_sync_at"
                + (refresh ? ", needs_refresh" : "")
                + " FROM " + qualified(WORK);
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                WorkSyncState state = new WorkSyncState();
                state.present = true;
                state.status = nullableString(rs, "status");
                state.lastSyncAt = timestamp(rs, "last_crossref_sync_at");
                state.needsRefresh = refresh && rs.getBoolean("needs_refresh");
                items.put(rs.getString("normalized_doi"), state);
            }
            return items;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load Crossref work states", ex);
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
                INNER JOIN %s m ON m.crossref_work_id = w.id
                WHERE COALESCE(m.active_in_pure, 1) = 1
                """.formatted(qualified(WORK), qualified(LINK));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                items.add(rs.getString(1));
            }
            return items;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load mapped Crossref DOIs", ex);
        }
    }

    public CrossrefReconcileResult reconcile(
            String normalizedDoi,
            String displayDoi,
            CrossrefFetchResult fetch,
            List<CrossrefDoiLink> links,
            String syncId
    ) {
        assertAvailable();
        CrossrefReconcileResult result = new CrossrefReconcileResult();
        try (Connection connection = connection()) {
            StoredWork stored = findByNormalizedDoi(connection, normalizedDoi);
            CrossrefWorkRecord incoming = fetch.work == null ? new CrossrefWorkRecord() : fetch.work;
            String parentSnap = CrossrefWorkSnapshot.parent(incoming, fetch.status);
            String childSnap = CrossrefWorkSnapshot.children(incoming);
            boolean parentChanged = stored == null || !CrossrefWorkSnapshot.same(stored.parentSnapshot, parentSnap);
            boolean childChanged = stored == null || !CrossrefWorkSnapshot.same(stored.childrenSnapshot, childSnap);
            String comparison = comparisonStatus(fetch.status, incoming, links);
            long workId;
            if (stored == null) {
                workId = insertWork(connection, normalizedDoi, displayDoi, incoming, fetch, syncId, parentSnap, childSnap, comparison);
                result.kind = "CREATED";
                result.changes.add(SyncChange.created().on("CROSSREF", workId, null, normalizedDoi));
            } else {
                workId = stored.id;
                if (parentChanged || childChanged) {
                    updateWork(connection, workId, displayDoi, incoming, fetch, syncId, parentSnap, childSnap, comparison);
                    result.kind = "UPDATED";
                    addFieldChanges(result, stored, incoming, fetch, workId, normalizedDoi);
                } else {
                    touch(connection, workId, fetch, syncId, comparison);
                    result.kind = "UNCHANGED";
                }
            }
            if (parentChanged || childChanged || stored == null) {
                replaceChildren(connection, workId, incoming);
            }
            reconcileLinks(connection, workId, links);
            return result;
        } catch (SQLException ex) {
            throw new DatabaseException(
                    "Failed to reconcile Crossref work doi=" + normalizedDoi
                            + " (children: crossref_author, crossref_author_affiliation, "
                            + "crossref_issn, crossref_funder, crossref_license, crossref_relation)",
                    ex);
        }
    }

    public void saveRunStats(SyncRunState run) {
        if (!tableExists(STATS) || run == null || run.syncId == null) {
            return;
        }
        Long id = parseNumericId(run.syncId);
        if (id == null) {
            return;
        }
        String sql = """
                INSERT INTO %s (sync_execution_id, found_count, other_agency_count, not_found_count, invalid_count, skipped_fresh_count)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    found_count = VALUES(found_count),
                    other_agency_count = VALUES(other_agency_count),
                    not_found_count = VALUES(not_found_count),
                    invalid_count = VALUES(invalid_count),
                    skipped_fresh_count = VALUES(skipped_fresh_count)
                """.formatted(qualified(STATS));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.setLong(2, run.oa.get());
            statement.setLong(3, run.nonOa.get());
            statement.setLong(4, run.notFound.get());
            statement.setLong(5, run.markedInactive.get());
            statement.setLong(6, run.reactivated.get());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to persist Crossref sync stats", ex);
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
        String sql = "SELECT found_count, other_agency_count, not_found_count, invalid_count FROM "
                + qualified(STATS) + " WHERE sync_execution_id = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                RunExtras extras = new RunExtras();
                extras.found = rs.getLong(1);
                extras.otherAgency = rs.getLong(2);
                extras.notFound = rs.getLong(3);
                extras.invalid = rs.getLong(4);
                return extras;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load Crossref sync stats", ex);
        }
    }

    public long countUniqueActiveDois() {
        return listDoiLinks(true).stream()
                .map(link -> cl.uss.ca.domain.DoiNormalizer.normalize(link.doi))
                .filter(value -> value != null)
                .distinct()
                .count();
    }

    public long countStatus(String status) {
        return scalarLong("SELECT COUNT(*) FROM " + qualified(WORK) + " WHERE status = ?", status);
    }

    public long countFlag(String column) {
        return scalarLong("SELECT COUNT(*) FROM " + qualified(WORK) + " WHERE `" + column + "` = 1");
    }

    public long countDifferences() {
        return scalarLong("SELECT COUNT(*) FROM " + qualified(WORK) + " WHERE comparison_status = ?", CrossrefStatus.COMPARE_DIFFERENCE);
    }

    public PageResponse<CrossrefWorkSummaryDto> list(CrossrefQuery query) {
        assertAvailable();
        FilterSql filter = filterSql(query);
        String order = orderSql(query);
        int page = Math.max(1, query.page);
        int size = Math.min(200, Math.max(1, query.pageSize));
        long total = scalarLong("SELECT COUNT(*) FROM " + qualified(WORK) + " w " + filter.sql, filter.params.toArray());
        String sql = """
                SELECT w.*,
                       (SELECT COUNT(*) FROM %s m WHERE m.crossref_work_id = w.id) AS research_output_count,
                       (SELECT o.title FROM %s m
                          JOIN %s o ON o.id = m.research_output_id
                         WHERE m.crossref_work_id = w.id
                         ORDER BY o.id LIMIT 1) AS publication_title,
                       (SELECT GROUP_CONCAT(i.issn ORDER BY i.id SEPARATOR '; ')
                          FROM %s i WHERE i.crossref_work_id = w.id) AS issn_list
                FROM %s w
                %s
                ORDER BY %s, w.id
                LIMIT ? OFFSET ?
                """.formatted(qualified(LINK), qualified(LINK), qualified("pure_research_output"), qualified(ISSN), qualified(WORK), filter.sql, order);
        List<CrossrefWorkSummaryDto> items = new ArrayList<>();
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
                    items.add(mapSummary(rs));
                }
            }
            for (CrossrefWorkSummaryDto item : items) {
                item.researchOutputs.addAll(loadLinks(connection, item.id));
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list Crossref works", ex);
        }
        return PageResponse.of(items, page - 1, size, total);
    }

    public CrossrefFiltersDto filters() {
        assertAvailable();
        CrossrefFiltersDto dto = new CrossrefFiltersDto();
        dto.statuses.addAll(distinct(WORK, "status"));
        dto.workTypes.addAll(distinct(WORK, "work_type"));
        return dto;
    }

    public CrossrefWorkDetailDto findDetail(long id) {
        assertAvailable();
        String sql = "SELECT * FROM " + qualified(WORK) + " WHERE id = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                CrossrefWorkDetailDto dto = mapDetail(rs);
                dto.researchOutputs.addAll(loadLinks(connection, id));
                dto.authors.addAll(loadAuthors(connection, id));
                dto.issns.addAll(loadIssns(connection, id));
                dto.funders.addAll(loadFunders(connection, id));
                dto.licenses.addAll(loadLicenses(connection, id));
                dto.relations.addAll(loadRelations(connection, id));
                dto.researchOutputCount = dto.researchOutputs.size();
                if (!dto.researchOutputs.isEmpty()) {
                    dto.publicationTitle = dto.researchOutputs.get(0).title;
                }
                return dto;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load Crossref detail", ex);
        }
    }

    public List<CrossrefWorkSummaryDto> listForExport(CrossrefQuery query) {
        CrossrefQuery copy = query;
        copy.page = 1;
        copy.pageSize = 20_000;
        return list(copy).items;
    }

    public List<CrossrefWorkSummaryDto> findByResearchOutput(long researchOutputId) {
        if (!available()) {
            return List.of();
        }
        String sql = """
                SELECT w.*, 1 AS research_output_count, o.title AS publication_title
                FROM %s w
                JOIN %s m ON m.crossref_work_id = w.id
                JOIN %s o ON o.id = m.research_output_id
                WHERE m.research_output_id = ?
                ORDER BY w.id
                """.formatted(qualified(WORK), qualified(LINK), qualified("pure_research_output"));
        List<CrossrefWorkSummaryDto> items = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, researchOutputId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    items.add(mapSummary(rs));
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load Crossref by research output", ex);
        }
        return items;
    }

    public List<QualityRow> listQualityRows() {
        return listQualityRows(null);
    }

    public List<QualityRow> listQualityRows(Long researchOutputId) {
        if (!available()) {
            return List.of();
        }
        String extra = researchOutputId == null ? "" : " AND o.id = ?";
        String sql = """
                SELECT w.id AS work_id, w.normalized_doi, w.status, w.title AS crossref_title, w.container_title,
                       w.has_orcid, w.has_affiliation, w.has_ror, w.has_funding, w.has_update, w.has_retraction,
                       o.id AS research_output_id, o.pure_id, o.uuid, o.title AS pure_title
                FROM %s w
                JOIN %s m ON m.crossref_work_id = w.id
                JOIN %s o ON o.id = m.research_output_id
                WHERE 1=1 %s
                """.formatted(qualified(WORK), qualified(LINK), qualified("pure_research_output"), extra);
        List<QualityRow> rows = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (researchOutputId != null) {
                statement.setLong(1, researchOutputId);
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    QualityRow row = new QualityRow();
                    row.workId = rs.getLong("work_id");
                    row.normalizedDoi = nullableString(rs, "normalized_doi");
                    row.status = nullableString(rs, "status");
                    row.crossrefTitle = nullableString(rs, "crossref_title");
                    row.containerTitle = nullableString(rs, "container_title");
                    row.hasOrcid = rs.getBoolean("has_orcid");
                    row.hasAffiliation = rs.getBoolean("has_affiliation");
                    row.hasRor = rs.getBoolean("has_ror");
                    row.hasFunding = rs.getBoolean("has_funding");
                    row.hasUpdate = rs.getBoolean("has_update");
                    row.hasRetraction = rs.getBoolean("has_retraction");
                    row.researchOutputId = rs.getLong("research_output_id");
                    row.pureId = nullableLong(rs, "pure_id");
                    row.uuid = nullableString(rs, "uuid");
                    row.pureTitle = nullableString(rs, "pure_title");
                    rows.add(row);
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load Crossref quality rows", ex);
        }
        return rows;
    }

    public List<String> listIssns(long workId) {
        List<String> items = new ArrayList<>();
        if (!tableExists(ISSN)) {
            return items;
        }
        String sql = "SELECT issn FROM " + qualified(ISSN) + " WHERE crossref_work_id = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, workId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String value = nullableString(rs, "issn");
                    if (value != null) {
                        items.add(value.toLowerCase(Locale.ROOT).replace("-", ""));
                    }
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load Crossref ISSNs", ex);
        }
        return items;
    }

    public List<String> listCrossrefAuthorNames(long workId) {
        List<String> items = new ArrayList<>();
        String sql = "SELECT full_name FROM " + qualified(AUTHOR) + " WHERE crossref_work_id = ? ORDER BY sequence_no";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, workId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String value = nullableString(rs, "full_name");
                    if (value != null) {
                        items.add(value);
                    }
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load Crossref authors", ex);
        }
        return items;
    }

    public List<String> listCrossrefOrcids(long workId) {
        List<String> items = new ArrayList<>();
        String sql = "SELECT orcid FROM " + qualified(AUTHOR)
                + " WHERE crossref_work_id = ? AND orcid IS NOT NULL ORDER BY sequence_no, id";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, workId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String value = nullableString(rs, "orcid");
                    if (value != null) {
                        items.add(value);
                    }
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load Crossref ORCIDs", ex);
        }
        return items;
    }

    public List<String> listPureAuthorNames(long researchOutputId) {
        List<String> items = new ArrayList<>();
        if (!tableExists("pure_research_output_contributor")) {
            return items;
        }
        String sql = """
                SELECT first_name, last_name FROM %s
                WHERE research_output_id = ? AND (type_discriminator IS NULL OR type_discriminator LIKE '%%Author%%'
                      OR logical_name LIKE '%%author%%' OR 1=1)
                ORDER BY id
                """.formatted(qualified("pure_research_output_contributor"));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, researchOutputId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String name = cl.uss.ca.domain.AuthorNameMatcher.displayName(
                            nullableString(rs, "first_name"), nullableString(rs, "last_name"));
                    if (name != null && !name.isBlank()) {
                        items.add(name);
                    }
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load PURE authors", ex);
        }
        return items;
    }

    public List<String> listPureOrcids(long researchOutputId) {
        List<String> items = new ArrayList<>();
        if (!tableExists("pure_person") || !tableExists("pure_research_output_contributor")) {
            return items;
        }
        String sql = """
                SELECT DISTINCT p.orcid
                FROM %s c
                JOIN %s p ON p.id = c.person_id
                WHERE c.research_output_id = ? AND p.orcid IS NOT NULL AND TRIM(p.orcid) <> ''
                ORDER BY p.orcid
                """.formatted(qualified("pure_research_output_contributor"), qualified("pure_person"));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, researchOutputId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String value = cl.uss.ca.quality.DataQualityIdentifiers.normalizeOrcid(nullableString(rs, "orcid"));
                    if (value != null) {
                        items.add(value);
                    }
                }
            }
        } catch (SQLException ex) {
            return items;
        }
        return items;
    }

    public String pureJournalTitle(long researchOutputId) {
        if (!tableExists("pure_research_output_journal") || !tableExists("pure_journal")) {
            return null;
        }
        String sql = """
                SELECT t.title
                FROM %s oj
                JOIN %s t ON t.journal_id = oj.journal_id
                WHERE oj.research_output_id = ?
                ORDER BY t.id
                LIMIT 1
                """.formatted(qualified("pure_research_output_journal"), qualified("pure_journal_title"));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, researchOutputId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? nullableString(rs, "title") : null;
            }
        } catch (SQLException ex) {
            return null;
        }
    }

    public List<String> pureJournalIssns(long researchOutputId) {
        List<String> items = new ArrayList<>();
        if (!tableExists("pure_journal_issn")) {
            return items;
        }
        String sql = """
                SELECT i.issn FROM %s oj
                JOIN %s i ON i.journal_id = oj.journal_id
                WHERE oj.research_output_id = ?
                """.formatted(qualified("pure_research_output_journal"), qualified("pure_journal_issn"));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, researchOutputId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String value = nullableString(rs, "issn");
                    if (value != null) {
                        items.add(value.toLowerCase(Locale.ROOT).replace("-", ""));
                    }
                }
            }
        } catch (SQLException ex) {
            return items;
        }
        return items;
    }

    public boolean pureHasFunding(long researchOutputId) {
        if (!columnExists("pure_research_output", "funding_org_uuid")) {
            return false;
        }
        return scalarLong(
                "SELECT COUNT(*) FROM " + qualified("pure_research_output")
                        + " WHERE id = ? AND funding_org_uuid IS NOT NULL AND TRIM(funding_org_uuid) <> ''",
                researchOutputId
        ) > 0;
    }

    public boolean pureHasAffiliation(long researchOutputId) {
        if (!tableExists("pure_research_output_contributor")
                || !tableExists("pure_research_output_contributor_organization")) {
            return false;
        }
        return scalarLong(
                """
                SELECT COUNT(*) FROM %s c
                JOIN %s o ON o.contributor_id = c.id
                WHERE c.research_output_id = ?
                """.formatted(
                        qualified("pure_research_output_contributor"),
                        qualified("pure_research_output_contributor_organization")
                ),
                researchOutputId
        ) > 0;
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
            CrossrefWorkRecord work,
            CrossrefFetchResult fetch,
            String syncId,
            String parentSnap,
            String childSnap,
            String comparison
    ) throws SQLException {
        String sql = """
                INSERT INTO %s (
                    original_doi, normalized_doi, canonical_doi, status, registration_agency, registration_agency_id,
                    work_type, title, subtitle, short_title, container_title, publisher, volume, issue, page_range,
                    article_number, language, reference_count, is_referenced_by_count, published_date,
                    published_print_date, published_online_date, issued_date, created_crossref_at, deposited_crossref_at,
                    indexed_crossref_at, has_orcid, has_affiliation, has_ror, has_funding, has_license, has_update,
                    has_retraction, comparison_status, last_http_status, error_code, error_message,
                    last_sync_execution_id, last_crossref_sync_at, last_change_detected_at, parent_snapshot,
                    children_snapshot, raw_json
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NOW(6),NOW(6),?,?,?)
                """.formatted(qualified(WORK));
        try (PreparedStatement statement = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            int next = bindWorkFields(statement, 1, displayDoi, normalizedDoi, work, fetch, syncId, parentSnap, childSnap, comparison);
            if (next != 42) {
                throw new SQLException("Unexpected Crossref insert bind count " + next);
            }
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("crossref_work insert did not return id");
                }
                return keys.getLong(1);
            }
        }
    }

    private void updateWork(
            Connection connection,
            long id,
            String displayDoi,
            CrossrefWorkRecord work,
            CrossrefFetchResult fetch,
            String syncId,
            String parentSnap,
            String childSnap,
            String comparison
    ) throws SQLException {
        String sql = """
                UPDATE %s SET
                    original_doi=?, canonical_doi=?, status=?, registration_agency=?, registration_agency_id=?,
                    work_type=?, title=?, subtitle=?, short_title=?, container_title=?, publisher=?, volume=?, issue=?,
                    page_range=?, article_number=?, language=?, reference_count=?, is_referenced_by_count=?,
                    published_date=?, published_print_date=?, published_online_date=?, issued_date=?,
                    created_crossref_at=?, deposited_crossref_at=?, indexed_crossref_at=?, has_orcid=?, has_affiliation=?,
                    has_ror=?, has_funding=?, has_license=?, has_update=?, has_retraction=?, comparison_status=?,
                    last_http_status=?, error_code=?, error_message=?, last_sync_execution_id=?,
                    last_crossref_sync_at=NOW(6), last_change_detected_at=NOW(6), parent_snapshot=?, children_snapshot=?,
                    raw_json=?%s
                WHERE id=?
                """.formatted(qualified(WORK), needsRefreshClearSql());
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = bindWorkFields(statement, 1, displayDoi, null, work, fetch, syncId, parentSnap, childSnap, comparison);
            statement.setLong(index, id);
            statement.executeUpdate();
        }
    }

    private void touch(Connection connection, long id, CrossrefFetchResult fetch, String syncId, String comparison)
            throws SQLException {
        String sql = """
                UPDATE %s SET last_crossref_sync_at = NOW(6), last_http_status = ?, error_code = ?,
                    error_message = ?, last_sync_execution_id = ?, comparison_status = ?%s
                WHERE id = ?
                """.formatted(qualified(WORK), needsRefreshClearSql());
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, fetch.httpStatus);
            statement.setString(2, fetch.errorCode);
            statement.setString(3, fetch.errorMessage);
            bindSyncId(statement, 4, WORK, "last_sync_execution_id", syncId);
            statement.setString(5, comparison);
            statement.setLong(6, id);
            statement.executeUpdate();
        }
    }

    private void replaceChildren(Connection connection, long workId, CrossrefWorkRecord work) throws SQLException {
        deleteByWork(connection, RELATION, workId);
        deleteByWork(connection, LICENSE, workId);
        deleteByWork(connection, FUNDER, workId);
        deleteByWork(connection, ISSN, workId);
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM " + qualified(AFF) + " WHERE crossref_author_id IN (SELECT id FROM "
                        + qualified(AUTHOR) + " WHERE crossref_work_id = ?)")) {
            statement.setLong(1, workId);
            statement.executeUpdate();
        }
        deleteByWork(connection, AUTHOR, workId);
        if (work == null) {
            return;
        }
        for (CrossrefAuthorRecord author : work.authors) {
            long authorId = insertAuthor(connection, workId, author);
            int seq = 0;
            for (CrossrefAffiliationRecord affiliation : author.affiliations) {
                insertAffiliation(connection, authorId, seq++, affiliation);
            }
        }
        for (CrossrefIssnRecord issn : work.issns) {
            insertIssn(connection, workId, issn);
        }
        for (CrossrefFunderRecord funder : work.funders) {
            insertFunder(connection, workId, funder);
        }
        for (CrossrefLicenseRecord license : work.licenses) {
            insertLicense(connection, workId, license);
        }
        for (CrossrefRelationRecord relation : work.relations) {
            insertRelation(connection, workId, relation);
        }
    }

    private void reconcileLinks(Connection connection, long workId, List<CrossrefDoiLink> links) throws SQLException {
        Map<String, Long> existing = new LinkedHashMap<>();
        String select = "SELECT id, research_output_id, electronic_version_id FROM " + qualified(LINK)
                + " WHERE crossref_work_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setLong(1, workId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    existing.put(linkKey(rs.getLong("research_output_id"), nullableLong(rs, "electronic_version_id")),
                            rs.getLong("id"));
                }
            }
        }
        if (links != null) {
            for (CrossrefDoiLink link : links) {
                String key = linkKey(link.researchOutputId, link.electronicVersionId);
                Long id = existing.remove(key);
                if (id == null) {
                    insertLink(connection, workId, link);
                } else {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "UPDATE " + qualified(LINK) + " SET active_in_pure = ? WHERE id = ?")) {
                        statement.setBoolean(1, link.activeInPure);
                        statement.setLong(2, id);
                        statement.executeUpdate();
                    }
                }
            }
        }
        for (Long id : existing.values()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE " + qualified(LINK) + " SET active_in_pure = 0 WHERE id = ?")) {
                statement.setLong(1, id);
                statement.executeUpdate();
            }
        }
    }

    private void insertLink(Connection connection, long workId, CrossrefDoiLink link) throws SQLException {
        String sql = "INSERT INTO " + qualified(LINK)
                + " (crossref_work_id, research_output_id, electronic_version_id, active_in_pure) VALUES (?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, workId);
            statement.setLong(2, link.researchOutputId);
            if (link.electronicVersionId == null) {
                statement.setNull(3, Types.BIGINT);
            } else {
                statement.setLong(3, link.electronicVersionId);
            }
            statement.setBoolean(4, link.activeInPure);
            statement.executeUpdate();
        }
    }

    private long insertAuthor(Connection connection, long workId, CrossrefAuthorRecord author) throws SQLException {
        String sql = "INSERT INTO " + qualified(AUTHOR)
                + " (crossref_work_id, sequence_no, given_name, family_name, suffix, full_name, orcid, authenticated_orcid)"
                + " VALUES (?,?,?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, workId);
            statement.setInt(2, author.sequenceNo);
            statement.setString(3, author.given);
            statement.setString(4, author.family);
            statement.setString(5, author.suffix);
            statement.setString(6, author.fullName);
            statement.setString(7, author.orcid);
            if (author.authenticatedOrcid == null) {
                statement.setNull(8, Types.BOOLEAN);
            } else {
                statement.setBoolean(8, author.authenticatedOrcid);
            }
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        } catch (SQLException ex) {
            throw sqlWriteFailed(AUTHOR, "given_name/family_name/full_name",
                    "given=" + length(author.given)
                            + " family=" + length(author.family)
                            + " full=" + length(author.fullName),
                    ex);
        }
    }

    private void insertAffiliation(Connection connection, long authorId, int seq, CrossrefAffiliationRecord affiliation)
            throws SQLException {
        String sql = "INSERT INTO " + qualified(AFF) + " (crossref_author_id, sequence_no, name, ror) VALUES (?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, authorId);
            statement.setInt(2, seq);
            statement.setString(3, affiliation.name);
            statement.setString(4, affiliation.ror);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw sqlWriteFailed(AFF, "name", "length=" + length(affiliation.name), ex);
        }
    }

    private void insertIssn(Connection connection, long workId, CrossrefIssnRecord issn) throws SQLException {
        String sql = "INSERT IGNORE INTO " + qualified(ISSN) + " (crossref_work_id, issn, issn_type) VALUES (?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, workId);
            statement.setString(2, issn.issn);
            statement.setString(3, issn.type);
            statement.executeUpdate();
        }
    }

    private void insertFunder(Connection connection, long workId, CrossrefFunderRecord funder) throws SQLException {
        String sql = "INSERT INTO " + qualified(FUNDER) + " (crossref_work_id, funder_name, funder_doi, award_number) VALUES (?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, workId);
            statement.setString(2, funder.name);
            statement.setString(3, funder.funderDoi);
            statement.setString(4, funder.award);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw sqlWriteFailed(FUNDER, "funder_name", "length=" + length(funder.name), ex);
        }
    }

    private void insertLicense(Connection connection, long workId, CrossrefLicenseRecord license) throws SQLException {
        String sql = "INSERT INTO " + qualified(LICENSE)
                + " (crossref_work_id, license_url, start_date, content_version, delay_in_days) VALUES (?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, workId);
            statement.setString(2, license.url);
            statement.setString(3, license.startDate);
            statement.setString(4, license.contentVersion);
            if (license.delayInDays == null) {
                statement.setNull(5, Types.INTEGER);
            } else {
                statement.setInt(5, license.delayInDays);
            }
            statement.executeUpdate();
        }
    }

    private void insertRelation(Connection connection, long workId, CrossrefRelationRecord relation) throws SQLException {
        String sql = "INSERT INTO " + qualified(RELATION)
                + " (crossref_work_id, relation_type, related_doi, related_id, asserted_by) VALUES (?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, workId);
            statement.setString(2, relation.type);
            statement.setString(3, relation.doi);
            statement.setString(4, relation.id);
            statement.setString(5, relation.assertedBy);
            statement.executeUpdate();
        }
    }

    private void deleteByWork(Connection connection, String table, long workId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM " + qualified(table) + " WHERE crossref_work_id = ?")) {
            statement.setLong(1, workId);
            statement.executeUpdate();
        }
    }

    private int bindWorkFields(
            PreparedStatement statement,
            int start,
            String displayDoi,
            String normalizedDoi,
            CrossrefWorkRecord work,
            CrossrefFetchResult fetch,
            String syncId,
            String parentSnap,
            String childSnap,
            String comparison
    ) throws SQLException {
        int i = start;
        statement.setString(i++, displayDoi);
        if (normalizedDoi != null) {
            statement.setString(i++, normalizedDoi);
        }
        statement.setString(i++, work.canonicalDoi);
        statement.setString(i++, fetch.status);
        statement.setString(i++, work.registrationAgency);
        statement.setString(i++, work.registrationAgencyId);
        statement.setString(i++, work.type);
        statement.setString(i++, work.title);
        statement.setString(i++, work.subtitle);
        statement.setString(i++, work.shortTitle);
        statement.setString(i++, work.containerTitle);
        statement.setString(i++, work.publisher);
        statement.setString(i++, work.volume);
        statement.setString(i++, work.issue);
        statement.setString(i++, work.page);
        statement.setString(i++, work.articleNumber);
        statement.setString(i++, work.language);
        setInt(statement, i++, work.referenceCount);
        setInt(statement, i++, work.isReferencedByCount);
        statement.setString(i++, work.publishedDate);
        statement.setString(i++, work.publishedPrintDate);
        statement.setString(i++, work.publishedOnlineDate);
        statement.setString(i++, work.issuedDate);
        setDateTime(statement, i++, work.createdCrossrefAt);
        setDateTime(statement, i++, work.depositedCrossrefAt);
        setDateTime(statement, i++, work.indexedCrossrefAt);
        statement.setBoolean(i++, hasOrcid(work));
        statement.setBoolean(i++, hasAffiliation(work));
        statement.setBoolean(i++, hasRor(work));
        statement.setBoolean(i++, !work.funders.isEmpty());
        statement.setBoolean(i++, !work.licenses.isEmpty());
        statement.setBoolean(i++, hasUpdate(work));
        statement.setBoolean(i++, hasRetraction(work));
        statement.setString(i++, comparison);
        statement.setInt(i++, fetch.httpStatus);
        statement.setString(i++, fetch.errorCode);
        statement.setString(i++, fetch.errorMessage);
        bindSyncId(statement, i++, WORK, "last_sync_execution_id", syncId);
        statement.setString(i++, parentSnap);
        statement.setString(i++, childSnap);
        statement.setString(i++, work.rawJson);
        return i;
    }

    private static void setInt(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private static void setDateTime(PreparedStatement statement, int index, String raw) throws SQLException {
        Timestamp value = toTimestamp(raw);
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP);
        } else {
            statement.setTimestamp(index, value);
        }
    }

    private static Timestamp toTimestamp(String raw) {
        String normalized = CrossrefMapper.mysqlDateTime(raw);
        if (normalized == null) {
            return null;
        }
        try {
            return Timestamp.valueOf(LocalDateTime.parse(normalized, MYSQL_DATE_TIME));
        } catch (RuntimeException ignored) {
            try {
                return Timestamp.valueOf(normalized.length() > 19 ? normalized.substring(0, 19) : normalized);
            } catch (RuntimeException ignoredAgain) {
                return null;
            }
        }
    }

    private static final DateTimeFormatter MYSQL_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

    private void addFieldChanges(
            CrossrefReconcileResult result,
            StoredWork stored,
            CrossrefWorkRecord incoming,
            CrossrefFetchResult fetch,
            long workId,
            String doi
    ) {
        if (!java.util.Objects.equals(stored.status, fetch.status)) {
            result.changes.add(SyncChange.updated("STATUS", "Crossref status changed", stored.status, fetch.status)
                    .on("CROSSREF", workId, null, doi));
        }
        if (stored.title != null && incoming.title != null && CrossrefComparison.titlesDiffer(stored.title, incoming.title)) {
            result.changes.add(SyncChange.updated("TITLE_CHANGED", "Crossref title changed", stored.title, incoming.title)
                    .on("CROSSREF", workId, null, doi));
        }
        if (hasOrcid(incoming) && !stored.hasOrcid) {
            result.changes.add(SyncChange.updated("ORCID_ADDED", "Crossref ORCID added", "0", "1")
                    .on("CROSSREF", workId, null, doi));
        }
        if (hasAffiliation(incoming) && !stored.hasAffiliation) {
            result.changes.add(SyncChange.updated("AFFILIATION_ADDED", "Crossref affiliation added", "0", "1")
                    .on("CROSSREF", workId, null, doi));
        }
        if (!incoming.funders.isEmpty() && !stored.hasFunding) {
            result.changes.add(SyncChange.updated("FUNDING_CHANGED", "Crossref funding added", "0", "1")
                    .on("CROSSREF", workId, null, doi));
        }
        if (!incoming.licenses.isEmpty() && !stored.hasLicense) {
            result.changes.add(SyncChange.updated("LICENSE_CHANGED", "Crossref license added", "0", "1")
                    .on("CROSSREF", workId, null, doi));
        }
        if (hasUpdate(incoming) && !stored.hasUpdate) {
            result.changes.add(SyncChange.updated("UPDATE_DETECTED", "Crossref update detected", "0", "1")
                    .on("CROSSREF", workId, null, doi));
        }
        if (hasRetraction(incoming) && !stored.hasRetraction) {
            result.changes.add(SyncChange.updated("RETRACTION_DETECTED", "Crossref retraction detected", "0", "1")
                    .on("CROSSREF", workId, null, doi));
        }
        if (result.changes.isEmpty()) {
            result.changes.add(SyncChange.updated("PARENT", "Crossref metadata updated", null, null)
                    .on("CROSSREF", workId, null, doi));
        }
    }

    private static boolean hasOrcid(CrossrefWorkRecord work) {
        return work.authors.stream().anyMatch(item -> item.orcid != null);
    }

    private static boolean hasAffiliation(CrossrefWorkRecord work) {
        return work.authors.stream().anyMatch(item -> !item.affiliations.isEmpty());
    }

    private static boolean hasRor(CrossrefWorkRecord work) {
        return work.authors.stream().anyMatch(item -> item.affiliations.stream().anyMatch(aff -> aff.ror != null));
    }

    private static boolean hasUpdate(CrossrefWorkRecord work) {
        return work.relations.stream().anyMatch(item -> {
            String type = item.type == null ? "" : item.type.toLowerCase(Locale.ROOT);
            return type.contains("update") || type.contains("correction") || type.contains("expression");
        });
    }

    private static boolean hasRetraction(CrossrefWorkRecord work) {
        return work.relations.stream().anyMatch(item -> {
            String type = item.type == null ? "" : item.type.toLowerCase(Locale.ROOT);
            return type.contains("retract");
        });
    }

    private static String comparisonStatus(String status, CrossrefWorkRecord work, List<CrossrefDoiLink> links) {
        if (!CrossrefStatus.FOUND.equals(status) || work == null || work.title == null || links == null) {
            return CrossrefStatus.COMPARE_NOT_APPLICABLE;
        }
        for (CrossrefDoiLink link : links) {
            if (CrossrefComparison.titlesDiffer(link.title, work.title)) {
                return CrossrefStatus.COMPARE_DIFFERENCE;
            }
        }
        return CrossrefStatus.COMPARE_MATCH;
    }

    private FilterSql filterSql(CrossrefQuery query) {
        FilterSql filter = new FilterSql();
        StringBuilder sql = new StringBuilder(" WHERE 1=1 ");
        if (notBlank(query.status)) {
            sql.append(" AND w.status = ? ");
            filter.params.add(query.status);
        }
        if (notBlank(query.workType)) {
            sql.append(" AND w.work_type = ? ");
            filter.params.add(query.workType);
        }
        if (query.hasOrcid != null) {
            sql.append(" AND w.has_orcid = ? ");
            filter.params.add(query.hasOrcid ? 1 : 0);
        }
        if (query.hasAffiliation != null) {
            sql.append(" AND w.has_affiliation = ? ");
            filter.params.add(query.hasAffiliation ? 1 : 0);
        }
        if (query.hasRor != null) {
            sql.append(" AND w.has_ror = ? ");
            filter.params.add(query.hasRor ? 1 : 0);
        }
        if (query.hasFunding != null) {
            sql.append(" AND w.has_funding = ? ");
            filter.params.add(query.hasFunding ? 1 : 0);
        }
        if (query.hasLicense != null) {
            sql.append(" AND w.has_license = ? ");
            filter.params.add(query.hasLicense ? 1 : 0);
        }
        if (query.hasUpdate != null) {
            sql.append(" AND (w.has_update = ? OR w.has_retraction = ?) ");
            filter.params.add(query.hasUpdate ? 1 : 0);
            filter.params.add(query.hasUpdate ? 1 : 0);
        }
        if (notBlank(query.comparison)) {
            sql.append(" AND w.comparison_status = ? ");
            filter.params.add(query.comparison);
        }
        if (notBlank(query.search)) {
            String like = likeContains(query.search.trim());
            sql.append("""
                     AND (
                        w.normalized_doi LIKE ? ESCAPE '!'
                        OR w.title LIKE ? ESCAPE '!'
                        OR EXISTS (
                            SELECT 1 FROM %s m
                            JOIN %s o ON o.id = m.research_output_id
                            WHERE m.crossref_work_id = w.id
                              AND (CAST(o.pure_id AS CHAR) LIKE ? ESCAPE '!' OR o.title LIKE ? ESCAPE '!')
                        )
                     )
                    """.formatted(qualified(LINK), qualified("pure_research_output")));
            filter.params.add(like);
            filter.params.add(like);
            filter.params.add(like);
            filter.params.add(like);
        }
        filter.sql = sql.toString();
        return filter;
    }

    private String orderSql(CrossrefQuery query) {
        boolean desc = query.direction == null || "desc".equalsIgnoreCase(query.direction);
        String sort = query.sort == null ? "syncedAt" : query.sort;
        if (!SORTS.contains(sort)) {
            sort = "syncedAt";
        }
        String column = switch (sort) {
            case "doi" -> "w.normalized_doi";
            case "pureTitle" -> "publication_title";
            case "title" -> "w.title";
            case "type" -> "w.work_type";
            case "container" -> "w.container_title";
            case "publishedDate" -> "w.published_date";
            case "status" -> "w.status";
            default -> "w.last_crossref_sync_at";
        };
        return orderByNullsLast(column, desc);
    }

    private List<String> distinct(String table, String column) {
        List<String> values = new ArrayList<>();
        String sql = "SELECT DISTINCT `" + column + "` FROM " + qualified(table)
                + " WHERE `" + column + "` IS NOT NULL AND TRIM(`" + column + "`) <> '' ORDER BY 1";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                values.add(rs.getString(1));
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load Crossref filters", ex);
        }
        return values;
    }

    private List<CrossrefLinkedOutputDto> loadLinks(Connection connection, long workId) throws SQLException {
        List<CrossrefLinkedOutputDto> items = new ArrayList<>();
        String sql = """
                SELECT m.research_output_id, m.electronic_version_id, m.active_in_pure, o.pure_id, o.uuid, o.title
                FROM %s m JOIN %s o ON o.id = m.research_output_id
                WHERE m.crossref_work_id = ?
                ORDER BY o.id
                """.formatted(qualified(LINK), qualified("pure_research_output"));
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, workId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    CrossrefLinkedOutputDto dto = new CrossrefLinkedOutputDto();
                    dto.researchOutputId = rs.getLong("research_output_id");
                    dto.electronicVersionId = nullableLong(rs, "electronic_version_id");
                    dto.pureId = nullableLong(rs, "pure_id");
                    dto.uuid = nullableString(rs, "uuid");
                    dto.title = nullableString(rs, "title");
                    dto.activeInPure = rs.getBoolean("active_in_pure");
                    items.add(dto);
                }
            }
        }
        return items;
    }

    private List<CrossrefAuthorDto> loadAuthors(Connection connection, long workId) throws SQLException {
        List<CrossrefAuthorDto> items = new ArrayList<>();
        String sql = "SELECT * FROM " + qualified(AUTHOR) + " WHERE crossref_work_id = ? ORDER BY sequence_no, id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, workId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    CrossrefAuthorDto dto = new CrossrefAuthorDto();
                    dto.sequenceNo = rs.getInt("sequence_no");
                    dto.givenName = nullableString(rs, "given_name");
                    dto.familyName = nullableString(rs, "family_name");
                    dto.fullName = nullableString(rs, "full_name");
                    dto.orcid = nullableString(rs, "orcid");
                    dto.authenticatedOrcid = nullableBoolean(rs, "authenticated_orcid");
                    long authorId = rs.getLong("id");
                    dto.affiliations.addAll(loadAffiliations(connection, authorId));
                    items.add(dto);
                }
            }
        }
        return items;
    }

    private List<CrossrefAffiliationDto> loadAffiliations(Connection connection, long authorId) throws SQLException {
        List<CrossrefAffiliationDto> items = new ArrayList<>();
        String sql = "SELECT name, ror FROM " + qualified(AFF) + " WHERE crossref_author_id = ? ORDER BY sequence_no";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, authorId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    CrossrefAffiliationDto dto = new CrossrefAffiliationDto();
                    dto.name = nullableString(rs, "name");
                    dto.ror = nullableString(rs, "ror");
                    items.add(dto);
                }
            }
        }
        return items;
    }

    private List<CrossrefIssnDto> loadIssns(Connection connection, long workId) throws SQLException {
        List<CrossrefIssnDto> items = new ArrayList<>();
        String sql = "SELECT issn, issn_type FROM " + qualified(ISSN) + " WHERE crossref_work_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, workId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    CrossrefIssnDto dto = new CrossrefIssnDto();
                    dto.issn = nullableString(rs, "issn");
                    dto.type = nullableString(rs, "issn_type");
                    items.add(dto);
                }
            }
        }
        return items;
    }

    private List<CrossrefFunderDto> loadFunders(Connection connection, long workId) throws SQLException {
        List<CrossrefFunderDto> items = new ArrayList<>();
        String sql = "SELECT funder_name, funder_doi, award_number FROM " + qualified(FUNDER) + " WHERE crossref_work_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, workId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    CrossrefFunderDto dto = new CrossrefFunderDto();
                    dto.name = nullableString(rs, "funder_name");
                    dto.funderDoi = nullableString(rs, "funder_doi");
                    dto.award = nullableString(rs, "award_number");
                    items.add(dto);
                }
            }
        }
        return items;
    }

    private List<CrossrefLicenseDto> loadLicenses(Connection connection, long workId) throws SQLException {
        List<CrossrefLicenseDto> items = new ArrayList<>();
        String sql = "SELECT license_url, start_date, content_version, delay_in_days FROM " + qualified(LICENSE)
                + " WHERE crossref_work_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, workId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    CrossrefLicenseDto dto = new CrossrefLicenseDto();
                    dto.url = nullableString(rs, "license_url");
                    dto.startDate = nullableString(rs, "start_date");
                    dto.contentVersion = nullableString(rs, "content_version");
                    dto.delayInDays = nullableInt(rs, "delay_in_days");
                    items.add(dto);
                }
            }
        }
        return items;
    }

    private List<CrossrefRelationDto> loadRelations(Connection connection, long workId) throws SQLException {
        List<CrossrefRelationDto> items = new ArrayList<>();
        String sql = "SELECT relation_type, related_doi, related_id, asserted_by FROM " + qualified(RELATION)
                + " WHERE crossref_work_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, workId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    CrossrefRelationDto dto = new CrossrefRelationDto();
                    dto.type = nullableString(rs, "relation_type");
                    dto.doi = nullableString(rs, "related_doi");
                    dto.id = nullableString(rs, "related_id");
                    dto.assertedBy = nullableString(rs, "asserted_by");
                    items.add(dto);
                }
            }
        }
        return items;
    }

    private CrossrefWorkSummaryDto mapSummary(ResultSet rs) throws SQLException {
        CrossrefWorkSummaryDto dto = new CrossrefWorkSummaryDto();
        fillSummary(dto, rs);
        dto.researchOutputCount = rs.getInt("research_output_count");
        dto.publicationTitle = nullableString(rs, "publication_title");
        return dto;
    }

    private CrossrefWorkDetailDto mapDetail(ResultSet rs) throws SQLException {
        CrossrefWorkDetailDto dto = new CrossrefWorkDetailDto();
        fillSummary(dto, rs);
        dto.subtitle = nullableString(rs, "subtitle");
        dto.volume = nullableString(rs, "volume");
        dto.issue = nullableString(rs, "issue");
        dto.pageRange = nullableString(rs, "page_range");
        dto.articleNumber = nullableString(rs, "article_number");
        dto.language = nullableString(rs, "language");
        dto.referenceCount = nullableInt(rs, "reference_count");
        dto.isReferencedByCount = nullableInt(rs, "is_referenced_by_count");
        dto.publishedPrintDate = nullableString(rs, "published_print_date");
        dto.publishedOnlineDate = nullableString(rs, "published_online_date");
        dto.issuedDate = nullableString(rs, "issued_date");
        dto.lastHttpStatus = nullableInt(rs, "last_http_status");
        dto.errorCode = nullableString(rs, "error_code");
        dto.errorMessage = nullableString(rs, "error_message");
        dto.rawJson = nullableString(rs, "raw_json");
        return dto;
    }

    private void fillSummary(CrossrefWorkSummaryDto dto, ResultSet rs) throws SQLException {
        dto.id = rs.getLong("id");
        dto.doi = nullableString(rs, "original_doi");
        dto.normalizedDoi = nullableString(rs, "normalized_doi");
        dto.canonicalDoi = nullableString(rs, "canonical_doi");
        dto.status = nullableString(rs, "status");
        dto.registrationAgency = nullableString(rs, "registration_agency");
        dto.workType = nullableString(rs, "work_type");
        dto.title = nullableString(rs, "title");
        dto.containerTitle = nullableString(rs, "container_title");
        dto.publisher = nullableString(rs, "publisher");
        dto.publishedDate = nullableString(rs, "published_date");
        dto.comparisonStatus = nullableString(rs, "comparison_status");
        dto.hasOrcid = rs.getBoolean("has_orcid");
        dto.hasAffiliation = rs.getBoolean("has_affiliation");
        dto.hasRor = rs.getBoolean("has_ror");
        dto.hasFunding = rs.getBoolean("has_funding");
        dto.hasLicense = rs.getBoolean("has_license");
        dto.hasUpdate = rs.getBoolean("has_update");
        dto.hasRetraction = rs.getBoolean("has_retraction");
        dto.lastCrossrefSyncAt = timestamp(rs, "last_crossref_sync_at");
        try {
            dto.issnList = nullableString(rs, "issn_list");
        } catch (SQLException ignored) {
            dto.issnList = null;
        }
    }

    private StoredWork mapStored(ResultSet rs) throws SQLException {
        StoredWork stored = new StoredWork();
        stored.id = rs.getLong("id");
        stored.status = nullableString(rs, "status");
        stored.title = nullableString(rs, "title");
        stored.parentSnapshot = nullableString(rs, "parent_snapshot");
        stored.childrenSnapshot = nullableString(rs, "children_snapshot");
        stored.hasOrcid = rs.getBoolean("has_orcid");
        stored.hasAffiliation = rs.getBoolean("has_affiliation");
        stored.hasFunding = rs.getBoolean("has_funding");
        stored.hasLicense = rs.getBoolean("has_license");
        stored.hasUpdate = rs.getBoolean("has_update");
        stored.hasRetraction = rs.getBoolean("has_retraction");
        stored.lastSyncAt = timestamp(rs, "last_crossref_sync_at");
        return stored;
    }

    private static OffsetDateTime timestamp(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant().atOffset(ZoneOffset.UTC);
    }

    private static String linkKey(long researchOutputId, Long electronicVersionId) {
        return researchOutputId + ":" + (electronicVersionId == null ? "0" : electronicVersionId);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private SQLException sqlWriteFailed(String table, String column, String detail, SQLException ex) {
        String mysql = ex.getMessage() == null || ex.getMessage().isBlank()
                ? ex.getClass().getSimpleName()
                : ex.getMessage().trim();
        return new SQLException(
                "Failed writing " + qualified(table) + ".`" + column + "` " + detail + ": " + mysql,
                ex.getSQLState(),
                ex.getErrorCode(),
                ex
        );
    }

    private static int length(String value) {
        return value == null ? 0 : value.length();
    }

    private String needsRefreshClearSql() {
        return columnExists(WORK, "needs_refresh") ? ", needs_refresh = 0" : "";
    }

    public static class StoredWork {
        public long id;
        public String status;
        public String title;
        public String parentSnapshot;
        public String childrenSnapshot;
        public boolean hasOrcid;
        public boolean hasAffiliation;
        public boolean hasFunding;
        public boolean hasLicense;
        public boolean hasUpdate;
        public boolean hasRetraction;
        public OffsetDateTime lastSyncAt;
    }

    public static class RunExtras {
        public long found;
        public long otherAgency;
        public long notFound;
        public long invalid;
    }

    public static class QualityRow {
        public long workId;
        public String normalizedDoi;
        public String status;
        public String crossrefTitle;
        public String containerTitle;
        public boolean hasOrcid;
        public boolean hasAffiliation;
        public boolean hasRor;
        public boolean hasFunding;
        public boolean hasUpdate;
        public boolean hasRetraction;
        public long researchOutputId;
        public Long pureId;
        public String uuid;
        public String pureTitle;
    }

    private static final class FilterSql {
        private String sql = "";
        private final List<Object> params = new ArrayList<>();
    }
}
