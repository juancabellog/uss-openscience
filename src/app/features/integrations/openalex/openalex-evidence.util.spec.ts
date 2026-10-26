import {
  OPENALEX_DRAWER_CLOSE_BUTTON_CLASS,
  OPENALEX_EVIDENCE_CODES,
  OPENALEX_EVIDENCE_PAGE_SIZE,
  clampOpenAlexWorkPage,
  clampOpenAlexWorkSize,
  compareOpenAlexOrcids,
  displayOpenAlexValue,
  joinOpenAlexName,
  openAlexEvidenceBadges,
  openAlexEvidenceCodesOf,
  openAlexEvidenceLabelKey,
  openAlexEvidenceUnits,
  openAlexEvidenceVisibleCount,
  openAlexOrcidComparisonCode,
  openAlexOrcidComparisonKey,
  openAlexOrcidSyncAllowed,
  openAlexPrimaryAffiliation,
  openAlexStatusTone,
  showOpenAlexEvidenceSearch,
  splitOpenAlexReasonCodes
} from './openalex-evidence.util';
import { OpenAlexMatchWork } from '../../../core/models/openscience.models';

function work(partial: Partial<OpenAlexMatchWork> = {}): OpenAlexMatchWork {
  return {
    researchOutputId: 1,
    normalizedDoi: '10.1/abc',
    openalexWorkId: 'W1',
    title: 'Paper',
    contributorFirstName: 'José',
    contributorLastName: 'Rodríguez',
    contributorPosition: 1,
    contributorCount: 3,
    rawAuthorName: 'Jose Rodriguez',
    authorPosition: 'first',
    sourceOrder: 0,
    nameCompatible: true,
    namePartitionDifferent: false,
    positionCode: 'POSITION_MATCH',
    orcidPure: '0000-0001-1111-1111',
    orcidOpenAlex: '0000-0001-1111-1111',
    ussAffiliation: true,
    affiliationDisplay: 'Universidad San Sebastián',
    ror: 'https://ror.org/04m5j1c54',
    reasonCodes: 'SAME_DOI,NAME_COMPATIBLE,ORCID_MATCH',
    evidences: [],
    ...partial
  };
}

describe('OpenAlex evidence presentation', () => {
  it('renders persona and candidate fields from real values', () => {
    expect(joinOpenAlexName('José Ramón', 'Rodríguez Pérez')).toBe('José Ramón Rodríguez Pérez');
    expect(displayOpenAlexValue('A5051422628')).toBe('A5051422628');
  });

  it('treats equal ORCID values as a match and different values as a conflict', () => {
    expect(compareOpenAlexOrcids('0000-0001-1111-1111', '0000-0001-1111-1111')).toBe('match');
    expect(openAlexOrcidComparisonKey('match')).toBe('openalex.orcidSame');
    expect(compareOpenAlexOrcids('0000-0001-1111-1111', '0000-0002-2222-2222')).toBe('different');
    expect(openAlexOrcidComparisonKey('different')).toBe('openalex.orcidDifferent');
  });

  it('does not infer ORCID equality when either side is missing', () => {
    expect(compareOpenAlexOrcids(null, '0000-0001-1111-1111')).toBe('missing');
    expect(compareOpenAlexOrcids('0000-0001-1111-1111', '  ')).toBe('missing');
    expect(openAlexOrcidComparisonKey('missing')).toBe('openalex.notInformed');
  });

  it('keeps each matching publication as a distinct unit', () => {
    const units = openAlexEvidenceUnits(
      work({
        evidences: [
          {
            sourceOrder: 0,
            rawAuthorName: 'Jose Rodriguez',
            authorDisplayName: 'José Rodríguez',
            authorPosition: 'first',
            affiliationDisplay: 'Universidad San Sebastián',
            ror: null,
            reasonCodes: 'SAME_DOI,NAME_COMPATIBLE'
          },
          {
            sourceOrder: 1,
            rawAuthorName: 'J. Rodriguez',
            authorDisplayName: 'José Rodríguez',
            authorPosition: 'middle',
            affiliationDisplay: null,
            ror: null,
            reasonCodes: 'SAME_DOI,POSITION_NOT_COMPARABLE'
          }
        ]
      })
    );
    expect(units.length).toBe(2);
    expect(units[1].authorPosition).toBe('middle');
  });

  it('translates known evidence codes and keeps the technical code as a secondary label', () => {
    const badges = openAlexEvidenceBadges({
      reasonCodes: 'SAME_DOI,NAME_COMPATIBLE,ORCID_MATCH,POSITION_NOT_COMPARABLE'
    });
    expect(badges.map((item) => item.labelKey)).toEqual([
      'openalex.evidenceCode.SAME_DOI',
      'openalex.evidenceCode.NAME_COMPATIBLE',
      'openalex.evidenceCode.ORCID_MATCH',
      'openalex.evidenceCode.POSITION_NOT_COMPARABLE'
    ]);
    expect(badges.map((item) => item.code)).toEqual([
      'SAME_DOI',
      'NAME_COMPATIBLE',
      'ORCID_MATCH',
      'POSITION_NOT_COMPARABLE'
    ]);
  });

  it('maps only matcher codes that actually exist', () => {
    expect(OPENALEX_EVIDENCE_CODES).toEqual([
      'SAME_DOI',
      'NAME_COMPATIBLE',
      'NAME_PARTITION_DIFFERENT',
      'ORCID_MATCH',
      'ORCID_CONFLICT',
      'USS_AFFILIATION',
      'POSITION_MATCH',
      'POSITION_DIFFERENT',
      'POSITION_NOT_COMPARABLE',
      'POSITION_INFO',
      'MULTI_WORK',
      'OPENALEX_RAW_DISPLAY_MISMATCH'
    ]);
    expect(openAlexEvidenceLabelKey('NOT_A_REAL_CODE')).toBe('NOT_A_REAL_CODE');
  });

  it('uses conflict, strong and manual-review tones from persisted status', () => {
    expect(openAlexStatusTone('CONFLICT')).toBe('warning');
    expect(openAlexStatusTone('STRONG')).toBe('success');
    expect(openAlexStatusTone('MANUAL_REVIEW')).toBe('review');
    expect(openAlexStatusTone('CONFIRMED')).toBe('success');
    expect(openAlexStatusTone('REJECTED')).toBe('danger');
  });

  it('shows a dash-friendly empty value when ROR is absent', () => {
    const affiliation = openAlexPrimaryAffiliation([
      work({ ror: null, affiliationDisplay: 'Universidad San Sebastián', evidences: [] })
    ]);
    expect(affiliation.display).toBe('Universidad San Sebastián');
    expect(affiliation.ror).toBeNull();
    expect(displayOpenAlexValue(null)).toBeNull();
    expect(displayOpenAlexValue('')).toBeNull();
  });

  it('does not dump thousands of evidence rows in one page', () => {
    expect(OPENALEX_EVIDENCE_PAGE_SIZE).toBe(25);
    expect(openAlexEvidenceVisibleCount(1172, 1, 25)).toBe(25);
    expect(openAlexEvidenceVisibleCount(1172, 47, 25)).toBe(22);
    expect(clampOpenAlexWorkPage(0)).toBe(1);
    expect(clampOpenAlexWorkSize(5000)).toBe(100);
  });

  it('prefers the structured code list when the backend already split reasonCodes', () => {
    expect(
      openAlexEvidenceCodesOf({
        reasonCodeList: ['SAME_DOI', 'NAME_COMPATIBLE'],
        reasonCodes: 'SAME_DOI,NAME_COMPATIBLE,ORCID_MATCH'
      })
    ).toEqual(['SAME_DOI', 'NAME_COMPATIBLE']);
    expect(splitOpenAlexReasonCodes('SAME_DOI, NAME_COMPATIBLE, SAME_DOI')).toEqual([
      'SAME_DOI',
      'NAME_COMPATIBLE'
    ]);
  });

  it('shows evidence search only when the match has many publications', () => {
    expect(showOpenAlexEvidenceSearch(3, '')).toBe(false);
    expect(showOpenAlexEvidenceSearch(11, '')).toBe(true);
    expect(showOpenAlexEvidenceSearch(3, '10.1/abc')).toBe(true);
  });

  it('keeps the drawer close control as a standard unwrapped button', () => {
    expect(OPENALEX_DRAWER_CLOSE_BUTTON_CLASS.split(' ')).toEqual([
      'page-action',
      'page-action--secondary',
      'upw-drawer__close-action'
    ]);
  });

  it('prefers backend ORCID comparison and never enables write from STRONG alone', () => {
    expect(openAlexOrcidComparisonCode('MATCH')).toBe('MATCH');
    expect(openAlexOrcidComparisonCode('CONFLICT')).toBe('CONFLICT');
    expect(openAlexOrcidComparisonCode('OPENALEX_ONLY')).toBe('OPENALEX_ONLY');
    expect(openAlexOrcidComparisonKey('CONFLICT')).toBe('openalex.orcidConflict');
    expect(openAlexOrcidComparisonKey('OPENALEX_ONLY')).toBe('openalex.orcidAvailableInOpenAlex');
    expect(openAlexOrcidSyncAllowed({ orcidSyncAvailable: true, status: 'CONFIRMED' })).toBe(true);
    expect(openAlexOrcidSyncAllowed({ orcidSyncAvailable: false, status: 'STRONG' })).toBe(false);
    expect(openAlexOrcidSyncAllowed({ status: 'STRONG' })).toBe(false);
  });
});
