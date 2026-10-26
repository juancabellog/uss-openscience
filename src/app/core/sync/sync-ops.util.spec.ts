import { canOfferSyncCancel, isActiveSyncStatus, isSyncControlDisabled, shouldConfirmSyncMode } from './sync-mode.util';
import { canShowOpenAlexAuthorRecovery, canViewOpenAlexAuthorRecovery } from './openalex-recovery.util';
import { SyncRun } from '../services/api/sync-api.service';

describe('sync operational UX contract', () => {
  it('does not start a sync until confirmation is accepted', () => {
    expect(shouldConfirmSyncMode('FULL')).toBe(true);
    expect(shouldConfirmSyncMode('INCREMENTAL')).toBe(true);
  });

  it('treats dismissing confirmation as no backend call', () => {
    expect(isSyncControlDisabled(false, true, false)).toBe(false);
  });

  it('disables the confirm action while the start POST is in flight', () => {
    expect(isSyncControlDisabled(false, true, true)).toBe(true);
  });

  it('keeps Synchronizing distinct from Cancel', () => {
    expect(canOfferSyncCancel(true, 'RUNNING')).toBe(true);
    expect(canOfferSyncCancel(true, 'CANCEL_REQUESTED')).toBe(false);
  });

  it('keeps historical #48 visible independently of a newer OpenAlex run', () => {
    const source48: SyncRun = {
      syncId: '48',
      entity: 'OPENALEX',
      status: 'FAILED',
      errorCode: 'STALE_SYNC_RECOVERED',
      processed: 22515,
      created: 0,
      updated: 0,
      unchanged: 0,
      reactivated: 0,
      markedInactive: 0,
      errors: 0,
      recoveryType: 'AUTHORS',
      pendingAuthors: 10,
      recoveryAvailable: false,
      recoveryBlockedReason: 'SYNC_ALREADY_RUNNING'
    };
    const newer: SyncRun = {
      syncId: '52',
      entity: 'OPENALEX',
      status: 'RUNNING',
      processed: 100,
      created: 0,
      updated: 0,
      unchanged: 0,
      reactivated: 0,
      markedInactive: 0,
      errors: 0
    };
    expect(source48.syncId).toBe('48');
    expect(newer.syncId).not.toBe('48');
    expect(canViewOpenAlexAuthorRecovery(source48, true)).toBe(true);
    expect(canShowOpenAlexAuthorRecovery(source48, true)).toBe(false);
    expect(isActiveSyncStatus(newer.status)).toBe(true);
    expect(isActiveSyncStatus(source48.status)).toBe(false);
  });
});
