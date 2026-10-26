import { SyncRun } from '../services/api/sync-api.service';
import { canViewOpenAlexAuthorRecovery } from './openalex-recovery.util';

export type OpenAlexTab = 'summary' | 'works' | 'authors' | 'matches' | 'history';

export const OPENALEX_HISTORY_PATH = '/integrations/openalex';
export const OPENALEX_RECOVER_AUTHORS_ENDPOINT = '/sync/openalex/{id}/recover-authors';

const TABS: OpenAlexTab[] = ['summary', 'works', 'authors', 'matches', 'history'];

export function isOpenAlexTab(value: string | null | undefined): value is OpenAlexTab {
  return TABS.includes((value ?? '') as OpenAlexTab);
}

export function openAlexHistoryQuery(syncId?: string | null): { tab: 'history'; syncId?: string } {
  return syncId ? { tab: 'history', syncId } : { tab: 'history' };
}

export function openAlexRecoverAuthorsUrl(sourceSyncId: string): string {
  return `/sync/openalex/${encodeURIComponent(sourceSyncId)}/recover-authors`;
}

export function sortOpenAlexHistoryDesc(items: SyncRun[] | null | undefined): SyncRun[] {
  return [...(items ?? [])].sort((left, right) => {
    const leftAt = left.startedAt ? Date.parse(left.startedAt) : 0;
    const rightAt = right.startedAt ? Date.parse(right.startedAt) : 0;
    if (rightAt !== leftAt) {
      return rightAt - leftAt;
    }
    return String(right.syncId ?? '').localeCompare(String(left.syncId ?? ''), undefined, { numeric: true });
  });
}

export function openAlexHistoryStatusKey(
  run: { status?: string | null; errorCode?: string | null } | null | undefined
): string {
  if ((run?.errorCode ?? '').trim() === 'USER_CANCELLED') {
    return 'openalex.statusUserCancelled';
  }
  return 'sync.status.' + (run?.status ?? 'FAILED');
}

export function openAlexStaleMessageKey(
  entity?: string | null,
  errorCode?: string | null
): string | null {
  if ((entity ?? '').trim().toUpperCase() !== 'OPENALEX') {
    return null;
  }
  if ((errorCode ?? '').trim() !== 'STALE_SYNC_RECOVERED') {
    return null;
  }
  return 'sync.detail.openalexStaleMessage';
}

export function openAlexHistoryShowsRecovery(
  run: SyncRun | null | undefined
): boolean {
  return !!run
    && run.recoveryType === 'AUTHORS'
    && (run.pendingAuthors ?? 0) > 0;
}

export function openAlexAuditShowsRecovery(
  event: {
    entity?: string | null;
    recoveryType?: string | null;
    pendingAuthors?: number | null;
    recoveryAvailable?: boolean | null;
  } | null | undefined
): boolean {
  return !!event
    && event.entity === 'OPENALEX'
    && event.recoveryType === 'AUTHORS'
    && (event.pendingAuthors ?? 0) > 0;
}

export function openAlexHistoryHasRecoveryAction(
  run: SyncRun | null | undefined,
  canAdminister: boolean
): boolean {
  return canViewOpenAlexAuthorRecovery(run, canAdminister);
}
