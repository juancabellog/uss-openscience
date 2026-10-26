import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, combineLatest, debounceTime, EMPTY, exhaustMap, filter, finalize, map, merge, of, startWith, switchMap, timer, type Observable } from 'rxjs';
import {
  DirectoryImportResult,
  DirectoryImportSummary,
  DirectoryPerson,
  DirectoryPresenceFilter,
  DirectoryQuery,
  DirectoryStats
} from '../../core/models/openscience.models';
import { AuthService } from '../../core/services/auth.service';
import { DirectoryApiService } from '../../core/services/api/directory-api.service';
import { emptyPage, toPageViewState } from '../../core/utils/page-view-state';
import { DataTableShellComponent } from '../../shared/components/data-table-shell/data-table-shell.component';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state/error-state.component';
import { FilterBarComponent } from '../../shared/components/filter-bar/filter-bar.component';
import { LoadingStateComponent } from '../../shared/components/loading-state/loading-state.component';
import { MiniStatComponent } from '../../shared/components/mini-stat/mini-stat.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { PaginationComponent } from '../../shared/components/pagination/pagination.component';
import { SearchBoxComponent } from '../../shared/components/search-box/search-box.component';
import { StatusBadgeComponent, type OsBadgeTone } from '../../shared/components/status-badge/status-badge.component';
import { I18N_HOST, I18N_IMPORTS } from '../../shared/i18n/i18n.imports';
import {
  directoryImportFailureKey,
  directoryImportProgress,
  isBlockingDirectoryImport,
  isLiveDirectoryImport,
  mergeDirectoryImportTrack,
  shouldPollDirectoryImport,
  type DirectoryImportPanelKind,
  type DirectoryImportProgressView
} from './directory-import-progress';

export const DIRECTORY_IMPORT_POLL_MS = 2500;

const PAGE_SIZE = 25;

export function directoryHasActiveFilters(state: { search?: string; presence?: string }): boolean {
  return !!(state.search?.trim() || (state.presence && state.presence !== 'ACTIVE'));
}

export function directoryStatsQuery(state: {
  search?: string;
  presence?: DirectoryPresenceFilter;
}): DirectoryQuery {
  return {
    search: state.search || undefined,
    presence: state.presence || undefined
  };
}

export function canSimulateDirectoryImport(state: {
  file: File | null;
  busy: boolean;
  blocked?: boolean;
}): boolean {
  return !!state.file && !state.busy && !state.blocked;
}

export function canConfirmDirectoryImport(state: {
  file: File | null;
  simulation: DirectoryImportResult | null;
  busy: boolean;
  canAdminister: boolean;
  blocked?: boolean;
}): boolean {
  return (
    !!state.file &&
    state.simulation?.dryRun === true &&
    !state.busy &&
    state.canAdminister &&
    !state.blocked
  );
}

export function directoryActiveImport(items: DirectoryImportSummary[] | null | undefined): DirectoryImportSummary | null {
  return items?.find((item) => item.status === 'RUNNING') ?? null;
}

export function directoryImportStatusKey(item: Pick<DirectoryImportSummary, 'status' | 'interrupted'>): string {
  if (item.interrupted) {
    return 'directory.statusInterrupted';
  }
  if (item.status === 'RUNNING') {
    return 'directory.statusRunning';
  }
  if (item.status === 'FAILED') {
    return 'directory.statusFailed';
  }
  if (item.status === 'COMPLETED') {
    return 'directory.statusCompleted';
  }
  return 'directory.statusUnknown';
}

export function directoryImportErrorKey(error: unknown, fallback: string): string {
  const code = String((error as { error?: { code?: string } })?.error?.code ?? '').toUpperCase();
  const known: Record<string, string> = {
    DIRECTORY_IMPORT_RUNNING: 'directory.errors.running',
    DIRECTORY_IMPORT_ABANDONED: 'directory.errors.abandoned',
    DIRECTORY_STORAGE_FAILED: 'directory.errors.storage',
    DIRECTORY_FILE_EMPTY: 'directory.errors.empty',
    DIRECTORY_FILE_INVALID: 'directory.errors.invalid',
    DIRECTORY_IMPORT_FAILED: 'directory.errors.failed',
    DIRECTORY_DRY_RUN_FAILED: 'directory.errors.dryRun'
  };
  return known[code] ?? fallback;
}

@Component({
  selector: 'os-directory-list',
  hostDirectives: [...I18N_HOST],
  imports: [
    ...I18N_IMPORTS,
    ReactiveFormsModule,
    RouterLink,
    DataTableShellComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    FilterBarComponent,
    LoadingStateComponent,
    MiniStatComponent,
    PageHeaderComponent,
    PaginationComponent,
    SearchBoxComponent,
    StatusBadgeComponent
  ],
  templateUrl: './directory-list.component.html',
  styleUrl: './directory-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DirectoryListComponent {
  private readonly api = inject(DirectoryApiService);
  readonly auth = inject(AuthService);
  readonly tab = signal<'catalog' | 'import' | 'history'>('catalog');
  readonly search = new FormControl('', { nonNullable: true });
  readonly presence = new FormControl<DirectoryPresenceFilter>('ACTIVE', { nonNullable: true });
  readonly page = signal(1);
  readonly reloadTick = signal(0);
  readonly statsUpdating = signal(false);
  readonly selectedFile = signal<File | null>(null);
  readonly simulation = signal<DirectoryImportResult | null>(null);
  readonly importBusy = signal(false);
  readonly importError = signal<string | null>(null);
  readonly historyIssues = signal<Record<number, DirectoryImportResult['issues']>>({});
  readonly historyItems = signal<DirectoryImportSummary[]>([]);
  readonly historyLoad = signal<'idle' | 'loading' | 'success' | 'error'>('idle');
  readonly trackedImport = signal<DirectoryImportSummary | null>(null);
  readonly trackStale = signal(false);
  readonly persistStarted = signal(false);
  private statsRequestId = 0;
  private importsLoaded = false;
  private catalogRefreshedForImportId: number | null = null;

  readonly canAdminister = computed(() => this.auth.canAdminister('DIRECTORY'));
  readonly liveImport = computed(() => isLiveDirectoryImport(this.trackedImport()));
  readonly importBlocked = computed(
    () => isBlockingDirectoryImport(this.trackedImport()) || this.persistStarted()
  );
  readonly importProgress = computed<DirectoryImportProgressView | null>(() => {
    const item = this.trackedImport();
    if (!item) {
      return null;
    }
    return directoryImportProgress(item, { stale: this.trackStale() });
  });
  readonly showDryRunResult = computed(
    () => this.simulation()?.dryRun === true && !this.importBlocked()
  );
  readonly canSimulateImport = computed(() =>
    canSimulateDirectoryImport({
      file: this.selectedFile(),
      busy: this.importBusy(),
      blocked: this.importBlocked()
    })
  );

  readonly viewStats = toSignal(
    combineLatest({
      search: this.search.valueChanges.pipe(debounceTime(400), startWith(this.search.value)),
      presence: this.presence.valueChanges.pipe(startWith(this.presence.value)),
      reload: toObservable(this.reloadTick)
    }).pipe(
      debounceTime(0),
      switchMap((state): Observable<DirectoryStats | null> => {
        const requestId = ++this.statsRequestId;
        queueMicrotask(() => {
          if (requestId === this.statsRequestId) {
            this.statsUpdating.set(true);
          }
        });
        return this.api.stats(directoryStatsQuery(state)).pipe(
          map((stats): DirectoryStats | null => stats),
          catchError((): Observable<DirectoryStats | null> => EMPTY),
          finalize(() => {
            if (requestId === this.statsRequestId) {
              this.statsUpdating.set(false);
            }
          })
        );
      })
    ),
    { initialValue: null as DirectoryStats | null }
  );

  readonly stats = computed(() => this.viewStats());
  readonly hasActiveFilters = computed(() => {
    this.stats();
    this.statsUpdating();
    return directoryHasActiveFilters({
      search: this.search.value,
      presence: this.presence.value
    });
  });

  readonly result = toSignal(
    combineLatest({
      search: this.search.valueChanges.pipe(debounceTime(400), startWith(this.search.value)),
      presence: this.presence.valueChanges.pipe(startWith(this.presence.value)),
      page: toObservable(this.page),
      reload: toObservable(this.reloadTick)
    }).pipe(
      switchMap(({ search, presence, page }) =>
        toPageViewState(
          this.api.list({
            search,
            presence,
            page,
            pageSize: PAGE_SIZE
          }),
          PAGE_SIZE
        )
      )
    ),
    { initialValue: { status: 'loading' as const, data: emptyPage<DirectoryPerson>(PAGE_SIZE) } }
  );

  readonly activeImport = computed(() => this.trackedImport() ?? directoryActiveImport(this.historyItems()));
  readonly canConfirmImport = computed(() =>
    canConfirmDirectoryImport({
      file: this.selectedFile(),
      simulation: this.simulation(),
      busy: this.importBusy(),
      canAdminister: this.canAdminister(),
      blocked: this.importBlocked()
    })
  );

  constructor() {
    combineLatest([this.search.valueChanges, this.presence.valueChanges])
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.page.set(1));
    merge(
      timer(0, DIRECTORY_IMPORT_POLL_MS).pipe(
        filter(() => !this.importsLoaded || shouldPollDirectoryImport(this.trackedImport()))
      ),
      toObservable(this.tab).pipe(filter((tab) => tab === 'import' || tab === 'history'))
    )
      .pipe(
        exhaustMap(() => this.fetchImports()),
        takeUntilDestroyed()
      )
      .subscribe();
  }

  statusKey(item: Pick<DirectoryImportSummary, 'status' | 'interrupted'>): string {
    return directoryImportStatusKey(item);
  }

  progressTitleKey(kind: DirectoryImportPanelKind): string {
    if (kind === 'completed') {
      return 'directory.completedTitle';
    }
    if (kind === 'failed') {
      return 'directory.failedTitle';
    }
    if (kind === 'interrupted') {
      return 'directory.interruptedTitle';
    }
    return 'directory.runningTitle';
  }

  progressTone(kind: DirectoryImportPanelKind): OsBadgeTone {
    if (kind === 'completed') {
      return 'success';
    }
    if (kind === 'failed') {
      return 'danger';
    }
    if (kind === 'interrupted') {
      return 'warning';
    }
    return 'review';
  }

  failureKey(message: string | null): string | null {
    return directoryImportFailureKey(message);
  }

  onPageChange(page: number): void {
    this.page.set(page);
  }

  reload(): void {
    this.reloadTick.update((value) => value + 1);
    this.fetchImports().subscribe();
  }

  reloadCatalog(): void {
    this.reloadTick.update((value) => value + 1);
  }

  openHistory(): void {
    this.tab.set('history');
  }

  openCatalog(): void {
    this.tab.set('catalog');
    this.reloadCatalog();
  }

  clearFilters(): void {
    this.search.setValue('');
    this.presence.setValue('ACTIVE');
  }

  onFile(event: Event): void {
    if (this.importBlocked()) {
      return;
    }
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }
    this.selectedFile.set(file);
    this.simulation.set(null);
    this.importError.set(null);
  }

  simulate(): void {
    const file = this.selectedFile();
    if (!file || this.importBusy() || this.importBlocked()) {
      return;
    }
    this.importBusy.set(true);
    this.importError.set(null);
    this.api.dryRun(file).pipe(finalize(() => this.importBusy.set(false))).subscribe({
      next: (result) => {
        this.importBusy.set(false);
        this.simulation.set(result);
      },
      error: (error: unknown) => {
        this.importBusy.set(false);
        this.importError.set(directoryImportErrorKey(error, 'directory.errors.dryRun'));
      }
    });
  }

  confirmImport(): void {
    const file = this.selectedFile();
    if (
      !file ||
      !canConfirmDirectoryImport({
        file,
        simulation: this.simulation(),
        busy: this.importBusy(),
        canAdminister: this.canAdminister(),
        blocked: this.importBlocked()
      })
    ) {
      return;
    }
    this.importBusy.set(true);
    this.persistStarted.set(true);
    this.importError.set(null);
    this.api.importFile(file).pipe(finalize(() => this.importBusy.set(false))).subscribe({
      next: () => {
        this.importBusy.set(false);
        this.fetchImports().subscribe();
      },
      error: (error: unknown) => {
        this.importBusy.set(false);
        this.persistStarted.set(false);
        this.importError.set(directoryImportErrorKey(error, 'directory.errors.failed'));
      }
    });
  }

  recoverImport(importId: number): void {
    if (this.importBusy() || !this.canAdminister() || this.liveImport()) {
      return;
    }
    this.importBusy.set(true);
    this.importError.set(null);
    this.api.recoverImport(importId).pipe(finalize(() => this.importBusy.set(false))).subscribe({
      next: () => {
        this.importBusy.set(false);
        this.fetchImports().subscribe();
      },
      error: (error: unknown) => {
        this.importBusy.set(false);
        this.importError.set(directoryImportErrorKey(error, 'directory.errors.failed'));
      }
    });
  }

  loadIssues(importId: number): void {
    if (this.historyIssues()[importId]) {
      return;
    }
    this.api.issues(importId).subscribe({
      next: (issues) => this.historyIssues.update((current) => ({ ...current, [importId]: issues })),
      error: () => this.historyIssues.update((current) => ({ ...current, [importId]: [] }))
    });
  }

  private fetchImports(): Observable<DirectoryImportSummary[] | null> {
    if (this.historyLoad() === 'idle' && this.historyItems().length === 0) {
      this.historyLoad.set('loading');
    }
    return this.api.imports().pipe(
      map((items) => {
        this.applyImportSnapshot(items);
        return items;
      }),
      catchError(() => {
        if (this.trackedImport()) {
          this.trackStale.set(true);
        }
        if (this.historyItems().length === 0) {
          this.historyLoad.set('error');
        }
        return of(null);
      })
    );
  }

  private applyImportSnapshot(items: DirectoryImportSummary[]): void {
    const previous = this.trackedImport();
    const next = mergeDirectoryImportTrack(previous, items, false, {
      adoptLatest: this.persistStarted()
    });
    this.importsLoaded = true;
    if (items.length > 0 || previous == null) {
      this.historyItems.set(items);
    }
    this.historyLoad.set('success');
    this.trackedImport.set(next);
    this.trackStale.set(items.length === 0 && previous != null);
    if (next && next.status !== 'RUNNING') {
      this.persistStarted.set(false);
    }
    if (next?.status === 'RUNNING') {
      this.persistStarted.set(false);
    }
    if (
      previous?.status === 'RUNNING' &&
      next &&
      next.status === 'COMPLETED' &&
      this.catalogRefreshedForImportId !== next.id
    ) {
      this.catalogRefreshedForImportId = next.id;
      this.reloadCatalog();
    }
  }
}
