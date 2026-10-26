import { peopleFilterParams } from '../../core/services/api/person-api.service';
import { PEOPLE_HISTORY_ENTITIES, peopleHasActiveFilters, peopleStatsQuery } from './people-list.component';

describe('People list filtered summary helpers', () => {
  it('treats the default directory as the current universe, not an extra filter', () => {
    expect(peopleHasActiveFilters({ population: 'ALL', orcidStatus: 'ALL', scopusStatus: 'ALL' })).toBe(false);
    expect(peopleHasActiveFilters({})).toBe(false);
  });

  it('detects cargo, ORCID, Scopus, population and text filters as active', () => {
    expect(peopleHasActiveFilters({ cargo: 'Profesor' })).toBe(true);
    expect(peopleHasActiveFilters({ orcidStatus: 'WITH_ORCID' })).toBe(true);
    expect(peopleHasActiveFilters({ scopusStatus: 'WITHOUT_SCOPUS' })).toBe(true);
    expect(peopleHasActiveFilters({ scopusCount: 2 })).toBe(true);
    expect(peopleHasActiveFilters({ minDistinctScopusCount: 3 })).toBe(true);
    expect(peopleHasActiveFilters({ population: 'RESEARCHERS' })).toBe(true);
    expect(peopleHasActiveFilters({ search: 'garcia' })).toBe(true);
  });

  it('builds a stats query with the same person filters and without pagination', () => {
    const query = peopleStatsQuery({
      search: 'garcia',
      cargo: 'Profesor',
      orcidStatus: 'WITH_ORCID',
      scopusStatus: 'ALL',
      scopusCount: 2,
      population: 'RESEARCHERS',
      page: 9,
      pageSize: 50,
      sort: 'name',
      direction: 'asc'
    });
    expect(query.search).toBe('garcia');
    expect(query.cargo).toBe('Profesor');
    expect(query.orcidStatus).toBe('WITH_ORCID');
    expect(query.scopusCount).toBe(2);
    expect(query.population).toBe('RESEARCHERS');
    expect(query.page).toBeUndefined();
    expect(query.pageSize).toBeUndefined();
    expect(query.sort).toBeUndefined();
  });

  it('omits page, size and sort from GET /people/stats params', () => {
    const params = peopleFilterParams(
      peopleStatsQuery({
        search: 'garcia',
        cargo: 'Profesor',
        page: 9,
        pageSize: 50,
        sort: 'name',
        direction: 'desc'
      })
    );
    expect(params.get('search')).toBe('garcia');
    expect(params.get('cargo')).toBe('Profesor');
    expect(params.get('page')).toBeNull();
    expect(params.get('size')).toBeNull();
    expect(params.get('sort')).toBeNull();
    expect(params.get('direction')).toBeNull();
  });

  it('keeps people entity sync and person metrics as distinct history operations', () => {
    expect(PEOPLE_HISTORY_ENTITIES).toEqual(['PEOPLE', 'PERSON_METRICS']);
  });
});
