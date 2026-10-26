package cl.uss.ca.repository;

import cl.uss.ca.api.dto.DspaceBitstreamDto;
import cl.uss.ca.api.dto.DspaceCollectionListDto;
import cl.uss.ca.api.dto.DspaceCollectionRefDto;
import cl.uss.ca.api.dto.DspaceCommunityListDto;
import cl.uss.ca.api.dto.DspaceItemDetailDto;
import cl.uss.ca.api.dto.DspaceItemListDto;
import cl.uss.ca.api.dto.DspaceItemQuery;
import cl.uss.ca.api.dto.DspaceItemSummaryDto;
import cl.uss.ca.api.dto.DspaceMatchListDto;
import cl.uss.ca.api.dto.DspaceMetadataDto;
import cl.uss.ca.api.dto.DspaceStatsDto;
import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.domain.DoiNormalizer;
import cl.uss.ca.domain.DspaceAccessComparison;
import cl.uss.ca.domain.DspaceCommunityPath;
import cl.uss.ca.domain.DspaceListSupport;
import cl.uss.ca.exception.DatabaseException;
import cl.uss.ca.exception.ValidationException;
import cl.uss.ca.external.dspace.DspaceAccessClassifier;
import cl.uss.ca.external.dspace.DspaceBitstreamRecord;
import cl.uss.ca.external.dspace.DspaceCollectionRecord;
import cl.uss.ca.external.dspace.DspaceCommunityRecord;
import cl.uss.ca.external.dspace.DspaceFulltextAvailability;
import cl.uss.ca.external.dspace.DspaceItemRecord;
import cl.uss.ca.external.dspace.DspaceMatchDecision;
import cl.uss.ca.external.dspace.DspaceMatcher;
import cl.uss.ca.external.dspace.DspaceMetadataValue;
import cl.uss.ca.external.dspace.DspacePureCandidate;
import cl.uss.ca.sync.SyncRunState;
import cl.uss.ca.sync.diff.SyncChange;
import jakarta.enterprise.context.ApplicationScoped;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class DspaceRepository extends JdbcRepository {
    static final String COMMUNITY = "dspace_community";
    static final String COLLECTION = "dspace_collection";
    static final String ITEM = "dspace_item";
    static final String METADATA = "dspace_item_metadata";
    static final String BITSTREAM = "dspace_bitstream";
    static final String ITEM_COLLECTION = "dspace_item_collection";
    static final String MATCH = "dspace_item_match";
    static final String CHECKPOINT = "dspace_sync_checkpoint";
    static final String STATS = "dspace_sync_stats";

    public boolean available() {
        return tableExists(COMMUNITY) && tableExists(COLLECTION) && tableExists(ITEM)
                && tableExists(METADATA) && tableExists(BITSTREAM) && tableExists(MATCH);
    }

    public void assertAvailable() {
        if (!available()) {
            throw new ValidationException(
                    "DSPACE_TABLES_UNAVAILABLE",
                    "dspace_item tables are required before synchronizing DSpace"
            );
        }
    }

    public String upsertCommunity(DspaceCommunityRecord record, String syncId) {
        assertAvailable();
        if (record == null || record.uuid == null) {
            return "UNCHANGED";
        }
        String sql = """
                INSERT INTO %s (uuid, handle, name, parent_uuid, archived_items_count, last_sync_at, last_sync_execution_id)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6), ?)
                ON DUPLICATE KEY UPDATE
                    handle = VALUES(handle),
                    name = VALUES(name),
                    parent_uuid = VALUES(parent_uuid),
                    archived_items_count = VALUES(archived_items_count),
                    last_sync_at = VALUES(last_sync_at),
                    last_sync_execution_id = VALUES(last_sync_execution_id)
                """.formatted(qualified(COMMUNITY));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindCommunity(statement, record, syncId);
            int updated = statement.executeUpdate();
            return updated == 1 ? "CREATED" : "UPDATED";
        } catch (SQLException ex) {
            throw sqlFailed(COMMUNITY, "uuid", record.uuid, ex);
        }
    }

    public String upsertCollection(DspaceCollectionRecord record, String syncId) {
        assertAvailable();
        if (record == null || record.uuid == null) {
            return "UNCHANGED";
        }
        String parent = record.parentCommunityUuid;
        if (parent != null && !communityExists(parent)) {
            parent = null;
        }
        String sql = """
                INSERT INTO %s (uuid, handle, name, parent_community_uuid, archived_items_count, last_sync_at, last_sync_execution_id)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6), ?)
                ON DUPLICATE KEY UPDATE
                    handle = VALUES(handle),
                    name = VALUES(name),
                    parent_community_uuid = VALUES(parent_community_uuid),
                    archived_items_count = VALUES(archived_items_count),
                    last_sync_at = VALUES(last_sync_at),
                    last_sync_execution_id = VALUES(last_sync_execution_id)
                """.formatted(qualified(COLLECTION));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.uuid);
            statement.setString(2, clip(record.handle, 64));
            statement.setString(3, record.name);
            statement.setString(4, parent);
            setInt(statement, 5, record.archivedItemsCount);
            bindSyncId(statement, 6, COLLECTION, "last_sync_execution_id", syncId);
            int updated = statement.executeUpdate();
            return updated == 1 ? "CREATED" : "UPDATED";
        } catch (SQLException ex) {
            throw sqlFailed(COLLECTION, "uuid", record.uuid, ex);
        }
    }

    public ItemUpsert upsertItem(DspaceItemRecord item, String syncId, Long executionId) {
        assertAvailable();
        ItemUpsert result = new ItemUpsert();
        if (item == null || item.uuid == null) {
            result.kind = "UNCHANGED";
            return result;
        }
        StoredItem stored = findItem(item.uuid);
        String collection = item.owningCollectionUuid;
        if (collection != null && !collectionExists(collection)) {
            collection = null;
        }
        if (collection == null && stored != null) {
            collection = stored.owningCollectionUuid;
        }
        String access = item.access == null ? null : DspaceAccessClassifier.normalizeStatus(item.access.status);
        String availability;
        if (item.bitstreamInventory != null && item.bitstreamInventory.mayReplaceBitstreams()) {
            availability = DspaceAccessClassifier.classify(
                    item.access == null ? access : item.access.status,
                    originalBitstreams(item)
            ).name();
        } else if (stored != null && stored.availability != null) {
            availability = stored.availability;
        } else {
            availability = DspaceFulltextAvailability.UNKNOWN.name();
        }
        if (item.access == null && stored != null) {
            access = stored.accessStatus;
            if (item.bitstreamInventory == null || !item.bitstreamInventory.mayReplaceBitstreams()) {
                availability = stored.availability == null
                        ? DspaceFulltextAvailability.UNKNOWN.name()
                        : stored.availability;
            }
        }
        boolean unchanged = stored != null
                && same(stored.lastModified, item.lastModified)
                && same(stored.accessStatus, access)
                && same(stored.availability, availability);
        String sql = """
                INSERT INTO %s (
                    uuid, handle, handle_url, name, dc_type, normalized_doi, doi_raw, access_status, embargo_date,
                    fulltext_availability, in_archive, discoverable, withdrawn, owning_collection_uuid,
                    last_modified_remote, last_outcome, last_http_status, last_error_code, last_error_message,
                    last_sync_at, last_sync_execution_id, seen_in_execution_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'FOUND', 200, NULL, NULL, CURRENT_TIMESTAMP(6), ?, ?)
                ON DUPLICATE KEY UPDATE
                    handle = VALUES(handle),
                    handle_url = VALUES(handle_url),
                    name = VALUES(name),
                    dc_type = VALUES(dc_type),
                    normalized_doi = VALUES(normalized_doi),
                    doi_raw = VALUES(doi_raw),
                    access_status = VALUES(access_status),
                    embargo_date = VALUES(embargo_date),
                    fulltext_availability = VALUES(fulltext_availability),
                    in_archive = VALUES(in_archive),
                    discoverable = VALUES(discoverable),
                    withdrawn = VALUES(withdrawn),
                    owning_collection_uuid = VALUES(owning_collection_uuid),
                    last_modified_remote = VALUES(last_modified_remote),
                    last_outcome = 'FOUND',
                    last_http_status = 200,
                    last_error_code = NULL,
                    last_error_message = NULL,
                    last_sync_at = VALUES(last_sync_at),
                    last_sync_execution_id = VALUES(last_sync_execution_id),
                    seen_in_execution_id = VALUES(seen_in_execution_id)
                """.formatted(qualified(ITEM));
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int i = 1;
                statement.setString(i++, item.uuid);
                statement.setString(i++, clip(item.handle, 64));
                statement.setString(i++, clip(item.handleUrl, 500));
                statement.setString(i++, item.name);
                statement.setString(i++, clip(item.dcType, 191));
                statement.setString(i++, clip(item.normalizedDoi, 500));
                statement.setString(i++, clip(item.doiRaw, 500));
                statement.setString(i++, clip(access, 32));
                setDate(statement, i++, embargoDate(item));
                statement.setString(i++, availability);
                setBool(statement, i++, item.inArchive);
                setBool(statement, i++, item.discoverable);
                setBool(statement, i++, item.withdrawn);
                statement.setString(i++, collection);
                setTimestamp(statement, i++, item.lastModified);
                bindSyncId(statement, i++, ITEM, "last_sync_execution_id", syncId);
                setLong(statement, i, executionId);
                statement.executeUpdate();
            }
            replaceMetadata(connection, item);
            if (item.bitstreamInventory != null && item.bitstreamInventory.mayReplaceBitstreams()) {
                replaceBitstreams(connection, item);
            }
            replaceItemCollections(connection, item, collection);
            connection.commit();
        } catch (SQLException ex) {
            throw sqlFailed(ITEM, "uuid", item.uuid, ex);
        }
        if (stored == null) {
            result.kind = "CREATED";
            result.changes.add(SyncChange.created().on("DSPACE", null, null, item.uuid));
        } else if (unchanged) {
            result.kind = "UNCHANGED";
        } else {
            result.kind = "UPDATED";
            result.changes.add(SyncChange.updated("PARENT", "DSpace item updated", stored.accessStatus, access)
                    .on("DSPACE", null, null, item.uuid));
        }
        result.availability = availability;
        result.accessStatus = access;
        return result;
    }

    public List<DspacePureCandidate> listPureCandidates() {
        List<DspacePureCandidate> items = new ArrayList<>();
        if (!tableExists("pure_research_output")) {
            return items;
        }
        String sql = """
                SELECT o.id, o.title, y.publication_year
                FROM %s o
                LEFT JOIN (%s) y ON y.research_output_id = o.id
                WHERE %s
                """.formatted(
                qualified("pure_research_output"),
                purePublicationYearDerivedSql(),
                activeInPurePredicate("pure_research_output", "o")
        );
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                DspacePureCandidate candidate = new DspacePureCandidate();
                candidate.researchOutputId = rs.getLong("id");
                candidate.title = nullableString(rs, "title");
                candidate.year = nullableInt(rs, "publication_year");
                items.add(candidate);
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load PURE candidates for DSpace matching", ex);
        }
        attachDois(items);
        attachHandles(items);
        attachAuthors(items);
        return items;
    }

    public void saveMatch(DspaceMatchDecision decision) {
        if (decision == null || decision.itemUuid == null) {
            return;
        }
        String sql = """
                INSERT INTO %s (item_uuid, research_output_id, match_method, match_status, canonical, confidence, evidence_json)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    match_method = VALUES(match_method),
                    match_status = VALUES(match_status),
                    canonical = VALUES(canonical),
                    confidence = VALUES(confidence),
                    evidence_json = VALUES(evidence_json)
                """.formatted(qualified(MATCH));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, decision.itemUuid);
            statement.setLong(2, decision.researchOutputId);
            statement.setString(3, decision.method);
            statement.setString(4, decision.status);
            statement.setBoolean(5, decision.canonical);
            if (decision.confidence == null) {
                statement.setNull(6, Types.DECIMAL);
            } else {
                statement.setBigDecimal(6, java.math.BigDecimal.valueOf(decision.confidence));
            }
            statement.setString(7, decision.evidenceJson);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw sqlFailed(MATCH, "item_uuid", decision.itemUuid, ex);
        }
    }

    public List<DspaceItemSummaryDto> byResearchOutput(long researchOutputId) {
        if (!available()) {
            return List.of();
        }
        String sql = """
                SELECT i.uuid, i.handle, i.handle_url, i.name, i.dc_type, i.normalized_doi, i.doi_raw,
                       i.access_status, i.fulltext_availability, i.embargo_date, m.match_method, m.match_status, m.canonical,
                       b.content_url, b.public_download
                FROM %s m
                JOIN %s i ON i.uuid = m.item_uuid
                LEFT JOIN %s b ON b.item_uuid = i.uuid AND b.bundle_name = 'ORIGINAL' AND b.public_download = 1
                WHERE m.research_output_id = ?
                  AND m.match_status IN ('CONFIRMED', 'CANDIDATE', 'SECONDARY')
                ORDER BY m.canonical DESC, m.match_status, i.handle
                """.formatted(qualified(MATCH), qualified(ITEM), qualified(BITSTREAM));
        List<DspaceItemSummaryDto> rows = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, researchOutputId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    DspaceItemSummaryDto dto = new DspaceItemSummaryDto();
                    dto.uuid = rs.getString("uuid");
                    dto.handle = rs.getString("handle");
                    dto.handleUrl = rs.getString("handle_url");
                    dto.name = rs.getString("name");
                    dto.dcType = rs.getString("dc_type");
                    dto.normalizedDoi = rs.getString("normalized_doi");
                    dto.doi = rs.getString("doi_raw");
                    dto.accessStatus = rs.getString("access_status");
                    dto.fulltextAvailability = rs.getString("fulltext_availability");
                    Date embargo = rs.getDate("embargo_date");
                    dto.embargoDate = embargo == null ? null : embargo.toLocalDate().toString();
                    dto.matchMethod = rs.getString("match_method");
                    dto.matchStatus = rs.getString("match_status");
                    dto.canonical = rs.getBoolean("canonical");
                    dto.contentUrl = rs.getString("content_url");
                    dto.publicDownload = rs.getBoolean("public_download");
                    rows.add(dto);
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load DSpace matches", ex);
        }
        return rows;
    }

    public DspaceStatsDto counts() {
        DspaceStatsDto dto = new DspaceStatsDto();
        if (!available()) {
            return dto;
        }
        dto.tablesAvailable = true;
        dto.items = scalarLong("SELECT COUNT(*) FROM " + qualified(ITEM) + " WHERE last_outcome = 'FOUND'");
        dto.communities = scalarLong("SELECT COUNT(*) FROM " + qualified(COMMUNITY));
        dto.collections = scalarLong("SELECT COUNT(*) FROM " + qualified(COLLECTION));
        dto.openAccess = scalarLong("SELECT COUNT(*) FROM " + qualified(ITEM)
                + " WHERE last_outcome = 'FOUND' AND access_status = 'open.access'");
        dto.metadataOnly = scalarLong("SELECT COUNT(*) FROM " + qualified(ITEM)
                + " WHERE last_outcome = 'FOUND' AND access_status = 'metadata.only'");
        dto.restricted = scalarLong("SELECT COUNT(*) FROM " + qualified(ITEM)
                + " WHERE last_outcome = 'FOUND' AND access_status = 'restricted'");
        dto.embargo = scalarLong("SELECT COUNT(*) FROM " + qualified(ITEM)
                + " WHERE last_outcome = 'FOUND' AND access_status = 'embargo'");
        dto.fulltextPublic = scalarLong("SELECT COUNT(*) FROM " + qualified(ITEM)
                + " WHERE last_outcome = 'FOUND' AND fulltext_availability = 'FULLTEXT_PUBLIC'");
        dto.matched = scalarLong("SELECT COUNT(DISTINCT research_output_id) FROM " + qualified(MATCH)
                + " WHERE match_status IN ('CONFIRMED', 'SECONDARY')");
        dto.candidates = scalarLong("SELECT COUNT(*) FROM " + qualified(MATCH) + " WHERE match_status = 'CANDIDATE'");
        dto.linkedItems = scalarLong("""
                SELECT COUNT(*) FROM %s i
                WHERE i.last_outcome = 'FOUND'
                  AND EXISTS (
                      SELECT 1 FROM %s m
                      WHERE m.item_uuid = i.uuid AND m.match_status IN ('CONFIRMED', 'SECONDARY')
                  )
                """.formatted(qualified(ITEM), qualified(MATCH)));
        dto.reviewItems = scalarLong("""
                SELECT COUNT(*) FROM %s i
                WHERE i.last_outcome = 'FOUND'
                  AND EXISTS (
                      SELECT 1 FROM %s m WHERE m.item_uuid = i.uuid AND m.match_status = 'CANDIDATE'
                  )
                  AND NOT EXISTS (
                      SELECT 1 FROM %s m WHERE m.item_uuid = i.uuid AND m.match_status IN ('CONFIRMED', 'SECONDARY')
                  )
                """.formatted(qualified(ITEM), qualified(MATCH), qualified(MATCH)));
        dto.unmatchedItems = scalarLong("""
                SELECT COUNT(*) FROM %s i
                WHERE i.last_outcome = 'FOUND'
                  AND NOT EXISTS (
                      SELECT 1 FROM %s m
                      WHERE m.item_uuid = i.uuid AND m.match_status IN ('CONFIRMED', 'SECONDARY', 'CANDIDATE')
                  )
                """.formatted(qualified(ITEM), qualified(MATCH)));
        dto.missingItems = scalarLong("SELECT COUNT(*) FROM " + qualified(ITEM) + " WHERE last_outcome = 'MISSING'");
        dto.errors = scalarLong("SELECT COUNT(*) FROM " + qualified(ITEM) + " WHERE last_outcome = 'ERROR'");
        dto.sharedDois = scalarLong("""
                SELECT COUNT(*) FROM (
                    SELECT i.normalized_doi
                    FROM %s i
                    WHERE i.last_outcome = 'FOUND'
                      AND i.normalized_doi IS NOT NULL AND TRIM(i.normalized_doi) <> ''
                    GROUP BY i.normalized_doi
                    HAVING COUNT(DISTINCT i.uuid) > 1
                ) shared
                """.formatted(qualified(ITEM)));
        dto.sharedDoiItems = scalarLong("""
                SELECT COUNT(*) FROM %s i
                WHERE i.last_outcome = 'FOUND'
                  AND i.normalized_doi IS NOT NULL AND TRIM(i.normalized_doi) <> ''
                  AND EXISTS (
                      SELECT 1 FROM %s other
                      WHERE other.last_outcome = 'FOUND'
                        AND other.normalized_doi = i.normalized_doi
                        AND other.uuid <> i.uuid
                  )
                """.formatted(qualified(ITEM), qualified(ITEM)));
        dto.withFile = scalarLong("""
                SELECT COUNT(*) FROM %s i
                WHERE i.last_outcome = 'FOUND'
                  AND EXISTS (
                      SELECT 1 FROM %s b
                      WHERE b.item_uuid = i.uuid AND b.bundle_name = 'ORIGINAL'
                  )
                """.formatted(qualified(ITEM), qualified(BITSTREAM)));
        dto.withoutFile = Math.max(0, dto.items - dto.withFile);
        dto.unknownAccess = scalarLong("SELECT COUNT(*) FROM " + qualified(ITEM)
                + " WHERE last_outcome = 'FOUND' AND (access_status IS NULL OR TRIM(access_status) = '')");
        dto.bitstreamCount = scalarLong("SELECT COUNT(*) FROM " + qualified(BITSTREAM));
        return dto;
    }

    /**
     * Item KPIs for the same universe as {@link #listItems(DspaceItemQuery)}.
     * Reuses the same itemFilter WHERE clause; page and sort are ignored.
     */
    public DspaceStatsDto counts(DspaceItemQuery query) {
        if (query == null) {
            return counts();
        }
        DspaceStatsDto dto = new DspaceStatsDto();
        if (!available()) {
            return dto;
        }
        dto.tablesAvailable = true;
        FilterSql filter = itemFilter(query);
        boolean comparison = DspaceListSupport.accessComparisonFilter(query.accessComparison) != null;
        boolean pure = comparison && tableExists("pure_research_output_electronic_version");
        String sql = """
                SELECT
                    COUNT(DISTINCT i.uuid) AS items,
                    COUNT(DISTINCT CASE WHEN i.access_status = 'open.access' THEN i.uuid END) AS open_access,
                    COUNT(DISTINCT CASE WHEN i.access_status = 'metadata.only' THEN i.uuid END) AS metadata_only,
                    COUNT(DISTINCT CASE WHEN i.access_status = 'restricted' THEN i.uuid END) AS restricted,
                    COUNT(DISTINCT CASE WHEN i.access_status = 'embargo' THEN i.uuid END) AS embargo,
                    COUNT(DISTINCT CASE WHEN i.fulltext_availability = 'FULLTEXT_PUBLIC' THEN i.uuid END) AS fulltext_public,
                    COUNT(DISTINCT CASE WHEN m.match_status IN ('CONFIRMED', 'SECONDARY') THEN i.uuid END) AS linked_items,
                    COUNT(DISTINCT CASE WHEN m.match_status = 'CANDIDATE' THEN i.uuid END) AS review_items,
                    COUNT(DISTINCT CASE WHEN m.item_uuid IS NULL OR m.match_status = 'REJECTED' THEN i.uuid END) AS unmatched_items,
                    COUNT(DISTINCT CASE WHEN i.last_outcome = 'MISSING' THEN i.uuid END) AS missing_items,
                    COUNT(DISTINCT CASE WHEN i.last_outcome = 'ERROR' THEN i.uuid END) AS errors,
                    COUNT(DISTINCT CASE WHEN shared.shared_item_count IS NOT NULL THEN i.normalized_doi END) AS shared_dois,
                    COUNT(DISTINCT CASE WHEN shared.shared_item_count IS NOT NULL THEN i.uuid END) AS shared_doi_items,
                    COUNT(DISTINCT CASE WHEN files.original_file_count IS NOT NULL THEN i.uuid END) AS with_file,
                    COUNT(DISTINCT CASE WHEN i.access_status IS NULL OR TRIM(i.access_status) = '' THEN i.uuid END) AS unknown_access
                FROM %s
                %s
                """.formatted(itemFromSql(true, true, pure), filter.sql);
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindParams(statement, filter.params);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    dto.items = rs.getLong("items");
                    dto.openAccess = rs.getLong("open_access");
                    dto.metadataOnly = rs.getLong("metadata_only");
                    dto.restricted = rs.getLong("restricted");
                    dto.embargo = rs.getLong("embargo");
                    dto.fulltextPublic = rs.getLong("fulltext_public");
                    dto.linkedItems = rs.getLong("linked_items");
                    dto.reviewItems = rs.getLong("review_items");
                    dto.unmatchedItems = rs.getLong("unmatched_items");
                    dto.missingItems = rs.getLong("missing_items");
                    dto.errors = rs.getLong("errors");
                    dto.sharedDois = rs.getLong("shared_dois");
                    dto.sharedDoiItems = rs.getLong("shared_doi_items");
                    dto.withFile = rs.getLong("with_file");
                    dto.unknownAccess = rs.getLong("unknown_access");
                    dto.withoutFile = Math.max(0, dto.items - dto.withFile);
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to summarize DSpace items", ex);
        }
        dto.bitstreamCount = scalarLong("SELECT COUNT(*) FROM " + qualified(BITSTREAM));
        return dto;
    }

    public boolean markItemSeen(String uuid, String syncId) {
        if (uuid == null || uuid.isBlank()) {
            return false;
        }
        String sql = """
                UPDATE %s
                   SET seen_in_execution_id = ?,
                       last_sync_at = CURRENT_TIMESTAMP(6)
                 WHERE uuid = ?
                """.formatted(qualified(ITEM));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindSyncId(statement, 1, ITEM, "seen_in_execution_id", syncId);
            statement.setString(2, uuid);
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw sqlFailed(ITEM, "uuid", uuid, ex);
        }
    }

    public PageResponse<DspaceItemListDto> listItems(DspaceItemQuery query) {
        assertAvailable();
        FilterSql filter = itemFilter(query);
        String order = DspaceListSupport.itemOrder(query.sort, query.direction);
        int page = Math.max(1, query.page);
        int size = Math.min(200, Math.max(1, query.pageSize));
        long total = scalarLong(
                "SELECT COUNT(DISTINCT i.uuid) FROM " + itemCountFromSql(query) + filter.sql,
                filter.params.toArray()
        );
        String sql = """
                SELECT i.uuid, i.handle, i.handle_url, i.name, i.dc_type, i.normalized_doi, i.doi_raw,
                       i.access_status, i.fulltext_availability, i.last_outcome, i.last_sync_at,
                       i.last_modified_remote, i.owning_collection_uuid,
                       col.name AS collection_name,
                       com.uuid AS community_uuid, com.name AS community_name,
                       m.match_method, m.match_status, m.canonical, m.confidence, m.research_output_id,
                       o.uuid AS research_output_uuid, o.title AS research_output_title,
                       shared.shared_item_count,
                       COALESCE(files.original_file_count, 0) AS original_file_count
                       %s
                FROM %s
                %s
                ORDER BY %s, i.uuid
                LIMIT ? OFFSET ?
                """.formatted(pureAccessSelect(), itemFromSql(), filter.sql, order);
        List<DspaceItemListDto> items = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = bindParams(statement, filter.params);
            statement.setInt(index++, size);
            statement.setInt(index, (page - 1) * size);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    items.add(mapItemList(rs));
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list DSpace items", ex);
        }
        attachCommunityPaths(items);
        return PageResponse.of(items, page - 1, size, total);
    }

    public List<DspaceItemListDto> exportItems(DspaceItemQuery query) {
        assertAvailable();
        FilterSql filter = itemFilter(query);
        String order = DspaceListSupport.itemOrder(query.sort, query.direction);
        int size = 20000;
        String sql = """
                SELECT i.uuid, i.handle, i.handle_url, i.name, i.dc_type, i.normalized_doi, i.doi_raw,
                       i.access_status, i.fulltext_availability, i.last_outcome, i.last_sync_at,
                       i.last_modified_remote, i.owning_collection_uuid,
                       col.name AS collection_name,
                       com.uuid AS community_uuid, com.name AS community_name,
                       m.match_method, m.match_status, m.canonical, m.confidence, m.research_output_id,
                       o.uuid AS research_output_uuid, o.title AS research_output_title,
                       shared.shared_item_count,
                       COALESCE(files.original_file_count, 0) AS original_file_count
                       %s
                FROM %s
                %s
                ORDER BY %s, i.uuid
                LIMIT ?
                """.formatted(pureAccessSelect(), itemFromSql(), filter.sql, order);
        List<DspaceItemListDto> items = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = bindParams(statement, filter.params);
            statement.setInt(index, size);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    items.add(mapItemList(rs));
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to export DSpace items", ex);
        }
        attachCommunityPaths(items);
        return items;
    }

    public DspaceItemDetailDto findItemDetail(String uuid) {
        assertAvailable();
        if (uuid == null || uuid.isBlank()) {
            return null;
        }
        String sql = """
                SELECT i.*, col.name AS collection_name, com.uuid AS community_uuid, com.name AS community_name,
                       m.match_method, m.match_status, m.canonical, m.confidence, m.research_output_id,
                       o.uuid AS research_output_uuid, o.title AS research_output_title,
                       shared.shared_item_count,
                       COALESCE(files.original_file_count, 0) AS original_file_count
                       %s
                FROM %s
                WHERE i.uuid = ?
                """.formatted(pureAccessSelect(), itemFromSql());
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.trim());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                DspaceItemDetailDto dto = mapItemDetail(rs);
                dto.bitstreams.addAll(loadBitstreams(connection, dto.uuid));
                dto.metadata.addAll(loadMetadata(connection, dto.uuid));
                dto.matches.addAll(loadMatches(connection, dto.uuid));
                dto.collections.addAll(loadItemCollections(connection, dto));
                attachCommunityPaths(List.of(dto));
                return dto;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load DSpace item", ex);
        }
    }

    public PageResponse<DspaceCollectionListDto> listCollections(int page, int size, String search, String sort, String direction) {
        assertAvailable();
        int safePage = Math.max(1, page);
        int safeSize = Math.min(200, Math.max(1, size));
        FilterSql filter = new FilterSql();
        if (search != null && !search.isBlank()) {
            filter.sql = " WHERE (c.name LIKE ? ESCAPE '!' OR c.handle LIKE ? ESCAPE '!' OR c.uuid = ?)";
            String like = likeContains(search.trim());
            filter.params.add(like);
            filter.params.add(like);
            filter.params.add(search.trim());
        }
        long total = scalarLong("SELECT COUNT(*) FROM " + qualified(COLLECTION) + " c" + filter.sql, filter.params.toArray());
        String sql = """
                SELECT c.uuid, c.handle, c.name, c.parent_community_uuid, c.archived_items_count, c.last_sync_at,
                       com.name AS community_name,
                       (SELECT COUNT(*) FROM %s i WHERE i.owning_collection_uuid = c.uuid AND i.last_outcome = 'FOUND') AS item_count
                FROM %s c
                LEFT JOIN %s com ON com.uuid = c.parent_community_uuid
                %s
                ORDER BY %s, c.uuid
                LIMIT ? OFFSET ?
                """.formatted(
                qualified(ITEM),
                qualified(COLLECTION),
                qualified(COMMUNITY),
                filter.sql,
                DspaceListSupport.collectionOrder(sort, direction)
        );
        List<DspaceCollectionListDto> items = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = bindParams(statement, filter.params);
            statement.setInt(index++, safeSize);
            statement.setInt(index, (safePage - 1) * safeSize);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    DspaceCollectionListDto dto = new DspaceCollectionListDto();
                    dto.uuid = rs.getString("uuid");
                    dto.handle = nullableString(rs, "handle");
                    dto.name = nullableString(rs, "name");
                    dto.communityUuid = nullableString(rs, "parent_community_uuid");
                    dto.communityName = nullableString(rs, "community_name");
                    dto.archivedItemsCount = nullableInt(rs, "archived_items_count");
                    dto.itemCount = rs.getLong("item_count");
                    dto.lastSyncAt = offset(rs.getTimestamp("last_sync_at"));
                    items.add(dto);
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list DSpace collections", ex);
        }
        return PageResponse.of(items, safePage - 1, safeSize, total);
    }

    public PageResponse<DspaceCommunityListDto> listCommunities(int page, int size, String search, String sort, String direction) {
        assertAvailable();
        int safePage = Math.max(1, page);
        int safeSize = Math.min(200, Math.max(1, size));
        FilterSql filter = new FilterSql();
        if (search != null && !search.isBlank()) {
            filter.sql = " WHERE (c.name LIKE ? ESCAPE '!' OR c.handle LIKE ? ESCAPE '!' OR c.uuid = ?)";
            String like = likeContains(search.trim());
            filter.params.add(like);
            filter.params.add(like);
            filter.params.add(search.trim());
        }
        long total = scalarLong("SELECT COUNT(*) FROM " + qualified(COMMUNITY) + " c" + filter.sql, filter.params.toArray());
        String sql = """
                SELECT c.uuid, c.handle, c.name, c.parent_uuid, c.archived_items_count, c.last_sync_at,
                       p.name AS parent_name,
                       (SELECT COUNT(*) FROM %s col WHERE col.parent_community_uuid = c.uuid) AS collection_count,
                       (SELECT COUNT(*) FROM %s i
                         INNER JOIN %s col ON col.uuid = i.owning_collection_uuid
                        WHERE col.parent_community_uuid = c.uuid AND i.last_outcome = 'FOUND') AS item_count
                FROM %s c
                LEFT JOIN %s p ON p.uuid = c.parent_uuid
                %s
                ORDER BY %s, c.uuid
                LIMIT ? OFFSET ?
                """.formatted(
                qualified(COLLECTION),
                qualified(ITEM),
                qualified(COLLECTION),
                qualified(COMMUNITY),
                qualified(COMMUNITY),
                filter.sql,
                DspaceListSupport.communityOrder(sort, direction)
        );
        List<DspaceCommunityListDto> items = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = bindParams(statement, filter.params);
            statement.setInt(index++, safeSize);
            statement.setInt(index, (safePage - 1) * safeSize);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    DspaceCommunityListDto dto = new DspaceCommunityListDto();
                    dto.uuid = rs.getString("uuid");
                    dto.handle = nullableString(rs, "handle");
                    dto.name = nullableString(rs, "name");
                    dto.parentUuid = nullableString(rs, "parent_uuid");
                    dto.parentName = nullableString(rs, "parent_name");
                    dto.archivedItemsCount = nullableInt(rs, "archived_items_count");
                    dto.collectionCount = rs.getLong("collection_count");
                    dto.itemCount = rs.getLong("item_count");
                    dto.lastSyncAt = offset(rs.getTimestamp("last_sync_at"));
                    items.add(dto);
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list DSpace communities", ex);
        }
        return PageResponse.of(items, safePage - 1, safeSize, total);
    }

    public void saveCheckpoint(String stage, int page, int size, String uuid) {
        if (!tableExists(CHECKPOINT)) {
            return;
        }
        String sql = """
                INSERT INTO %s (entity, stage, page_number, page_size, last_uuid)
                VALUES ('DSPACE', ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    stage = VALUES(stage),
                    page_number = VALUES(page_number),
                    page_size = VALUES(page_size),
                    last_uuid = VALUES(last_uuid)
                """.formatted(qualified(CHECKPOINT));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, stage);
            statement.setInt(2, page);
            statement.setInt(3, size);
            statement.setString(4, uuid);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw sqlFailed(CHECKPOINT, "stage", stage, ex);
        }
    }

    public Checkpoint loadCheckpoint() {
        if (!tableExists(CHECKPOINT)) {
            return null;
        }
        String sql = "SELECT stage, page_number, page_size, last_uuid FROM " + qualified(CHECKPOINT) + " WHERE entity = 'DSPACE'";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            if (!rs.next()) {
                return null;
            }
            Checkpoint checkpoint = new Checkpoint();
            checkpoint.stage = rs.getString("stage");
            checkpoint.page = rs.getInt("page_number");
            checkpoint.size = rs.getInt("page_size");
            checkpoint.lastUuid = rs.getString("last_uuid");
            return checkpoint;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load DSpace checkpoint", ex);
        }
    }

    public void clearCheckpoint() {
        if (!tableExists(CHECKPOINT)) {
            return;
        }
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM " + qualified(CHECKPOINT) + " WHERE entity = 'DSPACE'")) {
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw sqlFailed(CHECKPOINT, "entity", "DSPACE", ex);
        }
    }

    public int markMissing(long executionId) {
        if (executionId <= 0) {
            return 0;
        }
        String sql = """
                UPDATE %s
                SET last_outcome = 'MISSING'
                WHERE last_outcome = 'FOUND'
                  AND (seen_in_execution_id IS NULL OR seen_in_execution_id <> ?)
                """.formatted(qualified(ITEM));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, executionId);
            return statement.executeUpdate();
        } catch (SQLException ex) {
            throw sqlFailed(ITEM, "seen_in_execution_id", String.valueOf(executionId), ex);
        }
    }

    public List<DspaceItemRecord> listItemsForMatch() {
        List<DspaceItemRecord> items = new ArrayList<>();
        if (!available()) {
            return items;
        }
        String sql = """
                SELECT uuid, handle, handle_url, name, dc_type, normalized_doi, doi_raw, access_status, embargo_date
                FROM %s
                WHERE last_outcome = 'FOUND'
                """.formatted(qualified(ITEM));
        java.util.Map<String, DspaceItemRecord> byUuid = new java.util.LinkedHashMap<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                DspaceItemRecord item = new DspaceItemRecord();
                item.uuid = rs.getString("uuid");
                item.handle = rs.getString("handle");
                item.handleUrl = rs.getString("handle_url");
                item.name = rs.getString("name");
                item.dcType = rs.getString("dc_type");
                item.normalizedDoi = rs.getString("normalized_doi");
                item.doiRaw = rs.getString("doi_raw");
                item.access = new cl.uss.ca.external.dspace.DspaceAccessStatus();
                item.access.status = rs.getString("access_status");
                Date embargo = rs.getDate("embargo_date");
                item.access.embargoDate = embargo == null ? null : embargo.toLocalDate().toString();
                items.add(item);
                byUuid.put(item.uuid, item);
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load DSpace items for matching", ex);
        }
        if (byUuid.isEmpty()) {
            return items;
        }
        String metaSql = "SELECT item_uuid, field, place, language, value FROM " + qualified(METADATA)
                + " WHERE field IN ('dc.title','dc.date.issued','dc.date','dc.contributor.author','dc.identifier.doi')";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(metaSql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                DspaceItemRecord item = byUuid.get(rs.getString("item_uuid"));
                if (item == null) {
                    continue;
                }
                DspaceMetadataValue value = new DspaceMetadataValue();
                value.field = rs.getString("field");
                value.place = rs.getInt("place");
                value.language = rs.getString("language");
                value.value = rs.getString("value");
                item.metadata.add(value);
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load DSpace metadata for matching", ex);
        }
        return items;
    }

    public OffsetDateTime maxLastModified() {
        if (!available()) {
            return null;
        }
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT MAX(last_modified_remote) FROM " + qualified(ITEM));
             ResultSet rs = statement.executeQuery()) {
            if (!rs.next()) {
                return null;
            }
            Timestamp value = rs.getTimestamp(1);
            return value == null ? null : OffsetDateTime.ofInstant(value.toInstant(), ZoneOffset.UTC);
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to read DSpace watermark", ex);
        }
    }

    public void saveRunStats(SyncRunState run, DspaceStatsDto snapshot, String version, Long discoverTotal) {
        if (!tableExists(STATS) || run == null || run.syncId == null) {
            return;
        }
        Long id = parseNumericId(run.syncId);
        if (id == null) {
            return;
        }
        String sql = """
                INSERT INTO %s (
                    sync_execution_id, dspace_version, communities_count, collections_count, items_found,
                    open_access_count, metadata_only_count, restricted_count, embargo_count,
                    fulltext_public_count, matched_count, candidate_count, discover_total_elements
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    dspace_version = VALUES(dspace_version),
                    communities_count = VALUES(communities_count),
                    collections_count = VALUES(collections_count),
                    items_found = VALUES(items_found),
                    open_access_count = VALUES(open_access_count),
                    metadata_only_count = VALUES(metadata_only_count),
                    restricted_count = VALUES(restricted_count),
                    embargo_count = VALUES(embargo_count),
                    fulltext_public_count = VALUES(fulltext_public_count),
                    matched_count = VALUES(matched_count),
                    candidate_count = VALUES(candidate_count),
                    discover_total_elements = VALUES(discover_total_elements)
                """.formatted(qualified(STATS));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.setString(2, version);
            statement.setLong(3, snapshot.communities);
            statement.setLong(4, snapshot.collections);
            statement.setLong(5, snapshot.items);
            statement.setLong(6, snapshot.openAccess);
            statement.setLong(7, snapshot.metadataOnly);
            statement.setLong(8, snapshot.restricted);
            statement.setLong(9, snapshot.embargo);
            statement.setLong(10, snapshot.fulltextPublic);
            statement.setLong(11, snapshot.matched);
            statement.setLong(12, snapshot.candidates);
            setLong(statement, 13, discoverTotal);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw sqlFailed(STATS, "sync_execution_id", run.syncId, ex);
        }
    }

    public String findVersion() {
        if (!tableExists(STATS)) {
            return null;
        }
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT dspace_version FROM " + qualified(STATS) + " ORDER BY sync_execution_id DESC LIMIT 1");
             ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getString(1) : null;
        } catch (SQLException ex) {
            return null;
        }
    }

    private void replaceMetadata(Connection connection, DspaceItemRecord item) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM " + qualified(METADATA) + " WHERE item_uuid = ?")) {
            delete.setString(1, item.uuid);
            delete.executeUpdate();
        }
        if (item.metadata.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO %s (item_uuid, field, place, language, value, authority, confidence)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """.formatted(qualified(METADATA));
        try (PreparedStatement insert = connection.prepareStatement(sql)) {
            for (DspaceMetadataValue value : item.metadata) {
                if (value == null || value.field == null || value.value == null) {
                    continue;
                }
                insert.setString(1, item.uuid);
                insert.setString(2, clip(value.field, 191));
                insert.setInt(3, value.place);
                insert.setString(4, value.language == null ? "" : clip(value.language, 32));
                insert.setString(5, value.value);
                insert.setString(6, clip(value.authority, 191));
                setInt(insert, 7, value.confidence);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    public List<FoundItem> listFoundItems() {
        List<FoundItem> items = new ArrayList<>();
        if (!tableExists(ITEM)) {
            return items;
        }
        String sql = "SELECT uuid, access_status FROM " + qualified(ITEM)
                + " WHERE last_outcome = 'FOUND' ORDER BY uuid";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                FoundItem item = new FoundItem();
                item.uuid = rs.getString("uuid");
                item.accessStatus = nullableString(rs, "access_status");
                items.add(item);
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list DSpace FOUND items", ex);
        }
        return items;
    }

    /**
     * DELETE+INSERT ORIGINAL bitstreams only when the inventory was confirmed.
     * Does not touch communities, collections, matching or accessStatus.
     */
    public boolean replaceConfirmedBitstreams(DspaceItemRecord item, String accessStatus) {
        if (item == null || item.uuid == null || item.bitstreamInventory == null
                || !item.bitstreamInventory.mayReplaceBitstreams()) {
            return false;
        }
        String availability = DspaceAccessClassifier.classify(accessStatus, originalBitstreams(item)).name();
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            replaceBitstreams(connection, item);
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE " + qualified(ITEM) + " SET fulltext_availability = ?, last_sync_at = CURRENT_TIMESTAMP(6) WHERE uuid = ?"
            )) {
                update.setString(1, availability);
                update.setString(2, item.uuid);
                update.executeUpdate();
            }
            connection.commit();
        } catch (SQLException ex) {
            throw sqlFailed(ITEM, "uuid", item.uuid, ex);
        }
        return true;
    }

    public static final class FoundItem {
        public String uuid;
        public String accessStatus;
    }

    private void replaceBitstreams(Connection connection, DspaceItemRecord item) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM " + qualified(BITSTREAM) + " WHERE item_uuid = ?")) {
            delete.setString(1, item.uuid);
            delete.executeUpdate();
        }
        List<DspaceBitstreamRecord> originals = originalBitstreams(item);
        if (originals.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO %s (uuid, item_uuid, bundle_uuid, bundle_name, name, size_bytes, checksum_md5, mime_type,
                                sequence_id, access_status, content_url, public_download, last_sync_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6))
                """.formatted(qualified(BITSTREAM));
        try (PreparedStatement insert = connection.prepareStatement(sql)) {
            for (DspaceBitstreamRecord bitstream : originals) {
                if (bitstream == null || bitstream.uuid == null || bitstream.uuid.isBlank()) {
                    continue;
                }
                insert.setString(1, bitstream.uuid);
                insert.setString(2, item.uuid);
                insert.setString(3, bitstream.bundleUuid);
                insert.setString(4, clip(bitstream.bundleName, 32));
                insert.setString(5, clip(bitstream.name, 512));
                setLong(insert, 6, bitstream.sizeBytes);
                insert.setString(7, clip(bitstream.checksumMd5, 32));
                insert.setString(8, clip(bitstream.mimeType, 128));
                setInt(insert, 9, bitstream.sequenceId);
                insert.setString(10, clip(bitstream.accessStatus, 32));
                insert.setString(11, clip(bitstream.contentUrl, 1000));
                insert.setBoolean(12, bitstream.publicDownload);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private void attachDois(List<DspacePureCandidate> items) {
        if (items.isEmpty() || !tableExists("pure_research_output_electronic_version")) {
            return;
        }
        String sql = """
                SELECT research_output_id, doi
                FROM %s
                WHERE type_discriminator = 'DoiElectronicVersion' AND doi IS NOT NULL AND TRIM(doi) <> ''
                """.formatted(qualified("pure_research_output_electronic_version"));
        java.util.Map<Long, DspacePureCandidate> byId = index(items);
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                DspacePureCandidate candidate = byId.get(rs.getLong("research_output_id"));
                if (candidate != null && candidate.normalizedDoi == null) {
                    candidate.normalizedDoi = cl.uss.ca.domain.DoiNormalizer.normalize(rs.getString("doi"));
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load PURE DOIs for DSpace matching", ex);
        }
    }

    private void attachHandles(List<DspacePureCandidate> items) {
        if (items.isEmpty() || !tableExists("pure_research_output_link")) {
            return;
        }
        java.util.Map<Long, DspacePureCandidate> byId = index(items);
        String sql = "SELECT research_output_id, url FROM " + qualified("pure_research_output_link")
                + " WHERE url IS NOT NULL";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                DspacePureCandidate candidate = byId.get(rs.getLong("research_output_id"));
                String url = nullableString(rs, "url");
                String handle = DspaceMatcher.normalizeHandle(url);
                if (candidate != null && candidate.handle == null && handle != null && handle.startsWith("uss/")) {
                    candidate.handle = handle;
                }
            }
        } catch (SQLException ex) {
            // link table may exist without url column in some installs
        }
    }

    private void attachAuthors(List<DspacePureCandidate> items) {
        if (items.isEmpty() || !tableExists("pure_research_output_contributor")) {
            return;
        }
        java.util.Map<Long, DspacePureCandidate> byId = index(items);
        String sql = "SELECT research_output_id, first_name, last_name FROM "
                + qualified("pure_research_output_contributor");
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                DspacePureCandidate candidate = byId.get(rs.getLong("research_output_id"));
                if (candidate != null) {
                    candidate.authors.add(cl.uss.ca.domain.AuthorNameMatcher.displayName(
                            rs.getString("first_name"), rs.getString("last_name")
                    ));
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load PURE authors for DSpace matching", ex);
        }
    }

    private java.util.Map<Long, DspacePureCandidate> index(List<DspacePureCandidate> items) {
        java.util.Map<Long, DspacePureCandidate> map = new java.util.LinkedHashMap<>();
        for (DspacePureCandidate item : items) {
            map.put(item.researchOutputId, item);
        }
        return map;
    }

    private StoredItem findItem(String uuid) {
        String sql = "SELECT last_modified_remote, access_status, fulltext_availability, owning_collection_uuid FROM "
                + qualified(ITEM) + " WHERE uuid = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                StoredItem stored = new StoredItem();
                Timestamp modified = rs.getTimestamp("last_modified_remote");
                stored.lastModified = modified == null ? null : OffsetDateTime.ofInstant(modified.toInstant(), ZoneOffset.UTC);
                stored.accessStatus = nullableString(rs, "access_status");
                stored.availability = nullableString(rs, "fulltext_availability");
                stored.owningCollectionUuid = nullableString(rs, "owning_collection_uuid");
                return stored;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load DSpace item", ex);
        }
    }

    private boolean communityExists(String uuid) {
        return scalarLong("SELECT COUNT(*) FROM " + qualified(COMMUNITY) + " WHERE uuid = ?", uuid) > 0;
    }

    private boolean collectionExists(String uuid) {
        return scalarLong("SELECT COUNT(*) FROM " + qualified(COLLECTION) + " WHERE uuid = ?", uuid) > 0;
    }

    private static List<DspaceBitstreamRecord> originalBitstreams(DspaceItemRecord item) {
        List<DspaceBitstreamRecord> originals = new ArrayList<>();
        if (item == null) {
            return originals;
        }
        for (var bundle : item.bundles) {
            if (bundle == null) {
                continue;
            }
            for (DspaceBitstreamRecord bitstream : bundle.bitstreams) {
                if (bitstream == null) {
                    continue;
                }
                if (bitstream.bundleName == null) {
                    bitstream.bundleName = bundle.name;
                }
                if (bitstream.bundleUuid == null) {
                    bitstream.bundleUuid = bundle.uuid;
                }
                if ("ORIGINAL".equalsIgnoreCase(bitstream.bundleName)) {
                    originals.add(bitstream);
                }
            }
        }
        return originals;
    }

    private void bindCommunity(PreparedStatement statement, DspaceCommunityRecord record, String syncId)
            throws SQLException {
        statement.setString(1, record.uuid);
        statement.setString(2, clip(record.handle, 64));
        statement.setString(3, record.name);
        statement.setString(4, record.parentUuid);
        setInt(statement, 5, record.archivedItemsCount);
        bindSyncId(statement, 6, COMMUNITY, "last_sync_execution_id", syncId);
    }

    private static LocalDate embargoDate(DspaceItemRecord item) {
        if (item.access == null || item.access.embargoDate == null || item.access.embargoDate.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(item.access.embargoDate.substring(0, 10));
        } catch (Exception ex) {
            return null;
        }
    }

    private static void setInt(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private static void setLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    private static void setBool(PreparedStatement statement, int index, Boolean value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TINYINT);
        } else {
            statement.setBoolean(index, value);
        }
    }

    private static void setDate(PreparedStatement statement, int index, LocalDate value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.DATE);
        } else {
            statement.setDate(index, Date.valueOf(value));
        }
    }

    private static void setTimestamp(PreparedStatement statement, int index, OffsetDateTime value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP);
        } else {
            statement.setTimestamp(index, Timestamp.from(value.toInstant()));
        }
    }

    private static boolean same(Object left, Object right) {
        if (left == null && right == null) {
            return true;
        }
        return left != null && left.equals(right);
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private DatabaseException sqlFailed(String table, String column, String detail, SQLException ex) {
        return new DatabaseException(
                "DSpace write failed table=" + table + " column=" + column + " detail=" + detail + " " + ex.getMessage(),
                ex
        );
    }

    private String itemFromSql() {
        return itemFromSql(true, true, tableExists("pure_research_output_electronic_version"));
    }

    private String itemCountFromSql(DspaceItemQuery query) {
        boolean shared = query != null && Boolean.TRUE.equals(query.sharedDoi);
        boolean files = query != null && DspaceListSupport.hasFileFilter(query.hasFile) != null;
        boolean comparison = query != null && DspaceListSupport.accessComparisonFilter(query.accessComparison) != null;
        boolean pure = comparison && tableExists("pure_research_output_electronic_version");
        return itemFromSql(shared, files, pure);
    }

    private String itemFromSql(boolean includeShared, boolean includeFiles, boolean includePureAccess) {
        StringBuilder sql = new StringBuilder("""
                %s i
                LEFT JOIN %s col ON col.uuid = i.owning_collection_uuid
                LEFT JOIN %s com ON com.uuid = col.parent_community_uuid
                LEFT JOIN (%s) m ON m.item_uuid = i.uuid
                LEFT JOIN %s o ON o.id = m.research_output_id
                """.formatted(
                qualified(ITEM),
                qualified(COLLECTION),
                qualified(COMMUNITY),
                primaryMatchSql(),
                qualified("pure_research_output")
        ));
        if (includeShared) {
            sql.append(" LEFT JOIN (").append(sharedDoiSql()).append(") shared ON shared.normalized_doi = i.normalized_doi ");
        }
        if (includeFiles) {
            sql.append(" LEFT JOIN (").append(originalFileSql()).append(") files ON files.item_uuid = i.uuid ");
        }
        if (includePureAccess) {
            sql.append(" LEFT JOIN (").append(pureAccessSql())
                    .append(") pa ON pa.research_output_id = m.research_output_id ");
        }
        return sql.toString();
    }

    private String sharedDoiSql() {
        return """
                SELECT normalized_doi, COUNT(DISTINCT uuid) AS shared_item_count
                FROM %s
                WHERE last_outcome = 'FOUND'
                  AND normalized_doi IS NOT NULL AND TRIM(normalized_doi) <> ''
                GROUP BY normalized_doi
                HAVING COUNT(DISTINCT uuid) > 1
                """.formatted(qualified(ITEM));
    }

    private String originalFileSql() {
        return """
                SELECT item_uuid, COUNT(*) AS original_file_count
                FROM %s
                WHERE bundle_name = 'ORIGINAL'
                GROUP BY item_uuid
                """.formatted(qualified(BITSTREAM));
    }

    private String pureAccessSql() {
        String concat = DspaceListSupport.pureAccessConcatExpr("ev");
        return """
                SELECT ev.research_output_id,
                       %s AS pure_canonical,
                       GROUP_CONCAT(NULLIF(TRIM(CONCAT_WS(' | ',
                           NULLIF(ev.access_type_es, ''),
                           NULLIF(ev.access_type_en, ''),
                           NULLIF(ev.access_type_uri, '')
                       )), '') SEPARATOR 0x0A) AS pure_access_raw
                FROM %s ev
                GROUP BY ev.research_output_id
                """.formatted(
                DspaceListSupport.pureCanonicalAggregate(concat),
                qualified("pure_research_output_electronic_version")
        );
    }

    private String pureAccessSelect() {
        if (!tableExists("pure_research_output_electronic_version")) {
            return ", CAST(NULL AS CHAR) AS pure_canonical, CAST(NULL AS CHAR) AS pure_access_raw";
        }
        return ", pa.pure_canonical, pa.pure_access_raw";
    }

    private String primaryMatchSql() {
        return """
                SELECT ranked.item_uuid, ranked.match_method, ranked.match_status, ranked.canonical,
                       ranked.confidence, ranked.research_output_id
                FROM (
                    SELECT item_uuid, match_method, match_status, canonical, confidence, research_output_id,
                           ROW_NUMBER() OVER (
                               PARTITION BY item_uuid
                               ORDER BY CASE match_status
                                   WHEN 'CONFIRMED' THEN 0
                                   WHEN 'SECONDARY' THEN 1
                                   WHEN 'CANDIDATE' THEN 2
                                   ELSE 3 END,
                                   canonical DESC,
                                   id
                           ) AS rn
                    FROM %s
                ) ranked
                WHERE ranked.rn = 1
                """.formatted(qualified(MATCH));
    }

    private FilterSql itemFilter(DspaceItemQuery query) {
        FilterSql filter = new FilterSql();
        List<String> clauses = new ArrayList<>();
        if (query != null && query.uuids != null && !query.uuids.isEmpty()) {
            List<String> ids = new ArrayList<>();
            for (String uuid : query.uuids) {
                if (uuid != null && uuid.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
                    ids.add(uuid.trim());
                }
                if (ids.size() >= 5000) {
                    break;
                }
            }
            if (ids.isEmpty()) {
                filter.sql = " WHERE 1 = 0";
                return filter;
            }
            String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
            filter.sql = " WHERE i.uuid IN (" + placeholders + ")";
            filter.params.addAll(ids);
            return filter;
        }
        String outcome = DspaceListSupport.outcomeFilter(query == null ? null : query.outcome);
        if (!"ALL".equals(outcome)) {
            clauses.add("i.last_outcome = ?");
            filter.params.add(outcome);
        }
        if (query != null && query.search != null && !query.search.isBlank()) {
            String like = likeContains(query.search.trim());
            clauses.add("""
                    (i.name LIKE ? ESCAPE '!'
                     OR i.normalized_doi LIKE ? ESCAPE '!'
                     OR i.doi_raw LIKE ? ESCAPE '!'
                     OR i.handle LIKE ? ESCAPE '!'
                     OR i.uuid = ?
                     OR o.uuid = ?)
                    """);
            filter.params.add(like);
            filter.params.add(like);
            filter.params.add(like);
            filter.params.add(like);
            filter.params.add(query.search.trim());
            filter.params.add(query.search.trim());
        }
        if (query != null && query.accessStatus != null && !query.accessStatus.isBlank()) {
            String access = query.accessStatus.trim();
            if ("UNKNOWN".equalsIgnoreCase(access) || "undetermined".equalsIgnoreCase(access)) {
                clauses.add("(i.access_status IS NULL OR TRIM(i.access_status) = '')");
            } else {
                clauses.add("i.access_status = ?");
                filter.params.add(access);
            }
        }
        if (query != null && query.matchStatus != null && !query.matchStatus.isBlank()) {
            String match = query.matchStatus.trim().toUpperCase(Locale.ROOT);
            switch (match) {
                case "LINKED" -> clauses.add("m.match_status IN ('CONFIRMED', 'SECONDARY')");
                case "REVIEW" -> clauses.add("m.match_status = 'CANDIDATE'");
                case "UNMATCHED" -> clauses.add("(m.item_uuid IS NULL OR m.match_status = 'REJECTED')");
                case "CONFIRMED", "SECONDARY", "CANDIDATE", "REJECTED" -> {
                    clauses.add("m.match_status = ?");
                    filter.params.add(match);
                }
                default -> {
                }
            }
        }
        if (query != null && query.matchMethod != null && !query.matchMethod.isBlank()) {
            clauses.add("m.match_method = ?");
            filter.params.add(query.matchMethod.trim().toUpperCase(Locale.ROOT));
        }
        if (query != null && query.collectionUuid != null && !query.collectionUuid.isBlank()) {
            String collectionUuid = query.collectionUuid.trim();
            if (tableExists(ITEM_COLLECTION)) {
                clauses.add("""
                        (i.owning_collection_uuid = ?
                         OR EXISTS (
                             SELECT 1 FROM %s rel
                             WHERE rel.item_uuid = i.uuid AND rel.collection_uuid = ?
                         ))
                        """.formatted(qualified(ITEM_COLLECTION)));
                filter.params.add(collectionUuid);
                filter.params.add(collectionUuid);
            } else {
                clauses.add("i.owning_collection_uuid = ?");
                filter.params.add(collectionUuid);
            }
        }
        if (query != null && query.communityUuid != null && !query.communityUuid.isBlank()) {
            Set<String> communityIds = DspaceCommunityPath.selfAndDescendants(
                    query.communityUuid.trim(),
                    loadCommunityNodes()
            );
            if (communityIds.isEmpty()) {
                clauses.add("1 = 0");
            } else {
                String placeholders = String.join(",", java.util.Collections.nCopies(communityIds.size(), "?"));
                clauses.add("col.parent_community_uuid IN (" + placeholders + ")");
                filter.params.addAll(communityIds);
            }
        }
        if (query != null && query.doi != null && !query.doi.isBlank()) {
            String normalized = DoiNormalizer.normalize(query.doi);
            if (normalized != null) {
                clauses.add("i.normalized_doi = ?");
                filter.params.add(normalized);
            }
        }
        if (query != null && Boolean.TRUE.equals(query.sharedDoi)) {
            clauses.add("shared.shared_item_count IS NOT NULL");
        }
        String hasFile = query == null ? null : DspaceListSupport.hasFileFilter(query.hasFile);
        if ("YES".equals(hasFile)) {
            clauses.add("files.original_file_count IS NOT NULL");
        } else if ("NO".equals(hasFile)) {
            clauses.add("files.original_file_count IS NULL");
        }
        String comparison = query == null ? null : DspaceListSupport.accessComparisonFilter(query.accessComparison);
        if (comparison != null) {
            String dspaceCanon = DspaceListSupport.dspaceCanonicalExpr("i");
            String pureCanon = tableExists("pure_research_output_electronic_version")
                    ? "COALESCE(pa.pure_canonical, 'UNKNOWN')"
                    : "'UNKNOWN'";
            String linked = "m.match_status IN ('CONFIRMED', 'SECONDARY', 'CANDIDATE')";
            String known = linked
                    + " AND (" + dspaceCanon + ") <> 'UNKNOWN'"
                    + " AND (" + pureCanon + ") <> 'UNKNOWN'";
            String equal = DspaceListSupport.collapseAccessExpr("(" + dspaceCanon + ")")
                    + " = "
                    + DspaceListSupport.collapseAccessExpr("(" + pureCanon + ")");
            switch (comparison) {
                case DspaceAccessComparison.CONSISTENT -> {
                    clauses.add(known);
                    clauses.add(equal);
                }
                case DspaceAccessComparison.DIFFERENCE -> {
                    clauses.add(known);
                    clauses.add(DspaceListSupport.collapseAccessExpr("(" + dspaceCanon + ")")
                            + " <> "
                            + DspaceListSupport.collapseAccessExpr("(" + pureCanon + ")"));
                }
                case DspaceAccessComparison.NOT_EVALUABLE -> clauses.add("NOT (" + known + ")");
                default -> {
                }
            }
        }
        if (!clauses.isEmpty()) {
            filter.sql = " WHERE " + String.join(" AND ", clauses);
        }
        return filter;
    }

    private DspaceItemListDto mapItemList(ResultSet rs) throws SQLException {
        DspaceItemListDto dto = new DspaceItemListDto();
        fillItemList(dto, rs);
        return dto;
    }

    private DspaceItemDetailDto mapItemDetail(ResultSet rs) throws SQLException {
        DspaceItemDetailDto dto = new DspaceItemDetailDto();
        fillItemList(dto, rs);
        Date embargo = rs.getDate("embargo_date");
        dto.embargoDate = embargo == null ? null : embargo.toLocalDate().toString();
        dto.inArchive = nullableBoolean(rs, "in_archive");
        dto.discoverable = nullableBoolean(rs, "discoverable");
        dto.withdrawn = nullableBoolean(rs, "withdrawn");
        dto.lastErrorCode = nullableString(rs, "last_error_code");
        dto.lastErrorMessage = nullableString(rs, "last_error_message");
        Long syncId = nullableLong(rs, "last_sync_execution_id");
        dto.lastSyncExecutionId = syncId == null ? null : String.valueOf(syncId);
        Long seen = nullableLong(rs, "seen_in_execution_id");
        dto.seenInExecutionId = seen == null ? null : String.valueOf(seen);
        return dto;
    }

    private void fillItemList(DspaceItemListDto dto, ResultSet rs) throws SQLException {
        dto.uuid = rs.getString("uuid");
        dto.handle = nullableString(rs, "handle");
        dto.handleUrl = nullableString(rs, "handle_url");
        dto.name = nullableString(rs, "name");
        dto.dcType = nullableString(rs, "dc_type");
        dto.normalizedDoi = nullableString(rs, "normalized_doi");
        dto.doi = nullableString(rs, "doi_raw");
        dto.accessStatus = nullableString(rs, "access_status");
        dto.fulltextAvailability = nullableString(rs, "fulltext_availability");
        dto.collectionUuid = nullableString(rs, "owning_collection_uuid");
        dto.collectionName = nullableString(rs, "collection_name");
        dto.communityUuid = nullableString(rs, "community_uuid");
        dto.communityName = nullableString(rs, "community_name");
        dto.lastOutcome = nullableString(rs, "last_outcome");
        dto.lastSyncAt = offset(rs.getTimestamp("last_sync_at"));
        dto.lastModifiedRemote = offset(rs.getTimestamp("last_modified_remote"));
        dto.matchMethod = nullableString(rs, "match_method");
        dto.matchStatus = nullableString(rs, "match_status");
        dto.canonical = rs.getBoolean("canonical");
        BigDecimal confidence = rs.getBigDecimal("confidence");
        dto.confidence = confidence == null ? null : confidence.doubleValue();
        dto.researchOutputId = nullableLong(rs, "research_output_id");
        dto.researchOutputUuid = nullableString(rs, "research_output_uuid");
        dto.researchOutputTitle = nullableString(rs, "research_output_title");
        Integer shared = nullableInt(rs, "shared_item_count");
        dto.sharedDoiCount = shared;
        Long files = nullableLong(rs, "original_file_count");
        dto.hasOriginalFile = files != null && files > 0;
        dto.dspaceAccessCanonical = DspaceAccessComparison.fromDspace(dto.accessStatus);
        String pureRaw = hasColumn(rs, "pure_access_raw") ? nullableString(rs, "pure_access_raw") : null;
        String sqlCanonical = hasColumn(rs, "pure_canonical") ? nullableString(rs, "pure_canonical") : null;
        List<String> pureValues = DspaceAccessComparison.splitBundle(pureRaw);
        dto.pureAccessCanonical = pureValues.isEmpty() && sqlCanonical != null
                ? sqlCanonical
                : DspaceAccessComparison.fromPureValues(pureValues);
        dto.pureAccessRaw = DspaceAccessComparison.firstRaw(pureValues);
        boolean linked = dto.researchOutputId != null && dto.matchStatus != null
                && ("CONFIRMED".equals(dto.matchStatus)
                || "SECONDARY".equals(dto.matchStatus)
                || "CANDIDATE".equals(dto.matchStatus));
        dto.accessComparison = DspaceAccessComparison.compare(
                dto.dspaceAccessCanonical, dto.pureAccessCanonical, linked);
    }

    private static boolean hasColumn(ResultSet rs, String column) {
        try {
            rs.findColumn(column);
            return true;
        } catch (SQLException ex) {
            return false;
        }
    }

    private List<DspaceBitstreamDto> loadBitstreams(Connection connection, String uuid) throws SQLException {
        List<DspaceBitstreamDto> items = new ArrayList<>();
        String sql = """
                SELECT uuid, name, mime_type, size_bytes, bundle_name, access_status, public_download,
                       content_url, checksum_md5, sequence_id
                FROM %s
                WHERE item_uuid = ?
                ORDER BY bundle_name, sequence_id, name
                """.formatted(qualified(BITSTREAM));
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    DspaceBitstreamDto dto = new DspaceBitstreamDto();
                    dto.uuid = rs.getString("uuid");
                    dto.name = nullableString(rs, "name");
                    dto.mimeType = nullableString(rs, "mime_type");
                    dto.sizeBytes = nullableLong(rs, "size_bytes");
                    dto.bundleName = nullableString(rs, "bundle_name");
                    dto.accessStatus = nullableString(rs, "access_status");
                    dto.publicDownload = rs.getBoolean("public_download");
                    dto.contentUrl = nullableString(rs, "content_url");
                    dto.checksumMd5 = nullableString(rs, "checksum_md5");
                    dto.sequenceId = nullableInt(rs, "sequence_id");
                    items.add(dto);
                }
            }
        }
        return items;
    }

    public DspaceBitstreamDto findBitstream(String itemUuid, String bitstreamUuid) {
        assertAvailable();
        if (itemUuid == null || bitstreamUuid == null) {
            return null;
        }
        String sql = """
                SELECT uuid, name, mime_type, size_bytes, bundle_name, access_status, public_download,
                       content_url, checksum_md5, sequence_id
                FROM %s
                WHERE uuid = ? AND item_uuid = ?
                """.formatted(qualified(BITSTREAM));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, bitstreamUuid.trim());
            statement.setString(2, itemUuid.trim());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                DspaceBitstreamDto dto = new DspaceBitstreamDto();
                dto.uuid = rs.getString("uuid");
                dto.name = nullableString(rs, "name");
                dto.mimeType = nullableString(rs, "mime_type");
                dto.sizeBytes = nullableLong(rs, "size_bytes");
                dto.bundleName = nullableString(rs, "bundle_name");
                dto.accessStatus = nullableString(rs, "access_status");
                dto.publicDownload = rs.getBoolean("public_download");
                dto.contentUrl = nullableString(rs, "content_url");
                dto.checksumMd5 = nullableString(rs, "checksum_md5");
                dto.sequenceId = nullableInt(rs, "sequence_id");
                return dto;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load DSpace bitstream", ex);
        }
    }

    private List<DspaceMetadataDto> loadMetadata(Connection connection, String uuid) throws SQLException {
        List<DspaceMetadataDto> items = new ArrayList<>();
        String sql = """
                SELECT field, place, language, value
                FROM %s
                WHERE item_uuid = ?
                ORDER BY field, place
                """.formatted(qualified(METADATA));
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    DspaceMetadataDto dto = new DspaceMetadataDto();
                    dto.field = nullableString(rs, "field");
                    dto.place = rs.getInt("place");
                    dto.language = nullableString(rs, "language");
                    dto.value = nullableString(rs, "value");
                    items.add(dto);
                }
            }
        }
        return items;
    }

    private List<DspaceMatchListDto> loadMatches(Connection connection, String uuid) throws SQLException {
        List<DspaceMatchListDto> items = new ArrayList<>();
        String sql = """
                SELECT m.id, m.item_uuid, m.match_method, m.match_status, m.canonical, m.confidence,
                       m.evidence_json, m.updated_at, m.research_output_id, o.uuid AS research_output_uuid,
                       o.title AS research_output_title
                FROM %s m
                JOIN %s o ON o.id = m.research_output_id
                WHERE m.item_uuid = ?
                ORDER BY m.canonical DESC, CASE m.match_status
                    WHEN 'CONFIRMED' THEN 0 WHEN 'SECONDARY' THEN 1 WHEN 'CANDIDATE' THEN 2 ELSE 3 END, m.id
                """.formatted(qualified(MATCH), qualified("pure_research_output"));
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    DspaceMatchListDto dto = new DspaceMatchListDto();
                    dto.id = rs.getLong("id");
                    dto.itemUuid = rs.getString("item_uuid");
                    dto.matchMethod = nullableString(rs, "match_method");
                    dto.matchStatus = nullableString(rs, "match_status");
                    dto.canonical = rs.getBoolean("canonical");
                    BigDecimal confidence = rs.getBigDecimal("confidence");
                    dto.confidence = confidence == null ? null : confidence.doubleValue();
                    dto.evidenceJson = nullableString(rs, "evidence_json");
                    dto.updatedAt = offset(rs.getTimestamp("updated_at"));
                    dto.researchOutputId = nullableLong(rs, "research_output_id");
                    dto.researchOutputUuid = nullableString(rs, "research_output_uuid");
                    dto.researchOutputTitle = nullableString(rs, "research_output_title");
                    items.add(dto);
                }
            }
        }
        return items;
    }

    private void replaceItemCollections(Connection connection, DspaceItemRecord item, String owningUuid)
            throws SQLException {
        if (!tableExists(ITEM_COLLECTION) || item == null || item.uuid == null) {
            return;
        }
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM " + qualified(ITEM_COLLECTION) + " WHERE item_uuid = ?")) {
            delete.setString(1, item.uuid);
            delete.executeUpdate();
        }
        String sql = """
                INSERT INTO %s (item_uuid, collection_uuid, relation)
                VALUES (?, ?, ?)
                """.formatted(qualified(ITEM_COLLECTION));
        try (PreparedStatement insert = connection.prepareStatement(sql)) {
            boolean pending = false;
            if (owningUuid != null && collectionExists(owningUuid)) {
                insert.setString(1, item.uuid);
                insert.setString(2, owningUuid);
                insert.setString(3, DspaceCommunityPath.OWNING);
                insert.addBatch();
                pending = true;
            }
            for (String mapped : item.mappedCollectionUuids) {
                if (mapped == null || mapped.equals(owningUuid) || !collectionExists(mapped)) {
                    continue;
                }
                insert.setString(1, item.uuid);
                insert.setString(2, mapped);
                insert.setString(3, DspaceCommunityPath.MAPPED);
                insert.addBatch();
                pending = true;
            }
            if (pending) {
                insert.executeBatch();
            }
        }
    }

    private List<DspaceCollectionRefDto> loadItemCollections(Connection connection, DspaceItemListDto item)
            throws SQLException {
        List<DspaceCollectionRefDto> refs = new ArrayList<>();
        if (item != null && item.collectionUuid != null) {
            DspaceCollectionRefDto owning = new DspaceCollectionRefDto();
            owning.uuid = item.collectionUuid;
            owning.name = item.collectionName;
            owning.relation = DspaceCommunityPath.OWNING;
            refs.add(owning);
        }
        if (!tableExists(ITEM_COLLECTION) || item == null || item.uuid == null) {
            return refs;
        }
        String sql = """
                SELECT rel.collection_uuid, rel.relation, col.name
                FROM %s rel
                LEFT JOIN %s col ON col.uuid = rel.collection_uuid
                WHERE rel.item_uuid = ?
                ORDER BY CASE rel.relation WHEN 'OWNING' THEN 0 ELSE 1 END, col.name, rel.collection_uuid
                """.formatted(qualified(ITEM_COLLECTION), qualified(COLLECTION));
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, item.uuid);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String uuid = rs.getString("collection_uuid");
                    String relation = nullableString(rs, "relation");
                    boolean duplicate = false;
                    for (DspaceCollectionRefDto existing : refs) {
                        if (uuid != null && uuid.equals(existing.uuid) && relation != null
                                && relation.equals(existing.relation)) {
                            duplicate = true;
                            break;
                        }
                    }
                    if (duplicate) {
                        continue;
                    }
                    DspaceCollectionRefDto dto = new DspaceCollectionRefDto();
                    dto.uuid = uuid;
                    dto.name = nullableString(rs, "name");
                    dto.relation = relation;
                    refs.add(dto);
                }
            }
        }
        return refs;
    }

    private void attachCommunityPaths(List<? extends DspaceItemListDto> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        Map<String, DspaceCommunityPath.Node> byUuid = new LinkedHashMap<>();
        for (DspaceCommunityPath.Node node : loadCommunityNodes()) {
            byUuid.put(node.uuid, node);
        }
        for (DspaceItemListDto item : items) {
            item.communityPath = DspaceCommunityPath.format(item.communityUuid, item.collectionName, byUuid);
        }
    }

    private List<DspaceCommunityPath.Node> loadCommunityNodes() {
        List<DspaceCommunityPath.Node> nodes = new ArrayList<>();
        String sql = "SELECT uuid, parent_uuid, name FROM " + qualified(COMMUNITY);
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                nodes.add(new DspaceCommunityPath.Node(
                        rs.getString("uuid"),
                        nullableString(rs, "parent_uuid"),
                        nullableString(rs, "name")
                ));
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load DSpace communities", ex);
        }
        return nodes;
    }

    private static int bindParams(PreparedStatement statement, List<Object> params) throws SQLException {
        int index = 1;
        if (params == null) {
            return index;
        }
        for (Object param : params) {
            statement.setObject(index++, param);
        }
        return index;
    }

    private static OffsetDateTime offset(Timestamp value) {
        return value == null ? null : OffsetDateTime.ofInstant(value.toInstant(), ZoneOffset.UTC);
    }

    private static final class FilterSql {
        private String sql = "";
        private final List<Object> params = new ArrayList<>();
    }

    public static final class ItemUpsert {
        public String kind;
        public String availability;
        public String accessStatus;
        public final List<SyncChange> changes = new ArrayList<>();
    }

    public static final class StoredItem {
        public OffsetDateTime lastModified;
        public String accessStatus;
        public String availability;
        public String owningCollectionUuid;
    }

    public static final class Checkpoint {
        public String stage;
        public int page;
        int size;
        public String lastUuid;
    }
}
