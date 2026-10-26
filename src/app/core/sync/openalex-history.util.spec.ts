import { SyncRun } from '../services/api/sync-api.service';
import {
  OPENALEX_HISTORY_PATH,
  OPENALEX_RECOVER_AUTHORS_ENDPOINT,
  isOpenAlexTab,
  openAlexAuditShowsRecovery,
  openAlexHistoryHasRecoveryAction,
  openAlexHistoryQuery,
  openAlexHistoryShowsRecovery,
  openAlexHistoryStatusKey,
  openAlexRecoverAuthorsUrl,
  openAlexStaleMessageKey,
  sortOpenAlexHistoryDesc
} from './openalex-history.util';
import { OPENALEX_RECOVER_CONFIRM_KEY, canShowOpenAlexAuthorRecovery } from './openalex-recovery.util';

function run(partial: Partial<SyncRun>): SyncRun {
  return {
    syncId: '0',
    entity: 'OPENALEX',
    status: 'FAILED',
    processed: 0,
    created: 0,
    updated: 0,
    unchanged: 0,
    reactivated: 0,
    markedInactive: 0,
    errors: 0,
    ...partial
  };
}

const source48 = run({
  syncId: '48',
  syncMode: 'FULL',
  status: 'FAILED',
  errorCode: 'STALE_SYNC_RECOVERED',
  startedAt: '2026-10-13T16:12:25.413120-03:00',
  processed: 22515,
  totalExpected: 22515,
  phase: 'AUTHORS',
  recoveryAvailable: true,
  recoveryType: 'AUTHORS',
  pendingAuthors: 1342,
  recoveryBlockedReason: null
});

const cancelled51 = run({
  syncId: '51',
  syncMode: 'FULL',
  status: 'FAILED',
  errorCode: 'USER_CANCELLED',
  startedAt: '2026-10-14T15:48:00-03:00',
  processed: 0,
  totalExpected: 22515
});

describe('OpenAlex operational history', () => {
  it('loads OpenAlex executions in descending start order including #48 and #51', () => {
    const rows = sortOpenAlexHistoryDesc([source48, cancelled51, run({ syncId: '45', startedAt: '2026-10-10T10:00:00Z' })]);
    expect(rows.map((item) => item.syncId)).toEqual(['51', '48', '45']);
    expect(rows.some((item) => item.syncId === '48')).toBe(true);
    expect(rows.some((item) => item.syncId === '51')).toBe(true);
  });

  it('opens the correct execution from Historial query params', () => {
    expect(isOpenAlexTab('history')).toBe(true);
    expect(openAlexHistoryQuery('48')).toEqual({ tab: 'history', syncId: '48' });
    expect(OPENALEX_HISTORY_PATH).toBe('/integrations/openalex');
  });

  it('shows #48 recovery from backend flags and the 1342 pending authors', () => {
    expect(source48.recoveryAvailable).toBe(true);
    expect(source48.recoveryType).toBe('AUTHORS');
    expect(source48.pendingAuthors).toBe(1342);
    expect(openAlexHistoryShowsRecovery(source48)).toBe(true);
    expect(canShowOpenAlexAuthorRecovery(source48, true)).toBe(true);
    expect(openAlexHistoryHasRecoveryAction(source48, true)).toBe(true);
  });

  it('does not invent recovery for #51 USER_CANCELLED', () => {
    expect(openAlexHistoryShowsRecovery(cancelled51)).toBe(false);
    expect(canShowOpenAlexAuthorRecovery(cancelled51, true)).toBe(false);
    expect(openAlexHistoryStatusKey(cancelled51)).toBe('openalex.statusUserCancelled');
    expect(cancelled51.status).toBe('FAILED');
    expect(cancelled51.errorCode).toBe('USER_CANCELLED');
  });

  it('uses the real recover-authors endpoint and confirmation copy', () => {
    expect(OPENALEX_RECOVER_AUTHORS_ENDPOINT).toBe('/sync/openalex/{id}/recover-authors');
    expect(openAlexRecoverAuthorsUrl('48')).toBe('/sync/openalex/48/recover-authors');
    expect(OPENALEX_RECOVER_CONFIRM_KEY).toBe('sync.detail.openalexRecoverConfirmBody');
  });

  it('does not call fetchWork from recovery helpers', () => {
    expect(openAlexRecoverAuthorsUrl('48')).not.toContain('works');
    expect(openAlexRecoverAuthorsUrl('48')).toContain('recover-authors');
  });

  it('keeps a later recovery visible in the same descending history', () => {
    const recovery52 = run({
      syncId: '52',
      syncMode: 'RECOVERY',
      status: 'RUNNING',
      startedAt: '2026-10-14T18:00:00-03:00',
      sourceSyncExecutionId: '48'
    });
    const rows = sortOpenAlexHistoryDesc([source48, cancelled51, recovery52]);
    expect(rows.map((item) => item.syncId)).toEqual(['52', '51', '48']);
    expect(rows[0].sourceSyncExecutionId).toBe('48');
    expect(rows.find((item) => item.syncId === '48')?.status).toBe('FAILED');
    expect(rows.find((item) => item.syncId === '51')?.errorCode).toBe('USER_CANCELLED');
  });

  it('keeps Audit read-only and only links back to OpenAlex Historial', () => {
    expect(openAlexAuditShowsRecovery({
      entity: 'OPENALEX',
      recoveryType: 'AUTHORS',
      pendingAuthors: 1342,
      recoveryAvailable: true
    })).toBe(true);
    expect(openAlexAuditShowsRecovery({
      entity: 'OPENALEX',
      recoveryType: null,
      pendingAuthors: 0
    })).toBe(false);
    expect(openAlexHistoryQuery('48').tab).toBe('history');
  });

  it('presents OpenAlex stale without inactive marks and keeps USER_CANCELLED evidence', () => {
    expect(openAlexStaleMessageKey('OPENALEX', 'STALE_SYNC_RECOVERED')).toBe('sync.detail.openalexStaleMessage');
    expect(openAlexStaleMessageKey('PEOPLE', 'STALE_SYNC_RECOVERED')).toBeNull();
    expect(openAlexHistoryStatusKey(cancelled51)).toBe('openalex.statusUserCancelled');
    expect(openAlexHistoryStatusKey(source48)).toBe('sync.status.FAILED');
  });
});
