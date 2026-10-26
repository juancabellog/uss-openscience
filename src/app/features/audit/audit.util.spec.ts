import {
  auditActorLabel,
  auditEmptyKind,
  auditHasActiveFilters,
  auditIsActive,
  auditModeKey,
  auditOperationKey,
  auditOutcomeKey,
  auditSourceKey,
  auditStatusTone,
  formatAuditValue
} from './audit.util';

describe('Audit UI helpers', () => {
  it('uses loading before the first page arrives', () => {
    expect(auditEmptyKind({ status: 'loading', total: 0, filtered: false })).toBe('loading');
  });

  it('uses empty when there are no events and no filters', () => {
    expect(auditEmptyKind({ status: 'success', total: 0, filtered: false })).toBe('empty');
  });

  it('uses filtered empty when filters exclude every event', () => {
    expect(auditEmptyKind({ status: 'success', total: 0, filtered: true })).toBe('filtered');
    expect(
      auditHasActiveFilters({ search: '44', eventType: 'SYNC' })
    ).toBe(true);
  });

  it('maps real table rows from sync executions', () => {
    expect(auditSourceKey('OPENALEX')).toBe('audit.sourceName.OPENALEX');
    expect(auditOperationKey('SYNC_PERSONS')).toBe('audit.operation.SYNC_PERSONS');
    expect(auditOperationKey('OPENALEX_SYNC')).toBe('audit.operation.OPENALEX_SYNC');
    expect(auditOperationKey('OPENALEX_AUTHORS_RECOVERY')).toBe('audit.operation.OPENALEX_AUTHORS_RECOVERY');
    expect(auditOperationKey('DSPACE_BITSTREAM_BACKFILL')).toBe('audit.operation.DSPACE_BITSTREAM_BACKFILL');
    expect(auditOperationKey('SYNC_PERSON_METRICS')).toBe('audit.operation.SYNC_PERSON_METRICS');
    expect(auditOperationKey('SYNC_RESEARCH_OUTPUT_METRICS')).toBe('audit.operation.SYNC_RESEARCH_OUTPUT_METRICS');
    expect(auditOperationKey('SYNC_JOURNAL_METRICS')).toBe('audit.operation.SYNC_JOURNAL_METRICS');
  });

  it('keeps filter codes server-side rather than translating in the query', () => {
    expect(auditHasActiveFilters({ source: 'PURE', status: 'FAILED', syncMode: 'FULL' })).toBe(true);
    expect(auditHasActiveFilters({ eventType: 'SYNC' })).toBe(false);
  });

  it('supports pagination helpers via 1-based empty pages', () => {
    expect(auditEmptyKind({ status: 'success', total: 40, filtered: false })).toBe('ready');
  });

  it('maps status badges from real sync statuses', () => {
    expect(auditOutcomeKey('COMPLETED')).toBe('audit.outcome.COMPLETED');
    expect(auditStatusTone('COMPLETED')).toBe('success');
    expect(auditStatusTone('COMPLETED_WITH_WARNINGS')).toBe('warning');
    expect(auditStatusTone('FAILED')).toBe('danger');
    expect(auditStatusTone('RUNNING')).toBe('pending');
    expect(auditStatusTone('PENDING')).toBe('neutral');
  });

  it('exposes change/error value formatting without rendering huge JSON', () => {
    expect(formatAuditValue('{"a":1}').json).toBe(true);
    expect(formatAuditValue('plain text').json).toBe(false);
    expect(formatAuditValue('x'.repeat(3000), 2000).truncated).toBe(true);
  });

  it('treats RUNNING as an in-progress event', () => {
    expect(auditIsActive('RUNNING')).toBe(true);
    expect(auditIsActive('CANCEL_REQUESTED')).toBe(true);
    expect(auditIsActive('CANCELLED')).toBe(false);
    expect(auditIsActive('COMPLETED')).toBe(false);
    expect(auditOutcomeKey('CANCELLED')).toBe('audit.outcome.CANCELLED');
    expect(auditStatusTone('CANCEL_REQUESTED')).toBe('pending');
    expect(auditModeKey('INCREMENTAL')).toBe('sync.mode.incremental');
  });

  it('keeps Audit read-only for OpenAlex recovery', () => {
    expect(auditOperationKey('OPENALEX_AUTHORS_RECOVERY')).toBe('audit.operation.OPENALEX_AUTHORS_RECOVERY');
    expect(typeof (globalThis as { recoverOpenAlexAuthors?: unknown }).recoverOpenAlexAuthors).toBe('undefined');
  });

  it('uses error empty-kind when the backend fails', () => {
    expect(auditEmptyKind({ status: 'error', total: 0, filtered: false })).toBe('error');
  });

  it('reset filters is the inverse of active filters', () => {
    expect(auditHasActiveFilters({ search: '', from: '', to: '', eventType: 'SYNC' })).toBe(false);
    expect(auditActorLabel({ actorKind: 'SYSTEM', actorLabel: null, actorId: null })).toBeNull();
    expect(auditActorLabel({ actorKind: 'USER', actorLabel: 'Ana Pérez', actorId: '7' })).toBe('Ana Pérez');
  });
});
