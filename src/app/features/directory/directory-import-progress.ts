import { DirectoryImportSummary } from '../../core/models/openscience.models';

export type DirectoryImportPanelKind = 'running' | 'completed' | 'failed' | 'interrupted';

export interface DirectoryImportProgressView {
  id: number;
  filename: string | null;
  status: string;
  kind: DirectoryImportPanelKind;
  startedAt: string | null;
  finishedAt: string | null;
  processed: number;
  expected: number;
  pending: number;
  percent: number | null;
  issueCount: number;
  createdCount: number;
  updatedCount: number;
  unchangedCount: number;
  errorMessage: string | null;
  updating: boolean;
  stale: boolean;
}

export function directoryImportPercent(processed: number, expected: number): number | null {
  if (expected <= 0) {
    return null;
  }
  return Math.round((Math.max(0, processed) / expected) * 1000) / 10;
}

export function directoryImportPending(processed: number, expected: number): number {
  return Math.max(0, expected - Math.max(0, processed));
}

export function directoryImportPanelKind(
  item: Pick<DirectoryImportSummary, 'status' | 'interrupted'>
): DirectoryImportPanelKind | null {
  if (item.interrupted || (item.status === 'RUNNING' && item.interrupted)) {
    return 'interrupted';
  }
  if (item.status === 'RUNNING') {
    return 'running';
  }
  if (item.status === 'COMPLETED') {
    return 'completed';
  }
  if (item.status === 'FAILED') {
    return 'failed';
  }
  return null;
}

export function isLiveDirectoryImport(item: DirectoryImportSummary | null | undefined): boolean {
  return item?.status === 'RUNNING' && item.interrupted !== true;
}

export function shouldPollDirectoryImport(item: DirectoryImportSummary | null | undefined): boolean {
  return item?.status === 'RUNNING';
}

export function isBlockingDirectoryImport(item: DirectoryImportSummary | null | undefined): boolean {
  return item?.status === 'RUNNING';
}

export function mergeDirectoryImportTrack(
  previous: DirectoryImportSummary | null,
  incoming: DirectoryImportSummary[] | null | undefined,
  failed: boolean,
  options?: { adoptLatest?: boolean }
): DirectoryImportSummary | null {
  if (failed || incoming == null) {
    return previous;
  }
  const running = incoming.find((item) => item.status === 'RUNNING');
  if (running) {
    return retainProgress(previous, running);
  }
  if (previous) {
    const same = incoming.find((item) => item.id === previous.id);
    if (same) {
      return retainProgress(previous, same);
    }
  }
  if (!previous && options?.adoptLatest && incoming.length > 0) {
    return incoming[0];
  }
  return previous;
}

export function directoryImportProgress(
  item: DirectoryImportSummary,
  options?: { updating?: boolean; stale?: boolean }
): DirectoryImportProgressView {
  const expected = Math.max(item.expectedCount ?? 0, item.status === 'RUNNING' ? 0 : item.rowCount ?? 0);
  const processedRaw = item.status === 'RUNNING' ? (item.processedCount ?? 0) : Math.max(item.processedCount ?? 0, item.rowCount ?? 0);
  const expectedSafe = expected > 0 ? expected : processedRaw;
  const processed = expectedSafe > 0 ? Math.min(processedRaw, expectedSafe) : processedRaw;
  const kind = directoryImportPanelKind(item) ?? 'running';
  return {
    id: item.id,
    filename: item.filename,
    status: item.status ?? '',
    kind,
    startedAt: item.startedAt,
    finishedAt: item.finishedAt,
    processed,
    expected: expectedSafe,
    pending: directoryImportPending(processed, expectedSafe),
    percent: directoryImportPercent(processed, expectedSafe),
    issueCount: item.issueCount ?? 0,
    createdCount: item.createdCount ?? 0,
    updatedCount: item.updatedCount ?? 0,
    unchangedCount: item.unchangedCount ?? 0,
    errorMessage: item.errorMessage,
    updating: options?.updating === true,
    stale: options?.stale === true
  };
}

export function directoryImportFailureKey(message: string | null | undefined): string | null {
  if (!message) {
    return null;
  }
  if (message.includes('DIRECTORY_IMPORT_ABANDONED')) {
    return 'directory.errors.abandoned';
  }
  return null;
}

function retainProgress(previous: DirectoryImportSummary | null, next: DirectoryImportSummary): DirectoryImportSummary {
  if (!previous || previous.id !== next.id) {
    return next;
  }
  return {
    ...next,
    processedCount: Math.max(previous.processedCount ?? 0, next.processedCount ?? 0),
    expectedCount: Math.max(previous.expectedCount ?? 0, next.expectedCount ?? 0)
  };
}
