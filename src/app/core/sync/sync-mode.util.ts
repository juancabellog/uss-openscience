import { SyncEntity, SyncRun } from '../services/api/sync-api.service';
import { isMetricsSyncEntity } from '../utils/pure-metrics.util';

export type SyncMode = 'FULL' | 'INCREMENTAL';

export interface SyncModePolicy {
  entity: string;
  supportedSyncModes: SyncMode[];
  availableSyncModes: SyncMode[];
  hasValidBaseline: boolean;
  recommendedSyncMode: SyncMode;
  lastSuccessfulFullSyncAt?: string | null;
  lastFullSyncAt?: string | null;
  lastExecution?: SyncRun | null;
}

const INCREMENTAL_ENTITIES: ReadonlySet<SyncEntity> = new Set([
  'ORCID',
  'UNPAYWALL',
  'CROSSREF',
  'OPENALEX',
  'DSPACE',
  'JOURNAL_METRICS',
  'RESEARCH_OUTPUT_METRICS',
  'PERSON_METRICS'
]);

export function supportsIncrementalSync(entity: SyncEntity): boolean {
  return INCREMENTAL_ENTITIES.has(entity);
}

export function supportsFullSync(_entity: SyncEntity): boolean {
  return true;
}

/** Capability catalog only. The button must use {@link SyncModePolicy}. */
export function supportedSyncModes(entity: SyncEntity): SyncMode[] {
  return supportsIncrementalSync(entity) ? ['FULL', 'INCREMENTAL'] : ['FULL'];
}

/** Fallback until the backend policy arrives: recommend FULL and do not offer INCREMENTAL. */
export function conservativeSyncPolicy(entity: SyncEntity): SyncModePolicy {
  const supported = supportedSyncModes(entity);
  return {
    entity,
    supportedSyncModes: supported,
    availableSyncModes: ['FULL'],
    hasValidBaseline: false,
    recommendedSyncMode: 'FULL',
    lastSuccessfulFullSyncAt: null,
    lastFullSyncAt: null,
    lastExecution: null
  };
}

export function normalizeSyncMode(value?: string | null): SyncMode | null {
  return value === 'FULL' || value === 'INCREMENTAL' ? value : null;
}

export function mapSyncModePolicy(
  raw: {
    entity?: string;
    supportedSyncModes?: readonly string[] | null;
    availableSyncModes?: readonly string[] | null;
    hasValidBaseline?: boolean | null;
    recommendedSyncMode?: string | null;
    lastSuccessfulFullSyncAt?: string | null;
    lastFullSyncAt?: string | null;
    lastExecution?: SyncRun | null;
  } | null | undefined,
  entity: SyncEntity
): SyncModePolicy {
  const fallback = conservativeSyncPolicy(entity);
  if (!raw) {
    return fallback;
  }
  const supported = (raw.supportedSyncModes ?? [])
    .map(normalizeSyncMode)
    .filter((mode): mode is SyncMode => mode != null);
  const available = (raw.availableSyncModes ?? [])
    .map(normalizeSyncMode)
    .filter((mode): mode is SyncMode => mode != null);
  const recommended = normalizeSyncMode(raw.recommendedSyncMode) ?? 'FULL';
  const hasValidBaseline = raw.hasValidBaseline === true;
  return {
    entity: raw.entity || entity,
    supportedSyncModes: supported.length > 0 ? supported : fallback.supportedSyncModes,
    availableSyncModes:
      available.length > 0
        ? available
        : hasValidBaseline && (supported.length > 0 ? supported : fallback.supportedSyncModes).includes('INCREMENTAL')
          ? ['FULL', 'INCREMENTAL']
          : ['FULL'],
    hasValidBaseline,
    recommendedSyncMode: recommended,
    lastSuccessfulFullSyncAt: raw.lastSuccessfulFullSyncAt ?? null,
    lastFullSyncAt: raw.lastFullSyncAt ?? null,
    lastExecution: raw.lastExecution ?? null
  };
}

export function primarySyncMode(policy: SyncModePolicy): SyncMode {
  return policy.recommendedSyncMode === 'INCREMENTAL' && policy.supportedSyncModes.includes('INCREMENTAL')
    ? 'INCREMENTAL'
    : 'FULL';
}

export function showsSyncModeMenu(policy: SyncModePolicy): boolean {
  return policy.availableSyncModes.includes('FULL') && policy.availableSyncModes.includes('INCREMENTAL');
}

export function menuSyncModes(policy: SyncModePolicy): SyncMode[] {
  const primary = primarySyncMode(policy);
  const pool = policy.availableSyncModes.length > 0 ? policy.availableSyncModes : policy.supportedSyncModes;
  return pool.filter((mode) => mode !== primary);
}

export function primarySyncActionKey(policy: SyncModePolicy, running: boolean): string {
  if (isMetricsSyncEntity(policy.entity)) {
    if (running) {
      return 'sync.metrics.runningAction';
    }
    return primarySyncMode(policy) === 'INCREMENTAL' ? 'sync.metrics.actionIncremental' : 'sync.metrics.action';
  }
  if (running) {
    return 'sync.runningAction';
  }
  if (!policy.supportedSyncModes.includes('INCREMENTAL')) {
    return 'sync.action';
  }
  return primarySyncMode(policy) === 'INCREMENTAL' ? 'sync.actionIncremental' : 'sync.actionFull';
}

export function shouldConfirmSyncMode(mode: SyncMode, policy?: SyncModePolicy): boolean {
  if (isMetricsSyncEntity(policy?.entity)) {
    return mode === 'FULL';
  }
  return mode === 'FULL' || mode === 'INCREMENTAL';
}

export function metricsMenuTitleKey(mode: SyncMode): string {
  return mode === 'INCREMENTAL' ? 'sync.metrics.menuIncremental' : 'sync.metrics.menuFull';
}

export function metricsMenuHelpKey(mode: SyncMode): string {
  return mode === 'INCREMENTAL' ? 'sync.metrics.incrementalHelp' : 'sync.metrics.fullHelp';
}

export function openAlexFullConfirmBodyKey(count: number | null | undefined): string {
  return count != null && count > 0 ? 'sync.confirmOpenAlexFullBody' : 'sync.confirmOpenAlexFullBodyUnknown';
}

export function isActiveSyncStatus(status?: string | null): boolean {
  const value = (status ?? '').trim().toUpperCase();
  return value === 'PENDING' || value === 'RUNNING' || value === 'CANCEL_REQUESTED';
}

export function canOfferSyncCancel(canAdminister: boolean, status?: string | null): boolean {
  const value = (status ?? '').trim().toUpperCase();
  return canAdminister && (value === 'PENDING' || value === 'RUNNING');
}

export function canStartSync(canAdminister: boolean, syncEnabled: boolean): boolean {
  return syncEnabled && canAdminister;
}

export function isSyncControlDisabled(running: boolean, canStart: boolean, startBusy = false): boolean {
  return running || !canStart || startBusy;
}

export function syncModeLabelKey(mode?: string | null): string | null {
  if (mode === 'FULL') {
    return 'sync.mode.full';
  }
  if (mode === 'INCREMENTAL') {
    return 'sync.mode.incremental';
  }
  if (mode === 'RECOVERY') {
    return 'sync.mode.recovery';
  }
  return null;
}

export function syncModeMenuTitleKey(mode: SyncMode): string {
  return mode === 'INCREMENTAL' ? 'sync.menuIncremental' : 'sync.menuFull';
}

export function syncModeMenuHelpKey(mode: SyncMode): string {
  return mode === 'INCREMENTAL' ? 'sync.mode.incrementalHelp' : 'sync.mode.fullHelp';
}
