import { itemFilterParams } from '../../../core/services/api/dspace-api.service';
import {
  dspaceAccessInventoryKnown,
  dspaceAccessKey,
  dspaceCanRefreshFiles,
  dspaceCanonicalAccessKey,
  dspaceComparisonKey,
  dspaceComparisonTone,
  dspaceEmptyKind,
  dspaceFileInventoryKnown,
  dspaceHasActiveItemFilters,
  dspaceIsBitstreamBackfill,
  dspaceIsPublicFile,
  dspaceMatchKind,
  dspaceStatsQuery
} from './dspace-center.component';

describe('DSpace center helpers', () => {
  it('treats CONFIRMED and SECONDARY as linked, not review', () => {
    expect(dspaceMatchKind('CONFIRMED')).toBe('LINKED');
    expect(dspaceMatchKind('SECONDARY')).toBe('LINKED');
  });

  it('keeps TITLE+year+author candidates as review', () => {
    expect(dspaceMatchKind('CANDIDATE')).toBe('REVIEW');
    expect(dspaceMatchKind('CANDIDATE')).not.toBe('LINKED');
  });

  it('marks absent or rejected matches as unmatched', () => {
    expect(dspaceMatchKind(null)).toBe('UNMATCHED');
    expect(dspaceMatchKind('REJECTED')).toBe('UNMATCHED');
  });

  it('uses real DSpace access statuses', () => {
    expect(dspaceAccessKey('open.access')).toBe('dspace.accessStatus.openAccess');
    expect(dspaceAccessKey('metadata.only')).toBe('dspace.accessStatus.metadataOnly');
    expect(dspaceAccessKey('restricted')).toBe('dspace.accessStatus.restricted');
    expect(dspaceAccessKey('embargo')).toBe('dspace.accessStatus.embargo');
    expect(dspaceAccessKey(null)).toBe('dspace.accessStatus.undetermined');
  });

  it('does not treat missing access or bitstream inventory as a real zero', () => {
    expect(
      dspaceAccessInventoryKnown({ items: 9023, unknownAccess: 9023, bitstreamCount: 0 } as never)
    ).toBe(false);
    expect(
      dspaceFileInventoryKnown({ items: 9023, unknownAccess: 9023, bitstreamCount: 0 } as never)
    ).toBe(false);
    expect(
      dspaceFileInventoryKnown({ items: 10, unknownAccess: 0, bitstreamCount: 0 } as never)
    ).toBe(true);
    expect(
      dspaceAccessInventoryKnown({ items: 10, unknownAccess: 0, bitstreamCount: 4 } as never)
    ).toBe(true);
  });

  it('does not treat Acceso Abierto USS membership as open.access', () => {
    expect(dspaceAccessKey('metadata.only')).not.toBe('dspace.accessStatus.openAccess');
  });

  it('maps canonical access and comparison without inventing duplicate', () => {
    expect(dspaceCanonicalAccessKey('OPEN')).toBe('dspace.accessStatus.openAccess');
    expect(dspaceCanonicalAccessKey('CLOSED')).toBe('dspace.canonicalAccess.closed');
    expect(dspaceComparisonKey('DIFFERENCE')).toBe('dspace.comparison.difference');
    expect(dspaceComparisonKey('NOT_EVALUABLE')).toBe('dspace.comparison.notEvaluable');
    expect(dspaceComparisonTone('CONSISTENT')).toBe('success');
    expect(dspaceComparisonTone('DIFFERENCE')).toBe('warning');
  });

  it('allows download only for public or open.access bitstreams', () => {
    expect(dspaceIsPublicFile({ publicDownload: true, accessStatus: 'restricted' })).toBe(true);
    expect(dspaceIsPublicFile({ publicDownload: false, accessStatus: 'open.access' })).toBe(true);
    expect(dspaceIsPublicFile({ publicDownload: false, accessStatus: 'metadata.only' })).toBe(false);
  });

  it('distinguishes empty states', () => {
    expect(
      dspaceEmptyKind({
        loadError: false,
        enabled: false,
        configured: false,
        tablesAvailable: false,
        lastStatus: null,
        itemCount: 0,
        filtered: false
      })
    ).toBe('disabled');
    expect(
      dspaceEmptyKind({
        loadError: false,
        enabled: true,
        configured: true,
        tablesAvailable: true,
        lastStatus: null,
        itemCount: 0,
        filtered: false
      })
    ).toBe('never');
    expect(
      dspaceEmptyKind({
        loadError: false,
        enabled: true,
        configured: true,
        tablesAvailable: true,
        lastStatus: 'COMPLETED',
        itemCount: 0,
        filtered: true
      })
    ).toBe('filtered');
    expect(
      dspaceEmptyKind({
        loadError: false,
        enabled: true,
        configured: true,
        tablesAvailable: true,
        lastStatus: 'COMPLETED',
        itemCount: 0,
        filtered: false
      })
    ).toBe('none');
    expect(
      dspaceEmptyKind({
        loadError: true,
        enabled: true,
        configured: true,
        tablesAvailable: true,
        lastStatus: null,
        itemCount: 0,
        filtered: false
      })
    ).toBeNull();
  });

  it('treats default FOUND as the current universe, not an extra filter', () => {
    expect(dspaceHasActiveItemFilters({ outcome: 'FOUND' })).toBe(false);
    expect(dspaceHasActiveItemFilters({})).toBe(false);
  });

  it('detects access, PURE, DOI, file, community and text filters as active', () => {
    expect(
      dspaceHasActiveItemFilters({
        accessStatus: 'open.access',
        matchStatus: 'LINKED',
        doi: '10.1234/x'
      })
    ).toBe(true);
    expect(dspaceHasActiveItemFilters({ communityUuid: 'aaa' })).toBe(true);
    expect(dspaceHasActiveItemFilters({ collectionUuid: 'bbb' })).toBe(true);
    expect(dspaceHasActiveItemFilters({ hasFile: 'YES' })).toBe(true);
    expect(dspaceHasActiveItemFilters({ search: 'tesis' })).toBe(true);
    expect(dspaceHasActiveItemFilters({ sharedDoi: true })).toBe(true);
    expect(dspaceHasActiveItemFilters({ outcome: 'ERROR' })).toBe(true);
  });

  it('builds a stats query with the same item filters and without pagination', () => {
    const query = dspaceStatsQuery({
      search: 'tesis',
      accessStatus: 'open.access',
      matchStatus: 'LINKED',
      page: 9,
      pageSize: 50,
      sort: 'title',
      direction: 'asc'
    });
    expect(query.search).toBe('tesis');
    expect(query.accessStatus).toBe('open.access');
    expect(query.matchStatus).toBe('LINKED');
    expect(query.outcome).toBe('FOUND');
    expect(query.page).toBeUndefined();
    expect(query.pageSize).toBeUndefined();
    expect(query.sort).toBeUndefined();
  });

  it('omits page, size and sort from GET /dspace/stats params', () => {
    const params = itemFilterParams({
      search: 'tesis',
      outcome: 'FOUND',
      accessStatus: 'open.access',
      page: 9,
      pageSize: 50,
      sort: 'title',
      direction: 'desc'
    });
    expect(params.get('search')).toBe('tesis');
    expect(params.get('outcome')).toBe('FOUND');
    expect(params.get('accessStatus')).toBe('open.access');
    expect(params.get('page')).toBeNull();
    expect(params.get('size')).toBeNull();
    expect(params.get('sort')).toBeNull();
    expect(params.get('direction')).toBeNull();
  });

  it('clears extra filters back to the current-item universe', () => {
    expect(dspaceHasActiveItemFilters(dspaceStatsQuery({ outcome: 'FOUND' }))).toBe(false);
  });

  it('identifies the DSpace file update without treating it as FULL', () => {
    expect(dspaceIsBitstreamBackfill({ phase: 'BITSTREAMS', syncMode: 'RECOVERY' })).toBe(true);
    expect(dspaceIsBitstreamBackfill({ message: 'BITSTREAM_BACKFILL items=10', syncMode: 'RECOVERY' })).toBe(true);
    expect(dspaceIsBitstreamBackfill({ phase: 'DISCOVER', syncMode: 'FULL' })).toBe(false);
  });

  it('enables Update files only when DSpace is idle and the user can administer', () => {
    expect(dspaceCanRefreshFiles(true, true, true, false)).toBe(true);
    expect(dspaceCanRefreshFiles(true, true, true, true)).toBe(false);
    expect(dspaceCanRefreshFiles(false, true, true, false)).toBe(false);
  });

  it('treats persisted ORIGINAL rows as a known file inventory for the cards', () => {
    expect(
      dspaceFileInventoryKnown({ items: 10, unknownAccess: 0, bitstreamCount: 4 } as never)
    ).toBe(true);
  });
});
