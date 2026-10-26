package cl.uss.ca.domain;

/**
 * DSpace management list filters and match badges. Not executed by the agent.
 */
public final class DspaceListSupportTest {
    private DspaceListSupportTest() {
    }

    public static void main(String[] args) {
        defaultOutcomeIsFound();
        outcomeAllAndMissing();
        itemOrderColumns();
        matchBadgeDoesNotTreatCandidateAsLinked();
        secondaryDoiStaysLinked();
        unmatchedWhenRejectedOrAbsent();
        hasFileFilter();
        System.out.println("DspaceListSupportTest OK");
    }

    static void defaultOutcomeIsFound() {
        assertEquals("FOUND", DspaceListSupport.outcomeFilter(null));
        assertEquals("FOUND", DspaceListSupport.outcomeFilter(""));
        assertEquals("FOUND", DspaceListSupport.outcomeFilter("FOUND"));
        assertEquals("FOUND", DspaceListSupport.outcomeFilter("unknown"));
    }

    static void outcomeAllAndMissing() {
        assertEquals("ALL", DspaceListSupport.outcomeFilter("ALL"));
        assertEquals("MISSING", DspaceListSupport.outcomeFilter("MISSING"));
        assertEquals("ERROR", DspaceListSupport.outcomeFilter("ERROR"));
    }

    static void itemOrderColumns() {
        String titleAsc = DspaceListSupport.itemOrder("title", "asc");
        assert titleAsc.contains("NULLIF(TRIM(i.name), '')");
        assert titleAsc.contains("IS NULL");
        assert titleAsc.contains("ASC");
        String doiDesc = DspaceListSupport.itemOrder("doi", "desc");
        assert doiDesc.contains("i.normalized_doi");
        assert doiDesc.contains("IS NULL");
        assert doiDesc.contains("DESC");
        assert DspaceListSupport.itemOrder("handle", "asc").contains("i.handle");
        assert DspaceListSupport.itemOrder("collection", "asc").contains("col.name");
        assert DspaceListSupport.itemOrder("community", "asc").contains("com.name");
        assert DspaceListSupport.itemOrder("pureTitle", "asc").contains("o.title");
        assert DspaceListSupport.itemOrder("method", "asc").contains("m.match_method");
        String access = DspaceListSupport.itemOrder("access", "asc");
        assert access.contains("open.access");
        assert access.contains("metadata.only");
        assert access.contains("restricted");
        assert access.contains("embargo");
        String pure = DspaceListSupport.itemOrder("pure", "asc");
        assert pure.contains("CONFIRMED");
        assert pure.contains("SECONDARY");
        assert pure.contains("CANDIDATE");
        String status = DspaceListSupport.itemOrder("status", "asc");
        assert status.contains("FOUND");
        assert status.contains("MISSING");
        String pureAccess = DspaceListSupport.itemOrder("pureAccess", "asc");
        assert pureAccess.contains("pa.pure_canonical");
        assert pureAccess.contains("IS NULL");
        String comparison = DspaceListSupport.itemOrder("comparison", "asc");
        assert comparison.contains("CONSISTENT") || comparison.contains("THEN 0");
        assert comparison.contains("pa.pure_canonical");
        assert DspaceListSupport.itemOrder("file", "desc").contains("original_file_count");
        assert DspaceListSupport.itemOrder(null, null).contains("i.last_sync_at");
        assert DspaceListSupport.itemOrder("syncedAt", "desc").contains("i.last_sync_at");
        assert DspaceListSupport.itemOrder("injected; DROP TABLE", "asc").contains("i.last_sync_at");
        assertEquals("CONSISTENT", DspaceListSupport.accessComparisonFilter("consistent"));
        assertEquals("NOT_EVALUABLE", DspaceListSupport.accessComparisonFilter("NOT_EVALUABLE"));
        assert DspaceListSupport.accessComparisonFilter("conflict") == null;
        assert DspaceListSupport.collectionOrder("name", "asc").startsWith("c.name ASC");
        assert DspaceListSupport.communityOrder("items", "desc").startsWith("item_count DESC");
    }

    static void matchBadgeDoesNotTreatCandidateAsLinked() {
        assertEquals("REVIEW", DspaceListSupport.matchBadge("CANDIDATE"));
        assertEquals("LINKED", DspaceListSupport.matchBadge("CONFIRMED"));
    }

    static void secondaryDoiStaysLinked() {
        assertEquals("LINKED", DspaceListSupport.matchBadge("SECONDARY"));
    }

    static void unmatchedWhenRejectedOrAbsent() {
        assertEquals("UNMATCHED", DspaceListSupport.matchBadge(null));
        assertEquals("UNMATCHED", DspaceListSupport.matchBadge(""));
        assertEquals("UNMATCHED", DspaceListSupport.matchBadge("REJECTED"));
    }

    static void hasFileFilter() {
        assertEquals("YES", DspaceListSupport.hasFileFilter("yes"));
        assertEquals("NO", DspaceListSupport.hasFileFilter("false"));
        assert DspaceListSupport.hasFileFilter(null) == null;
        assert DspaceListSupport.hasFileFilter("maybe") == null;
    }

    private static void assertEquals(String expected, String actual) {
        assert expected.equals(actual) : expected + " != " + actual;
    }
}
