package cl.uss.ca.domain;

import cl.uss.ca.domain.OpenAlexWorkIdentity.Decision;
import cl.uss.ca.domain.OpenAlexWorkIdentity.Snapshot;
import cl.uss.ca.external.openalex.OpenAlexWorkRecord;
import cl.uss.ca.quality.DataQualityIdentifiers;
import cl.uss.ca.repository.OpenAlexDoiLink;
import cl.uss.ca.service.OpenAlexSyncJob;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * OpenAlex work identity, invalid DOI skip, duplicate-key recovery. Not executed by the agent.
 */
public final class OpenAlexWorkIdentityTest {
    private OpenAlexWorkIdentityTest() {
    }

    public static void main(String[] args) {
        twoLookupDoisReuseOneWork();
        duplicateKeyIsSpecificIndex();
        otherConstraintIsNotSwallowed();
        repeatedFullIsReuse();
        commercialUrlIsNotFetchable();
        validDoiIsFetchable();
        canonicalDoiIsOpenAlexNotLookup();
        doiOaIdConflictIsNotOverwrite();
        System.out.println("OpenAlexWorkIdentityTest OK");
    }

    static void twoLookupDoisReuseOneWork() {
        Snapshot existing = Snapshot.of(10L, "W2912581704", "10.1038/s41598-018-36861-8");
        Decision second = OpenAlexWorkIdentity.decide(existing, null, null, "W2912581704");
        assert Decision.Action.REUSE.equals(second.action);
        assert second.existing.id == 10L;
        Decision insertFirst = OpenAlexWorkIdentity.decide(null, null, null, "W2912581704");
        assert Decision.Action.INSERT.equals(insertFirst.action);
    }

    static void duplicateKeyIsSpecificIndex() {
        SQLException duplicate = new SQLException(
                "Duplicate entry 'W2912581704' for key 'openalex_work.uq_openalex_work_oa_id'",
                "23000",
                1062
        );
        assert MysqlDuplicateKey.of(duplicate, MysqlDuplicateKey.OPENALEX_WORK_OA_ID);
        assert !MysqlDuplicateKey.of(duplicate, MysqlDuplicateKey.OPENALEX_WORK_DOI);
        SQLException wrapped = new SQLException("Failed to reconcile OpenAlex work", duplicate);
        assert MysqlDuplicateKey.of(wrapped, MysqlDuplicateKey.OPENALEX_WORK_OA_ID);
    }

    static void otherConstraintIsNotSwallowed() {
        SQLException fk = new SQLException("Cannot add or update a child row", "23000", 1452);
        assert !MysqlDuplicateKey.of(fk, MysqlDuplicateKey.OPENALEX_WORK_OA_ID);
        SQLException otherUnique = new SQLException(
                "Duplicate entry '1-2' for key 'openalex_person_match_work.uq_openalex_match_work'",
                "23000",
                1062
        );
        assert !MysqlDuplicateKey.of(otherUnique, MysqlDuplicateKey.OPENALEX_WORK_OA_ID);
    }

    static void repeatedFullIsReuse() {
        Snapshot existing = Snapshot.of(10L, "W2912581704", "10.1038/s41598-018-36861-8");
        Decision again = OpenAlexWorkIdentity.decide(existing, existing, existing, "W2912581704");
        assert Decision.Action.REUSE.equals(again.action);
    }

    static void commercialUrlIsNotFetchable() {
        OpenAlexDoiLink link = new OpenAlexDoiLink();
        link.doi = "https://tienda.thomsonreuters.cl/desafios-actuales-del-derecho-privado-en-homenaje";
        link.researchOutputId = 1;
        Map<String, List<OpenAlexDoiLink>> unique = OpenAlexSyncJob.uniqueDois(List.of(link));
        assert unique.size() == 1;
        assert OpenAlexSyncJob.fetchableDois(unique).isEmpty();
        assert !DataQualityIdentifiers.isValidDoi(unique.keySet().iterator().next());
    }

    static void validDoiIsFetchable() {
        OpenAlexDoiLink link = new OpenAlexDoiLink();
        link.doi = "https://doi.org/10.1038/s41598-018-36861-8";
        link.researchOutputId = 1;
        Map<String, List<OpenAlexDoiLink>> unique = OpenAlexSyncJob.uniqueDois(List.of(link));
        assert OpenAlexSyncJob.fetchableDois(unique).size() == 1;
        assert DataQualityIdentifiers.isValidDoi("10.1038/s41598-018-36861-8");
    }

    static void canonicalDoiIsOpenAlexNotLookup() {
        OpenAlexWorkRecord work = new OpenAlexWorkRecord();
        work.doi = "https://doi.org/10.1038/s41598-018-36861-8";
        assertEquals(
                "10.1038/s41598-018-36861-8",
                OpenAlexWorkIdentity.canonicalNormalizedDoi("10.1101/350330", work)
        );
    }

    static void doiOaIdConflictIsNotOverwrite() {
        Snapshot stored = Snapshot.of(7L, "W111", "10.1038/example");
        Decision decision = OpenAlexWorkIdentity.decide(null, stored, stored, "W222");
        assert Decision.Action.CONFLICT.equals(decision.action);
        assert decision.existing.id == 7L;
    }

    private static void assertEquals(String expected, String actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }
}
