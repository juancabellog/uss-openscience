package cl.uss.ca.quality.consistency;

import cl.uss.ca.repository.DataQualityRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class UnpaywallEvidenceProvider implements ExternalEvidenceProvider {
    public static final String SOURCE = "UNPAYWALL";

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
                ConsistencyField.LICENSE
        );
    }

    @Override
    public boolean available() {
        return EvidenceSql.table(repository, "unpaywall_work");
    }

    @Override
    public Map<String, SourceSnapshot> loadByDois(List<String> normalizedDois) {
        Map<String, SourceSnapshot> byDoi = new LinkedHashMap<>();
        if (!available()) {
            return byDoi;
        }
        for (List<String> chunk : EvidenceSql.chunks(normalizedDois)) {
            String sql = """
                    SELECT id, normalized_doi, title, year, publisher, journal_name, journal_issns, journal_issn_l,
                           is_oa, oa_status, best_oa_license, retrieved_at
                    FROM %s
                    WHERE last_outcome = 'FOUND' AND normalized_doi IN (%s)
                    """.formatted(repository.qualifiedPublic("unpaywall_work"), EvidenceSql.placeholders(chunk.size()));
            repository.select(sql, rs -> {
                SourceSnapshot snapshot = new SourceSnapshot();
                snapshot.source = SOURCE;
                snapshot.sourceRecordId = String.valueOf(rs.getLong("id"));
                snapshot.normalizedDoi = EvidenceSql.str(rs, "normalized_doi");
                snapshot.lastSyncAt = EvidenceSql.offset(rs, "retrieved_at");
                snapshot.title = EvidenceSql.str(rs, "title");
                snapshot.publicationYear = EvidenceSql.integer(rs, "year");
                snapshot.journalTitle = EvidenceSql.str(rs, "journal_name");
                snapshot.publisher = EvidenceSql.str(rs, "publisher");
                addIssn(snapshot, EvidenceSql.str(rs, "journal_issn_l"));
                String csv = EvidenceSql.str(rs, "journal_issns");
                if (csv != null) {
                    for (String part : csv.split("[,;]")) {
                        addIssn(snapshot, part);
                    }
                }
                Boolean oa = EvidenceSql.bool(rs, "is_oa");
                snapshot.accessRaw = EvidenceSql.str(rs, "oa_status");
                snapshot.access = AccessMapper.fromOa(oa, snapshot.accessRaw);
                snapshot.licenseRaw = EvidenceSql.str(rs, "best_oa_license");
                snapshot.licenseNormalized = LicenseNormalizer.normalize(snapshot.licenseRaw);
                byDoi.put(snapshot.normalizedDoi, snapshot);
                return snapshot;
            }, chunk.toArray());
        }
        return byDoi;
    }

    private static void addIssn(SourceSnapshot snapshot, String issn) {
        if (issn == null || issn.isBlank()) {
            return;
        }
        String trimmed = issn.trim();
        if (!snapshot.issns.contains(trimmed)) {
            snapshot.issns.add(trimmed);
        }
    }
}
