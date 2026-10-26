package cl.uss.ca.quality.consistency;

import cl.uss.ca.repository.DataQualityRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class DspaceEvidenceProvider implements ExternalEvidenceProvider {
    public static final String SOURCE = "DSPACE";

    @Inject
    DataQualityRepository repository;

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public Set<ConsistencyField> capabilities() {
        return EnumSet.of(
                ConsistencyField.TITLE,
                ConsistencyField.PUBLICATION_YEAR,
                ConsistencyField.JOURNAL_TITLE,
                ConsistencyField.ISSN,
                ConsistencyField.PUBLISHER,
                ConsistencyField.ACCESS_TYPE,
                ConsistencyField.LICENSE,
                ConsistencyField.AUTHORS
        );
    }

    @Override
    public boolean available() {
        return EvidenceSql.table(repository, "dspace_item");
    }

    @Override
    public Map<String, SourceSnapshot> loadByDois(List<String> normalizedDois) {
        Map<String, SourceSnapshot> byDoi = new LinkedHashMap<>();
        if (!available()) {
            return byDoi;
        }
        Map<String, SourceSnapshot> byUuid = new LinkedHashMap<>();
        for (List<String> chunk : EvidenceSql.chunks(normalizedDois)) {
            String sql = """
                    SELECT uuid, normalized_doi, name, dc_type, access_status, fulltext_availability, last_sync_at
                    FROM %s
                    WHERE last_outcome = 'FOUND' AND normalized_doi IN (%s)
                    """.formatted(repository.qualifiedPublic("dspace_item"), EvidenceSql.placeholders(chunk.size()));
            repository.select(sql, rs -> {
                SourceSnapshot snapshot = new SourceSnapshot();
                snapshot.source = SOURCE;
                snapshot.sourceRecordId = EvidenceSql.str(rs, "uuid");
                snapshot.normalizedDoi = EvidenceSql.str(rs, "normalized_doi");
                snapshot.lastSyncAt = EvidenceSql.offset(rs, "last_sync_at");
                snapshot.title = EvidenceSql.str(rs, "name");
                snapshot.accessRaw = EvidenceSql.str(rs, "access_status");
                snapshot.access = AccessMapper.fromDspace(
                        snapshot.accessRaw,
                        EvidenceSql.str(rs, "fulltext_availability")
                );
                if (snapshot.normalizedDoi != null) {
                    SourceSnapshot previous = byDoi.get(snapshot.normalizedDoi);
                    if (previous == null || prefer(snapshot, previous)) {
                        byDoi.put(snapshot.normalizedDoi, snapshot);
                    }
                }
                if (snapshot.sourceRecordId != null) {
                    byUuid.put(snapshot.sourceRecordId, snapshot);
                }
                return snapshot;
            }, chunk.toArray());
        }
        loadMetadata(byUuid);
        return byDoi;
    }

    private void loadMetadata(Map<String, SourceSnapshot> byUuid) {
        if (byUuid.isEmpty() || !EvidenceSql.table(repository, "dspace_item_metadata")) {
            return;
        }
        for (List<String> chunk : EvidenceSql.chunks(byUuid.keySet())) {
            String sql = """
                    SELECT item_uuid, field, place, value
                    FROM %s
                    WHERE item_uuid IN (%s)
                    ORDER BY place
                    """.formatted(repository.qualifiedPublic("dspace_item_metadata"), EvidenceSql.placeholders(chunk.size()));
            repository.select(sql, rs -> {
                SourceSnapshot snapshot = byUuid.get(EvidenceSql.str(rs, "item_uuid"));
                if (snapshot == null) {
                    return null;
                }
                String field = EvidenceSql.str(rs, "field");
                String value = EvidenceSql.str(rs, "value");
                if (field == null || value == null) {
                    return value;
                }
                switch (field) {
                    case "dc.title" -> {
                        if (snapshot.title == null) {
                            snapshot.title = value;
                        }
                    }
                    case "dc.date.issued", "dc.date" -> {
                        if (snapshot.publicationYear == null) {
                            snapshot.publicationYear = PublishedYearExtractor.fromOne(value);
                        }
                    }
                    case "dc.source" -> {
                        if (snapshot.journalTitle == null) {
                            snapshot.journalTitle = value;
                        }
                    }
                    case "dc.identifier.issn" -> snapshot.issns.add(value);
                    case "dc.publisher" -> {
                        if (snapshot.publisher == null) {
                            snapshot.publisher = value;
                        }
                    }
                    case "dc.rights", "dc.rights.uri" -> {
                        if (snapshot.licenseRaw == null) {
                            snapshot.licenseRaw = value;
                        }
                        snapshot.licenseNormalized = LicenseNormalizer.normalize(
                                snapshot.licenseRaw == null ? value : snapshot.licenseRaw + " " + value
                        );
                    }
                    case "dc.contributor.author" -> snapshot.authors.add(
                            EvidenceSql.author(value, null, null, null, EvidenceSql.integer(rs, "place"))
                    );
                    default -> {
                    }
                }
                return value;
            }, chunk.toArray());
        }
    }

    private static boolean prefer(SourceSnapshot incoming, SourceSnapshot previous) {
        return rank(incoming) > rank(previous);
    }

    private static int rank(SourceSnapshot snapshot) {
        if (snapshot.access == AccessSemantic.OPEN) {
            return 2;
        }
        if (snapshot.access == AccessSemantic.CLOSED) {
            return 1;
        }
        return 0;
    }
}
