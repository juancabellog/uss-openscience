import {
  OPENALEX_RECOVER_CONFIRM_KEY,
  canShowOpenAlexAuthorRecovery,
  canViewOpenAlexAuthorRecovery,
  openAlexInvalidCount,
  openAlexPhaseLabelKey,
  openAlexRecoveryBlockedReason,
  openAlexShowsMarkedInactive
} from './openalex-recovery.util';
import { SyncRun } from '../services/api/sync-api.service';

function run(partial: Partial<SyncRun>): SyncRun {
  return {
    syncId: '48',
    entity: 'OPENALEX',
    status: 'FAILED',
    processed: 22515,
    created: 0,
    updated: 0,
    unchanged: 0,
    reactivated: 0,
    markedInactive: 0,
    errors: 0,
    ...partial
  };
}

describe('OpenAlex recovery UI helpers', () => {
  it('shows WORKS and AUTHORS phases', () => {
    expect(openAlexPhaseLabelKey('WORKS')).toBe('sync.detail.openalexPhaseWorks');
    expect(openAlexPhaseLabelKey('AUTHORS')).toBe('sync.detail.openalexPhaseAuthors');
  });

  it('shows the recovery button only when the backend allows it', () => {
    expect(canShowOpenAlexAuthorRecovery(run({ recoveryAvailable: true, recoveryType: 'AUTHORS', pendingAuthors: 10 }), true)).toBe(true);
    expect(canShowOpenAlexAuthorRecovery(run({ recoveryAvailable: true, recoveryType: 'AUTHORS', pendingAuthors: 10 }), false)).toBe(false);
    expect(canShowOpenAlexAuthorRecovery(run({ recoveryAvailable: false }), true)).toBe(false);
    expect(canShowOpenAlexAuthorRecovery(run({}), true)).toBe(false);
  });

  it('keeps #48 visible as a recovery candidate when another OpenAlex run is active', () => {
    const blocked = run({
      recoveryAvailable: false,
      recoveryType: 'AUTHORS',
      pendingAuthors: 38211,
      recoveryBlockedReason: 'SYNC_ALREADY_RUNNING'
    });
    expect(canViewOpenAlexAuthorRecovery(blocked, true)).toBe(true);
    expect(canShowOpenAlexAuthorRecovery(blocked, true)).toBe(false);
    expect(openAlexRecoveryBlockedReason(blocked)).toBe('SYNC_ALREADY_RUNNING');
  });

  it('hides recovery when a later execution cleared pending authors', () => {
    const stale = run({
      recoveryAvailable: false,
      recoveryType: null,
      pendingAuthors: 0,
      errorCode: 'STALE_SYNC_RECOVERED'
    });
    expect(canViewOpenAlexAuthorRecovery(stale, true)).toBe(false);
    expect(canShowOpenAlexAuthorRecovery(stale, true)).toBe(false);
    expect(stale.status).toBe('FAILED');
    expect(stale.syncId).toBe('48');
  });

  it('does not show 67 invalid DOIs as inactive marks', () => {
    const stale = run({ invalidCount: 67, markedInactive: 0, errorCode: 'STALE_SYNC_RECOVERED' });
    expect(openAlexInvalidCount(stale)).toBe(67);
    expect(openAlexShowsMarkedInactive(stale)).toBe(false);
  });

  it('keeps stale as a failed recovered execution', () => {
    const stale = run({ status: 'FAILED', errorCode: 'STALE_SYNC_RECOVERED' });
    expect(stale.status).toBe('FAILED');
    expect(stale.errorCode).toBe('STALE_SYNC_RECOVERED');
  });

  it('uses a confirmation that does not repeat the original Works loop', () => {
    expect(OPENALEX_RECOVER_CONFIRM_KEY).toBe('sync.detail.openalexRecoverConfirmBody');
  });
});
