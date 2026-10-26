import { journalFilterParams } from '../../core/services/api/journal-api.service';
import { JOURNAL_HISTORY_ENTITIES, journalHasActiveFilters, journalStatsQuery } from './journal-list.component';

describe('Journal list filtered summary helpers', () => {
  it('treats the default catalog as the current universe, not an extra filter', () => {
    expect(journalHasActiveFilters({})).toBe(false);
    expect(journalHasActiveFilters({ search: '', typeUri: '', workflowStep: '', issnCount: '', publications: '', citescore: '', quartile: '' })).toBe(
      false
    );
  });

  it('detects type, workflow, ISSN, publications and text filters as active', () => {
    expect(journalHasActiveFilters({ typeUri: '/dk/atira/pure/journal/journaltypes/journal' })).toBe(true);
    expect(journalHasActiveFilters({ workflowStep: 'approved' })).toBe(true);
    expect(journalHasActiveFilters({ issnCount: '0' })).toBe(true);
    expect(journalHasActiveFilters({ issnCount: '2+' })).toBe(true);
    expect(journalHasActiveFilters({ publications: 'WITHOUT_PUBLICATIONS' })).toBe(true);
    expect(journalHasActiveFilters({ search: 'nature' })).toBe(true);
    expect(journalHasActiveFilters({ citescore: 'WITH' })).toBe(true);
    expect(journalHasActiveFilters({ quartile: 'Q1' })).toBe(true);
  });

  it('builds a stats query with the same journal filters and without pagination', () => {
    const query = journalStatsQuery({
      search: 'nature',
      typeUri: '/dk/atira/pure/journal/journaltypes/journal',
      workflowStep: 'approved',
      issnCount: '1',
      publications: 'WITH_PUBLICATIONS',
      citescore: 'WITH',
      quartile: 'Q1',
      page: 9,
      pageSize: 50,
      sort: 'title',
      direction: 'asc'
    });
    expect(query.search).toBe('nature');
    expect(query.typeUri).toBe('/dk/atira/pure/journal/journaltypes/journal');
    expect(query.workflowStep).toBe('approved');
    expect(query.issnCount).toBe('1');
    expect(query.publications).toBe('WITH_PUBLICATIONS');
    expect(query.citescore).toBe('WITH');
    expect(query.quartile).toBe('Q1');
    expect(query.page).toBeUndefined();
    expect(query.pageSize).toBeUndefined();
    expect(query.sort).toBeUndefined();
  });

  it('omits page, size and sort from GET /journals/stats params', () => {
    const params = journalFilterParams(
      journalStatsQuery({
        search: 'nature',
        issnCount: '0',
        page: 9,
        pageSize: 50,
        sort: 'title',
        direction: 'asc'
      })
    );
    expect(params.get('search')).toBe('nature');
    expect(params.get('issnCount')).toBe('0');
    expect(params.get('page')).toBeNull();
    expect(params.get('size')).toBeNull();
    expect(params.get('sort')).toBeNull();
    expect(params.get('direction')).toBeNull();
  });

  it('keeps journal entity sync and journal metrics as distinct history operations', () => {
    expect(JOURNAL_HISTORY_ENTITIES).toEqual(['JOURNALS', 'JOURNAL_METRICS']);
  });
});
