import {
  canShowOpenAlexSync,
  OPENALEX_PAGE_SIZE,
  openAlexEmptyKind,
  openAlexStatusLabel
} from './openalex-center.component';
import {
  OPENALEX_DRAWER_CLOSE_BUTTON_CLASS,
  OPENALEX_EVIDENCE_PAGE_SIZE,
  compareOpenAlexOrcids,
  openAlexEvidenceLabelKey,
  openAlexEvidenceVisibleCount,
  openAlexOrcidComparisonKey,
  openAlexOrcidSyncAllowed,
  openAlexStatusTone
} from './openalex-evidence.util';

describe('OpenAlex center helpers', () => {
  it('hides sync without ADMINISTRAR or when disabled', () => {
    expect(canShowOpenAlexSync(false, true, true)).toBe(false);
    expect(canShowOpenAlexSync(true, false, true)).toBe(false);
    expect(canShowOpenAlexSync(true, true, false)).toBe(false);
    expect(canShowOpenAlexSync(true, true, true)).toBe(true);
  });

  it('labels match states without treating them as confirmed facts', () => {
    expect(openAlexStatusLabel('STRONG')).toBe('openalex.status.STRONG');
    expect(openAlexStatusLabel('CONFIRMED')).toBe('openalex.status.CONFIRMED');
    expect(openAlexStatusLabel('CONFLICT')).toBe('openalex.status.CONFLICT');
  });

  it('distinguishes empty states', () => {
    expect(
      openAlexEmptyKind({
        loadError: false,
        enabled: false,
        configured: false,
        tablesAvailable: false,
        lastStatus: null,
        evaluated: 0,
        itemCount: 0,
        filtered: false
      })
    ).toBe('disabled');
    expect(
      openAlexEmptyKind({
        loadError: false,
        enabled: true,
        configured: true,
        tablesAvailable: true,
        lastStatus: null,
        evaluated: 0,
        itemCount: 0,
        filtered: false
      })
    ).toBe('never');
    expect(
      openAlexEmptyKind({
        loadError: false,
        enabled: true,
        configured: true,
        tablesAvailable: true,
        lastStatus: 'COMPLETED',
        evaluated: 10,
        itemCount: 0,
        filtered: false
      })
    ).toBe('none');
    expect(
      openAlexEmptyKind({
        loadError: false,
        enabled: true,
        configured: true,
        tablesAvailable: true,
        lastStatus: 'COMPLETED',
        evaluated: 10,
        itemCount: 0,
        filtered: true
      })
    ).toBe('filtered');
  });

  it('keeps confirm/sync behind ADMINISTRAR', () => {
    expect(canShowOpenAlexSync(false, true, true)).toBe(false);
  });

  it('does not treat STRONG as a confirmed identity', () => {
    expect(openAlexStatusLabel('STRONG')).not.toBe('openalex.status.CONFIRMED');
    expect(openAlexStatusLabel('MANUAL_REVIEW')).toBe('openalex.status.MANUAL_REVIEW');
    expect(openAlexStatusLabel('REJECTED')).toBe('openalex.status.REJECTED');
  });

  it('keeps server-side pagination at 25 rows per page', () => {
    expect(OPENALEX_PAGE_SIZE).toBe(25);
    expect(OPENALEX_EVIDENCE_PAGE_SIZE).toBe(25);
    expect(openAlexEvidenceVisibleCount(1172, 1, OPENALEX_EVIDENCE_PAGE_SIZE)).toBe(25);
  });

  it('keeps confirm and reject behind ADMINISTRAR and existing status gates', () => {
    expect(canShowOpenAlexSync(true, true, true)).toBe(true);
    expect(openAlexStatusLabel('CONFIRMED')).toBe('openalex.status.CONFIRMED');
    expect(openAlexStatusLabel('REJECTED')).toBe('openalex.status.REJECTED');
  });

  it('maps evidence codes and ORCID comparison without using raw matcher codes as the primary label', () => {
    expect(openAlexEvidenceLabelKey('SAME_DOI')).toBe('openalex.evidenceCode.SAME_DOI');
    expect(openAlexEvidenceLabelKey('POSITION_NOT_COMPARABLE')).toBe(
      'openalex.evidenceCode.POSITION_NOT_COMPARABLE'
    );
    expect(compareOpenAlexOrcids('0000-0001-1111-1111', '0000-0001-1111-1111')).toBe('match');
    expect(openAlexStatusTone('CONFLICT')).toBe('warning');
  });

  it('keeps the evidence drawer close label on one line', () => {
    expect(OPENALEX_DRAWER_CLOSE_BUTTON_CLASS).toContain('page-action');
    expect(OPENALEX_DRAWER_CLOSE_BUTTON_CLASS).toContain('upw-drawer__close-action');
    expect(OPENALEX_DRAWER_CLOSE_BUTTON_CLASS).not.toContain('w-');
  });

  it('uses backend eligibility for ORCID write and never treats STRONG as enough', () => {
    expect(openAlexOrcidSyncAllowed({ orcidSyncAvailable: true, status: 'CONFIRMED' })).toBe(true);
    expect(openAlexOrcidSyncAllowed({ orcidSyncAvailable: false, status: 'STRONG' })).toBe(false);
    expect(openAlexOrcidSyncAllowed({ status: 'STRONG' })).toBe(false);
    expect(openAlexOrcidComparisonKey('MATCH')).toBe('openalex.orcidSame');
    expect(openAlexOrcidComparisonKey('CONFLICT')).toBe('openalex.orcidConflict');
    expect(openAlexOrcidComparisonKey('OPENALEX_ONLY')).toBe('openalex.orcidAvailableInOpenAlex');
    expect(openAlexOrcidComparisonKey('NONE')).toBe('openalex.notInformed');
  });

  it('keeps ORCID write behind a confirmation dialog and backend orcidSyncAvailable', () => {
    expect(openAlexOrcidSyncAllowed({ orcidSyncAvailable: true, orcidComparison: 'OPENALEX_ONLY' })).toBe(true);
    expect(openAlexOrcidSyncAllowed({ orcidSyncAvailable: false, orcidComparison: 'MATCH' })).toBe(false);
    expect(openAlexOrcidSyncAllowed({ orcidSyncAvailable: false, orcidComparison: 'CONFLICT' })).toBe(false);
  });
});
