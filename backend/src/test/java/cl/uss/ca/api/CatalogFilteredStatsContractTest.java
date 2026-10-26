package cl.uss.ca.api;

import jakarta.ws.rs.QueryParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * List and summary share the same FilterSpec per catalog module. Not executed by the agent.
 */
public final class CatalogFilteredStatsContractTest {
    private static final Set<String> PAGINATION = Set.of("sort", "direction", "page", "size");
    private static final Set<String> PERSON_FILTERS = Set.of(
            "search",
            "cargo",
            "population",
            "orcidStatus",
            "hasOrcid",
            "scopusStatus",
            "hasScopus",
            "scopusCount",
            "minDistinctScopusCount",
            "pureStatus"
    );
    private static final Set<String> OUTPUT_FILTERS = Set.of(
            "search",
            "type",
            "year",
            "hasDoi",
            "doiStatus",
            "workflow",
            "pureStatus"
    );
    private static final Set<String> JOURNAL_FILTERS = Set.of(
            "search",
            "typeUri",
            "workflowStep",
            "issnCount",
            "publications"
    );
    private static final Set<String> PROJECT_FILTERS = Set.of(
            "search",
            "typeUri",
            "workflowStep",
            "visibilityKey",
            "presence",
            "startYear"
    );
    private static final Set<String> ORGANIZATION_FILTERS = Set.of(
            "search",
            "typeUri",
            "presence",
            "hasParent"
    );

    private CatalogFilteredStatsContractTest() {
    }

    public static void main(String[] args) throws Exception {
        peopleStatsSharesListFilters();
        outputStatsSharesListFilters();
        journalStatsSharesListFilters();
        projectStatsSharesListFilters();
        organizationStatsSharesListFilters();
        statsEndpointsIgnorePagination();
        System.out.println("CatalogFilteredStatsContractTest OK");
    }

    static void peopleStatsSharesListFilters() throws Exception {
        assertFiltersShared(PersonResource.class, "list", "stats", PERSON_FILTERS);
    }

    static void outputStatsSharesListFilters() throws Exception {
        assertFiltersShared(ResearchOutputResource.class, "list", "stats", OUTPUT_FILTERS);
    }

    static void journalStatsSharesListFilters() throws Exception {
        assertFiltersShared(JournalResource.class, "list", "stats", JOURNAL_FILTERS);
    }

    static void projectStatsSharesListFilters() throws Exception {
        assertFiltersShared(ProjectResource.class, "list", "stats", PROJECT_FILTERS);
        assertFiltersShared(ProjectResource.class, "list", "summary", PROJECT_FILTERS);
    }

    static void organizationStatsSharesListFilters() throws Exception {
        assertFiltersShared(OrganizationResource.class, "list", "stats", ORGANIZATION_FILTERS);
    }

    static void statsEndpointsIgnorePagination() throws Exception {
        assertStatsOmitsPagination(PersonResource.class, "list", "stats");
        assertStatsOmitsPagination(ResearchOutputResource.class, "list", "stats");
        assertStatsOmitsPagination(JournalResource.class, "list", "stats");
        assertStatsOmitsPagination(ProjectResource.class, "list", "stats");
        assertStatsOmitsPagination(ProjectResource.class, "list", "summary");
        assertStatsOmitsPagination(OrganizationResource.class, "list", "stats");
    }

    private static void assertFiltersShared(
            Class<?> type,
            String listMethod,
            String statsMethod,
            Set<String> filters
    ) {
        Set<String> list = queryParams(type, listMethod);
        Set<String> stats = queryParams(type, statsMethod);
        for (String name : filters) {
            assert list.contains(name) : type.getSimpleName() + " list missing filter " + name;
            assert stats.contains(name) : type.getSimpleName() + " stats missing filter " + name;
        }
    }

    private static void assertStatsOmitsPagination(Class<?> type, String listMethod, String statsMethod) {
        Set<String> stats = queryParams(type, statsMethod);
        Set<String> list = queryParams(type, listMethod);
        for (String name : PAGINATION) {
            assert !stats.contains(name) : type.getSimpleName() + " stats must ignore pagination param " + name;
            assert list.contains(name) : type.getSimpleName() + " list missing pagination param " + name;
        }
    }

    private static Set<String> queryParams(Class<?> type, String methodName) {
        Method match = null;
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                match = method;
                break;
            }
        }
        assert match != null : "missing method " + type.getSimpleName() + "#" + methodName;
        Set<String> names = new LinkedHashSet<>();
        for (Parameter parameter : match.getParameters()) {
            QueryParam queryParam = parameter.getAnnotation(QueryParam.class);
            if (queryParam != null) {
                names.add(queryParam.value());
            }
        }
        return names;
    }
}
