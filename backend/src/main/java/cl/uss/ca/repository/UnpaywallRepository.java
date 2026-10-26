package cl.uss.ca.repository;

import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.api.dto.UnpaywallFiltersDto;
import cl.uss.ca.api.dto.UnpaywallLinkedOutputDto;
import cl.uss.ca.api.dto.UnpaywallLocationDto;
import cl.uss.ca.api.dto.UnpaywallQuery;
import cl.uss.ca.api.dto.UnpaywallStatusCountDto;
import cl.uss.ca.api.dto.UnpaywallWorkDetailDto;
import cl.uss.ca.api.dto.UnpaywallWorkSummaryDto;
import cl.uss.ca.exception.DatabaseException;
import cl.uss.ca.exception.ValidationException;
import cl.uss.ca.external.unpaywall.UnpaywallFetchResult;
import cl.uss.ca.external.unpaywall.UnpaywallLocationIdentity;
import cl.uss.ca.external.unpaywall.UnpaywallLocationRecord;
import cl.uss.ca.external.unpaywall.UnpaywallWorkRecord;
import cl.uss.ca.external.unpaywall.UnpaywallWorkSnapshot;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class UnpaywallRepository extends JdbcRepository {
    private static final String WORK = "unpaywall_work";
    private static final String LOCATION = "unpaywall_oa_location";
    private static final String LINK = "unpaywall_research_output";
    private static final String STATS = "unpaywall_sync_stats";
    private static final String DOI_TYPE = "DoiElectronicVersion";
    private static final Set<String> SORTS = Set.of(
            "doi", "title", "isOa", "oaStatus", "bestOaHostType", "bestOaLicense",
            "bestOaVersion", "retrievedAt", "year", "journalName", "hasRepositoryCopy"
    );

    public boolean available() {
        return tableExists(WORK) && tableExists(LOCATION) && tableExists(LINK);
    }

    public void assertAvailable() {
        if (!available()) {
            throw new ValidationException(
                    "UNPAYWALL_TABLES_UNAVAILABLE",
                    "unpaywall_work tables are required before synchronizing Unpaywall"
            );
        }
    }

    public List<UnpaywallDoiLink> listActiveDoiLinks() {
        if (!tableExists("pure_research_output_electronic_version") || !tableExists("pure_research_output")) {
            return List.of();
        }
        String sql = """
                SELECT ev.id AS electronic_version_id,
                       ev.research_output_id,
                       ev.doi,
                       o.uuid,
                       o.title,
                       o.pure_id
                FROM %s ev
                JOIN %s o ON o.id = ev.research_output_id
                WHERE ev.type_discriminator = ?
                  AND ev.doi IS NOT NULL
                  AND TRIM(ev.doi) <> ''
                  AND %s
                ORDER BY ev.research_output_id, ev.id
                """.formatted(
                qualified("pure_research_output_electronic_version"),
                qualified("pure_research_output"),
                activeInPurePredicate("pure_research_output", "o").trim()
        );
        List<UnpaywallDoiLink> links = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, DOI_TYPE);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    UnpaywallDoiLink link = new UnpaywallDoiLink();
                    link.electronicVersionId = nullableLong(rs, "electronic_version_id");
                    link.researchOutputId = rs.getLong("research_output_id");
                    link.doi = nullableString(rs, "doi");
                    link.uuid = nullableString(rs, "uuid");
                    link.title = nullableString(rs, "title");
                    link.pureId = nullableLong(rs, "pure_id");
                    links.add(link);
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list active DOI universe", ex);
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
                if (!rs.next()) {
                    return null;
                }
                return mapStored(rs);
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load Unpaywall work", ex);
        }
    }

    public boolean isFresh(StoredWork stored, int refreshDays) {
        if (stored == null || refreshDays <= 0 || stored.retrievedAt == null) {
            return false;
        }
        if (!UnpaywallFetchResult.FOUND.equals(stored.lastOutcome)
                && !UnpaywallFetchResult.NOT_FOUND.equals(stored.lastOutcome)) {
            return false;
        }
        return stored.retrievedAt.isAfter(OffsetDateTime.now().minusDays(refreshDays));
    }

    public Map<String, WorkSyncState> loadWorkStates() {
        Map<String, WorkSyncState> items = new HashMap<>();
        if (!tableExists(WORK)) {
            return items;
        }
        boolean refresh = columnExists(WORK, "needs_refresh");
        String sql = "SELECT normalized_doi, last_outcome, retrieved_at"
                + (refresh ? ", needs_refresh" : "")
                + " FROM " + qualified(WORK);
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                WorkSyncState state = new WorkSyncState();
                state.present = true;
                state.status = nullableString(rs, "last_outcome");
                state.lastSyncAt = timestamp(rs, "retrieved_at");
                state.needsRefresh = refresh && rs.getBoolean("needs_refresh");
                items.put(rs.getString("normalized_doi"), state);
            }
            return items;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load Unpaywall work states", ex);
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
                INNER JOIN %s m ON m.unpaywall_work_id = w.id
                """.formatted(qualified(WORK), qualified(LINK));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                items.add(rs.getString(1));
            }
            return items;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load mapped Unpaywall DOIs", ex);
        }
    }

    public UnpaywallReconcileResult reconcile(
            String normalizedDoi,
            String displayDoi,
            UnpaywallFetchResult fetch,
            List<UnpaywallDoiLink> links,
            String syncId
    ) {
        assertAvailable();
        UnpaywallReconcileResult result = new UnpaywallReconcileResult();
        try (Connection connection = connection()) {
            StoredWork stored = findByNormalizedDoi(connection, normalizedDoi);
            UnpaywallWorkRecord incoming = fetch.found() ? fetch.work : emptyNotFound(displayDoi);
            boolean repositoryCopy = fetch.found()
                    && UnpaywallLocationIdentity.hasRepositoryCopy(incoming.oaLocations);
            if (fetch.found()) {
                incoming.hasRepositoryCopy = repositoryCopy;
            }
            String parentSnap = UnpaywallWorkSnapshot.parent(incoming, fetch.outcome);
            String locSnap = fetch.found() ? UnpaywallWorkSnapshot.locations(incoming.oaLocations) : "";
            String storedParent = stored == null ? null : stored.parentSnapshot;
            String storedLoc = stored == null ? null : stored.locationSnapshot;
            boolean parentChanged = stored == null || !UnpaywallWorkSnapshot.sameParent(storedParent, parentSnap);
            boolean locChanged = stored == null || !UnpaywallWorkSnapshot.sameLocations(storedLoc, locSnap);
            long workId;
            if (stored == null) {
                workId = insertWork(connection, normalizedDoi, displayDoi, incoming, fetch, syncId, parentSnap, locSnap);
                result.kind = "CREATED";
                result.changes.add(SyncChange.created().on("UNPAYWALL", workId, null, normalizedDoi));
            } else {
                workId = stored.id;
                if (parentChanged || locChanged) {
                    updateWork(connection, workId, displayDoi, incoming, fetch, syncId, parentSnap, locSnap);
                    result.kind = "UPDATED";
                    addFieldChanges(result, stored, incoming, fetch, workId, normalizedDoi);
                } else {
                    touchRetrieved(connection, workId, fetch, syncId);
                    result.kind = "UNCHANGED";
                }
            }
            if (fetch.found()) {
                reconcileLocations(connection, workId, incoming);
            } else if (locChanged || stored == null) {
                deleteLocations(connection, workId);
            }
            reconcileLinks(connection, workId, links);
            return result;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to reconcile Unpaywall work", ex);
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
                INSERT INTO %s (sync_execution_id, not_found_count, oa_count, non_oa_count, skipped_fresh_count)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    not_found_count = VALUES(not_found_count),
                    oa_count = VALUES(oa_count),
                    non_oa_count = VALUES(non_oa_count),
                    skipped_fresh_count = VALUES(skipped_fresh_count)
                """.formatted(qualified(STATS));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.setLong(2, run.notFound.get());
            statement.setLong(3, run.oa.get());
            statement.setLong(4, run.nonOa.get());
            statement.setLong(5, run.reactivated.get());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to persist Unpaywall sync stats", ex);
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
        String sql = "SELECT not_found_count, oa_count, non_oa_count FROM " + qualified(STATS)
                + " WHERE sync_execution_id = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                RunExtras extras = new RunExtras();
                extras.notFound = rs.getLong(1);
                extras.oa = rs.getLong(2);
                extras.nonOa = rs.getLong(3);
                return extras;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load Unpaywall sync stats", ex);
        }
    }

    public long countUniqueActiveDois() {
        return listActiveDoiLinks().stream()
                .map(link -> cl.uss.ca.domain.DoiNormalizer.normalize(link.doi))
                .filter(value -> value != null)
                .distinct()
                .count();
    }

    public long countEvaluated() {
        return scalarLong("SELECT COUNT(*) FROM " + qualified(WORK) + " WHERE last_outcome = 'FOUND'");
    }

    public long countOa(boolean oa) {
        return scalarLong(
                "SELECT COUNT(*) FROM " + qualified(WORK) + " WHERE last_outcome = 'FOUND' AND is_oa = ?",
                oa ? 1 : 0
        );
    }

    public long countRepositoryCopy() {
        return scalarLong(
                "SELECT COUNT(*) FROM " + qualified(WORK) + " WHERE last_outcome = 'FOUND' AND has_repository_copy = 1"
        );
    }

    public long countNotFound() {
        return scalarLong("SELECT COUNT(*) FROM " + qualified(WORK) + " WHERE last_outcome = 'NOT_FOUND'");
    }

    public List<UnpaywallStatusCountDto> statusDistribution() {
        String sql = """
                SELECT oa_status, COUNT(*) AS total
                FROM %s
                WHERE last_outcome = 'FOUND' AND oa_status IS NOT NULL AND TRIM(oa_status) <> ''
                GROUP BY oa_status
                ORDER BY total DESC, oa_status ASC
                """.formatted(qualified(WORK));
        List<UnpaywallStatusCountDto> rows = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                UnpaywallStatusCountDto row = new UnpaywallStatusCountDto();
                row.oaStatus = rs.getString(1);
                row.count = rs.getLong(2);
                rows.add(row);
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load Unpaywall OA status distribution", ex);
        }
        return rows;
    }

    public UnpaywallFiltersDto filters() {
        UnpaywallFiltersDto dto = new UnpaywallFiltersDto();
        dto.oaStatuses.addAll(distinct(WORK, "oa_status"));
        dto.hostTypes.addAll(distinct(LOCATION, "host_type"));
        dto.licenses.addAll(distinct(LOCATION, "license"));
        dto.versions.addAll(distinct(LOCATION, "version"));
        return dto;
    }

    public PageResponse<UnpaywallWorkSummaryDto> list(UnpaywallQuery query) {
        assertAvailable();
        FilterSql filter = filterSql(query);
        long total = scalarLong("SELECT COUNT(*) FROM " + qualified(WORK) + " w " + filter.fromExtra + filter.where, filter.params.toArray());
        int page = Math.max(1, query.page);
        int size = Math.min(100, Math.max(1, query.pageSize));
        String sql = """
                SELECT w.*,
                       (SELECT COUNT(*) FROM %s l WHERE l.unpaywall_work_id = w.id) AS research_output_count,
                       (SELECT MIN(o.title) FROM %s l
                            JOIN %s o ON o.id = l.research_output_id
                        WHERE l.unpaywall_work_id = w.id) AS publication_title
                FROM %s w
                %s
                %s
                ORDER BY %s
                LIMIT ? OFFSET ?
                """.formatted(
                qualified(LINK),
                qualified(LINK),
                qualified("pure_research_output"),
                qualified(WORK),
                filter.fromExtra,
                filter.where,
                orderBy(query)
        );
        List<UnpaywallWorkSummaryDto> items = new ArrayList<>();
        List<Object> params = new ArrayList<>(filter.params);
        params.add(size);
        params.add((page - 1) * size);
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params.toArray());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    items.add(mapSummary(rs));
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list Unpaywall works", ex);
        }
        attachOutputs(items);
        return PageResponse.of(items, page - 1, size, total);
    }

    public List<UnpaywallWorkSummaryDto> listForExport(UnpaywallQuery query) {
        UnpaywallQuery export = copyQuery(query);
        export.page = 1;
        export.pageSize = 20_000;
        return list(export).items;
    }

    public UnpaywallWorkDetailDto findDetail(long id) {
        assertAvailable();
        String sql = "SELECT * FROM " + qualified(WORK) + " WHERE id = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                UnpaywallWorkDetailDto dto = mapDetail(rs);
                dto.researchOutputs.addAll(loadOutputs(connection, id));
                dto.locations.addAll(loadLocations(connection, id));
                dto.researchOutputCount = dto.researchOutputs.size();
                if (!dto.researchOutputs.isEmpty()) {
                    dto.publicationTitle = dto.researchOutputs.get(0).title;
                }
                return dto;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load Unpaywall work detail", ex);
        }
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
            UnpaywallWorkRecord work,
            UnpaywallFetchResult fetch,
            String syncId,
            String parentSnap,
            String locSnap
    ) throws SQLException {
        String sql = """
                INSERT INTO %s (
                    doi, normalized_doi, doi_url, last_outcome, is_oa, oa_status, genre, title, year,
                    published_date, publisher, journal_name, journal_issns, journal_issn_l,
                    journal_is_oa, journal_is_in_doaj, has_repository_copy,
                    best_oa_host_type, best_oa_license, best_oa_version, best_oa_url,
                    best_oa_landing_page_url, best_oa_pdf_url, api_updated_at, retrieved_at,
                    last_http_status, last_error_code, last_error_message, last_sync_execution_id
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NOW(6),?,?,?,?)
                """.formatted(qualified(WORK));
        try (PreparedStatement statement = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            bindWork(statement, displayDoi, normalizedDoi, work, fetch, syncId);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("unpaywall_work insert did not return id");
                }
                return keys.getLong(1);
            }
        }
    }

    private void updateWork(
            Connection connection,
            long id,
            String displayDoi,
            UnpaywallWorkRecord work,
            UnpaywallFetchResult fetch,
            String syncId,
            String parentSnap,
            String locSnap
    ) throws SQLException {
        String sql = """
                UPDATE %s SET
                    doi = ?, doi_url = ?, last_outcome = ?, is_oa = ?, oa_status = ?, genre = ?, title = ?,
                    year = ?, published_date = ?, publisher = ?, journal_name = ?, journal_issns = ?,
                    journal_issn_l = ?, journal_is_oa = ?, journal_is_in_doaj = ?, has_repository_copy = ?,
                    best_oa_host_type = ?, best_oa_license = ?, best_oa_version = ?, best_oa_url = ?,
                    best_oa_landing_page_url = ?, best_oa_pdf_url = ?, api_updated_at = ?, retrieved_at = NOW(6),
                    last_http_status = ?, last_error_code = ?, last_error_message = ?, last_sync_execution_id = ?%s
                WHERE id = ?
                """.formatted(qualified(WORK), needsRefreshClearSql());
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindWorkUpdate(statement, displayDoi, work, fetch, syncId);
            statement.setLong(28, id);
            statement.executeUpdate();
        }
    }

    private void touchRetrieved(Connection connection, long id, UnpaywallFetchResult fetch, String syncId)
            throws SQLException {
        String sql = """
                UPDATE %s SET retrieved_at = NOW(6), last_http_status = ?, last_error_code = ?,
                    last_error_message = ?, last_sync_execution_id = ?%s
                WHERE id = ?
                """.formatted(qualified(WORK), needsRefreshClearSql());
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, fetch.httpStatus);
            statement.setString(2, fetch.errorCode);
            statement.setString(3, fetch.errorMessage);
            setSyncId(statement, 4, syncId);
            statement.setLong(5, id);
            statement.executeUpdate();
        }
    }

    private void reconcileLocations(Connection connection, long workId, UnpaywallWorkRecord work) throws SQLException {
        Map<String, Long> existing = new LinkedHashMap<>();
        String select = "SELECT id, location_key FROM " + qualified(LOCATION) + " WHERE unpaywall_work_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setLong(1, workId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    existing.put(rs.getString("location_key"), rs.getLong("id"));
                }
            }
        }
        for (UnpaywallLocationRecord location : work.oaLocations) {
            String key = UnpaywallLocationIdentity.key(location);
            if (key == null) {
                continue;
            }
            Long id = existing.remove(key);
            if (id == null) {
                insertLocation(connection, workId, key, location);
            } else {
                updateLocation(connection, id, location);
            }
        }
        if (!existing.isEmpty()) {
            String sql = "DELETE FROM " + qualified(LOCATION) + " WHERE id IN (" + inPlaceholders(existing.size()) + ")";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int i = 1;
                for (Long id : existing.values()) {
                    statement.setLong(i++, id);
                }
                statement.executeUpdate();
            }
        }
    }

    private void deleteLocations(Connection connection, long workId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM " + qualified(LOCATION) + " WHERE unpaywall_work_id = ?")) {
            statement.setLong(1, workId);
            statement.executeUpdate();
        }
    }

    private void reconcileLinks(Connection connection, long workId, List<UnpaywallDoiLink> links) throws SQLException {
        Map<String, Long> existing = new LinkedHashMap<>();
        String select = """
                SELECT id, research_output_id, electronic_version_id
                FROM %s WHERE unpaywall_work_id = ?
                """.formatted(qualified(LINK));
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
            for (UnpaywallDoiLink link : links) {
                String key = linkKey(link.researchOutputId, link.electronicVersionId);
                if (existing.remove(key) != null) {
                    continue;
                }
                insertLink(connection, workId, link);
            }
        }
        if (!existing.isEmpty()) {
            String sql = "DELETE FROM " + qualified(LINK) + " WHERE id IN (" + inPlaceholders(existing.size()) + ")";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int i = 1;
                for (Long id : existing.values()) {
                    statement.setLong(i++, id);
                }
                statement.executeUpdate();
            }
        }
    }

    private void insertLink(Connection connection, long workId, UnpaywallDoiLink link) throws SQLException {
        String sql = """
                INSERT INTO %s (unpaywall_work_id, research_output_id, electronic_version_id)
                VALUES (?, ?, ?)
                """.formatted(qualified(LINK));
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, workId);
            statement.setLong(2, link.researchOutputId);
            if (link.electronicVersionId == null) {
                statement.setNull(3, Types.BIGINT);
            } else {
                statement.setLong(3, link.electronicVersionId);
            }
            statement.executeUpdate();
        }
    }

    private void insertLocation(Connection connection, long workId, String key, UnpaywallLocationRecord location)
            throws SQLException {
        String sql = """
                INSERT INTO %s (unpaywall_work_id, location_key, endpoint_id, host_type, license, version,
                    url, url_for_landing_page, url_for_pdf, oa_date, is_best, evidence,
                    repository_institution, pmh_id)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """.formatted(qualified(LOCATION));
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, workId);
            statement.setString(2, key);
            bindLocation(statement, 3, location);
            statement.executeUpdate();
        }
    }

    private void updateLocation(Connection connection, long id, UnpaywallLocationRecord location) throws SQLException {
        String sql = """
                UPDATE %s SET endpoint_id = ?, host_type = ?, license = ?, version = ?, url = ?,
                    url_for_landing_page = ?, url_for_pdf = ?, oa_date = ?, is_best = ?, evidence = ?,
                    repository_institution = ?, pmh_id = ?
                WHERE id = ?
                """.formatted(qualified(LOCATION));
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindLocation(statement, 1, location);
            statement.setLong(13, id);
            statement.executeUpdate();
        }
    }

    private void bindWork(
            PreparedStatement statement,
            String displayDoi,
            String normalizedDoi,
            UnpaywallWorkRecord work,
            UnpaywallFetchResult fetch,
            String syncId
    ) throws SQLException {
        statement.setString(1, displayDoi);
        statement.setString(2, normalizedDoi);
        bindWorkBody(statement, 3, work, fetch, syncId);
    }

    private void bindWorkUpdate(
            PreparedStatement statement,
            String displayDoi,
            UnpaywallWorkRecord work,
            UnpaywallFetchResult fetch,
            String syncId
    ) throws SQLException {
        statement.setString(1, displayDoi);
        bindWorkBody(statement, 2, work, fetch, syncId);
    }

    private void bindWorkBody(
            PreparedStatement statement,
            int start,
            UnpaywallWorkRecord work,
            UnpaywallFetchResult fetch,
            String syncId
    ) throws SQLException {
        UnpaywallLocationRecord best = work == null ? null : work.bestOaLocation;
        int i = start;
        statement.setString(i++, work == null ? null : work.doiUrl);
        statement.setString(i++, fetch.outcome);
        setBoolean(statement, i++, work == null ? null : work.isOa);
        statement.setString(i++, work == null ? null : work.oaStatus);
        statement.setString(i++, work == null ? null : work.genre);
        statement.setString(i++, work == null ? null : work.title);
        setInteger(statement, i++, work == null ? null : work.year);
        statement.setString(i++, work == null ? null : work.publishedDate);
        statement.setString(i++, work == null ? null : work.publisher);
        statement.setString(i++, work == null ? null : work.journalName);
        statement.setString(i++, work == null ? null : work.journalIssns);
        statement.setString(i++, work == null ? null : work.journalIssnL);
        setBoolean(statement, i++, work == null ? null : work.journalIsOa);
        setBoolean(statement, i++, work == null ? null : work.journalIsInDoaj);
        setBoolean(statement, i++, work == null ? null : work.hasRepositoryCopy);
        statement.setString(i++, best == null ? null : best.hostType);
        statement.setString(i++, best == null ? null : best.license);
        statement.setString(i++, best == null ? null : best.version);
        statement.setString(i++, best == null ? null : best.url);
        statement.setString(i++, best == null ? null : best.urlForLandingPage);
        statement.setString(i++, best == null ? null : best.urlForPdf);
        if (work != null && work.apiUpdatedAt != null) {
            statement.setTimestamp(i++, Timestamp.from(work.apiUpdatedAt.toInstant()));
        } else {
            statement.setNull(i++, Types.TIMESTAMP);
        }
        statement.setInt(i++, fetch.httpStatus);
        statement.setString(i++, fetch.errorCode);
        statement.setString(i++, fetch.errorMessage);
        setSyncId(statement, i, syncId);
    }

    private void bindLocation(PreparedStatement statement, int start, UnpaywallLocationRecord location)
            throws SQLException {
        int i = start;
        statement.setString(i++, location.endpointId);
        statement.setString(i++, location.hostType);
        statement.setString(i++, location.license);
        statement.setString(i++, location.version);
        statement.setString(i++, location.url);
        statement.setString(i++, location.urlForLandingPage);
        statement.setString(i++, location.urlForPdf);
        statement.setString(i++, location.oaDate);
        statement.setBoolean(i++, Boolean.TRUE.equals(location.isBest));
        statement.setString(i++, location.evidence);
        statement.setString(i++, location.repositoryInstitution);
        statement.setString(i, location.pmhId);
    }

    private UnpaywallWorkRecord emptyNotFound(String doi) {
        UnpaywallWorkRecord work = new UnpaywallWorkRecord();
        work.doi = doi;
        return work;
    }

    private void addFieldChanges(
            UnpaywallReconcileResult result,
            StoredWork stored,
            UnpaywallWorkRecord incoming,
            UnpaywallFetchResult fetch,
            long workId,
            String normalizedDoi
    ) {
        if (stored.isOa != incoming.isOa) {
            result.changes.add(SyncChange.updated("IS_OA", "is_oa changed", flag(stored.isOa), flag(incoming.isOa))
                    .on("UNPAYWALL", workId, null, normalizedDoi));
        }
        if (!eq(stored.oaStatus, incoming.oaStatus)) {
            result.changes.add(SyncChange.updated("OA_STATUS", "oa_status changed", stored.oaStatus, incoming.oaStatus)
                    .on("UNPAYWALL", workId, null, normalizedDoi));
        }
        String newHost = incoming.bestOaLocation == null ? null : incoming.bestOaLocation.hostType;
        if (!eq(stored.bestHost, newHost)) {
            result.changes.add(SyncChange.updated("BEST_LOCATION", "best OA location changed", stored.bestHost, newHost)
                    .on("UNPAYWALL", workId, null, normalizedDoi));
        }
        String newLicense = incoming.bestOaLocation == null ? null : incoming.bestOaLocation.license;
        if (!eq(stored.bestLicense, newLicense)) {
            result.changes.add(SyncChange.updated("LICENSE", "best OA license changed", stored.bestLicense, newLicense)
                    .on("UNPAYWALL", workId, null, normalizedDoi));
        }
        if (result.changes.isEmpty()) {
            result.changes.add(SyncChange.updated("PARENT", "Unpaywall work updated", stored.lastOutcome, fetch.outcome)
                    .on("UNPAYWALL", workId, null, normalizedDoi));
        }
    }

    private FilterSql filterSql(UnpaywallQuery query) {
        FilterSql filter = new FilterSql();
        List<String> clauses = new ArrayList<>();
        if (query.search != null && !query.search.isBlank()) {
            String like = likeContains(query.search.trim());
            clauses.add("""
                    (w.normalized_doi LIKE ? ESCAPE '!'
                     OR w.doi LIKE ? ESCAPE '!'
                     OR w.title LIKE ? ESCAPE '!'
                     OR EXISTS (
                        SELECT 1 FROM %s l
                        JOIN %s o ON o.id = l.research_output_id
                        WHERE l.unpaywall_work_id = w.id
                          AND (o.uuid LIKE ? ESCAPE '!' OR CAST(o.pure_id AS CHAR) LIKE ? ESCAPE '!'
                               OR CAST(o.id AS CHAR) LIKE ? ESCAPE '!')
                     ))
                    """.formatted(qualified(LINK), qualified("pure_research_output")));
            filter.params.add(like);
            filter.params.add(like);
            filter.params.add(like);
            filter.params.add(like);
            filter.params.add(like);
            filter.params.add(like);
        }
        if (notBlank(query.oaStatus)) {
            clauses.add("w.oa_status = ?");
            filter.params.add(query.oaStatus.trim());
        }
        if (query.isOa != null) {
            clauses.add("w.last_outcome = 'FOUND' AND w.is_oa = ?");
            filter.params.add(query.isOa ? 1 : 0);
        }
        if (query.repositoryCopy != null) {
            clauses.add("w.last_outcome = 'FOUND' AND w.has_repository_copy = ?");
            filter.params.add(query.repositoryCopy ? 1 : 0);
        }
        if (notBlank(query.outcome)) {
            clauses.add("w.last_outcome = ?");
            filter.params.add(query.outcome.trim().toUpperCase());
        }
        if (notBlank(query.hostType) || notBlank(query.license) || notBlank(query.version)) {
            List<String> loc = new ArrayList<>();
            if (notBlank(query.hostType)) {
                loc.add("loc.host_type = ?");
                filter.params.add(query.hostType.trim());
            }
            if (notBlank(query.license)) {
                loc.add("loc.license = ?");
                filter.params.add(query.license.trim());
            }
            if (notBlank(query.version)) {
                loc.add("loc.version = ?");
                filter.params.add(query.version.trim());
            }
            clauses.add("EXISTS (SELECT 1 FROM " + qualified(LOCATION)
                    + " loc WHERE loc.unpaywall_work_id = w.id AND " + String.join(" AND ", loc) + ")");
        }
        filter.where = clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
        return filter;
    }

    private String orderBy(UnpaywallQuery query) {
        String sort = query.sort == null ? "retrievedAt" : query.sort;
        if (!SORTS.contains(sort)) {
            sort = "retrievedAt";
        }
        boolean desc = query.direction == null || "desc".equalsIgnoreCase(query.direction);
        String expression = switch (sort) {
            case "doi" -> "w.normalized_doi";
            case "title" -> "w.title";
            case "isOa" -> "w.is_oa";
            case "oaStatus" -> "w.oa_status";
            case "bestOaHostType" -> "w.best_oa_host_type";
            case "bestOaLicense" -> "w.best_oa_license";
            case "bestOaVersion" -> "w.best_oa_version";
            case "year" -> "w.year";
            case "journalName" -> "w.journal_name";
            case "hasRepositoryCopy" -> "w.has_repository_copy";
            default -> "w.retrieved_at";
        };
        return orderByNullsLast(expression, desc);
    }

    private void attachOutputs(List<UnpaywallWorkSummaryDto> items) {
        if (items.isEmpty()) {
            return;
        }
        Map<Long, UnpaywallWorkSummaryDto> byId = new LinkedHashMap<>();
        for (UnpaywallWorkSummaryDto item : items) {
            byId.put(item.id, item);
        }
        String sql = """
                SELECT l.unpaywall_work_id, l.research_output_id, l.electronic_version_id,
                       o.pure_id, o.uuid, o.title
                FROM %s l
                JOIN %s o ON o.id = l.research_output_id
                WHERE l.unpaywall_work_id IN (%s)
                ORDER BY o.title
                """.formatted(qualified(LINK), qualified("pure_research_output"), inPlaceholders(byId.size()));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int i = 1;
            for (Long id : byId.keySet()) {
                statement.setLong(i++, id);
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    UnpaywallWorkSummaryDto parent = byId.get(rs.getLong("unpaywall_work_id"));
                    if (parent != null) {
                        parent.researchOutputs.add(mapOutput(rs));
                    }
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load Unpaywall research outputs", ex);
        }
    }

    private List<UnpaywallLinkedOutputDto> loadOutputs(Connection connection, long workId) throws SQLException {
        String sql = """
                SELECT l.unpaywall_work_id, l.research_output_id, l.electronic_version_id,
                       o.pure_id, o.uuid, o.title
                FROM %s l
                JOIN %s o ON o.id = l.research_output_id
                WHERE l.unpaywall_work_id = ?
                ORDER BY o.title
                """.formatted(qualified(LINK), qualified("pure_research_output"));
        List<UnpaywallLinkedOutputDto> items = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, workId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    items.add(mapOutput(rs));
                }
            }
        }
        return items;
    }

    private List<UnpaywallLocationDto> loadLocations(Connection connection, long workId) throws SQLException {
        String sql = "SELECT * FROM " + qualified(LOCATION) + " WHERE unpaywall_work_id = ? ORDER BY is_best DESC, id";
        List<UnpaywallLocationDto> items = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, workId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    UnpaywallLocationDto dto = new UnpaywallLocationDto();
                    dto.id = rs.getLong("id");
                    dto.hostType = nullableString(rs, "host_type");
                    dto.license = nullableString(rs, "license");
                    dto.version = nullableString(rs, "version");
                    dto.url = nullableString(rs, "url");
                    dto.urlForLandingPage = nullableString(rs, "url_for_landing_page");
                    dto.urlForPdf = nullableString(rs, "url_for_pdf");
                    dto.oaDate = nullableString(rs, "oa_date");
                    dto.best = rs.getBoolean("is_best");
                    dto.evidence = nullableString(rs, "evidence");
                    dto.repositoryInstitution = nullableString(rs, "repository_institution");
                    dto.endpointId = nullableString(rs, "endpoint_id");
                    items.add(dto);
                }
            }
        }
        return items;
    }

    private List<String> distinct(String table, String column) {
        if (!tableExists(table)) {
            return List.of();
        }
        String sql = "SELECT DISTINCT `" + column + "` FROM " + qualified(table)
                + " WHERE `" + column + "` IS NOT NULL AND TRIM(`" + column + "`) <> '' ORDER BY 1";
        List<String> values = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                values.add(rs.getString(1));
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load Unpaywall filter values", ex);
        }
        return values;
    }

    private UnpaywallWorkSummaryDto mapSummary(ResultSet rs) throws SQLException {
        UnpaywallWorkSummaryDto dto = new UnpaywallWorkSummaryDto();
        fillSummary(dto, rs);
        dto.researchOutputCount = rs.getInt("research_output_count");
        dto.publicationTitle = nullableString(rs, "publication_title");
        return dto;
    }

    private UnpaywallWorkDetailDto mapDetail(ResultSet rs) throws SQLException {
        UnpaywallWorkDetailDto dto = new UnpaywallWorkDetailDto();
        fillSummary(dto, rs);
        dto.genre = nullableString(rs, "genre");
        dto.publishedDate = nullableString(rs, "published_date");
        dto.journalIssns = nullableString(rs, "journal_issns");
        dto.journalIssnL = nullableString(rs, "journal_issn_l");
        dto.journalIsOa = nullableBoolean(rs, "journal_is_oa");
        dto.lastHttpStatus = nullableInt(rs, "last_http_status");
        dto.lastErrorCode = nullableString(rs, "last_error_code");
        dto.lastErrorMessage = nullableString(rs, "last_error_message");
        return dto;
    }

    private void fillSummary(UnpaywallWorkSummaryDto dto, ResultSet rs) throws SQLException {
        dto.id = rs.getLong("id");
        dto.doi = nullableString(rs, "doi");
        dto.normalizedDoi = nullableString(rs, "normalized_doi");
        dto.doiUrl = nullableString(rs, "doi_url");
        dto.lastOutcome = nullableString(rs, "last_outcome");
        dto.isOa = nullableBoolean(rs, "is_oa");
        dto.oaStatus = nullableString(rs, "oa_status");
        dto.title = nullableString(rs, "title");
        dto.year = nullableInt(rs, "year");
        dto.publisher = nullableString(rs, "publisher");
        dto.journalName = nullableString(rs, "journal_name");
        dto.journalIsInDoaj = nullableBoolean(rs, "journal_is_in_doaj");
        dto.hasRepositoryCopy = nullableBoolean(rs, "has_repository_copy");
        dto.bestOaHostType = nullableString(rs, "best_oa_host_type");
        dto.bestOaLicense = nullableString(rs, "best_oa_license");
        dto.bestOaVersion = nullableString(rs, "best_oa_version");
        dto.bestOaUrl = nullableString(rs, "best_oa_url");
        dto.bestOaLandingPageUrl = nullableString(rs, "best_oa_landing_page_url");
        dto.bestOaPdfUrl = nullableString(rs, "best_oa_pdf_url");
        dto.retrievedAt = timestamp(rs, "retrieved_at");
        dto.apiUpdatedAt = timestamp(rs, "api_updated_at");
    }

    private UnpaywallLinkedOutputDto mapOutput(ResultSet rs) throws SQLException {
        UnpaywallLinkedOutputDto dto = new UnpaywallLinkedOutputDto();
        dto.researchOutputId = rs.getLong("research_output_id");
        dto.electronicVersionId = nullableLong(rs, "electronic_version_id");
        dto.pureId = nullableLong(rs, "pure_id");
        dto.uuid = nullableString(rs, "uuid");
        dto.title = nullableString(rs, "title");
        return dto;
    }

    private StoredWork mapStored(ResultSet rs) throws SQLException {
        StoredWork stored = new StoredWork();
        stored.id = rs.getLong("id");
        stored.lastOutcome = nullableString(rs, "last_outcome");
        stored.isOa = nullableBoolean(rs, "is_oa");
        stored.oaStatus = nullableString(rs, "oa_status");
        stored.bestHost = nullableString(rs, "best_oa_host_type");
        stored.bestLicense = nullableString(rs, "best_oa_license");
        stored.retrievedAt = timestamp(rs, "retrieved_at");
        UnpaywallWorkRecord record = new UnpaywallWorkRecord();
        record.isOa = stored.isOa;
        record.oaStatus = stored.oaStatus;
        record.genre = nullableString(rs, "genre");
        record.title = nullableString(rs, "title");
        record.year = nullableInt(rs, "year");
        record.publishedDate = nullableString(rs, "published_date");
        record.publisher = nullableString(rs, "publisher");
        record.journalName = nullableString(rs, "journal_name");
        record.journalIssns = nullableString(rs, "journal_issns");
        record.journalIssnL = nullableString(rs, "journal_issn_l");
        record.journalIsOa = nullableBoolean(rs, "journal_is_oa");
        record.journalIsInDoaj = nullableBoolean(rs, "journal_is_in_doaj");
        record.hasRepositoryCopy = nullableBoolean(rs, "has_repository_copy");
        record.doiUrl = nullableString(rs, "doi_url");
        UnpaywallLocationRecord best = new UnpaywallLocationRecord();
        best.hostType = stored.bestHost;
        best.license = stored.bestLicense;
        best.version = nullableString(rs, "best_oa_version");
        best.url = nullableString(rs, "best_oa_url");
        best.urlForLandingPage = nullableString(rs, "best_oa_landing_page_url");
        best.urlForPdf = nullableString(rs, "best_oa_pdf_url");
        record.bestOaLocation = best.url == null && best.hostType == null ? null : best;
        stored.parentSnapshot = UnpaywallWorkSnapshot.parent(record, stored.lastOutcome);
        stored.locationSnapshot = loadLocationSnapshot(rs.getLong("id"));
        return stored;
    }

    private String loadLocationSnapshot(long workId) {
        String sql = "SELECT * FROM " + qualified(LOCATION) + " WHERE unpaywall_work_id = ?";
        List<UnpaywallLocationRecord> locations = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, workId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    UnpaywallLocationRecord location = new UnpaywallLocationRecord();
                    location.endpointId = nullableString(rs, "endpoint_id");
                    location.hostType = nullableString(rs, "host_type");
                    location.license = nullableString(rs, "license");
                    location.version = nullableString(rs, "version");
                    location.url = nullableString(rs, "url");
                    location.urlForLandingPage = nullableString(rs, "url_for_landing_page");
                    location.urlForPdf = nullableString(rs, "url_for_pdf");
                    location.oaDate = nullableString(rs, "oa_date");
                    location.isBest = rs.getBoolean("is_best");
                    location.evidence = nullableString(rs, "evidence");
                    location.repositoryInstitution = nullableString(rs, "repository_institution");
                    location.pmhId = nullableString(rs, "pmh_id");
                    locations.add(location);
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load Unpaywall locations", ex);
        }
        return UnpaywallWorkSnapshot.locations(locations);
    }

    private static UnpaywallQuery copyQuery(UnpaywallQuery query) {
        UnpaywallQuery copy = new UnpaywallQuery();
        copy.search = query.search;
        copy.oaStatus = query.oaStatus;
        copy.hostType = query.hostType;
        copy.license = query.license;
        copy.version = query.version;
        copy.isOa = query.isOa;
        copy.repositoryCopy = query.repositoryCopy;
        copy.outcome = query.outcome;
        copy.sort = query.sort;
        copy.direction = query.direction;
        copy.page = query.page;
        copy.pageSize = query.pageSize;
        return copy;
    }

    private static String linkKey(long researchOutputId, Long electronicVersionId) {
        return researchOutputId + ":" + (electronicVersionId == null ? "0" : electronicVersionId);
    }

    private String needsRefreshClearSql() {
        return columnExists(WORK, "needs_refresh") ? ", needs_refresh = 0" : "";
    }

    private static void setBoolean(PreparedStatement statement, int index, Boolean value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TINYINT);
        } else {
            statement.setBoolean(index, value);
        }
    }

    private static void setInteger(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private void setSyncId(PreparedStatement statement, int index, String syncId) throws SQLException {
        Long parsed = parseNumericId(syncId);
        if (parsed == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, parsed);
        }
    }

    private static OffsetDateTime timestamp(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant().atOffset(ZoneOffset.UTC);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean eq(String left, String right) {
        String a = left == null ? "" : left.trim();
        String b = right == null ? "" : right.trim();
        return a.equals(b);
    }

    private static String flag(Boolean value) {
        if (value == null) {
            return "";
        }
        return value ? "1" : "0";
    }

    public static class StoredWork {
        public long id;
        public String lastOutcome;
        public Boolean isOa;
        public String oaStatus;
        public String bestHost;
        public String bestLicense;
        public OffsetDateTime retrievedAt;
        public String parentSnapshot;
        public String locationSnapshot;
    }

    public static class RunExtras {
        public long notFound;
        public long oa;
        public long nonOa;
    }

    private static class FilterSql {
        String fromExtra = "";
        String where = "";
        final List<Object> params = new ArrayList<>();
    }
}
