import { SyncRun } from '../services/api/sync-api.service';

export function canShowOpenAlexAuthorRecovery(
  run: SyncRun | null | undefined,
  canAdminister: boolean
): boolean {
  return canViewOpenAlexAuthorRecovery(run, canAdminister) && run?.recoveryAvailable === true;
}

export function canViewOpenAlexAuthorRecovery(
  run: SyncRun | null | undefined,
  canAdminister: boolean
): boolean {
  return !!run
    && canAdminister
    && run.recoveryType === 'AUTHORS'
    && (run.pendingAuthors ?? 0) > 0;
}

export function openAlexRecoveryBlockedReason(run: SyncRun | null | undefined): string | null {
  return run?.recoveryBlockedReason?.trim() || null;
}

export function openAlexPhaseLabelKey(phase?: string | null): string | null {
  switch ((phase ?? '').trim().toUpperCase()) {
    case 'WORKS':
      return 'sync.detail.openalexPhaseWorks';
    case 'AUTHORS':
      return 'sync.detail.openalexPhaseAuthors';
    case 'CONFLICTS':
      return 'sync.detail.openalexPhaseConflicts';
    case 'FINALIZING':
      return 'sync.detail.openalexPhaseFinalizing';
    default:
      return null;
  }
}

export function openAlexShowsMarkedInactive(run: SyncRun | null | undefined): boolean {
  return !!run && (run.markedInactive ?? 0) > 0;
}

export function openAlexInvalidCount(run: SyncRun | null | undefined): number {
  return run?.invalidCount ?? 0;
}

export const OPENALEX_RECOVER_CONFIRM_KEY = 'sync.detail.openalexRecoverConfirmBody';
