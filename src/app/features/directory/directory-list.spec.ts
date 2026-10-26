import { DirectoryImportResult, DirectoryImportSummary } from '../../core/models/openscience.models';
import {
  canConfirmDirectoryImport,
  canSimulateDirectoryImport,
  directoryActiveImport,
  directoryHasActiveFilters,
  directoryImportErrorKey,
  directoryImportStatusKey,
  directoryStatsQuery
} from './directory-list.component';
import { isLiveDirectoryImport } from './directory-import-progress';

describe('Directory list helpers', () => {
  it('treats the default catalog as the current universe, not an extra filter', () => {
    expect(directoryHasActiveFilters({})).toBe(false);
    expect(directoryHasActiveFilters({ search: '', presence: 'ACTIVE' })).toBe(false);
  });

  it('detects search and inactive presence as active filters', () => {
    expect(directoryHasActiveFilters({ search: 'medicina' })).toBe(true);
    expect(directoryHasActiveFilters({ presence: 'INACTIVE' })).toBe(true);
    expect(directoryHasActiveFilters({ presence: 'ALL' })).toBe(true);
  });

  it('builds a stats query without pagination', () => {
    const query = directoryStatsQuery({ search: 'medicina', presence: 'ALL' });
    expect(query.search).toBe('medicina');
    expect(query.presence).toBe('ALL');
    expect(query.page).toBeUndefined();
    expect(query.pageSize).toBeUndefined();
  });

  it('enables confirm only after a successful dry-run with the selected file', () => {
    const file = { name: 'Matriz academica filtrada.xlsx' } as File;
    const simulation = { dryRun: true, rowCount: 2943 } as DirectoryImportResult;
    expect(canSimulateDirectoryImport({ file, busy: false })).toBe(true);
    expect(
      canConfirmDirectoryImport({
        file,
        simulation,
        busy: false,
        canAdminister: true
      })
    ).toBe(true);
  });

  it('keeps confirm disabled while busy, without a dry-run, or after persist', () => {
    const file = { name: 'Matriz academica filtrada.xlsx' } as File;
    const simulation = { dryRun: true, rowCount: 2943 } as DirectoryImportResult;
    expect(canSimulateDirectoryImport({ file, busy: true })).toBe(false);
    expect(
      canConfirmDirectoryImport({
        file,
        simulation,
        busy: true,
        canAdminister: true
      })
    ).toBe(false);
    expect(
      canConfirmDirectoryImport({
        file,
        simulation: null,
        busy: false,
        canAdminister: true
      })
    ).toBe(false);
    expect(
      canConfirmDirectoryImport({
        file,
        simulation: { ...simulation, dryRun: false },
        busy: false,
        canAdminister: true
      })
    ).toBe(false);
    expect(
      canConfirmDirectoryImport({
        file: null,
        simulation,
        busy: false,
        canAdminister: true
      })
    ).toBe(false);
    expect(
      canConfirmDirectoryImport({
        file,
        simulation,
        busy: false,
        canAdminister: false
      })
    ).toBe(false);
    expect(
      canConfirmDirectoryImport({
        file,
        simulation,
        busy: false,
        canAdminister: true,
        blocked: true
      })
    ).toBe(false);
    expect(canSimulateDirectoryImport({ file, busy: false, blocked: true })).toBe(false);
  });

  it('prevents a second definitive import while one is running', () => {
    const file = { name: 'Matriz academica filtrada.xlsx' } as File;
    const simulation = { dryRun: true, rowCount: 2943 } as DirectoryImportResult;
    const running = {
      id: 1,
      status: 'RUNNING',
      interrupted: false
    } as DirectoryImportSummary;
    expect(isLiveDirectoryImport(running)).toBe(true);
    expect(
      canConfirmDirectoryImport({
        file,
        simulation,
        busy: false,
        canAdminister: true,
        blocked: isLiveDirectoryImport(running)
      })
    ).toBe(false);
  });

  it('classifies a running import without an owner as interrupted', () => {
    const running = {
      id: 1,
      status: 'RUNNING',
      owned: false,
      interrupted: true
    } as DirectoryImportSummary;
    expect(directoryActiveImport([running])?.id).toBe(1);
    expect(directoryImportStatusKey(running)).toBe('directory.statusInterrupted');
    expect(directoryImportErrorKey({ error: { code: 'DIRECTORY_IMPORT_RUNNING' } }, 'directory.errors.failed')).toBe(
      'directory.errors.running'
    );
  });
});
