import { projectFilterParams } from '../../core/services/api/project-api.service';
import { projectHasActiveFilters, projectStatsQuery } from './project-list.component';

describe('Project list filtered summary helpers', () => {
  it('treats the default catalog as the current universe, not an extra filter', () => {
    expect(projectHasActiveFilters({})).toBe(false);
    expect(projectHasActiveFilters({ search: '', typeUri: '', workflowStep: '', presence: 'ACTIVE' })).toBe(false);
  });

  it('detects type, workflow, visibility, year, inactive and text filters as active', () => {
    expect(projectHasActiveFilters({ typeUri: '/dk/atira/pure/upmproject/upmprojecttypes/upmproject/other' })).toBe(true);
    expect(projectHasActiveFilters({ workflowStep: 'forApproval' })).toBe(true);
    expect(projectHasActiveFilters({ visibilityKey: 'FREE' })).toBe(true);
    expect(projectHasActiveFilters({ startYear: 2022 })).toBe(true);
    expect(projectHasActiveFilters({ presence: 'INACTIVE' })).toBe(true);
    expect(projectHasActiveFilters({ search: 'fondecyt' })).toBe(true);
  });

  it('builds a stats query with the same project filters and without pagination', () => {
    const query = projectStatsQuery({
      search: 'fondecyt',
      typeUri: '/dk/atira/pure/upmproject/upmprojecttypes/upmproject/other',
      workflowStep: 'forApproval',
      visibilityKey: 'FREE',
      presence: 'ACTIVE',
      startYear: 2022,
      page: 9,
      pageSize: 50,
      sort: 'title',
      direction: 'asc'
    });
    expect(query.search).toBe('fondecyt');
    expect(query.typeUri).toBe('/dk/atira/pure/upmproject/upmprojecttypes/upmproject/other');
    expect(query.workflowStep).toBe('forApproval');
    expect(query.visibilityKey).toBe('FREE');
    expect(query.presence).toBe('ACTIVE');
    expect(query.startYear).toBe(2022);
    expect(query.page).toBeUndefined();
    expect(query.pageSize).toBeUndefined();
    expect(query.sort).toBeUndefined();
  });

  it('omits page, size and sort from GET /projects/stats params', () => {
    const params = projectFilterParams(
      projectStatsQuery({
        search: 'fondecyt',
        presence: 'ACTIVE',
        page: 9,
        pageSize: 50,
        sort: 'title',
        direction: 'asc'
      })
    );
    expect(params.get('search')).toBe('fondecyt');
    expect(params.get('presence')).toBe('ACTIVE');
    expect(params.get('page')).toBeNull();
    expect(params.get('size')).toBeNull();
    expect(params.get('sort')).toBeNull();
    expect(params.get('direction')).toBeNull();
  });
});
