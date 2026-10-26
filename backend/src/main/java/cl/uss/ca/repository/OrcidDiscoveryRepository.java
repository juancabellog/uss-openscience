package cl.uss.ca.repository;

import cl.uss.ca.api.dto.OrcidCandidateDto;
import cl.uss.ca.api.dto.OrcidCandidateEvidenceDto;
import cl.uss.ca.api.dto.OrcidDiscoveryExecutionDto;
import cl.uss.ca.api.dto.OrcidDiscoveryPersonDto;
import cl.uss.ca.api.dto.OrcidDiscoverySelectedItemDto;
import cl.uss.ca.api.dto.OrcidDiscoveryStatsDto;
import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.domain.AuthorNameMatcher;
import cl.uss.ca.domain.OrcidCandidateMatcher;
import cl.uss.ca.domain.OrcidDiscoverySelection;
import cl.uss.ca.exception.DatabaseException;
import cl.uss.ca.exception.NotFoundException;
import jakarta.enterprise.context.ApplicationScoped;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class OrcidDiscoveryRepository extends JdbcRepository {

    public boolean available() {
        return tableExists("orcid_discovery_execution")
                && tableExists("orcid_discovery_person")
                && tableExists("orcid_candidate");
    }

    public OrcidDiscoveryStatsDto stats() {
        OrcidDiscoveryStatsDto dto = new OrcidDiscoveryStatsDto();
        dto.people = scalarLong("SELECT COUNT(*) FROM " + qualified("pure_person")
                + " p WHERE " + activeInPurePredicate("pure_person", "p"));
        dto.withOrcid = scalarLong("""
                SELECT COUNT(*) FROM %s p
                WHERE %s
                  AND p.orcid IS NOT NULL AND TRIM(p.orcid) <> ''
                """.formatted(qualified("pure_person"), activeInPurePredicate("pure_person", "p")));
        dto.withoutOrcid = Math.max(0, dto.people - dto.withOrcid);
        OrcidDiscoveryExecutionDto latest = findLatest();
        if (latest != null) {
            String peopleTable = qualified("orcid_discovery_person");
            dto.withCandidates = scalarLong(
                    "SELECT COUNT(*) FROM " + peopleTable + " WHERE execution_id = ? AND candidate_count > 0 AND person_status <> 'SYNCED'",
                    latest.id
            );
            dto.strongMatches = scalarLong(
                    "SELECT COUNT(*) FROM " + peopleTable + " WHERE execution_id = ? AND person_status = 'STRONG'",
                    latest.id
            );
            dto.manualReview = latest.manualReviewCount;
            dto.noCandidate = latest.noCandidateCount;
            dto.conflicts = latest.conflictCount;
        }
        return dto;
    }

    public List<PersonSeed> listPeopleWithoutOrcid() {
        String sql = """
                SELECT p.id, p.uuid, p.pure_id, p.first_name, p.last_name, p.cargo, p.orcid
                FROM %s p
                WHERE %s
                  AND (p.orcid IS NULL OR TRIM(p.orcid) = '')
                ORDER BY p.last_name, p.first_name, p.id
                """.formatted(qualified("pure_person"), activeInPurePredicate("pure_person", "p"));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<PersonSeed> items = new ArrayList<>();
            while (rs.next()) {
                PersonSeed seed = new PersonSeed();
                seed.personId = rs.getLong("id");
                seed.uuid = nullableString(rs, "uuid");
                seed.pureId = nullableLong(rs, "pure_id");
                seed.firstName = nullableString(rs, "first_name");
                seed.lastName = nullableString(rs, "last_name");
                seed.cargo = nullableString(rs, "cargo");
                seed.orcid = nullableString(rs, "orcid");
                items.add(seed);
            }
            return items;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list people without ORCID", ex);
        }
    }

    public Map<Long, List<LocalPublication>> loadPublications(List<Long> personIds) {
        Map<Long, List<LocalPublication>> byPerson = new LinkedHashMap<>();
        if (personIds == null || personIds.isEmpty()
                || !tableExists("pure_research_output_contributor")
                || !tableExists("pure_research_output_electronic_version")) {
            return byPerson;
        }
        String sql = """
                SELECT p.id AS person_id, o.id AS research_output_id, ev.doi,
                       c.id AS contributor_id, c.first_name, c.last_name,
                       (
                           SELECT COUNT(*) + 1
                           FROM %s c2
                           WHERE c2.research_output_id = c.research_output_id
                             AND c2.id < c.id
                       ) AS local_position
                FROM %s p
                JOIN %s c ON c.person_uuid = p.uuid
                JOIN %s o ON o.id = c.research_output_id
                JOIN %s ev ON ev.research_output_id = o.id
                WHERE p.id IN (%s)
                  AND ev.type_discriminator = 'DoiElectronicVersion'
                  AND ev.doi IS NOT NULL AND TRIM(ev.doi) <> ''
                """.formatted(
                qualified("pure_research_output_contributor"),
                qualified("pure_person"),
                qualified("pure_research_output_contributor"),
                qualified("pure_research_output"),
                qualified("pure_research_output_electronic_version"),
                inPlaceholders(personIds.size())
        );
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, personIds.toArray());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    LocalPublication pub = new LocalPublication();
                    pub.personId = rs.getLong("person_id");
                    pub.researchOutputId = rs.getLong("research_output_id");
                    pub.doi = nullableString(rs, "doi");
                    pub.contributorFirstName = nullableString(rs, "first_name");
                    pub.contributorLastName = nullableString(rs, "last_name");
                    pub.author = AuthorNameMatcher.fullName(
                            pub.contributorFirstName,
                            pub.contributorLastName
                    );
                    pub.position = nullableInt(rs, "local_position");
                    byPerson.computeIfAbsent(pub.personId, key -> new ArrayList<>()).add(pub);
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load local publications for ORCID discovery", ex);
        }
        return byPerson;
    }

    public Long findPersonIdByOrcid(String orcid, Long excludePersonId) {
        String sql = "SELECT id FROM " + qualified("pure_person")
                + " WHERE orcid = ? AND (? IS NULL OR id <> ?) LIMIT 1";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, orcid);
            if (excludePersonId == null) {
                statement.setNull(2, Types.BIGINT);
                statement.setNull(3, Types.BIGINT);
            } else {
                statement.setLong(2, excludePersonId);
                statement.setLong(3, excludePersonId);
            }
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to look up existing ORCID owner", ex);
        }
    }

    public long createExecution(String startedBy, int totalPeople) {
        String sql = """
                INSERT INTO %s (status, started_at, started_by, total_people)
                VALUES ('RUNNING', NOW(6), ?, ?)
                """.formatted(qualified("orcid_discovery_execution"));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, startedBy);
            statement.setInt(2, totalPeople);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to create ORCID discovery execution", ex);
        }
        throw new DatabaseException(
                "Failed to create ORCID discovery execution",
                new IllegalStateException("INSERT did not return a generated id")
        );
    }

    public boolean hasRunning() {
        return scalarLong("SELECT COUNT(*) FROM " + qualified("orcid_discovery_execution")
                + " WHERE status IN ('PENDING','RUNNING')") > 0;
    }

    public void incrementProcessed(long executionId, String bucket) {
        String column = switch (bucket) {
            case "STRONG" -> "strong_match_count";
            case "MANUAL_REVIEW" -> "manual_review_count";
            case "NO_CANDIDATE" -> "no_candidate_count";
            case "TOO_MANY_CANDIDATES" -> "too_many_count";
            case "CONFLICT" -> "conflict_count";
            case "ERROR" -> "error_count";
            default -> null;
        };
        String extra = column == null ? "" : ", " + column + " = " + column + " + 1";
        String withCandidates = "STRONG".equals(bucket) || "MANUAL_REVIEW".equals(bucket) || "CONFLICT".equals(bucket)
                ? ", people_with_candidates = people_with_candidates + 1"
                : "";
        String sql = "UPDATE " + qualified("orcid_discovery_execution")
                + " SET processed_people = processed_people + 1" + extra + withCandidates
                + " WHERE id = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, executionId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to update ORCID discovery progress", ex);
        }
    }

    public void finish(long executionId, String status) {
        String sql = "UPDATE " + qualified("orcid_discovery_execution")
                + " SET status = ?, finished_at = NOW(6) WHERE id = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setLong(2, executionId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to finish ORCID discovery", ex);
        }
    }

    public long insertPerson(long executionId, PersonSeed seed, String given, String family, String status, String error) {
        String sql = """
                INSERT INTO %s (
                    execution_id, person_id, person_uuid, pure_id, first_name, last_name, cargo,
                    search_given_name, search_family_name, person_status, last_error
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(qualified("orcid_discovery_person"));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, executionId);
            statement.setLong(2, seed.personId);
            statement.setString(3, seed.uuid);
            setLong(statement, 4, seed.pureId);
            statement.setString(5, seed.firstName);
            statement.setString(6, seed.lastName);
            statement.setString(7, seed.cargo);
            statement.setString(8, given);
            statement.setString(9, family);
            statement.setString(10, status);
            statement.setString(11, error);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to insert ORCID discovery person", ex);
        }
        throw new DatabaseException(
                "Failed to insert ORCID discovery person",
                new IllegalStateException("INSERT did not return a generated id")
        );
    }

    public void updatePersonSummary(long discoveryPersonId, String status, int candidates, int doiMatches) {
        String sql = """
                UPDATE %s
                SET person_status = ?, candidate_count = ?, doi_match_count = ?
                WHERE id = ?
                """.formatted(qualified("orcid_discovery_person"));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setInt(2, candidates);
            statement.setInt(3, doiMatches);
            statement.setLong(4, discoveryPersonId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to update ORCID discovery person", ex);
        }
    }

    public long upsertCandidate(CandidateWrite write) {
        String sql = """
                INSERT INTO %s (
                    execution_id, person_id, discovery_person_id, candidate_orcid,
                    search_given_name, search_family_name, score, match_level,
                    name_match, doi_match_count, author_match, position_match,
                    already_used_person_id, status, last_checked_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(6))
                ON DUPLICATE KEY UPDATE
                    id = LAST_INSERT_ID(id),
                    score = VALUES(score),
                    match_level = VALUES(match_level),
                    name_match = VALUES(name_match),
                    doi_match_count = VALUES(doi_match_count),
                    author_match = VALUES(author_match),
                    position_match = VALUES(position_match),
                    already_used_person_id = VALUES(already_used_person_id),
                    status = VALUES(status),
                    last_checked_at = NOW(6)
                """.formatted(qualified("orcid_candidate"));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, write.executionId);
            statement.setLong(2, write.personId);
            statement.setLong(3, write.discoveryPersonId);
            statement.setString(4, write.orcid);
            statement.setString(5, write.searchGiven);
            statement.setString(6, write.searchFamily);
            statement.setInt(7, write.score);
            statement.setString(8, write.matchLevel);
            setBoolean(statement, 9, write.nameMatch);
            statement.setInt(10, write.doiMatchCount);
            setBoolean(statement, 11, write.authorMatch);
            setBoolean(statement, 12, write.positionMatch);
            setLong(statement, 13, write.alreadyUsedPersonId);
            statement.setString(14, write.status);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to upsert ORCID candidate", ex);
        }
        throw new DatabaseException(
                "Failed to upsert ORCID candidate",
                new IllegalStateException("INSERT did not return a generated id")
        );
    }

    public void replaceEvidence(long candidateId, List<EvidenceWrite> items) {
        if (!tableExists("orcid_candidate_evidence")) {
            return;
        }
        try (Connection connection = connection();
             PreparedStatement delete = connection.prepareStatement(
                     "DELETE FROM " + qualified("orcid_candidate_evidence") + " WHERE candidate_id = ?")) {
            delete.setLong(1, candidateId);
            delete.executeUpdate();
            if (items == null || items.isEmpty()) {
                return;
            }
            boolean extended = columnExists("orcid_candidate_evidence", "reason_codes");
            String insert = extended
                    ? """
                    INSERT INTO %s (
                        candidate_id, research_output_id, doi, local_author, local_position,
                        orcid_author, orcid_position, name_match, position_match, note,
                        contributor_first_name, contributor_last_name, person_first_name, person_last_name,
                        person_full_name, contributor_full_name, name_partition_different, reason_codes
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.formatted(qualified("orcid_candidate_evidence"))
                    : """
                    INSERT INTO %s (
                        candidate_id, research_output_id, doi, local_author, local_position,
                        orcid_author, orcid_position, name_match, position_match, note
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.formatted(qualified("orcid_candidate_evidence"));
            try (PreparedStatement statement = connection.prepareStatement(insert)) {
                for (EvidenceWrite item : items) {
                    statement.setLong(1, candidateId);
                    setLong(statement, 2, item.researchOutputId);
                    statement.setString(3, item.doi);
                    statement.setString(4, item.localAuthor);
                    setInt(statement, 5, item.localPosition);
                    statement.setString(6, item.orcidAuthor);
                    setInt(statement, 7, item.orcidPosition);
                    setBoolean(statement, 8, item.nameMatch);
                    setBoolean(statement, 9, item.positionMatch);
                    statement.setString(10, item.note);
                    if (extended) {
                        statement.setString(11, item.contributorFirstName);
                        statement.setString(12, item.contributorLastName);
                        statement.setString(13, item.personFirstName);
                        statement.setString(14, item.personLastName);
                        statement.setString(15, item.personFullName);
                        statement.setString(16, item.contributorFullName);
                        setBoolean(statement, 17, item.namePartitionDifferent);
                        statement.setString(18, item.reasonCodes);
                    }
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to save ORCID candidate evidence", ex);
        }
    }

    public List<RecalcPerson> listRecalculablePeople(long executionId) {
        String sql = """
                SELECT d.id, d.person_id, d.search_given_name, d.search_family_name,
                       COALESCE(p.first_name, d.first_name) AS first_name,
                       COALESCE(p.last_name, d.last_name) AS last_name
                FROM %s d
                LEFT JOIN %s p ON p.id = d.person_id
                WHERE d.execution_id = ?
                  AND d.person_status IN ('STRONG','MANUAL_REVIEW','CONFLICT')
                  AND (p.orcid IS NULL OR TRIM(p.orcid) = '')
                ORDER BY d.id
                """.formatted(qualified("orcid_discovery_person"), qualified("pure_person"));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, executionId);
            try (ResultSet rs = statement.executeQuery()) {
                List<RecalcPerson> items = new ArrayList<>();
                while (rs.next()) {
                    RecalcPerson item = new RecalcPerson();
                    item.discoveryPersonId = rs.getLong("id");
                    item.personId = rs.getLong("person_id");
                    item.firstName = nullableString(rs, "first_name");
                    item.lastName = nullableString(rs, "last_name");
                    item.searchGiven = nullableString(rs, "search_given_name");
                    item.searchFamily = nullableString(rs, "search_family_name");
                    items.add(item);
                }
                return items;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list ORCID candidates for recalculation", ex);
        }
    }

    public void recountExecution(long executionId) {
        String sql = """
                UPDATE %s e
                JOIN (
                    SELECT execution_id,
                           SUM(person_status = 'STRONG') AS strong_match_count,
                           SUM(person_status = 'MANUAL_REVIEW') AS manual_review_count,
                           SUM(person_status = 'NO_CANDIDATE') AS no_candidate_count,
                           SUM(person_status = 'TOO_MANY_CANDIDATES') AS too_many_count,
                           SUM(person_status = 'CONFLICT') AS conflict_count,
                           SUM(person_status = 'ERROR') AS error_count,
                           SUM(candidate_count > 0 AND person_status <> 'SYNCED') AS people_with_candidates
                    FROM %s
                    WHERE execution_id = ?
                    GROUP BY execution_id
                ) s ON s.execution_id = e.id
                SET e.strong_match_count = s.strong_match_count,
                    e.manual_review_count = s.manual_review_count,
                    e.no_candidate_count = s.no_candidate_count,
                    e.too_many_count = s.too_many_count,
                    e.conflict_count = s.conflict_count,
                    e.error_count = s.error_count,
                    e.people_with_candidates = s.people_with_candidates
                WHERE e.id = ?
                """.formatted(qualified("orcid_discovery_execution"), qualified("orcid_discovery_person"));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, executionId);
            statement.setLong(2, executionId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to recount ORCID discovery execution", ex);
        }
    }

    public OrcidDiscoveryExecutionDto findLatest() {
        return findExecution("SELECT * FROM " + qualified("orcid_discovery_execution")
                + " ORDER BY id DESC LIMIT 1", null);
    }

    public OrcidDiscoveryExecutionDto findById(long id) {
        OrcidDiscoveryExecutionDto dto = findExecution(
                "SELECT * FROM " + qualified("orcid_discovery_execution") + " WHERE id = ?", id);
        if (dto == null) {
            throw new NotFoundException("ORCID_DISCOVERY_NOT_FOUND", "ORCID discovery execution was not found");
        }
        return dto;
    }

    public PageResponse<OrcidDiscoveryPersonDto> listPeople(
            long executionId,
            String status,
            String cargo,
            String search,
            String sort,
            String direction,
            int page,
            int size
    ) {
        StringBuilder where = new StringBuilder(" WHERE d.execution_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(executionId);
            if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
                if ("MANUAL_REVIEW".equalsIgnoreCase(status.trim())) {
                    where.append(" AND d.person_status IN ('MANUAL_REVIEW','TOO_MANY_CANDIDATES')");
                } else {
                    where.append(" AND d.person_status = ?");
                    params.add(status.trim());
                }
            } else {
                where.append(" AND d.person_status <> 'SYNCED'");
            }
        if (cargo != null && !cargo.isBlank()) {
            if ("SIN_CARGO".equals(cargo.trim())) {
                where.append(" AND (d.cargo IS NULL OR TRIM(d.cargo) = '')");
            } else {
                where.append(" AND d.cargo = ?");
                params.add(cargo.trim());
            }
        }
        if (search != null && !search.isBlank()) {
            String like = likeContains(search.trim());
            where.append(" AND (d.first_name LIKE ? ESCAPE '!' OR d.last_name LIKE ? ESCAPE '!' OR CAST(d.pure_id AS CHAR) = ?");
            params.add(like);
            params.add(like);
            params.add(search.trim());
            where.append(" OR EXISTS (SELECT 1 FROM ").append(qualified("orcid_candidate"))
                    .append(" c WHERE c.discovery_person_id = d.id AND c.candidate_orcid LIKE ? ESCAPE '!'))");
            params.add(like);
        }
        String order = switch (sort == null ? "" : sort) {
            case "pureId" -> "d.pure_id";
            case "cargo" -> "d.cargo";
            case "status" -> "d.person_status";
            case "doiMatchCount", "evidence" -> "d.doi_match_count";
            case "candidateCount" -> displayedCandidateCountSql();
            case "foundOrcid" -> "COALESCE("
                    + "(SELECT sc.candidate_orcid FROM " + qualified("orcid_candidate")
                    + " sc WHERE sc.id = d.selected_candidate_id),"
                    + "(SELECT c.candidate_orcid FROM " + qualified("orcid_candidate")
                    + " c WHERE c.discovery_person_id = d.id AND c.match_level = 'STRONG'"
                    + " AND c.already_used_person_id IS NULL"
                    + " AND (SELECT COUNT(*) FROM " + qualified("orcid_candidate")
                    + " c2 WHERE c2.discovery_person_id = d.id AND c2.match_level = 'STRONG'"
                    + " AND c2.already_used_person_id IS NULL) = 1"
                    + " LIMIT 1))";
            default -> "d.last_name";
        };
        boolean desc = "desc".equalsIgnoreCase(direction);
        String countSql = "SELECT COUNT(*) FROM " + qualified("orcid_discovery_person") + " d" + where;
        String sql = """
                SELECT d.*, p.orcid AS pure_orcid
                FROM %s d
                LEFT JOIN %s p ON p.id = d.person_id
                %s
                ORDER BY %s
                LIMIT ? OFFSET ?
                """.formatted(
                qualified("orcid_discovery_person"),
                qualified("pure_person"),
                where,
                orderByNullsLast(order, desc) + ", d.pure_id ASC, d.id ASC"
        );
        long total = scalarLong(countSql, params.toArray());
        params.add(size);
        params.add((long) page * size);
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params.toArray());
            try (ResultSet rs = statement.executeQuery()) {
                List<OrcidDiscoveryPersonDto> items = new ArrayList<>();
                while (rs.next()) {
                    items.add(mapPerson(rs, false));
                }
                attachSelectionMeta(items);
                return PageResponse.of(items, page, size, total);
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list ORCID discovery people", ex);
        }
    }

    public OrcidDiscoveryPersonDto findPerson(long executionId, long personId) {
        String sql = """
                SELECT d.*, p.orcid AS pure_orcid
                FROM %s d
                LEFT JOIN %s p ON p.id = d.person_id
                WHERE d.execution_id = ? AND d.person_id = ?
                """.formatted(qualified("orcid_discovery_person"), qualified("pure_person"));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, executionId);
            statement.setLong(2, personId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new NotFoundException("ORCID_DISCOVERY_PERSON_NOT_FOUND", "Discovery person was not found");
                }
                OrcidDiscoveryPersonDto dto = mapPerson(rs, true);
                attachSelectionMeta(List.of(dto));
                dto.candidates = listCandidates(dto.id);
                return dto;
            }
        } catch (NotFoundException ex) {
            throw ex;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load ORCID discovery person", ex);
        }
    }

    public List<OrcidCandidateDto> listCandidates(long discoveryPersonId) {
        String sql = "SELECT * FROM " + qualified("orcid_candidate")
                + " WHERE discovery_person_id = ? ORDER BY score DESC, id";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, discoveryPersonId);
            try (ResultSet rs = statement.executeQuery()) {
                List<OrcidCandidateDto> items = new ArrayList<>();
                while (rs.next()) {
                    OrcidCandidateDto dto = mapCandidate(rs);
                    dto.evidence = listEvidence(dto.id);
                    items.add(dto);
                }
                return items;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list ORCID candidates", ex);
        }
    }

    public void selectCandidate(
            long executionId,
            long personId,
            Long candidateId,
            Boolean queued,
            String origin,
            String selectedBy
    ) {
        boolean extra = columnExists("orcid_discovery_person", "selection_origin");
        boolean queuedColumn = columnExists("orcid_discovery_person", "apply_queued");
        StringBuilder sql = new StringBuilder("UPDATE ")
                .append(qualified("orcid_discovery_person"))
                .append(" SET selected_candidate_id = ?");
        if (queuedColumn) {
            sql.append(", apply_queued = ?");
        }
        if (extra) {
            sql.append(", selection_origin = ?, selected_by = ?, selected_at = ");
            sql.append(candidateId == null ? "NULL" : "NOW(6)");
        }
        sql.append(" WHERE execution_id = ? AND person_id = ?");
        boolean applyQueued = candidateId != null && Boolean.TRUE.equals(queued);
        if (candidateId == null) {
            applyQueued = false;
        } else if (queued == null && !queuedColumn) {
            applyQueued = true;
        }
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int index = 1;
            setLong(statement, index++, candidateId);
            if (queuedColumn) {
                statement.setBoolean(index++, applyQueued);
            }
            if (extra) {
                statement.setString(index++, candidateId == null ? null : origin);
                statement.setString(index++, candidateId == null ? null : selectedBy);
            }
            statement.setLong(index++, executionId);
            statement.setLong(index, personId);
            if (statement.executeUpdate() == 0) {
                throw new NotFoundException("ORCID_DISCOVERY_PERSON_NOT_FOUND", "Discovery person was not found");
            }
        } catch (NotFoundException ex) {
            throw ex;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to select ORCID candidate", ex);
        }
    }

    public void setApplyQueued(long executionId, long personId, boolean queued) {
        if (!columnExists("orcid_discovery_person", "apply_queued")) {
            if (!queued) {
                selectCandidate(executionId, personId, null, false, null, null);
            }
            return;
        }
        String sql = "UPDATE " + qualified("orcid_discovery_person")
                + " SET apply_queued = ? WHERE execution_id = ? AND person_id = ?"
                + " AND selected_candidate_id IS NOT NULL";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, queued);
            statement.setLong(2, executionId);
            statement.setLong(3, personId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to queue ORCID candidate", ex);
        }
    }

    public int selectStrong(long executionId) {
        boolean extra = columnExists("orcid_discovery_person", "selection_origin");
        boolean queuedColumn = columnExists("orcid_discovery_person", "apply_queued");
        StringBuilder extraSet = new StringBuilder();
        if (queuedColumn) {
            extraSet.append(", apply_queued = 1");
        }
        if (extra) {
            extraSet.append(", selection_origin = 'AUTO_STRONG', selected_at = NOW(6)");
        }
        String sql = """
                UPDATE %s d
                SET selected_candidate_id = (
                    SELECT c.id FROM %s c
                    WHERE c.discovery_person_id = d.id
                      AND c.match_level = 'STRONG'
                      AND c.already_used_person_id IS NULL
                    ORDER BY c.score DESC, c.id
                    LIMIT 1
                )%s
                WHERE d.execution_id = ?
                  AND d.person_status = 'STRONG'
                  AND (
                      SELECT COUNT(*) FROM %s c2
                      WHERE c2.discovery_person_id = d.id AND c2.match_level = 'STRONG'
                        AND c2.already_used_person_id IS NULL
                  ) = 1
                """.formatted(
                qualified("orcid_discovery_person"),
                qualified("orcid_candidate"),
                extraSet,
                qualified("orcid_candidate")
        );
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, executionId);
            return statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to select strong ORCID candidates", ex);
        }
    }

    public List<SelectedApply> listSelected(long executionId) {
        boolean queuedColumn = columnExists("orcid_discovery_person", "apply_queued");
        String queuedFilter = queuedColumn ? " AND d.apply_queued = 1" : "";
        String originSelect = columnExists("orcid_discovery_person", "selection_origin")
                ? ", d.selection_origin"
                : ", NULL AS selection_origin";
        String sql = """
                SELECT d.person_id, d.person_uuid, d.pure_id, d.first_name, d.last_name,
                       d.person_status, c.id AS candidate_id, c.candidate_orcid, c.match_level, c.score
                       %s
                FROM %s d
                JOIN %s c ON c.id = d.selected_candidate_id
                  AND c.discovery_person_id = d.id
                  AND c.person_id = d.person_id
                WHERE d.execution_id = ?
                  AND d.selected_candidate_id IS NOT NULL
                  AND d.person_status NOT IN ('SYNCED','SYNC_FAILED','REMOTE_CHANGED','ALREADY_USED')
                  %s
                ORDER BY d.last_name, d.first_name, d.id
                """.formatted(
                originSelect,
                qualified("orcid_discovery_person"),
                qualified("orcid_candidate"),
                queuedFilter
        );
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, executionId);
            try (ResultSet rs = statement.executeQuery()) {
                List<SelectedApply> items = new ArrayList<>();
                while (rs.next()) {
                    SelectedApply item = new SelectedApply();
                    item.personId = rs.getLong("person_id");
                    item.uuid = nullableString(rs, "person_uuid");
                    item.pureId = nullableLong(rs, "pure_id");
                    item.firstName = nullableString(rs, "first_name");
                    item.lastName = nullableString(rs, "last_name");
                    item.candidateId = rs.getLong("candidate_id");
                    item.orcid = rs.getString("candidate_orcid");
                    item.matchLevel = rs.getString("match_level");
                    item.score = rs.getInt("score");
                    item.personStatus = nullableString(rs, "person_status");
                    item.selectionOrigin = nullableString(rs, "selection_origin");
                    items.add(item);
                }
                return items;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list selected ORCID candidates", ex);
        }
    }

    public void markPersonStatus(long executionId, long personId, String status) {
        String sql = "UPDATE " + qualified("orcid_discovery_person")
                + " SET person_status = ?"
                + ("SYNCED".equals(status)
                ? ", selected_candidate_id = NULL"
                    + (columnExists("orcid_discovery_person", "apply_queued") ? ", apply_queued = 0" : "")
                : "")
                + " WHERE execution_id = ? AND person_id = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setLong(2, executionId);
            statement.setLong(3, personId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to update discovery person status", ex);
        }
    }

    private List<OrcidCandidateEvidenceDto> listEvidence(long candidateId) {
        if (!tableExists("orcid_candidate_evidence")) {
            return List.of();
        }
        String sql = "SELECT * FROM " + qualified("orcid_candidate_evidence") + " WHERE candidate_id = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, candidateId);
            try (ResultSet rs = statement.executeQuery()) {
                List<OrcidCandidateEvidenceDto> items = new ArrayList<>();
                while (rs.next()) {
                    OrcidCandidateEvidenceDto dto = new OrcidCandidateEvidenceDto();
                    dto.id = rs.getLong("id");
                    dto.researchOutputId = nullableLong(rs, "research_output_id");
                    dto.doi = nullableString(rs, "doi");
                    dto.localAuthor = nullableString(rs, "local_author");
                    dto.localPosition = nullableInt(rs, "local_position");
                    dto.orcidAuthor = nullableString(rs, "orcid_author");
                    dto.orcidPosition = nullableInt(rs, "orcid_position");
                    dto.nameMatch = nullableBoolean(rs, "name_match");
                    dto.positionMatch = nullableBoolean(rs, "position_match");
                    dto.note = nullableString(rs, "note");
                    dto.contributorFirstName = hasColumn(rs, "contributor_first_name")
                            ? nullableString(rs, "contributor_first_name") : null;
                    dto.contributorLastName = hasColumn(rs, "contributor_last_name")
                            ? nullableString(rs, "contributor_last_name") : null;
                    dto.personFirstName = hasColumn(rs, "person_first_name")
                            ? nullableString(rs, "person_first_name") : null;
                    dto.personLastName = hasColumn(rs, "person_last_name")
                            ? nullableString(rs, "person_last_name") : null;
                    dto.personFullName = hasColumn(rs, "person_full_name")
                            ? nullableString(rs, "person_full_name") : null;
                    dto.contributorFullName = hasColumn(rs, "contributor_full_name")
                            ? nullableString(rs, "contributor_full_name") : null;
                    dto.namePartitionDifferent = hasColumn(rs, "name_partition_different")
                            ? nullableBoolean(rs, "name_partition_different") : null;
                    dto.reasonCodes = OrcidCandidateMatcher.splitReasonCodes(
                            hasColumn(rs, "reason_codes") ? nullableString(rs, "reason_codes") : null
                    );
                    items.add(dto);
                }
                return items;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list ORCID evidence", ex);
        }
    }

    private OrcidDiscoveryExecutionDto findExecution(String sql, Long id) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (id != null) {
                statement.setLong(1, id);
            }
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return mapExecution(rs);
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load ORCID discovery execution", ex);
        }
    }

    private OrcidDiscoveryExecutionDto mapExecution(ResultSet rs) throws SQLException {
        OrcidDiscoveryExecutionDto dto = new OrcidDiscoveryExecutionDto();
        dto.id = rs.getLong("id");
        dto.status = rs.getString("status");
        dto.startedAt = timestamp(rs, "started_at");
        dto.finishedAt = timestamp(rs, "finished_at");
        dto.startedBy = nullableString(rs, "started_by");
        dto.totalPeople = rs.getInt("total_people");
        dto.processedPeople = rs.getInt("processed_people");
        dto.peopleWithCandidates = rs.getInt("people_with_candidates");
        dto.strongMatchCount = rs.getInt("strong_match_count");
        dto.manualReviewCount = rs.getInt("manual_review_count");
        dto.noCandidateCount = rs.getInt("no_candidate_count");
        dto.tooManyCount = rs.getInt("too_many_count");
        dto.conflictCount = rs.getInt("conflict_count");
        dto.errorCount = rs.getInt("error_count");
        return dto;
    }

    private OrcidDiscoveryPersonDto mapPerson(ResultSet rs, boolean detail) throws SQLException {
        OrcidDiscoveryPersonDto dto = new OrcidDiscoveryPersonDto();
        dto.id = rs.getLong("id");
        dto.personId = rs.getLong("person_id");
        dto.pureId = nullableLong(rs, "pure_id");
        dto.uuid = nullableString(rs, "person_uuid");
        dto.firstName = nullableString(rs, "first_name");
        dto.lastName = nullableString(rs, "last_name");
        dto.fullName = AuthorNameMatcher.displayName(dto.firstName, dto.lastName);
        dto.cargo = nullableString(rs, "cargo");
        dto.pureOrcid = nullableString(rs, "pure_orcid");
        dto.personStatus = rs.getString("person_status");
        dto.statusReason = statusReason(dto.personStatus);
        dto.candidateCount = rs.getInt("candidate_count");
        dto.searchGivenName = nullableString(rs, "search_given_name");
        dto.searchFamilyName = nullableString(rs, "search_family_name");
        dto.lastError = nullableString(rs, "last_error");
        dto.searchHitCount = parseSearchHitCount(dto.lastError, dto.candidateCount);
        dto.doiMatchCount = rs.getInt("doi_match_count");
        dto.selectedCandidateId = nullableLong(rs, "selected_candidate_id");
        dto.selectionOrigin = hasColumn(rs, "selection_origin") ? nullableString(rs, "selection_origin") : null;
        dto.selectedBy = hasColumn(rs, "selected_by") ? nullableString(rs, "selected_by") : null;
        dto.selectedAt = hasColumn(rs, "selected_at") ? timestamp(rs, "selected_at") : null;
        boolean queued = hasColumn(rs, "apply_queued")
                ? Boolean.TRUE.equals(nullableBoolean(rs, "apply_queued"))
                : dto.selectedCandidateId != null;
        dto.selected = queued && dto.selectedCandidateId != null;
        dto.selectable = false;
        if (detail) {
            BestCandidate best = bestCandidate(dto.id);
            dto.bestCandidateId = best == null ? null : best.id;
        }
        return dto;
    }

    private void attachSelectionMeta(List<OrcidDiscoveryPersonDto> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        Map<Long, OrcidDiscoveryPersonDto> byId = new LinkedHashMap<>();
        StringBuilder placeholders = new StringBuilder();
        List<Object> params = new ArrayList<>();
        for (OrcidDiscoveryPersonDto item : items) {
            if (item.id == null) {
                continue;
            }
            byId.put(item.id, item);
            if (!placeholders.isEmpty()) {
                placeholders.append(',');
            }
            placeholders.append('?');
            params.add(item.id);
        }
        if (byId.isEmpty()) {
            return;
        }
        String sql = """
                SELECT discovery_person_id,
                       SUM(CASE WHEN match_level = 'STRONG' AND already_used_person_id IS NULL THEN 1 ELSE 0 END) AS strong_valid,
                       MIN(CASE WHEN match_level = 'STRONG' AND already_used_person_id IS NULL THEN id END) AS strong_id,
                       MIN(CASE WHEN match_level = 'STRONG' AND already_used_person_id IS NULL THEN candidate_orcid END) AS strong_orcid
                FROM %s
                WHERE discovery_person_id IN (%s)
                GROUP BY discovery_person_id
                """.formatted(qualified("orcid_candidate"), placeholders);
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params.toArray());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    OrcidDiscoveryPersonDto dto = byId.get(rs.getLong("discovery_person_id"));
                    if (dto == null) {
                        continue;
                    }
                    int strongValid = rs.getInt("strong_valid");
                    if (strongValid == 1) {
                        dto.uniqueStrongCandidateId = nullableLong(rs, "strong_id");
                        if (dto.selectedOrcid == null && dto.selectedCandidateId == null) {
                            dto.foundOrcid = nullableString(rs, "strong_orcid");
                            dto.bestCandidateId = dto.uniqueStrongCandidateId;
                        }
                    }
                    boolean uniqueStrong = strongValid == 1 && dto.uniqueStrongCandidateId != null;
                    int count = dto.candidateCount == null ? 0 : dto.candidateCount;
                    dto.selectable = OrcidDiscoverySelection.checkboxEnabled(
                            dto.personStatus, dto.selectedCandidateId, uniqueStrong, count
                    );
                    dto.selectionDisabledReason = OrcidDiscoverySelection.disabledReason(
                            dto.personStatus, dto.selectedCandidateId, uniqueStrong, count
                    );
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load ORCID selection metadata", ex);
        }
        attachSelectedOrcids(byId, placeholders.toString(), params);
        for (OrcidDiscoveryPersonDto dto : byId.values()) {
            int count = dto.candidateCount == null ? 0 : dto.candidateCount;
            boolean uniqueStrong = dto.uniqueStrongCandidateId != null;
            dto.selectable = OrcidDiscoverySelection.checkboxEnabled(
                    dto.personStatus, dto.selectedCandidateId, uniqueStrong, count
            );
            dto.selectionDisabledReason = OrcidDiscoverySelection.disabledReason(
                    dto.personStatus, dto.selectedCandidateId, uniqueStrong, count
            );
            if (dto.selectedOrcid != null) {
                dto.foundOrcid = dto.selectedOrcid;
            }
        }
    }

    private void attachSelectedOrcids(
            Map<Long, OrcidDiscoveryPersonDto> byId,
            String placeholders,
            List<Object> params
    ) {
        String sql = """
                SELECT d.id AS discovery_person_id, c.id AS candidate_id, c.candidate_orcid
                FROM %s d
                JOIN %s c ON c.id = d.selected_candidate_id
                WHERE d.id IN (%s)
                """.formatted(
                qualified("orcid_discovery_person"),
                qualified("orcid_candidate"),
                placeholders
        );
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params.toArray());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    OrcidDiscoveryPersonDto dto = byId.get(rs.getLong("discovery_person_id"));
                    if (dto != null) {
                        dto.bestCandidateId = dto.bestCandidateId != null
                                ? dto.bestCandidateId
                                : rs.getLong("candidate_id");
                        dto.selectedOrcid = rs.getString("candidate_orcid");
                        dto.foundOrcid = dto.selectedOrcid;
                    }
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load selected ORCID values", ex);
        }
    }

    private static String statusReason(String personStatus) {
        return "TOO_MANY_CANDIDATES".equals(personStatus) ? "TOO_MANY_CANDIDATES" : null;
    }

    /**
     * Same number shown in the CANDIDATOS column: ORCID num-found when the person
     * exceeded the analysis cap, otherwise the persisted candidate_count.
     */
    private static String displayedCandidateCountSql() {
        return """
                CASE
                  WHEN d.person_status = 'TOO_MANY_CANDIDATES'
                       AND d.last_error IS NOT NULL
                       AND LOCATE('num-found=', d.last_error) > 0
                  THEN CAST(SUBSTRING(d.last_error, LOCATE('num-found=', d.last_error) + 10) AS UNSIGNED)
                  ELSE COALESCE(d.candidate_count, 0)
                END
                """;
    }

    private static Integer parseSearchHitCount(String lastError, Integer candidateCount) {
        if (lastError != null) {
            String prefix = "num-found=";
            int index = lastError.indexOf(prefix);
            if (index >= 0) {
                String raw = lastError.substring(index + prefix.length()).trim();
                int end = 0;
                while (end < raw.length() && Character.isDigit(raw.charAt(end))) {
                    end++;
                }
                if (end > 0) {
                    try {
                        return Integer.parseInt(raw.substring(0, end));
                    } catch (NumberFormatException ignored) {
                        // fall through to stored candidate_count
                    }
                }
            }
        }
        return candidateCount;
    }

    public List<String> listCargos(long executionId) {
        String sql = """
                SELECT DISTINCT cargo FROM %s
                WHERE execution_id = ? AND cargo IS NOT NULL AND TRIM(cargo) <> ''
                ORDER BY cargo
                """.formatted(qualified("orcid_discovery_person"));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, executionId);
            try (ResultSet rs = statement.executeQuery()) {
                List<String> items = new ArrayList<>();
                while (rs.next()) {
                    items.add(rs.getString(1));
                }
                return items;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list ORCID discovery cargos", ex);
        }
    }

    public List<OrcidDiscoveryExecutionDto> listHistory(int limit) {
        String sql = "SELECT * FROM " + qualified("orcid_discovery_execution")
                + " ORDER BY id DESC LIMIT ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Math.max(1, Math.min(limit, 50)));
            try (ResultSet rs = statement.executeQuery()) {
                List<OrcidDiscoveryExecutionDto> items = new ArrayList<>();
                while (rs.next()) {
                    items.add(mapExecution(rs));
                }
                return items;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list ORCID discovery history", ex);
        }
    }

    private BestCandidate bestCandidate(long discoveryPersonId) {
        String sql = "SELECT id, candidate_orcid FROM " + qualified("orcid_candidate")
                + " WHERE discovery_person_id = ? ORDER BY score DESC, id LIMIT 1";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, discoveryPersonId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new BestCandidate(rs.getLong(1), rs.getString(2));
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load best ORCID candidate", ex);
        }
    }

    private record BestCandidate(long id, String orcid) {
    }

    private OrcidCandidateDto mapCandidate(ResultSet rs) throws SQLException {
        OrcidCandidateDto dto = new OrcidCandidateDto();
        dto.id = rs.getLong("id");
        dto.orcid = rs.getString("candidate_orcid");
        dto.score = rs.getInt("score");
        dto.matchLevel = rs.getString("match_level");
        dto.status = rs.getString("status");
        dto.nameMatch = nullableBoolean(rs, "name_match");
        dto.doiMatchCount = rs.getInt("doi_match_count");
        dto.authorMatch = nullableBoolean(rs, "author_match");
        dto.positionMatch = nullableBoolean(rs, "position_match");
        dto.alreadyUsedPersonId = nullableLong(rs, "already_used_person_id");
        return dto;
    }

    private static String timestamp(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        if (value == null) {
            return null;
        }
        return OffsetDateTime.ofInstant(value.toInstant(), ZoneOffset.UTC).toString();
    }

    private static void setBoolean(PreparedStatement statement, int index, Boolean value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BOOLEAN);
        } else {
            statement.setBoolean(index, value);
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

    private static boolean hasColumn(ResultSet rs, String column) {
        try {
            var meta = rs.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                if (column.equalsIgnoreCase(meta.getColumnLabel(i))) {
                    return true;
                }
            }
            return false;
        } catch (SQLException ex) {
            return false;
        }
    }

    public static final class PersonSeed {
        public long personId;
        public String uuid;
        public Long pureId;
        public String firstName;
        public String lastName;
        public String cargo;
        public String orcid;
    }

    public static final class LocalPublication {
        public long personId;
        public long researchOutputId;
        public String doi;
        public String author;
        public String contributorFirstName;
        public String contributorLastName;
        public Integer position;
    }

    public static final class CandidateWrite {
        public long executionId;
        public long personId;
        public long discoveryPersonId;
        public String orcid;
        public String searchGiven;
        public String searchFamily;
        public int score;
        public String matchLevel;
        public Boolean nameMatch;
        public int doiMatchCount;
        public Boolean authorMatch;
        public Boolean positionMatch;
        public Long alreadyUsedPersonId;
        public String status;
    }

    public static final class EvidenceWrite {
        public Long researchOutputId;
        public String doi;
        public String localAuthor;
        public Integer localPosition;
        public String orcidAuthor;
        public Integer orcidPosition;
        public Boolean nameMatch;
        public Boolean positionMatch;
        public String note;
        public String personFullName;
        public String contributorFullName;
        public String personFirstName;
        public String personLastName;
        public String contributorFirstName;
        public String contributorLastName;
        public Boolean namePartitionDifferent;
        public String reasonCodes;
    }

    public static final class RecalcPerson {
        public long discoveryPersonId;
        public long personId;
        public String firstName;
        public String lastName;
        public String searchGiven;
        public String searchFamily;
    }

    public static final class SelectedApply {
        public long personId;
        public String uuid;
        public Long pureId;
        public String firstName;
        public String lastName;
        public long candidateId;
        public String orcid;
        public String matchLevel;
        public int score;
        public String personStatus;
        public String selectionOrigin;
    }
}
