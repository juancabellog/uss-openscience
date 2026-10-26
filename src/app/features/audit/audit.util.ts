import { AuditEvent } from '../../core/models/openscience.models';
import { OsBadgeTone } from '../../shared/components/status-badge/status-badge.component';

export const AUDIT_SOURCES = ['PURE', 'ORCID', 'SCOPUS', 'OPENALEX', 'CROSSREF', 'UNPAYWALL', 'DSPACE', 'SCIVAL'] as const;

export const AUDIT_ENTITIES = [
  'PEOPLE',
  'RESEARCH_OUTPUT',
  'JOURNALS',
  'PROJECTS',
  'ORCID',
  'UNPAYWALL',
  'CROSSREF',
  'OPENALEX',
  'DSPACE',
  'JOURNAL_METRICS',
  'RESEARCH_OUTPUT_METRICS',
  'PERSON_METRICS'
] as const;

export const AUDIT_STATUSES = [
  'PENDING',
  'RUNNING',
  'CANCEL_REQUESTED',
  'CANCELLED',
  'COMPLETED',
  'COMPLETED_WITH_WARNINGS',
  'FAILED'
] as const;

export function auditSourceKey(source?: string | null): string {
  const value = (source ?? '').trim().toUpperCase();
  if (value === 'PURE') return 'audit.sourceName.PURE';
  if (value === 'ORCID') return 'audit.sourceName.ORCID';
  if (value === 'SCOPUS') return 'audit.sourceName.SCOPUS';
  if (value === 'OPENALEX') return 'audit.sourceName.OPENALEX';
  if (value === 'CROSSREF') return 'audit.sourceName.CROSSREF';
  if (value === 'UNPAYWALL') return 'audit.sourceName.UNPAYWALL';
  if (value === 'DSPACE') return 'audit.sourceName.DSPACE';
  if (value === 'SCIVAL') return 'audit.sourceName.SCIVAL';
  return 'audit.sourceName.unknown';
}

export function auditOperationKey(operation?: string | null): string {
  const value = (operation ?? '').trim().toUpperCase();
  switch (value) {
    case 'SYNC_PERSONS':
      return 'audit.operation.SYNC_PERSONS';
    case 'SYNC_RESEARCH_OUTPUTS':
      return 'audit.operation.SYNC_RESEARCH_OUTPUTS';
    case 'SYNC_JOURNALS':
      return 'audit.operation.SYNC_JOURNALS';
    case 'SYNC_PROJECTS':
      return 'audit.operation.SYNC_PROJECTS';
    case 'ORCID_SYNC':
      return 'audit.operation.ORCID_SYNC';
    case 'UNPAYWALL_SYNC':
      return 'audit.operation.UNPAYWALL_SYNC';
    case 'CROSSREF_SYNC':
      return 'audit.operation.CROSSREF_SYNC';
    case 'OPENALEX_SYNC':
      return 'audit.operation.OPENALEX_SYNC';
    case 'OPENALEX_AUTHORS_RECOVERY':
      return 'audit.operation.OPENALEX_AUTHORS_RECOVERY';
    case 'DSPACE_SYNC':
      return 'audit.operation.DSPACE_SYNC';
    case 'DSPACE_BITSTREAM_BACKFILL':
      return 'audit.operation.DSPACE_BITSTREAM_BACKFILL';
    case 'SYNC_JOURNAL_METRICS':
      return 'audit.operation.SYNC_JOURNAL_METRICS';
    case 'SYNC_RESEARCH_OUTPUT_METRICS':
      return 'audit.operation.SYNC_RESEARCH_OUTPUT_METRICS';
    case 'SYNC_PERSON_METRICS':
      return 'audit.operation.SYNC_PERSON_METRICS';
    default:
      return 'audit.operation.SYNC';
  }
}

export function auditEntityKey(entity?: string | null): string {
  const value = (entity ?? '').trim().toUpperCase();
  if (value === 'PEOPLE') return 'navigation.people';
  if (value === 'RESEARCH_OUTPUT' || value === 'RESEARCH_OUTPUTS') return 'navigation.researchOutput';
  if (value === 'JOURNALS' || value === 'JOURNAL') return 'navigation.journals';
  if (value === 'PROJECTS' || value === 'PROJECT') return 'navigation.projects';
  if (value === 'ORCID') return 'sync.detail.orcidEntity';
  if (value === 'UNPAYWALL') return 'sync.detail.unpaywallEntity';
  if (value === 'CROSSREF') return 'sync.detail.crossrefEntity';
  if (value === 'OPENALEX') return 'sync.detail.openalexEntity';
  if (value === 'DSPACE') return 'sync.detail.dspaceEntity';
  if (value === 'JOURNAL_METRICS') return 'metrics.journalSyncEntity';
  if (value === 'RESEARCH_OUTPUT_METRICS') return 'metrics.outputSyncEntity';
  if (value === 'PERSON_METRICS') return 'metrics.personSyncEntity';
  return 'audit.entityUnknown';
}

export function auditTypeKey(type?: string | null): string {
  const value = (type ?? '').trim().toUpperCase();
  if (value === 'SYNC') return 'audit.type.SYNC';
  if (value === 'CORRECTION') return 'audit.type.CORRECTION';
  if (value === 'DATA_QUALITY') return 'audit.type.DATA_QUALITY';
  if (value === 'ADMINISTRATION') return 'audit.type.ADMINISTRATION';
  return 'audit.type.SYNC';
}

export function auditOutcomeKey(status?: string | null): string {
  switch ((status ?? '').trim().toUpperCase()) {
    case 'COMPLETED':
      return 'audit.outcome.COMPLETED';
    case 'COMPLETED_WITH_WARNINGS':
      return 'audit.outcome.COMPLETED_WITH_WARNINGS';
    case 'FAILED':
      return 'audit.outcome.FAILED';
    case 'RUNNING':
      return 'audit.outcome.RUNNING';
    case 'CANCEL_REQUESTED':
      return 'audit.outcome.CANCEL_REQUESTED';
    case 'CANCELLED':
      return 'audit.outcome.CANCELLED';
    case 'PENDING':
      return 'audit.outcome.PENDING';
    default:
      return 'audit.outcome.PENDING';
  }
}

export function auditStatusTone(status?: string | null): OsBadgeTone {
  switch ((status ?? '').trim().toUpperCase()) {
    case 'COMPLETED':
      return 'success';
    case 'COMPLETED_WITH_WARNINGS':
      return 'warning';
    case 'FAILED':
      return 'danger';
    case 'RUNNING':
      return 'pending';
    case 'CANCEL_REQUESTED':
      return 'pending';
    case 'CANCELLED':
      return 'neutral';
    case 'PENDING':
      return 'neutral';
    default:
      return 'neutral';
  }
}

export function auditModeKey(mode?: string | null): string | null {
  const value = (mode ?? '').trim().toUpperCase();
  if (value === 'FULL') return 'sync.mode.full';
  if (value === 'INCREMENTAL') return 'sync.mode.incremental';
  if (value === 'RECOVERY') return 'sync.mode.recovery';
  return null;
}

export function auditActorLabel(event: Pick<AuditEvent, 'actorKind' | 'actorLabel' | 'actorId'>): string | null {
  if (event.actorKind === 'SYSTEM' || (!event.actorLabel && !event.actorId)) {
    return null;
  }
  return event.actorLabel || event.actorId || null;
}

export function auditHasActiveFilters(query: {
  search?: string;
  from?: string;
  to?: string;
  eventType?: string;
  source?: string;
  entity?: string;
  status?: string;
  syncMode?: string;
  actor?: string;
}): boolean {
  return Boolean(
    query.search
    || query.from
    || query.to
    || (query.eventType && query.eventType !== 'SYNC')
    || query.source
    || query.entity
    || query.status
    || query.syncMode
    || query.actor
  );
}

export function auditEmptyKind(input: { status: string; total: number; filtered: boolean }): 'loading' | 'error' | 'filtered' | 'empty' | 'ready' {
  if (input.status === 'loading') return 'loading';
  if (input.status === 'error') return 'error';
  if (input.total > 0) return 'ready';
  return input.filtered ? 'filtered' : 'empty';
}

export function auditIsActive(status?: string | null): boolean {
  const value = (status ?? '').trim().toUpperCase();
  return value === 'RUNNING' || value === 'PENDING' || value === 'CANCEL_REQUESTED';
}

export function auditDurationLabel(durationMs?: number | null): { minutes: number; seconds: number } | null {
  if (durationMs == null || durationMs < 0) {
    return null;
  }
  const totalSeconds = Math.round(durationMs / 1000);
  return { minutes: Math.floor(totalSeconds / 60), seconds: totalSeconds % 60 };
}

export function formatAuditValue(value?: string | null, max = 2000): { text: string; json: boolean; truncated: boolean } {
  if (value == null || value === '') {
    return { text: '', json: false, truncated: false };
  }
  const trimmed = value.trim();
  try {
    const parsed = JSON.parse(trimmed);
    const pretty = JSON.stringify(parsed, null, 2);
    if (pretty.length > max) {
      return { text: pretty.slice(0, max), json: true, truncated: true };
    }
    return { text: pretty, json: true, truncated: false };
  } catch {
    if (trimmed.length > max) {
      return { text: trimmed.slice(0, max), json: false, truncated: true };
    }
    return { text: trimmed, json: false, truncated: false };
  }
}
