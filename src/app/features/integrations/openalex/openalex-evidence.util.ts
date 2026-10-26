import { OsBadgeTone } from '../../../shared/components/status-badge/status-badge.component';
import { OpenAlexMatchWork, OpenAlexMatchWorkEvidence } from '../../../core/models/openscience.models';

export const OPENALEX_EVIDENCE_PAGE_SIZE = 25;
export const OPENALEX_EVIDENCE_MAX_PAGE_SIZE = 100;

export const OPENALEX_EVIDENCE_CODES = [
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
] as const;

export type OpenAlexEvidenceCode = (typeof OPENALEX_EVIDENCE_CODES)[number];
export type OpenAlexOrcidComparison = 'match' | 'different' | 'missing';
export const OPENALEX_DRAWER_CLOSE_BUTTON_CLASS = 'page-action page-action--secondary upw-drawer__close-action';

const CODE_SET = new Set<string>(OPENALEX_EVIDENCE_CODES);

const SUPPORTING = new Set<string>([
  'SAME_DOI',
  'NAME_COMPATIBLE',
  'ORCID_MATCH',
  'USS_AFFILIATION',
  'POSITION_MATCH',
  'MULTI_WORK'
]);

const CAUTION = new Set<string>([
  'ORCID_CONFLICT',
  'NAME_PARTITION_DIFFERENT',
  'POSITION_DIFFERENT',
  'OPENALEX_RAW_DISPLAY_MISMATCH'
]);

export interface OpenAlexEvidenceBadge {
  code: string;
  labelKey: string;
  tone: OsBadgeTone;
  icon: string | null;
}

export function splitOpenAlexReasonCodes(raw: string | null | undefined): string[] {
  if (!raw || !raw.trim()) {
    return [];
  }
  const seen = new Set<string>();
  const items: string[] = [];
  for (const part of raw.split(',')) {
    const code = part.trim();
    if (!code || seen.has(code)) {
      continue;
    }
    seen.add(code);
    items.push(code);
  }
  return items;
}

export function openAlexEvidenceCodesOf(source: {
  reasonCodeList?: string[] | null;
  reasonCodes?: string | null;
} | null | undefined): string[] {
  if (!source) {
    return [];
  }
  if (source.reasonCodeList && source.reasonCodeList.length > 0) {
    return source.reasonCodeList.filter((code) => !!code && code.trim()).map((code) => code.trim());
  }
  return splitOpenAlexReasonCodes(source.reasonCodes);
}

export function openAlexEvidenceLabelKey(code: string): string {
  return CODE_SET.has(code) ? `openalex.evidenceCode.${code}` : code;
}

export function openAlexEvidenceTone(code: string): OsBadgeTone {
  if (SUPPORTING.has(code)) {
    return 'success';
  }
  if (CAUTION.has(code)) {
    return 'warning';
  }
  return 'muted';
}

export function openAlexEvidenceIcon(code: string): string | null {
  if (SUPPORTING.has(code)) {
    return 'check';
  }
  if (CAUTION.has(code)) {
    return 'warning';
  }
  return null;
}

export function openAlexEvidenceBadges(source: {
  reasonCodeList?: string[] | null;
  reasonCodes?: string | null;
} | null | undefined): OpenAlexEvidenceBadge[] {
  return openAlexEvidenceCodesOf(source).map((code) => ({
    code,
    labelKey: openAlexEvidenceLabelKey(code),
    tone: openAlexEvidenceTone(code),
    icon: openAlexEvidenceIcon(code)
  }));
}

export function compareOpenAlexOrcids(
  personOrcid: string | null | undefined,
  openalexOrcid: string | null | undefined
): OpenAlexOrcidComparison {
  const left = displayOpenAlexValue(personOrcid);
  const right = displayOpenAlexValue(openalexOrcid);
  if (!left || !right) {
    return 'missing';
  }
  return left.toLowerCase() === right.toLowerCase() ? 'match' : 'different';
}

export function openAlexOrcidComparisonCode(
  backend: string | null | undefined,
  personOrcid?: string | null,
  openalexOrcid?: string | null
): string {
  if (backend) {
    return backend;
  }
  const fallback = compareOpenAlexOrcids(personOrcid, openalexOrcid);
  if (fallback === 'match') {
    return 'MATCH';
  }
  if (fallback === 'different') {
    return 'CONFLICT';
  }
  if (displayOpenAlexValue(openalexOrcid) && !displayOpenAlexValue(personOrcid)) {
    return 'OPENALEX_ONLY';
  }
  if (displayOpenAlexValue(personOrcid) && !displayOpenAlexValue(openalexOrcid)) {
    return 'PURE_ONLY';
  }
  return 'NONE';
}

export function openAlexOrcidComparisonKey(result: OpenAlexOrcidComparison | string): string {
  if (result === 'match' || result === 'MATCH') {
    return 'openalex.orcidSame';
  }
  if (result === 'CONFLICT') {
    return 'openalex.orcidConflict';
  }
  if (result === 'different') {
    return 'openalex.orcidDifferent';
  }
  if (result === 'OPENALEX_ONLY') {
    return 'openalex.orcidAvailableInOpenAlex';
  }
  return 'openalex.notInformed';
}

export function openAlexOrcidSyncAllowed(detail: {
  orcidSyncAvailable?: boolean | null;
  status?: string | null;
} | null | undefined): boolean {
  return detail?.orcidSyncAvailable === true;
}

export function openAlexStatusTone(status: string | null | undefined): OsBadgeTone {
  if (status === 'STRONG' || status === 'CONFIRMED') {
    return 'success';
  }
  if (status === 'MANUAL_REVIEW') {
    return 'review';
  }
  if (status === 'CONFLICT') {
    return 'warning';
  }
  if (status === 'REJECTED') {
    return 'danger';
  }
  return 'neutral';
}

export function displayOpenAlexValue(value: string | number | null | undefined): string | null {
  if (value == null) {
    return null;
  }
  const text = String(value).trim();
  return text ? text : null;
}

export function joinOpenAlexName(
  first: string | null | undefined,
  last: string | null | undefined
): string | null {
  return displayOpenAlexValue([first, last].map((part) => (part ?? '').trim()).filter(Boolean).join(' '));
}

export function openAlexPrimaryAffiliation(works: OpenAlexMatchWork[] | null | undefined): {
  display: string | null;
  ror: string | null;
} {
  for (const work of works ?? []) {
    for (const evidence of work.evidences ?? []) {
      const display = displayOpenAlexValue(evidence.affiliationDisplay);
      const ror = displayOpenAlexValue(evidence.ror);
      if (display || ror) {
        return { display, ror };
      }
    }
    const display = displayOpenAlexValue(work.affiliationDisplay);
    const ror = displayOpenAlexValue(work.ror);
    if (display || ror) {
      return { display, ror };
    }
  }
  return { display: null, ror: null };
}

export function clampOpenAlexWorkPage(page: number | null | undefined): number {
  return page == null || page < 1 ? 1 : page;
}

export function clampOpenAlexWorkSize(size: number | null | undefined): number {
  if (size == null || size < 1) {
    return OPENALEX_EVIDENCE_PAGE_SIZE;
  }
  return Math.min(size, OPENALEX_EVIDENCE_MAX_PAGE_SIZE);
}

export function openAlexEvidenceVisibleCount(total: number, page: number, pageSize: number): number {
  const size = clampOpenAlexWorkSize(pageSize);
  const offset = (clampOpenAlexWorkPage(page) - 1) * size;
  return Math.max(0, Math.min(size, total - offset));
}

export function openAlexEvidenceUnits(work: OpenAlexMatchWork): OpenAlexMatchWorkEvidence[] {
  if (work.evidences && work.evidences.length > 0) {
    return work.evidences;
  }
  return [
    {
      rawAuthorName: work.rawAuthorName,
      authorDisplayName: null,
      authorPosition: work.authorPosition,
      affiliationDisplay: work.affiliationDisplay,
      ror: work.ror,
      nameCompatible: work.nameCompatible,
      namePartitionDifferent: work.namePartitionDifferent,
      positionCode: work.positionCode,
      orcidPure: work.orcidPure,
      orcidOpenAlex: work.orcidOpenAlex,
      ussAffiliation: work.ussAffiliation,
      reasonCodes: work.reasonCodes,
      reasonCodeList: work.reasonCodeList
    }
  ];
}

export function showOpenAlexEvidenceSearch(matchingWorkCount: number, search: string | null | undefined): boolean {
  return matchingWorkCount > 10 || !!displayOpenAlexValue(search);
}
