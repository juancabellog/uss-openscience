import { ORGANIZATION_HISTORY_ENTITIES, organizationHasActiveFilters, organizationStatsQuery } from './organization-list.component';

describe('Organization list filtered summary helpers', () => {
  it('treats the default catalog as the current universe, not an extra filter', () => {
    expect(organizationHasActiveFilters({})).toBe(false);
    expect(organizationHasActiveFilters({ search: '', typeUri: '', presence: 'ACTIVE', hasParent: '' })).toBe(false);
  });

  it('detects search, type, inactive presence and parent filters as active', () => {
    expect(organizationHasActiveFilters({ search: 'medicina' })).toBe(true);
    expect(organizationHasActiveFilters({ typeUri: '/dk/atira/pure/organisation/faculty' })).toBe(true);
    expect(organizationHasActiveFilters({ presence: 'INACTIVE' })).toBe(true);
    expect(organizationHasActiveFilters({ presence: 'ALL' })).toBe(true);
    expect(organizationHasActiveFilters({ hasParent: 'WITH' })).toBe(true);
  });

  it('builds a stats query with the same organization filters and without pagination', () => {
    const query = organizationStatsQuery({
      search: 'medicina',
      typeUri: '/dk/atira/pure/organisation/faculty',
      presence: 'ALL',
      hasParent: 'WITH',
      page: 9,
      pageSize: 50,
      sort: 'name',
      direction: 'asc'
    });
    expect(query.search).toBe('medicina');
    expect(query.typeUri).toBe('/dk/atira/pure/organisation/faculty');
    expect(query.presence).toBe('ALL');
    expect(query.hasParent).toBe('WITH');
    expect(query.page).toBeUndefined();
    expect(query.pageSize).toBeUndefined();
    expect(query.sort).toBeUndefined();
  });

  it('uses ORGANIZATIONS as the history entity', () => {
    expect(ORGANIZATION_HISTORY_ENTITIES).toEqual(['ORGANIZATIONS']);
  });
});
