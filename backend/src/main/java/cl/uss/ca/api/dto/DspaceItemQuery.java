package cl.uss.ca.api.dto;

import cl.uss.ca.domain.DspaceListSupport;

public class DspaceItemQuery {
    public String search;
    /** FOUND (default), MISSING, ERROR, ALL */
    public String outcome;
    /** open.access, metadata.only, restricted, embargo */
    public String accessStatus;
    /** LINKED, UNMATCHED, REVIEW, or a raw match_status */
    public String matchStatus;
    public String matchMethod;
    public String collectionUuid;
    public String communityUuid;
    /** exact normalized DOI */
    public String doi;
    public Boolean sharedDoi;
    /** YES / NO */
    public String hasFile;
    /** CONSISTENT, DIFFERENCE, NOT_EVALUABLE */
    public String accessComparison;
    public java.util.List<String> uuids;
    public String sort = "syncedAt";
    public String direction = "desc";
    public int page = 1;
    public int pageSize = 10;

    /**
     * Extra filters beyond the default current-item universe ({@code last_outcome = FOUND}).
     * Pagination and sort are not filters.
     */
    public boolean hasActiveFilters() {
        if (filled(search)
                || filled(accessStatus)
                || filled(matchStatus)
                || filled(matchMethod)
                || filled(collectionUuid)
                || filled(communityUuid)
                || filled(doi)
                || Boolean.TRUE.equals(sharedDoi)
                || DspaceListSupport.hasFileFilter(hasFile) != null
                || DspaceListSupport.accessComparisonFilter(accessComparison) != null) {
            return true;
        }
        if (uuids != null && !uuids.isEmpty()) {
            return true;
        }
        return !"FOUND".equals(DspaceListSupport.outcomeFilter(outcome));
    }

    private static boolean filled(String value) {
        return value != null && !value.isBlank();
    }
}
