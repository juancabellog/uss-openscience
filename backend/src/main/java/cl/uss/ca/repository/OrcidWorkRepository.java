package cl.uss.ca.repository;

import cl.uss.ca.api.dto.OrcidWorkContributorDto;
import cl.uss.ca.api.dto.OrcidWorkDto;
import cl.uss.ca.api.dto.OrcidWorkExternalIdDto;
import cl.uss.ca.api.dto.OrcidWorkGroupDto;
import cl.uss.ca.domain.OrcidExternalIdNormalizer;
import cl.uss.ca.domain.OrcidFormat;
import cl.uss.ca.domain.OrcidGroupKey;
import cl.uss.ca.exception.DatabaseException;
import cl.uss.ca.external.orcid.OrcidContributorRecord;
import cl.uss.ca.external.orcid.OrcidExternalIdRecord;
import cl.uss.ca.external.orcid.OrcidWorkGroupRecord;
import cl.uss.ca.external.orcid.OrcidWorkRecord;
import cl.uss.ca.sync.diff.SyncChange;
import cl.uss.ca.sync.IncrementalReason;
import cl.uss.ca.sync.OrcidIncrementalClassifier;
import cl.uss.ca.sync.SyncMode;
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
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@ApplicationScoped
public class OrcidWorkRepository extends JdbcRepository {

    public boolean available() {
        return tableExists("orcid_work_group")
                && tableExists("orcid_work_group_external_id")
                && tableExists("orcid_work")
                && tableExists("orcid_work_external_id")
                && tableExists("orcid_work_contributor");
    }

    public List<PersonCandidate> listPeopleWithOrcid() {
        return listPeopleCandidates(SyncMode.FULL, 0);
    }

    public List<PersonCandidate> listPeopleCandidates(SyncMode mode, int refreshDays) {
        List<PersonCandidate> people = listPeopleWithOrcidDetails();
        if (mode == null || mode.isFull()) {
            return people;
        }
        OffsetDateTime now = OffsetDateTime.now();
        List<PersonCandidate> selected = new ArrayList<>();
        for (PersonCandidate person : people) {
            IncrementalReason reason = OrcidIncrementalClassifier.classify(
                    person.hasSyncRow,
                    person.storedOrcid,
                    person.orcid,
                    person.lastSyncAt,
                    person.lastSyncStatus,
                    person.syncPending,
                    refreshDays,
                    now
            );
            if (reason != null) {
                person.inclusionReason = reason;
                selected.add(person);
            }
        }
        return selected;
    }

    private List<PersonCandidate> listPeopleWithOrcidDetails() {
        boolean personSync = tableExists("orcid_person_sync");
        boolean pendingColumn = columnExists("pure_person", "orcid_sync_pending");
        String pendingOrder = personSync
                ? "COALESCE(s.pending, 0) DESC, "
                : pendingColumn ? "p.orcid_sync_pending DESC, " : "";
        String join = personSync
                ? " LEFT JOIN " + qualified("orcid_person_sync") + " s ON s.person_id = p.id "
                : " ";
        String extra = "";
        if (personSync) {
            extra += ", s.pending AS sync_pending, s.last_sync_at AS person_sync_at, s.last_sync_status AS person_sync_status, s.orcid AS stored_orcid, s.person_id AS sync_person_id";
        }
        if (pendingColumn) {
            extra += ", p.orcid_sync_pending";
        }
        String sql = "SELECT p.id, p.orcid" + extra + " FROM " + qualified("pure_person") + " p" + join + "WHERE "
                + activeInPurePredicate("pure_person", "p")
                + " AND p.orcid IS NOT NULL AND TRIM(p.orcid) <> '' ORDER BY "
                + pendingOrder + "p.id ASC";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<PersonCandidate> items = new ArrayList<>();
            while (rs.next()) {
                PersonCandidate person = new PersonCandidate(rs.getLong("id"), nullableString(rs, "orcid"));
                if (personSync) {
                    person.hasSyncRow = rs.getObject("sync_person_id") != null;
                    Boolean pending = nullableBoolean(rs, "sync_pending");
                    person.syncPending = pending != null && pending;
                    Timestamp at = rs.getTimestamp("person_sync_at");
                    person.lastSyncAt = at == null ? null : at.toInstant().atOffset(ZoneOffset.UTC);
                    person.lastSyncStatus = nullableString(rs, "person_sync_status");
                    person.storedOrcid = OrcidFormat.normalize(nullableString(rs, "stored_orcid"));
                }
                if (!person.syncPending && pendingColumn) {
                    person.syncPending = rs.getBoolean("orcid_sync_pending");
                }
                items.add(person);
            }
            return items;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list people with ORCID", ex);
        }
    }

    public PersonCandidate requirePerson(long personId) {
        String extra = "";
        if (columnExists("pure_person", "orcid_sync_pending")) {
            extra += ", p.orcid_sync_pending";
        }
        if (columnExists("pure_person", "last_orcid_sync_at")) {
            extra += ", p.last_orcid_sync_at, p.last_orcid_sync_status";
        }
        boolean personSync = tableExists("orcid_person_sync");
        String join = personSync
                ? " LEFT JOIN " + qualified("orcid_person_sync") + " s ON s.person_id = p.id "
                : " ";
        if (personSync) {
            extra += ", s.pending AS sync_pending, s.last_sync_at AS person_sync_at, s.last_sync_status AS person_sync_status";
        }
        String sql = "SELECT p.id, p.orcid" + extra + " FROM " + qualified("pure_person")
                + " p" + join + "WHERE p.id = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, personId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                PersonCandidate person = new PersonCandidate(rs.getLong("id"), nullableString(rs, "orcid"));
                if (personSync) {
                    Boolean pending = nullableBoolean(rs, "sync_pending");
                    person.syncPending = pending != null && pending;
                    Timestamp at = rs.getTimestamp("person_sync_at");
                    person.lastSyncAt = at == null ? null : at.toInstant().atOffset(ZoneOffset.UTC);
                    person.lastSyncStatus = nullableString(rs, "person_sync_status");
                }
                if (person.lastSyncStatus == null && columnExists("pure_person", "last_orcid_sync_at")) {
                    Timestamp at = rs.getTimestamp("last_orcid_sync_at");
                    person.lastSyncAt = at == null ? null : at.toInstant().atOffset(ZoneOffset.UTC);
                    person.lastSyncStatus = nullableString(rs, "last_orcid_sync_status");
                }
                if (!person.syncPending && columnExists("pure_person", "orcid_sync_pending")) {
                    person.syncPending = rs.getBoolean("orcid_sync_pending");
                }
                return person;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load person ORCID state", ex);
        }
    }

    public ReconcileResult reconcilePerson(
            long personId,
            String orcid,
            List<OrcidWorkGroupRecord> incomingGroups,
            WorkDetailFetcher detailFetcher,
            String syncId
    ) {
        if (!available()) {
            throw new DatabaseException("orcid_work tables are not available", null);
        }
        ReconcileResult result = new ReconcileResult();
        result.groupsReceived = incomingGroups == null ? 0 : incomingGroups.size();
        try (Connection connection = connection()) {
            boolean previous = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                Map<String, StoredGroup> groupsByKey = new LinkedHashMap<>();
                Map<Long, StoredGroup> groupsById = new LinkedHashMap<>();
                loadExistingGroups(connection, personId, groupsByKey, groupsById);
                Map<Long, StoredWork> worksByPutCode = loadExistingWorks(connection, personId);
                List<SyncChange> changes = new ArrayList<>();
                for (OrcidWorkGroupRecord incoming : incomingGroups == null ? List.<OrcidWorkGroupRecord>of() : incomingGroups) {
                    reconcileGroup(
                            connection,
                            personId,
                            orcid,
                            incoming,
                            groupsByKey,
                            groupsById,
                            worksByPutCode,
                            detailFetcher,
                            syncId,
                            result,
                            changes
                    );
                }
                for (StoredGroup stored : groupsById.values()) {
                    if (stored.active && !stored.seen) {
                        markGroupInactive(connection, stored.id, syncId);
                        result.groupsInactive++;
                        changes.add(SyncChange.inactivated().on("ORCID_GROUP", stored.id, null, orcid));
                    }
                }
                for (StoredWork stored : worksByPutCode.values()) {
                    if (stored.active && !stored.seen) {
                        markWorkInactive(connection, stored.id, syncId);
                        result.assertionsInactive++;
                        changes.add(SyncChange.inactivated().on("ORCID", stored.id, stored.putCode, orcid));
                    }
                }
                markPersonSynced(connection, personId, orcid, result.detailErrors > 0
                        ? "COMPLETED_WITH_WARNINGS"
                        : "COMPLETED");
                connection.commit();
                result.changes = changes;
                return result;
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            } catch (RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(previous);
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to reconcile ORCID works", ex);
        }
    }

    public List<OrcidWorkGroupDto> listGroupsByPerson(long personId) {
        if (!available()) {
            return List.of();
        }
        Map<Long, OrcidWorkGroupDto> groups = loadGroupDtos(personId);
        if (groups.isEmpty()) {
            return List.of();
        }
        attachGroupExternalIds(groups);
        Map<Long, OrcidWorkDto> works = attachAssertions(groups);
        attachWorkExternalIds(works);
        attachContributors(works);
        return new ArrayList<>(groups.values());
    }

    public List<OrcidWorkDto> listByPerson(long personId) {
        List<OrcidWorkDto> works = new ArrayList<>();
        for (OrcidWorkGroupDto group : listGroupsByPerson(personId)) {
            works.addAll(group.assertions);
        }
        return works;
    }

    public List<String> listPersonPureDois(String personUuid) {
        if (personUuid == null || !tableExists("pure_research_output")
                || !tableExists("pure_research_output_contributor")
                || !tableExists("pure_research_output_identifier")) {
            return List.of();
        }
        String sql = """
                SELECT i.value
                FROM %s o
                JOIN %s c ON c.research_output_id = o.id
                JOIN %s i ON i.research_output_id = o.id
                WHERE c.person_uuid = ?
                  AND (
                      LOWER(i.id_source) LIKE '%%doi%%'
                      OR LOWER(i.type_discriminator) LIKE '%%doi%%'
                  )
                  AND i.value IS NOT NULL AND TRIM(i.value) <> ''
                """.formatted(
                qualified("pure_research_output"),
                qualified("pure_research_output_contributor"),
                qualified("pure_research_output_identifier")
        );
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, personUuid);
            try (ResultSet rs = statement.executeQuery()) {
                List<String> values = new ArrayList<>();
                while (rs.next()) {
                    values.add(rs.getString(1));
                }
                return values;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list person PURE DOIs", ex);
        }
    }

    public long countWorkGroups() {
        if (!available()) {
            return 0L;
        }
        return scalarLong("SELECT COUNT(*) FROM " + qualified("orcid_work_group") + " WHERE active_in_orcid = 1");
    }

    public long countAssertions() {
        if (!available()) {
            return 0L;
        }
        return scalarLong("SELECT COUNT(*) FROM " + qualified("orcid_work") + " WHERE active_in_orcid = 1");
    }

    public long countWorks() {
        return countWorkGroups();
    }

    public long countPeopleSynced() {
        if (!available()) {
            return 0L;
        }
        return scalarLong("SELECT COUNT(DISTINCT person_id) FROM " + qualified("orcid_work_group"));
    }

    public void markOrcidPending(Connection connection, long personId) throws SQLException {
        if (tableExists("orcid_person_sync")) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO %s (person_id, pending)
                    VALUES (?, 1)
                    ON DUPLICATE KEY UPDATE pending = 1
                    """.formatted(qualified("orcid_person_sync"))
            )) {
                statement.setLong(1, personId);
                statement.executeUpdate();
            }
        }
        if (columnExists("pure_person", "orcid_sync_pending")) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE " + qualified("pure_person") + " SET orcid_sync_pending = 1 WHERE id = ?"
            )) {
                statement.setLong(1, personId);
                statement.executeUpdate();
            }
        }
    }

    public void markPersonFailed(long personId) {
        try (Connection connection = connection()) {
            PersonCandidate person = requirePerson(personId);
            markPersonSynced(connection, personId, person == null ? null : person.orcid, "FAILED");
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to mark ORCID sync failure", ex);
        }
    }

    private void reconcileGroup(
            Connection connection,
            long personId,
            String orcid,
            OrcidWorkGroupRecord incoming,
            Map<String, StoredGroup> groupsByKey,
            Map<Long, StoredGroup> groupsById,
            Map<Long, StoredWork> worksByPutCode,
            WorkDetailFetcher detailFetcher,
            String syncId,
            ReconcileResult result,
            List<SyncChange> changes
    ) throws SQLException {
        OrcidGroupKey.Result key = groupKey(orcid, incoming);
        GroupView view = groupView(incoming);
        StoredGroup stored = resolveGroup(key, incoming, groupsByKey, groupsById, worksByPutCode);
        String fingerprint = groupFingerprint(view, incoming.externalIds);
        ApplyKind groupKind;
        if (stored == null) {
            long id = insertGroup(connection, personId, orcid, key, view, incoming, syncId);
            replaceGroupExternalIds(connection, id, incoming.externalIds);
            stored = new StoredGroup();
            stored.id = id;
            stored.groupKey = key.key;
            stored.active = true;
            groupsByKey.put(key.key, stored);
            groupsById.put(id, stored);
            groupKind = ApplyKind.CREATED;
        } else {
            boolean same = Objects.equals(stored.fingerprint, fingerprint);
            if (same && stored.active) {
                touchGroup(connection, stored.id, syncId);
                groupKind = ApplyKind.UNCHANGED;
            } else {
                updateGroup(connection, stored.id, orcid, key, view, incoming, syncId);
                if (!same) {
                    replaceGroupExternalIds(connection, stored.id, incoming.externalIds);
                }
                groupKind = stored.active ? ApplyKind.UPDATED : ApplyKind.REACTIVATED;
                groupsByKey.remove(stored.groupKey);
                stored.groupKey = key.key;
                groupsByKey.put(key.key, stored);
            }
        }
        stored.seen = true;
        stored.active = true;
        stored.fingerprint = fingerprint;
        classifyGroup(result, groupKind);
        SyncChange groupChange = changeFor(groupKind, "ORCID group reconciled");
        if (groupChange != null) {
            groupChange.on("ORCID_GROUP", stored.id, null, orcid);
            changes.add(groupChange);
        }
        for (OrcidWorkRecord summary : incoming.summaries) {
            if (summary.putCode == null) {
                continue;
            }
            result.assertionsReceived++;
            StoredWork existing = worksByPutCode.get(summary.putCode);
            OrcidWorkRecord toApply = summary;
            boolean needDetail = existing == null
                    || !existing.detailLoaded
                    || !Objects.equals(existing.lastModifiedOrcid, summary.lastModifiedOrcid);
            if (needDetail && detailFetcher != null) {
                try {
                    OrcidWorkRecord detail = detailFetcher.fetch(orcid, summary.putCode);
                    if (detail != null) {
                        toApply = detail;
                        result.detailsFetched++;
                    }
                } catch (RuntimeException ex) {
                    result.detailErrors++;
                    DetailError error = new DetailError();
                    error.putCode = summary.putCode;
                    error.code = "DETAIL_FETCH_FAILED";
                    error.message = ex.getMessage();
                    result.detailFailures.add(error);
                }
            }
            ApplyKind workKind = applyWork(connection, personId, stored.id, orcid, toApply, existing, syncId);
            classifyAssertion(result, workKind);
            SyncChange workChange = changeFor(workKind, "ORCID assertion reconciled");
            if (workChange != null) {
                workChange.on("ORCID", existing == null ? null : existing.id, toApply.putCode, orcid);
                changes.add(workChange);
            }
            StoredWork seen = existing == null ? new StoredWork() : existing;
            seen.putCode = toApply.putCode;
            seen.active = true;
            seen.seen = true;
            seen.detailLoaded = existing != null && existing.detailLoaded || toApply.detailLoaded;
            seen.lastModifiedOrcid = toApply.lastModifiedOrcid;
            worksByPutCode.put(toApply.putCode, seen);
        }
    }

    private StoredGroup resolveGroup(
            OrcidGroupKey.Result key,
            OrcidWorkGroupRecord incoming,
            Map<String, StoredGroup> groupsByKey,
            Map<Long, StoredGroup> groupsById,
            Map<Long, StoredWork> worksByPutCode
    ) {
        StoredGroup stored = groupsByKey.get(key.key);
        if (stored != null) {
            return stored;
        }
        for (OrcidWorkRecord summary : incoming.summaries) {
            if (summary.putCode == null) {
                continue;
            }
            StoredWork work = worksByPutCode.get(summary.putCode);
            if (work != null) {
                return groupsById.get(work.workGroupId);
            }
        }
        return null;
    }

    private static OrcidGroupKey.Result groupKey(String orcid, OrcidWorkGroupRecord group) {
        List<String> identity = new ArrayList<>();
        for (OrcidExternalIdRecord id : group.externalIds) {
            if (OrcidExternalIdNormalizer.isIdentityType(id.type)
                    && OrcidExternalIdNormalizer.isSelf(id.relationship)
                    && id.normalizedValue != null
                    && !id.normalizedValue.isBlank()) {
                identity.add(id.type.trim().toLowerCase() + ":" + id.normalizedValue);
            }
        }
        OrcidGroupKey.Result fromIds = OrcidGroupKey.fromIdentityIds(orcid, identity);
        if (fromIds != null) {
            return fromIds;
        }
        List<Long> putCodes = new ArrayList<>();
        for (OrcidWorkRecord summary : group.summaries) {
            if (summary.putCode != null) {
                putCodes.add(summary.putCode);
            }
        }
        return OrcidGroupKey.fromPutCodes(orcid, putCodes);
    }

    private static GroupView groupView(OrcidWorkGroupRecord group) {
        GroupView view = new GroupView();
        view.lastModifiedOrcid = group.lastModifiedOrcid;
        view.rawJson = group.rawJson;
        for (OrcidWorkRecord summary : group.summaries) {
            if (view.title == null && summary.title != null) {
                view.title = summary.title;
                view.journalTitle = summary.journalTitle;
                view.workType = summary.workType;
                view.publicationYear = summary.publicationYear;
                view.publicationMonth = summary.publicationMonth;
                view.publicationDay = summary.publicationDay;
            }
            if (view.lastModifiedOrcid == null
                    || (summary.lastModifiedOrcid != null && summary.lastModifiedOrcid > view.lastModifiedOrcid)) {
                view.lastModifiedOrcid = summary.lastModifiedOrcid;
            }
        }
        view.displayDoi = displayDoi(group.externalIds);
        return view;
    }

    private static String displayDoi(List<OrcidExternalIdRecord> ids) {
        for (OrcidExternalIdRecord id : ids) {
            if (id.type != null
                    && "doi".equalsIgnoreCase(id.type)
                    && OrcidExternalIdNormalizer.isSelf(id.relationship)) {
                return id.normalizedValue != null ? id.normalizedValue : id.value;
            }
        }
        return null;
    }

    private void loadExistingGroups(
            Connection connection,
            long personId,
            Map<String, StoredGroup> byKey,
            Map<Long, StoredGroup> byId
    ) throws SQLException {
        String sql = """
                SELECT g.id, g.group_key, g.group_key_kind, g.last_modified_orcid, g.title, g.journal_title,
                       g.work_type, g.publication_year, g.publication_month, g.publication_day, g.display_doi,
                       g.active_in_orcid, e.identifier_type, e.identifier_value, e.normalized_value,
                       e.identifier_url, e.relationship
                FROM %s g
                LEFT JOIN %s e ON e.work_group_id = g.id
                WHERE g.person_id = ?
                ORDER BY g.id, e.id
                """.formatted(qualified("orcid_work_group"), qualified("orcid_work_group_external_id"));
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, personId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    StoredGroup stored = byId.get(id);
                    if (stored == null) {
                        stored = new StoredGroup();
                        stored.id = id;
                        stored.groupKey = rs.getString("group_key");
                        stored.active = rs.getBoolean("active_in_orcid");
                        stored.externalFingerprints = new ArrayList<>();
                        GroupView view = new GroupView();
                        view.lastModifiedOrcid = nullableLong(rs, "last_modified_orcid");
                        view.title = nullableString(rs, "title");
                        view.journalTitle = nullableString(rs, "journal_title");
                        view.workType = nullableString(rs, "work_type");
                        view.publicationYear = nullableInt(rs, "publication_year");
                        view.publicationMonth = nullableInt(rs, "publication_month");
                        view.publicationDay = nullableInt(rs, "publication_day");
                        view.displayDoi = nullableString(rs, "display_doi");
                        stored.view = view;
                        byId.put(id, stored);
                        byKey.put(stored.groupKey, stored);
                    }
                    String type = nullableString(rs, "identifier_type");
                    String value = nullableString(rs, "identifier_value");
                    if (type != null && value != null) {
                        stored.externalFingerprints.add(externalFingerprint(
                                type,
                                value,
                                nullableString(rs, "normalized_value"),
                                nullableString(rs, "identifier_url"),
                                nullableString(rs, "relationship")
                        ));
                    }
                }
            }
        }
        for (StoredGroup stored : byId.values()) {
            stored.fingerprint = groupFingerprint(stored.view, stored.externalFingerprints);
        }
    }

    private Map<Long, StoredWork> loadExistingWorks(Connection connection, long personId) throws SQLException {
        String sql = """
                SELECT id, work_group_id, put_code, last_modified_orcid, detail_loaded, active_in_orcid
                FROM %s WHERE person_id = ?
                """.formatted(qualified("orcid_work"));
        Map<Long, StoredWork> byPutCode = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, personId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    StoredWork stored = new StoredWork();
                    stored.id = rs.getLong("id");
                    stored.workGroupId = rs.getLong("work_group_id");
                    stored.putCode = rs.getLong("put_code");
                    stored.lastModifiedOrcid = nullableLong(rs, "last_modified_orcid");
                    stored.detailLoaded = rs.getBoolean("detail_loaded");
                    stored.active = rs.getBoolean("active_in_orcid");
                    byPutCode.put(stored.putCode, stored);
                }
            }
        }
        return byPutCode;
    }

    private ApplyKind applyWork(
            Connection connection,
            long personId,
            long workGroupId,
            String orcid,
            OrcidWorkRecord incoming,
            StoredWork existing,
            String syncId
    ) throws SQLException {
        if (existing == null) {
            long id = insertWork(connection, personId, workGroupId, orcid, incoming, syncId);
            replaceWorkExternalIds(connection, id, incoming.externalIds);
            if (incoming.detailLoaded) {
                replaceContributors(connection, id, incoming.contributors);
            }
            return ApplyKind.CREATED;
        }
        boolean lastModifiedSame = Objects.equals(existing.lastModifiedOrcid, incoming.lastModifiedOrcid);
        boolean skipPayload = lastModifiedSame && existing.detailLoaded && existing.active && incoming.detailLoaded;
        if (skipPayload || (lastModifiedSame && existing.detailLoaded && existing.active && !incoming.detailLoaded)) {
            touchWork(connection, existing.id, workGroupId, syncId);
            return ApplyKind.UNCHANGED;
        }
        updateWork(connection, existing.id, workGroupId, orcid, incoming, syncId);
        replaceWorkExternalIds(connection, existing.id, incoming.externalIds);
        if (incoming.detailLoaded) {
            replaceContributors(connection, existing.id, incoming.contributors);
        }
        return existing.active ? ApplyKind.UPDATED : ApplyKind.REACTIVATED;
    }

    private long insertGroup(
            Connection connection,
            long personId,
            String orcid,
            OrcidGroupKey.Result key,
            GroupView view,
            OrcidWorkGroupRecord incoming,
            String syncId
    ) throws SQLException {
        String sql = """
                INSERT INTO %s (
                    person_id, orcid, group_key, group_key_kind, last_modified_orcid, title, journal_title,
                    work_type, publication_year, publication_month, publication_day, display_doi,
                    active_in_orcid, last_seen_in_orcid_at, last_seen_sync_id, raw_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, NOW(6), ?, ?)
                """.formatted(qualified("orcid_work_group"));
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int index = 1;
            statement.setLong(index++, personId);
            statement.setString(index++, orcid);
            statement.setString(index++, key.key);
            statement.setString(index++, key.kind);
            setNullableLong(statement, index++, view.lastModifiedOrcid);
            setNullable(statement, index++, view.title);
            setNullable(statement, index++, view.journalTitle);
            setNullable(statement, index++, view.workType);
            setNullableInt(statement, index++, view.publicationYear);
            setNullableInt(statement, index++, view.publicationMonth);
            setNullableInt(statement, index++, view.publicationDay);
            setNullable(statement, index++, view.displayDoi);
            setNullable(statement, index++, syncId);
            setJson(statement, index, incoming.rawJson);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("orcid_work_group insert did not return id");
                }
                return keys.getLong(1);
            }
        }
    }

    private void updateGroup(
            Connection connection,
            long id,
            String orcid,
            OrcidGroupKey.Result key,
            GroupView view,
            OrcidWorkGroupRecord incoming,
            String syncId
    ) throws SQLException {
        String sql = """
                UPDATE %s SET
                    orcid = ?, group_key = ?, group_key_kind = ?, last_modified_orcid = ?, title = ?,
                    journal_title = ?, work_type = ?, publication_year = ?, publication_month = ?,
                    publication_day = ?, display_doi = ?, active_in_orcid = 1, removed_from_orcid_at = NULL,
                    last_seen_in_orcid_at = NOW(6), last_seen_sync_id = ?, raw_json = ?
                WHERE id = ?
                """.formatted(qualified("orcid_work_group"));
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setString(index++, orcid);
            statement.setString(index++, key.key);
            statement.setString(index++, key.kind);
            setNullableLong(statement, index++, view.lastModifiedOrcid);
            setNullable(statement, index++, view.title);
            setNullable(statement, index++, view.journalTitle);
            setNullable(statement, index++, view.workType);
            setNullableInt(statement, index++, view.publicationYear);
            setNullableInt(statement, index++, view.publicationMonth);
            setNullableInt(statement, index++, view.publicationDay);
            setNullable(statement, index++, view.displayDoi);
            setNullable(statement, index++, syncId);
            setJson(statement, index++, incoming.rawJson);
            statement.setLong(index, id);
            statement.executeUpdate();
        }
    }

    private long insertWork(
            Connection connection,
            long personId,
            long workGroupId,
            String orcid,
            OrcidWorkRecord work,
            String syncId
    ) throws SQLException {
        String sql = """
                INSERT INTO %s (
                    work_group_id, person_id, orcid, put_code, created_date_orcid, last_modified_orcid,
                    title, subtitle, translated_title, translated_title_language, journal_title,
                    short_description, citation_type, citation_value, work_type,
                    publication_year, publication_month, publication_day, url, language_code, country_code,
                    visibility, path, display_index, source_orcid_uri, source_orcid_path,
                    source_client_id_uri, source_client_id_path, source_name,
                    assertion_origin_orcid_uri, assertion_origin_orcid_path,
                    assertion_origin_client_id_uri, assertion_origin_client_id_path, assertion_origin_name,
                    active_in_orcid, last_seen_in_orcid_at, last_seen_sync_id,
                    detail_loaded, detail_loaded_at, raw_json
                ) VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?, 1, NOW(6), ?, ?, %s, ?
                )
                """.formatted(
                qualified("orcid_work"),
                work.detailLoaded ? "NOW(6)" : "NULL"
        );
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindWork(statement, workGroupId, personId, orcid, work, syncId);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("orcid_work insert did not return id");
                }
                return keys.getLong(1);
            }
        }
    }

    private void updateWork(
            Connection connection,
            long id,
            long workGroupId,
            String orcid,
            OrcidWorkRecord work,
            String syncId
    ) throws SQLException {
        String detail = work.detailLoaded
                ? ", detail_loaded = 1, detail_loaded_at = NOW(6), raw_json = ?"
                : "";
        String sql = """
                UPDATE %s SET
                    work_group_id = ?, orcid = ?, created_date_orcid = COALESCE(?, created_date_orcid),
                    last_modified_orcid = ?, title = ?, subtitle = COALESCE(?, subtitle),
                    translated_title = COALESCE(?, translated_title),
                    translated_title_language = COALESCE(?, translated_title_language),
                    journal_title = ?, short_description = COALESCE(?, short_description),
                    citation_type = COALESCE(?, citation_type), citation_value = COALESCE(?, citation_value),
                    work_type = ?, publication_year = ?, publication_month = ?, publication_day = ?,
                    url = ?, language_code = COALESCE(?, language_code), country_code = COALESCE(?, country_code),
                    visibility = ?, path = ?, display_index = ?,
                    source_orcid_uri = COALESCE(?, source_orcid_uri),
                    source_orcid_path = COALESCE(?, source_orcid_path),
                    source_client_id_uri = COALESCE(?, source_client_id_uri),
                    source_client_id_path = COALESCE(?, source_client_id_path),
                    source_name = ?,
                    assertion_origin_orcid_uri = COALESCE(?, assertion_origin_orcid_uri),
                    assertion_origin_orcid_path = COALESCE(?, assertion_origin_orcid_path),
                    assertion_origin_client_id_uri = COALESCE(?, assertion_origin_client_id_uri),
                    assertion_origin_client_id_path = COALESCE(?, assertion_origin_client_id_path),
                    assertion_origin_name = COALESCE(?, assertion_origin_name),
                    active_in_orcid = 1, removed_from_orcid_at = NULL,
                    last_seen_in_orcid_at = NOW(6), last_seen_sync_id = ?
                    %s
                WHERE id = ?
                """.formatted(qualified("orcid_work"), detail);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = bindWorkUpdate(statement, workGroupId, orcid, work, syncId);
            if (work.detailLoaded) {
                setJson(statement, index++, work.rawJson);
            }
            statement.setLong(index, id);
            statement.executeUpdate();
        }
    }

    private void bindWork(
            PreparedStatement statement,
            long workGroupId,
            long personId,
            String orcid,
            OrcidWorkRecord work,
            String syncId
    ) throws SQLException {
        int index = 1;
        statement.setLong(index++, workGroupId);
        statement.setLong(index++, personId);
        statement.setString(index++, orcid);
        statement.setLong(index++, work.putCode);
        setNullableLong(statement, index++, work.createdDateOrcid);
        setNullableLong(statement, index++, work.lastModifiedOrcid);
        setNullable(statement, index++, work.title);
        setNullable(statement, index++, work.subtitle);
        setNullable(statement, index++, work.translatedTitle);
        setNullable(statement, index++, work.translatedTitleLanguage);
        setNullable(statement, index++, work.journalTitle);
        setNullable(statement, index++, work.shortDescription);
        setNullable(statement, index++, work.citationType);
        setNullable(statement, index++, work.citationValue);
        setNullable(statement, index++, work.workType);
        setNullableInt(statement, index++, work.publicationYear);
        setNullableInt(statement, index++, work.publicationMonth);
        setNullableInt(statement, index++, work.publicationDay);
        setNullable(statement, index++, work.url);
        setNullable(statement, index++, work.languageCode);
        setNullable(statement, index++, work.countryCode);
        setNullable(statement, index++, work.visibility);
        setNullable(statement, index++, work.path);
        setNullableInt(statement, index++, work.displayIndex);
        setNullable(statement, index++, work.sourceOrcidUri);
        setNullable(statement, index++, work.sourceOrcidPath);
        setNullable(statement, index++, work.sourceClientIdUri);
        setNullable(statement, index++, work.sourceClientIdPath);
        setNullable(statement, index++, work.sourceName);
        setNullable(statement, index++, work.assertionOriginOrcidUri);
        setNullable(statement, index++, work.assertionOriginOrcidPath);
        setNullable(statement, index++, work.assertionOriginClientIdUri);
        setNullable(statement, index++, work.assertionOriginClientIdPath);
        setNullable(statement, index++, work.assertionOriginName);
        setNullable(statement, index++, syncId);
        statement.setInt(index++, work.detailLoaded ? 1 : 0);
        setJson(statement, index, work.rawJson);
    }

    private int bindWorkUpdate(
            PreparedStatement statement,
            long workGroupId,
            String orcid,
            OrcidWorkRecord work,
            String syncId
    ) throws SQLException {
        int index = 1;
        statement.setLong(index++, workGroupId);
        statement.setString(index++, orcid);
        setNullableLong(statement, index++, work.createdDateOrcid);
        setNullableLong(statement, index++, work.lastModifiedOrcid);
        setNullable(statement, index++, work.title);
        setNullable(statement, index++, work.subtitle);
        setNullable(statement, index++, work.translatedTitle);
        setNullable(statement, index++, work.translatedTitleLanguage);
        setNullable(statement, index++, work.journalTitle);
        setNullable(statement, index++, work.shortDescription);
        setNullable(statement, index++, work.citationType);
        setNullable(statement, index++, work.citationValue);
        setNullable(statement, index++, work.workType);
        setNullableInt(statement, index++, work.publicationYear);
        setNullableInt(statement, index++, work.publicationMonth);
        setNullableInt(statement, index++, work.publicationDay);
        setNullable(statement, index++, work.url);
        setNullable(statement, index++, work.languageCode);
        setNullable(statement, index++, work.countryCode);
        setNullable(statement, index++, work.visibility);
        setNullable(statement, index++, work.path);
        setNullableInt(statement, index++, work.displayIndex);
        setNullable(statement, index++, work.sourceOrcidUri);
        setNullable(statement, index++, work.sourceOrcidPath);
        setNullable(statement, index++, work.sourceClientIdUri);
        setNullable(statement, index++, work.sourceClientIdPath);
        setNullable(statement, index++, work.sourceName);
        setNullable(statement, index++, work.assertionOriginOrcidUri);
        setNullable(statement, index++, work.assertionOriginOrcidPath);
        setNullable(statement, index++, work.assertionOriginClientIdUri);
        setNullable(statement, index++, work.assertionOriginClientIdPath);
        setNullable(statement, index++, work.assertionOriginName);
        setNullable(statement, index++, syncId);
        return index;
    }

    private void touchGroup(Connection connection, long id, String syncId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE " + qualified("orcid_work_group")
                        + " SET last_seen_in_orcid_at = NOW(6), last_seen_sync_id = ? WHERE id = ?"
        )) {
            setNullable(statement, 1, syncId);
            statement.setLong(2, id);
            statement.executeUpdate();
        }
    }

    private void touchWork(Connection connection, long id, long workGroupId, String syncId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE " + qualified("orcid_work")
                        + " SET work_group_id = ?, last_seen_in_orcid_at = NOW(6), last_seen_sync_id = ? WHERE id = ?"
        )) {
            statement.setLong(1, workGroupId);
            setNullable(statement, 2, syncId);
            statement.setLong(3, id);
            statement.executeUpdate();
        }
    }

    private void markGroupInactive(Connection connection, long id, String syncId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE " + qualified("orcid_work_group")
                        + " SET active_in_orcid = 0, removed_from_orcid_at = NOW(6), last_seen_sync_id = ? WHERE id = ?"
        )) {
            setNullable(statement, 1, syncId);
            statement.setLong(2, id);
            statement.executeUpdate();
        }
    }

    private void markWorkInactive(Connection connection, long id, String syncId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE " + qualified("orcid_work")
                        + " SET active_in_orcid = 0, removed_from_orcid_at = NOW(6), last_seen_sync_id = ? WHERE id = ?"
        )) {
            setNullable(statement, 1, syncId);
            statement.setLong(2, id);
            statement.executeUpdate();
        }
    }

    private void replaceGroupExternalIds(Connection connection, long groupId, List<OrcidExternalIdRecord> ids)
            throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM " + qualified("orcid_work_group_external_id") + " WHERE work_group_id = ?"
        )) {
            delete.setLong(1, groupId);
            delete.executeUpdate();
        }
        insertExternalIds(connection, qualified("orcid_work_group_external_id"), "work_group_id", groupId, ids);
    }

    private void replaceWorkExternalIds(Connection connection, long workId, List<OrcidExternalIdRecord> ids)
            throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM " + qualified("orcid_work_external_id") + " WHERE orcid_work_id = ?"
        )) {
            delete.setLong(1, workId);
            delete.executeUpdate();
        }
        insertExternalIds(connection, qualified("orcid_work_external_id"), "orcid_work_id", workId, ids);
    }

    private void insertExternalIds(
            Connection connection,
            String table,
            String ownerColumn,
            long ownerId,
            List<OrcidExternalIdRecord> ids
    ) throws SQLException {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO %s (
                    %s, identifier_type, identifier_value, normalized_value, normalized_transient,
                    normalization_error, identifier_url, relationship
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(table, ownerColumn);
        try (PreparedStatement insert = connection.prepareStatement(sql)) {
            for (OrcidExternalIdRecord id : ids) {
                if (id.type == null || id.value == null) {
                    continue;
                }
                insert.setLong(1, ownerId);
                insert.setString(2, id.type);
                insert.setString(3, id.value);
                setNullable(insert, 4, id.normalizedValue);
                insert.setInt(5, id.normalizedTransient ? 1 : 0);
                setNullable(insert, 6, id.normalizationError);
                setNullable(insert, 7, id.url);
                setNullable(insert, 8, id.relationship);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private void replaceContributors(Connection connection, long workId, List<OrcidContributorRecord> contributors)
            throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM " + qualified("orcid_work_contributor") + " WHERE orcid_work_id = ?"
        )) {
            delete.setLong(1, workId);
            delete.executeUpdate();
        }
        if (contributors == null || contributors.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO %s (orcid_work_id, position, contributor_orcid, credit_name, contributor_email, attributes_json)
                VALUES (?, ?, ?, ?, ?, ?)
                """.formatted(qualified("orcid_work_contributor"));
        try (PreparedStatement insert = connection.prepareStatement(sql)) {
            for (OrcidContributorRecord contributor : contributors) {
                insert.setLong(1, workId);
                insert.setInt(2, contributor.position);
                setNullable(insert, 3, contributor.contributorOrcid);
                setNullable(insert, 4, contributor.creditName);
                setNullable(insert, 5, contributor.contributorEmail);
                setJson(insert, 6, contributor.attributesJson);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private void markPersonSynced(Connection connection, long personId, String orcid, String status)
            throws SQLException {
        if (tableExists("orcid_person_sync")) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO %s (person_id, orcid, pending, last_sync_at, last_sync_status)
                    VALUES (?, ?, ?, NOW(6), ?)
                    ON DUPLICATE KEY UPDATE
                        orcid = VALUES(orcid),
                        pending = VALUES(pending),
                        last_sync_at = VALUES(last_sync_at),
                        last_sync_status = VALUES(last_sync_status)
                    """.formatted(qualified("orcid_person_sync"))
            )) {
                statement.setLong(1, personId);
                setNullable(statement, 2, orcid);
                statement.setInt(3, "COMPLETED".equals(status) || "COMPLETED_WITH_WARNINGS".equals(status) ? 0 : 1);
                statement.setString(4, status);
                statement.executeUpdate();
            }
        }
        if (!columnExists("pure_person", "last_orcid_sync_at")) {
            return;
        }
        String pending = columnExists("pure_person", "orcid_sync_pending")
                ? ", orcid_sync_pending = ?"
                : "";
        String sql = "UPDATE " + qualified("pure_person")
                + " SET last_orcid_sync_at = NOW(6), last_orcid_sync_status = ?"
                + pending + " WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            int index = 2;
            if (!pending.isEmpty()) {
                statement.setInt(index++, "FAILED".equals(status) ? 1 : 0);
            }
            statement.setLong(index, personId);
            statement.executeUpdate();
        }
    }

    private Map<Long, OrcidWorkGroupDto> loadGroupDtos(long personId) {
        String sql = """
                SELECT id, orcid, title, journal_title, work_type, publication_year, display_doi, active_in_orcid
                FROM %s WHERE person_id = ?
                ORDER BY publication_year DESC, id DESC
                """.formatted(qualified("orcid_work_group"));
        Map<Long, OrcidWorkGroupDto> groups = new LinkedHashMap<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, personId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    OrcidWorkGroupDto dto = new OrcidWorkGroupDto();
                    dto.id = rs.getLong("id");
                    dto.orcid = nullableString(rs, "orcid");
                    dto.title = nullableString(rs, "title");
                    dto.journalTitle = nullableString(rs, "journal_title");
                    dto.workType = nullableString(rs, "work_type");
                    dto.publicationYear = nullableInt(rs, "publication_year");
                    dto.doi = nullableString(rs, "display_doi");
                    dto.activeInOrcid = rs.getBoolean("active_in_orcid");
                    groups.put(dto.id, dto);
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list ORCID work groups", ex);
        }
        return groups;
    }

    private void attachGroupExternalIds(Map<Long, OrcidWorkGroupDto> groups) {
        String sql = """
                SELECT work_group_id, identifier_type, identifier_value, normalized_value, identifier_url, relationship
                FROM %s WHERE work_group_id IN (%s) ORDER BY id
                """.formatted(
                qualified("orcid_work_group_external_id"),
                inPlaceholders(groups.size())
        );
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindIds(statement, groups.keySet());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    OrcidWorkGroupDto group = groups.get(rs.getLong("work_group_id"));
                    if (group != null) {
                        group.externalIds.add(mapExternalDto(rs));
                    }
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list ORCID group external IDs", ex);
        }
    }

    private Map<Long, OrcidWorkDto> attachAssertions(Map<Long, OrcidWorkGroupDto> groups) {
        String sql = """
                SELECT id, work_group_id, put_code, orcid, work_type, title, subtitle, journal_title,
                       publication_year, publication_month, publication_day, url, visibility,
                       source_name, source_orcid_path, source_client_id_path, assertion_origin_name,
                       citation_type, detail_loaded, active_in_orcid, last_seen_in_orcid_at
                FROM %s WHERE work_group_id IN (%s)
                ORDER BY display_index ASC, id ASC
                """.formatted(qualified("orcid_work"), inPlaceholders(groups.size()));
        Map<Long, OrcidWorkDto> works = new LinkedHashMap<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindIds(statement, groups.keySet());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    OrcidWorkDto dto = mapWork(rs);
                    works.put(dto.id, dto);
                    OrcidWorkGroupDto group = groups.get(dto.workGroupId);
                    if (group != null) {
                        group.assertions.add(dto);
                        if (dto.sourceName != null && !group.sources.contains(dto.sourceName)) {
                            group.sources.add(dto.sourceName);
                        }
                        group.sourceCount = group.sources.size();
                    }
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list ORCID assertions", ex);
        }
        return works;
    }

    private void attachWorkExternalIds(Map<Long, OrcidWorkDto> works) {
        if (works.isEmpty()) {
            return;
        }
        String sql = """
                SELECT orcid_work_id, identifier_type, identifier_value, normalized_value, identifier_url, relationship
                FROM %s WHERE orcid_work_id IN (%s) ORDER BY id
                """.formatted(qualified("orcid_work_external_id"), inPlaceholders(works.size()));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindIds(statement, works.keySet());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    OrcidWorkDto work = works.get(rs.getLong("orcid_work_id"));
                    if (work == null) {
                        continue;
                    }
                    OrcidWorkExternalIdDto external = mapExternalDto(rs);
                    work.externalIds.add(external);
                    if (work.doi == null && external.type != null && "doi".equalsIgnoreCase(external.type)
                            && OrcidExternalIdNormalizer.isSelf(external.relationship)) {
                        work.doi = external.normalizedValue != null ? external.normalizedValue : external.value;
                    }
                    if (work.eid == null && external.type != null && "eid".equalsIgnoreCase(external.type)
                            && OrcidExternalIdNormalizer.isSelf(external.relationship)) {
                        work.eid = external.normalizedValue != null ? external.normalizedValue : external.value;
                    }
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list ORCID work external IDs", ex);
        }
    }

    private void attachContributors(Map<Long, OrcidWorkDto> works) {
        if (works.isEmpty()) {
            return;
        }
        String sql = """
                SELECT orcid_work_id, position, contributor_orcid, credit_name, contributor_email, attributes_json
                FROM %s WHERE orcid_work_id IN (%s) ORDER BY position ASC, id ASC
                """.formatted(qualified("orcid_work_contributor"), inPlaceholders(works.size()));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindIds(statement, works.keySet());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    OrcidWorkDto work = works.get(rs.getLong("orcid_work_id"));
                    if (work == null) {
                        continue;
                    }
                    OrcidWorkContributorDto dto = new OrcidWorkContributorDto();
                    dto.position = nullableInt(rs, "position");
                    dto.contributorOrcid = nullableString(rs, "contributor_orcid");
                    dto.creditName = nullableString(rs, "credit_name");
                    dto.contributorEmail = nullableString(rs, "contributor_email");
                    dto.attributesJson = nullableString(rs, "attributes_json");
                    work.contributors.add(dto);
                    work.contributorCount = work.contributors.size();
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list ORCID contributors", ex);
        }
    }

    private static OrcidWorkDto mapWork(ResultSet rs) throws SQLException {
        OrcidWorkDto dto = new OrcidWorkDto();
        dto.id = rs.getLong("id");
        dto.workGroupId = rs.getLong("work_group_id");
        dto.putCode = rs.getLong("put_code");
        dto.orcid = nullableString(rs, "orcid");
        dto.workType = nullableString(rs, "work_type");
        dto.title = nullableString(rs, "title");
        dto.subtitle = nullableString(rs, "subtitle");
        dto.journalTitle = nullableString(rs, "journal_title");
        dto.publicationYear = nullableInt(rs, "publication_year");
        dto.publicationMonth = nullableInt(rs, "publication_month");
        dto.publicationDay = nullableInt(rs, "publication_day");
        dto.url = nullableString(rs, "url");
        dto.visibility = nullableString(rs, "visibility");
        dto.sourceName = nullableString(rs, "source_name");
        dto.sourceOrcidPath = nullableString(rs, "source_orcid_path");
        dto.sourceClientIdPath = nullableString(rs, "source_client_id_path");
        dto.assertionOriginName = nullableString(rs, "assertion_origin_name");
        dto.citationType = nullableString(rs, "citation_type");
        dto.detailLoaded = rs.getBoolean("detail_loaded");
        dto.activeInOrcid = rs.getBoolean("active_in_orcid");
        Timestamp seen = rs.getTimestamp("last_seen_in_orcid_at");
        dto.lastSeenInOrcidAt = seen == null ? null : seen.toInstant().atOffset(ZoneOffset.UTC);
        return dto;
    }

    private static OrcidWorkExternalIdDto mapExternalDto(ResultSet rs) throws SQLException {
        OrcidWorkExternalIdDto dto = new OrcidWorkExternalIdDto();
        dto.type = nullableString(rs, "identifier_type");
        dto.value = nullableString(rs, "identifier_value");
        dto.normalizedValue = nullableString(rs, "normalized_value");
        dto.url = nullableString(rs, "identifier_url");
        dto.relationship = nullableString(rs, "relationship");
        return dto;
    }

    private static void bindIds(PreparedStatement statement, Iterable<Long> ids) throws SQLException {
        int index = 1;
        for (Long id : ids) {
            statement.setLong(index++, id);
        }
    }

    private static String groupFingerprint(GroupView view, List<?> externals) {
        List<String> ext;
        if (externals != null && !externals.isEmpty() && externals.get(0) instanceof OrcidExternalIdRecord) {
            ext = externals.stream()
                    .map(OrcidExternalIdRecord.class::cast)
                    .map(id -> externalFingerprint(id.type, id.value, id.normalizedValue, id.url, id.relationship))
                    .collect(Collectors.toCollection(ArrayList::new));
        } else {
            ext = externals == null
                    ? List.of()
                    : externals.stream().map(String.class::cast).collect(Collectors.toCollection(ArrayList::new));
        }
        return String.join("|",
                nz(view == null ? null : view.title),
                nz(view == null ? null : view.journalTitle),
                nz(view == null ? null : view.workType),
                String.valueOf(view == null ? null : view.publicationYear),
                String.valueOf(view == null ? null : view.publicationMonth),
                String.valueOf(view == null ? null : view.publicationDay),
                nz(view == null ? null : view.displayDoi),
                String.valueOf(view == null ? null : view.lastModifiedOrcid),
                ext.stream().sorted().collect(Collectors.joining(";"))
        );
    }

    private static String externalFingerprint(
            String type,
            String value,
            String normalized,
            String url,
            String relationship
    ) {
        return nz(type) + "=" + nz(value) + "=" + nz(normalized) + "=" + nz(url) + "=" + nz(relationship);
    }

    private static String nz(String value) {
        return value == null ? "" : value.trim();
    }

    private static void classifyGroup(ReconcileResult result, ApplyKind kind) {
        switch (kind) {
            case CREATED -> result.groupsCreated++;
            case UPDATED, REACTIVATED -> result.groupsUpdated++;
            case UNCHANGED -> result.groupsUnchanged++;
        }
    }

    private static void classifyAssertion(ReconcileResult result, ApplyKind kind) {
        switch (kind) {
            case CREATED -> result.assertionsCreated++;
            case UPDATED, REACTIVATED -> result.assertionsUpdated++;
            case UNCHANGED -> result.assertionsUnchanged++;
        }
    }

    private static SyncChange changeFor(ApplyKind kind, String description) {
        return switch (kind) {
            case CREATED -> SyncChange.created();
            case UPDATED -> SyncChange.of("UPDATED", "PARENT", description, null, null);
            case REACTIVATED -> SyncChange.reactivated();
            case UNCHANGED -> null;
        };
    }

    private static void setNullable(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    private static void setJson(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.NULL);
        } else {
            statement.setString(index, value);
        }
    }

    private static void setNullableInt(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private static void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    @FunctionalInterface
    public interface WorkDetailFetcher {
        OrcidWorkRecord fetch(String orcid, long putCode);
    }

    public static class PersonCandidate {
        public final long id;
        public final String orcid;
        public boolean syncPending;
        public OffsetDateTime lastSyncAt;
        public String lastSyncStatus;
        public boolean hasSyncRow;
        public String storedOrcid;
        public IncrementalReason inclusionReason;

        PersonCandidate(long id, String orcid) {
            this.id = id;
            this.orcid = OrcidFormat.normalize(orcid);
        }
    }

    public static class ReconcileResult {
        public long groupsReceived;
        public long groupsCreated;
        public long groupsUpdated;
        public long groupsUnchanged;
        public long groupsInactive;
        public long assertionsReceived;
        public long assertionsCreated;
        public long assertionsUpdated;
        public long assertionsUnchanged;
        public long assertionsInactive;
        public long detailsFetched;
        public long detailErrors;
        public List<DetailError> detailFailures = new ArrayList<>();
        public List<SyncChange> changes = List.of();

        public long getCreated() {
            return groupsCreated;
        }

        public long getUpdated() {
            return groupsUpdated;
        }

        public long getUnchanged() {
            return groupsUnchanged;
        }

        public long getMarkedInactive() {
            return groupsInactive;
        }
    }

    public static class DetailError {
        public long putCode;
        public String code;
        public String message;
    }

    private enum ApplyKind {
        CREATED, UPDATED, UNCHANGED, REACTIVATED
    }

    private static class StoredGroup {
        long id;
        String groupKey;
        boolean active;
        boolean seen;
        String fingerprint;
        GroupView view;
        List<String> externalFingerprints = new ArrayList<>();
    }

    private static class StoredWork {
        long id;
        long workGroupId;
        long putCode;
        Long lastModifiedOrcid;
        boolean detailLoaded;
        boolean active;
        boolean seen;
    }

    private static class GroupView {
        Long lastModifiedOrcid;
        String title;
        String journalTitle;
        String workType;
        Integer publicationYear;
        Integer publicationMonth;
        Integer publicationDay;
        String displayDoi;
        String rawJson;
    }
}
