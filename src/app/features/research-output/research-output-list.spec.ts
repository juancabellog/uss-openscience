import { outputFilterParams } from '../../core/services/api/research-output-api.service';
import { OUTPUT_HISTORY_ENTITIES, outputHasActiveFilters, outputStatsQuery } from './research-output-list.component';

describe('Research output list filtered summary helpers', () => {
  it('treats the default DOI universe as unfiltered', () => {
    expect(outputHasActiveFilters({ doiStatus: 'ALL' })).toBe(false);
    expect(outputHasActiveFilters({})).toBe(false);
  });

  it('detects type, year, DOI status, workflow and text filters as active', () => {
    expect(outputHasActiveFilters({ type: 'Article' })).toBe(true);
    expect(outputHasActiveFilters({ year: 2025 })).toBe(true);
    expect(outputHasActiveFilters({ doiStatus: 'WITH_DOI' })).toBe(true);
    expect(outputHasActiveFilters({ doiStatus: 'WITHOUT_DOI' })).toBe(true);
    expect(outputHasActiveFilters({ doiStatus: 'MULTIPLE_DOI_VERSIONS' })).toBe(true);
    expect(outputHasActiveFilters({ doiStatus: 'SHARED_DOI' })).toBe(true);
    expect(outputHasActiveFilters({ workflowStep: 'approved' })).toBe(true);
    expect(outputHasActiveFilters({ search: 'climate' })).toBe(true);
  });

  it('builds a stats query with the same output filters and without pagination', () => {
    const query = outputStatsQuery({
      search: 'climate',
      type: 'Article',
      year: 2025,
      doiStatus: 'WITH_DOI',
      workflowStep: 'approved',
      page: 9,
      pageSize: 50,
      sort: 'year',
      direction: 'desc'
    });
    expect(query.search).toBe('climate');
    expect(query.type).toBe('Article');
    expect(query.year).toBe(2025);
    expect(query.doiStatus).toBe('WITH_DOI');
    expect(query.workflowStep).toBe('approved');
    expect(query.page).toBeUndefined();
    expect(query.pageSize).toBeUndefined();
    expect(query.sort).toBeUndefined();
  });

  it('omits page, size and sort from GET /research-output/stats params', () => {
    const params = outputFilterParams(
      outputStatsQuery({
        search: 'climate',
        type: 'Article',
        year: 2025,
        doiStatus: 'WITH_DOI',
        page: 9,
        pageSize: 50,
        sort: 'year',
        direction: 'desc'
      })
    );
    expect(params.get('search')).toBe('climate');
    expect(params.get('type')).toBe('Article');
    expect(params.get('year')).toBe('2025');
    expect(params.get('doiStatus')).toBe('WITH_DOI');
    expect(params.get('page')).toBeNull();
    expect(params.get('size')).toBeNull();
    expect(params.get('sort')).toBeNull();
    expect(params.get('direction')).toBeNull();
  });

  it('keeps research output entity sync and metrics as distinct history operations', () => {
    expect(OUTPUT_HISTORY_ENTITIES).toEqual(['RESEARCH_OUTPUT', 'RESEARCH_OUTPUT_METRICS']);
  });
});
