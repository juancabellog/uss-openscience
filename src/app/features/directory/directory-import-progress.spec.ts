import { DirectoryImportSummary } from '../../core/models/openscience.models';
import {
  directoryImportFailureKey,
  directoryImportPanelKind,
  directoryImportPending,
  directoryImportPercent,
  directoryImportProgress,
  isLiveDirectoryImport,
  mergeDirectoryImportTrack,
  shouldPollDirectoryImport
} from './directory-import-progress';

function summary(partial: Partial<DirectoryImportSummary>): DirectoryImportSummary {
  return {
    id: 1,
    publicId: 'imp-1',
    filename: 'Matriz academica filtrada.xlsx',
    status: 'RUNNING',
    importedBy: 'admin',
    startedAt: '2026-10-20T12:00:00Z',
    finishedAt: null,
    rowCount: 0,
    createdCount: 0,
    updatedCount: 0,
    unchangedCount: 0,
    reactivatedCount: 0,
    inactivatedCount: 0,
    issueCount: 4,
    expectedCount: 2943,
    processedCount: 1073,
    errorMessage: null,
    heartbeatAt: '2026-10-20T12:05:00Z',
    owned: true,
    interrupted: false,
    ...partial
  };
}

describe('directory import progress', () => {
  it('computes Chilean-style percent from real backend counters', () => {
    expect(directoryImportPercent(1073, 2943)).toBe(36.5);
    expect(directoryImportPending(1073, 2943)).toBe(1870);
    expect(directoryImportPercent(0, 0)).toBeNull();
  });

  it('updates progress from successive RUNNING snapshots', () => {
    const first = summary({ processedCount: 400 });
    const second = summary({ processedCount: 1073 });
    const merged = mergeDirectoryImportTrack(first, [second], false);
    const view = directoryImportProgress(merged!);
    expect(view.processed).toBe(1073);
    expect(view.expected).toBe(2943);
    expect(view.pending).toBe(1870);
    expect(view.percent).toBe(36.5);
    expect(view.kind).toBe('running');
    expect(shouldPollDirectoryImport(merged)).toBe(true);
  });

  it('keeps the last valid snapshot when polling fails', () => {
    const previous = summary({ processedCount: 1073 });
    expect(mergeDirectoryImportTrack(previous, null, true)).toEqual(previous);
    expect(isLiveDirectoryImport(previous)).toBe(true);
  });

  it('keeps the last valid snapshot when the temporary payload is empty', () => {
    const previous = summary({ processedCount: 1073 });
    expect(mergeDirectoryImportTrack(previous, [], false)).toEqual(previous);
  });

  it('does not let a transient zero overwrite known progress', () => {
    const previous = summary({ processedCount: 1073, expectedCount: 2943 });
    const incoming = summary({ processedCount: 0, expectedCount: 0 });
    const merged = mergeDirectoryImportTrack(previous, [incoming], false);
    expect(merged?.processedCount).toBe(1073);
    expect(merged?.expectedCount).toBe(2943);
  });

  it('stops polling after a successful completion and keeps final counters', () => {
    const previous = summary({ processedCount: 2800 });
    const completed = summary({
      status: 'COMPLETED',
      processedCount: 2943,
      rowCount: 2943,
      createdCount: 2100,
      finishedAt: '2026-10-20T13:00:00Z'
    });
    const merged = mergeDirectoryImportTrack(previous, [completed], false);
    const view = directoryImportProgress(merged!);
    expect(merged?.status).toBe('COMPLETED');
    expect(view.kind).toBe('completed');
    expect(view.percent).toBe(100);
    expect(shouldPollDirectoryImport(merged)).toBe(false);
    expect(isLiveDirectoryImport(merged)).toBe(false);
  });

  it('keeps the execution id and error after a failed completion', () => {
    const previous = summary({ processedCount: 800 });
    const failed = summary({
      status: 'FAILED',
      errorMessage: 'DIRECTORY_IMPORT_FAILED: workbook closed',
      finishedAt: '2026-10-20T13:10:00Z'
    });
    const merged = mergeDirectoryImportTrack(previous, [failed], false);
    const view = directoryImportProgress(merged!);
    expect(view.kind).toBe('failed');
    expect(view.id).toBe(1);
    expect(view.errorMessage).toContain('DIRECTORY_IMPORT_FAILED');
    expect(shouldPollDirectoryImport(merged)).toBe(false);
  });

  it('recovers a running import after a page reload', () => {
    const running = summary({ id: 7, processedCount: 512 });
    const recovered = mergeDirectoryImportTrack(null, [running], false);
    expect(recovered?.id).toBe(7);
    expect(recovered?.status).toBe('RUNNING');
    expect(directoryImportPanelKind(recovered!)).toBe('running');
  });

  it('adopts the latest terminal import after a persist that skipped RUNNING snapshots', () => {
    const completed = summary({
      id: 9,
      status: 'COMPLETED',
      processedCount: 2943,
      rowCount: 2943
    });
    const adopted = mergeDirectoryImportTrack(null, [completed], false, { adoptLatest: true });
    expect(adopted?.id).toBe(9);
    expect(directoryImportPanelKind(adopted!)).toBe('completed');
  });

  it('classifies interrupted and failed states without treating them as live', () => {
    const interrupted = summary({ owned: false, interrupted: true });
    const failed = summary({ status: 'FAILED', interrupted: false });
    expect(directoryImportPanelKind(interrupted)).toBe('interrupted');
    expect(isLiveDirectoryImport(interrupted)).toBe(false);
    expect(directoryImportPanelKind(failed)).toBe('failed');
    expect(directoryImportFailureKey('DIRECTORY_IMPORT_ABANDONED')).toBe('directory.errors.abandoned');
  });
});
