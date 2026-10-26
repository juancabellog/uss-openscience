package cl.uss.ca.api;

import cl.uss.ca.api.dto.DspaceItemQuery;
import cl.uss.ca.domain.DspaceListSupport;
import jakarta.ws.rs.QueryParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * List and summary share {@link DspaceItemQuery} / itemFilter. Not executed by the agent.
 */
public final class DspaceFilteredStatsContractTest {
    private static final Set<String> ITEM_FILTERS = Set.of(
            "search",
            "outcome",
            "accessStatus",
            "matchStatus",
            "matchMethod",
            "collectionUuid",
            "communityUuid",
            "doi",
            "sharedDoi",
            "hasFile",
            "accessComparison",
            "uuid"
    );
    private static final Set<String> PAGINATION = Set.of("sort", "direction", "page", "size");

    private DspaceFilteredStatsContractTest() {
    }

    public static void main(String[] args) throws Exception {
        defaultSummaryUniverseIsFound();
        statsEndpointSharesItemFilterParams();
        statsEndpointIgnoresPaginationParams();
        activeFiltersCoverItemDimensions();
        defaultFoundIsNotAnActiveFilter();
        System.out.println("DspaceFilteredStatsContractTest OK");
    }

    static void defaultSummaryUniverseIsFound() {
        assertEquals("FOUND", DspaceListSupport.outcomeFilter(null));
        assertEquals("FOUND", DspaceListSupport.outcomeFilter(new DspaceItemQuery().outcome));
    }

    static void statsEndpointSharesItemFilterParams() throws Exception {
        Set<String> items = queryParams("items");
        Set<String> stats = queryParams("stats");
        for (String name : ITEM_FILTERS) {
            assert items.contains(name) : "list missing filter " + name;
            assert stats.contains(name) : "stats missing filter " + name;
        }
    }

    static void statsEndpointIgnoresPaginationParams() throws Exception {
        Set<String> stats = queryParams("stats");
        for (String name : PAGINATION) {
            assert !stats.contains(name) : "stats must ignore pagination param " + name;
        }
        Set<String> items = queryParams("items");
        for (String name : PAGINATION) {
            assert items.contains(name) : "list missing pagination param " + name;
        }
    }

    static void activeFiltersCoverItemDimensions() {
        assert newQuery(q -> q.accessStatus = "open.access").hasActiveFilters();
        assert newQuery(q -> q.communityUuid = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee").hasActiveFilters();
        assert newQuery(q -> q.collectionUuid = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee").hasActiveFilters();
        assert newQuery(q -> q.matchStatus = "LINKED").hasActiveFilters();
        assert newQuery(q -> q.hasFile = "YES").hasActiveFilters();
        assert newQuery(q -> q.doi = "10.1234/x").hasActiveFilters();
        assert newQuery(q -> q.search = "tesis").hasActiveFilters();
        assert newQuery(q -> q.sharedDoi = true).hasActiveFilters();
        assert newQuery(q -> q.outcome = "ERROR").hasActiveFilters();
        assert newQuery(q -> q.accessComparison = "DIFFERENCE").hasActiveFilters();
        assert newQuery(q -> q.matchMethod = "DOI").hasActiveFilters();
        DspaceItemQuery combined = new DspaceItemQuery();
        combined.accessStatus = "open.access";
        combined.matchStatus = "LINKED";
        combined.doi = "10.1234/x";
        assert combined.hasActiveFilters();
    }

    static void defaultFoundIsNotAnActiveFilter() {
        DspaceItemQuery query = new DspaceItemQuery();
        query.outcome = "FOUND";
        query.page = 9;
        query.pageSize = 50;
        query.sort = "title";
        assert !query.hasActiveFilters();
        assertEquals("FOUND", DspaceListSupport.outcomeFilter(query.outcome));
    }

    private static DspaceItemQuery newQuery(java.util.function.Consumer<DspaceItemQuery> mutate) {
        DspaceItemQuery query = new DspaceItemQuery();
        mutate.accept(query);
        return query;
    }

    private static Set<String> queryParams(String methodName) {
        Method match = null;
        for (Method method : DspaceResource.class.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                match = method;
                break;
            }
        }
        assert match != null : "missing method " + methodName;
        Set<String> names = new LinkedHashSet<>();
        for (Parameter parameter : match.getParameters()) {
            QueryParam queryParam = parameter.getAnnotation(QueryParam.class);
            if (queryParam != null) {
                names.add(queryParam.value());
            }
        }
        return names;
    }

    private static void assertEquals(String expected, String actual) {
        assert expected.equals(actual) : expected + " != " + actual;
    }
}
