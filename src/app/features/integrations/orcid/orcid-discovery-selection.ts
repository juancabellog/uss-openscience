import {
  OrcidDiscoveryApplyResult,
  OrcidDiscoveryPerson,
  OrcidDiscoverySelectedItem
} from '../../../core/models/openscience.models';

const TERMINAL_STATUSES = new Set(['SYNCED', 'SYNC_FAILED', 'REMOTE_CHANGED', 'ALREADY_USED']);

const DROP_FROM_QUEUE_STATUSES = new Set([
  'UPDATED',
  'SUCCESS',
  'SYNCED',
  'LOCAL_RECONCILED',
  'REMOTE_SUCCESS_LOCAL_SYNC_FAILED',
  'SYNC_FAILED',
  'CONFLICT_REMOTE_CHANGED',
  'REMOTE_CHANGED',
  'ALREADY_USED',
  'PURE_VERIFICATION_FAILED',
  'FAILED'
]);

export function applyButtonI18nKey(count: number): string {
  return count === 1
    ? 'integrations.orcid.discovery.applyOne'
    : 'integrations.orcid.discovery.apply';
}

export function applyConfirmI18nKey(count: number): string {
  return count === 1
    ? 'integrations.orcid.discovery.applyConfirmOne'
    : 'integrations.orcid.discovery.applyConfirm';
}

export function isApplyDisabled(
  selectedCount: number,
  running: boolean,
  busy: boolean,
  applying: boolean
): boolean {
  return selectedCount === 0 || running || busy || applying;
}

export function isTerminalDiscoveryStatus(status: string | null | undefined): boolean {
  return TERMINAL_STATUSES.has((status ?? '').trim().toUpperCase());
}

export function queuedPersonIds(queued: readonly OrcidDiscoverySelectedItem[]): number[] {
  return queued.map((item) => item.personId);
}

export function queuedPersonIdSet(queued: readonly OrcidDiscoverySelectedItem[]): Set<number> {
  return new Set(queuedPersonIds(queued));
}

export function isSelectedForSync(
  queuedIds: ReadonlySet<number>,
  personId: number
): boolean {
  return queuedIds.has(personId);
}

export function canToggleSyncSelection(options: {
  canAdminister: boolean;
  queued: boolean;
  selectable: boolean;
  personStatus: string;
}): boolean {
  if (!options.canAdminister) {
    return false;
  }
  if (options.queued) {
    return true;
  }
  if (isTerminalDiscoveryStatus(options.personStatus)) {
    return false;
  }
  return options.selectable;
}

export function queuedItemFromPerson(
  person: OrcidDiscoveryPerson,
  candidateId?: number | null
): OrcidDiscoverySelectedItem {
  return {
    personId: person.personId,
    displayName: person.fullName || null,
    pureId: person.pureId,
    orcid: person.selectedOrcid || person.foundOrcid,
    candidateId: candidateId ?? person.selectedCandidateId ?? person.uniqueStrongCandidateId,
    personStatus: person.personStatus,
    selectionOrigin: person.selectionOrigin,
    queued: true
  };
}

export function withQueuedPerson(
  queued: readonly OrcidDiscoverySelectedItem[],
  item: OrcidDiscoverySelectedItem
): OrcidDiscoverySelectedItem[] {
  if (queued.some((current) => current.personId === item.personId)) {
    return [...queued];
  }
  return [...queued, item];
}

export function withoutQueuedPerson(
  queued: readonly OrcidDiscoverySelectedItem[],
  personId: number
): OrcidDiscoverySelectedItem[] {
  return queued.filter((item) => item.personId !== personId);
}

export function shouldDropFromQueue(status: string | null | undefined): boolean {
  return DROP_FROM_QUEUE_STATUSES.has((status ?? '').trim().toUpperCase());
}

export function pruneQueuedAfterApply(
  queued: readonly OrcidDiscoverySelectedItem[],
  result: Pick<OrcidDiscoveryApplyResult, 'items'>
): OrcidDiscoverySelectedItem[] {
  const remove = new Set<number>();
  for (const item of result.items) {
    if (item.localParentId == null || !shouldDropFromQueue(item.status)) {
      continue;
    }
    remove.add(item.localParentId);
  }
  return queued.filter((item) => !remove.has(item.personId));
}

export function applyPersonIds(queued: readonly OrcidDiscoverySelectedItem[]): number[] {
  return queuedPersonIds(queued);
}

export function resolveCandidateForQueue(person: OrcidDiscoveryPerson): number | null {
  if (person.personStatus === 'STRONG' && person.uniqueStrongCandidateId != null) {
    return person.uniqueStrongCandidateId;
  }
  return person.selectedCandidateId;
}

export function keepQueuedOnFilterChange(
  queued: readonly OrcidDiscoverySelectedItem[]
): OrcidDiscoverySelectedItem[] {
  return [...queued];
}