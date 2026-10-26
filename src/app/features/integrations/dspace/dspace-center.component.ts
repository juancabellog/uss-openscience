import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, HostListener, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, combineLatest, debounceTime, EMPTY, finalize, map, Observable, of, startWith, switchMap } from 'rxjs';
import { I18nService } from '../../../core/i18n/i18n.service';
import { AuthService } from '../../../core/services/auth.service';
import { NotificationService } from '../../../core/services/notification.service';
import { SyncApiService, SyncRun } from '../../../core/services/api/sync-api.service';
import { SyncMonitorService } from '../../../core/services/sync-monitor.service';
import { isActiveSyncStatus } from '../../../core/sync/sync-mode.util';
import { apiErrorCode } from '../../../core/utils/api-error';
import { downloadBlob, filenameFromDisposition } from '../../../core/utils/download';
import {
  DspaceCollectionList,
  DspaceCommunityList,
  DspaceBitstream,
  DspaceIncident,
  DspaceItemDetail,
  DspaceItemList,
  DspaceItemQuery,
  DspaceMatchKind,
  DspaceMetadata,
  DspaceSort,
  DspaceStats,
  DspaceTab,
  SortDirection
} from '../../../core/models/openscience.models';
import { DspaceApiService } from '../../../core/services/api/dspace-api.service';
import { emptyPage, toPageViewState } from '../../../core/utils/page-view-state';
import { DataTableShellComponent } from '../../../shared/components/data-table-shell/data-table-shell.component';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { ErrorStateComponent } from '../../../shared/components/error-state/error-state.component';
import { FilterBarComponent } from '../../../shared/components/filter-bar/filter-bar.component';
import { LoadingStateComponent } from '../../../shared/components/loading-state/loading-state.component';
import { MiniStatComponent } from '../../../shared/components/mini-stat/mini-stat.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { PaginationComponent } from '../../../shared/components/pagination/pagination.component';
import { SearchBoxComponent } from '../../../shared/components/search-box/search-box.component';
import { SortableHeaderComponent } from '../../../shared/components/sortable-header/sortable-header.component';
import { StatusBadgeComponent, OsBadgeTone } from '../../../shared/components/status-badge/status-badge.component';
import { I18N_HOST, I18N_IMPORTS } from '../../../shared/i18n/i18n.imports';
import { ariaSortValue, nextTableSort, TableSort } from '../../../shared/table/table-sort';

const PAGE_SIZES = [10, 25, 50] as const;
const DEFAULT_PAGE_SIZE = 10;
const INCIDENT_STAGES = ['COMMUNITIES', 'COLLECTIONS', 'DISCOVER', 'MATCH', 'BITSTREAMS', 'BITSTREAM_BACKFILL'] as const;

export type DspaceEmptyKind = 'never' | 'none' | 'filtered' | 'disabled' | null;
export type DspaceQuickFilter =
  | 'all'
  | 'linked'
  | 'unmatched'
  | 'review'
  | 'open'
  | 'metadata'
  | 'sharedDoi'
  | 'accessDiff'
  | 'withFile'
  | 'withoutFile'
  | 'inactive'
  | 'errors';

export function dspaceMatchKind(status: string | null | undefined): DspaceMatchKind {
  if (status === 'CONFIRMED' || status === 'SECONDARY') {
    return 'LINKED';
  }
  if (status === 'CANDIDATE') {
    return 'REVIEW';
  }
  return 'UNMATCHED';
}

export function dspaceAccessKey(status: string | null | undefined): string {
  switch (status) {
    case 'open.access':
      return 'dspace.accessStatus.openAccess';
    case 'metadata.only':
      return 'dspace.accessStatus.metadataOnly';
    case 'restricted':
      return 'dspace.accessStatus.restricted';
    case 'embargo':
      return 'dspace.accessStatus.embargo';
    default:
      return 'dspace.accessStatus.undetermined';
  }
}

export function dspaceAccessInventoryKnown(stats: DspaceStats | null | undefined): boolean {
  if (!stats || stats.items <= 0) {
    return true;
  }
  return (stats.unknownAccess ?? 0) < stats.items;
}

export function dspaceFileInventoryKnown(stats: DspaceStats | null | undefined): boolean {
  if (!stats || stats.items <= 0) {
    return true;
  }
  if ((stats.bitstreamCount ?? 0) > 0) {
    return true;
  }
  return (stats.unknownAccess ?? 0) < stats.items;
}

export function dspaceCanonicalAccessKey(canonical: string | null | undefined): string {
  switch (canonical) {
    case 'OPEN':
      return 'dspace.accessStatus.openAccess';
    case 'METADATA_ONLY':
      return 'dspace.accessStatus.metadataOnly';
    case 'RESTRICTED':
      return 'dspace.accessStatus.restricted';
    case 'EMBARGOED':
      return 'dspace.accessStatus.embargo';
    case 'CLOSED':
      return 'dspace.canonicalAccess.closed';
    case 'UNKNOWN':
      return 'dspace.canonicalAccess.unknown';
    default:
      return 'common.dash';
  }
}

export function dspaceComparisonKey(value: string | null | undefined): string {
  switch (value) {
    case 'CONSISTENT':
      return 'dspace.comparison.consistent';
    case 'DIFFERENCE':
      return 'dspace.comparison.difference';
    case 'NOT_EVALUABLE':
      return 'dspace.comparison.notEvaluable';
    default:
      return 'common.dash';
  }
}

export function dspaceComparisonTone(value: string | null | undefined): OsBadgeTone {
  switch (value) {
    case 'CONSISTENT':
      return 'success';
    case 'DIFFERENCE':
      return 'warning';
    default:
      return 'muted';
  }
}

export function dspaceIsPublicFile(file: { publicDownload: boolean; accessStatus: string | null }): boolean {
  return file.publicDownload || file.accessStatus === 'open.access';
}

export function dspaceIsBitstreamBackfill(run: {
  syncMode?: string | null;
  phase?: string | null;
  message?: string | null;
} | null | undefined): boolean {
  if (!run) {
    return false;
  }
  if (run.phase === 'BITSTREAMS' || run.phase === 'BITSTREAM_BACKFILL') {
    return true;
  }
  const message = (run.message ?? '').toUpperCase();
  if (message.includes('BITSTREAM_BACKFILL')) {
    return true;
  }
  return run.syncMode === 'RECOVERY';
}

export function dspaceCanRefreshFiles(
  canAdminister: boolean,
  enabled: boolean,
  configured: boolean,
  running: boolean
): boolean {
  return canAdminister && enabled && configured && !running;
}

export function dspaceFulltextKey(availability: string | null | undefined): string {
  if (!availability) {
    return 'common.dash';
  }
  return `dspace.fulltextAvailability.${availability}`;
}

export function dspaceEmptyKind(options: {
  loadError: boolean;
  enabled: boolean;
  configured: boolean;
  tablesAvailable: boolean;
  lastStatus: string | null | undefined;
  itemCount: number;
  filtered: boolean;
}): DspaceEmptyKind {
  if (options.loadError) {
    return null;
  }
  if (!options.enabled || !options.configured || !options.tablesAvailable) {
    return 'disabled';
  }
  if (options.itemCount > 0) {
    return null;
  }
  if (options.filtered) {
    return 'filtered';
  }
  if (!options.lastStatus) {
    return 'never';
  }
  return 'none';
}

export function dspaceHasActiveItemFilters(state: {
  search?: string;
  outcome?: string;
  accessStatus?: string;
  matchStatus?: string;
  matchMethod?: string;
  collectionUuid?: string;
  communityUuid?: string;
  doi?: string;
  sharedDoi?: boolean;
  hasFile?: string;
  accessComparison?: string;
}): boolean {
  return !!(
    state.search
    || state.accessStatus
    || state.matchStatus
    || state.matchMethod
    || state.collectionUuid
    || state.communityUuid
    || state.doi
    || state.sharedDoi
    || state.hasFile
    || state.accessComparison
    || (state.outcome && state.outcome !== 'FOUND')
  );
}

export function dspaceStatsQuery(state: {
  search?: string;
  outcome?: string;
  accessStatus?: string;
  matchStatus?: string;
  matchMethod?: string;
  collectionUuid?: string;
  communityUuid?: string;
  doi?: string;
  sharedDoi?: boolean;
  hasFile?: string;
  accessComparison?: string;
  page?: number;
  pageSize?: number;
  sort?: DspaceSort;
  direction?: SortDirection;
}): DspaceItemQuery {
  return {
    search: state.search || undefined,
    outcome: state.outcome || 'FOUND',
    accessStatus: state.accessStatus || undefined,
    matchStatus: state.matchStatus || undefined,
    matchMethod: state.matchMethod || undefined,
    collectionUuid: state.collectionUuid || undefined,
    communityUuid: state.communityUuid || undefined,
    doi: state.doi || undefined,
    sharedDoi: state.sharedDoi || undefined,
    hasFile: state.hasFile || undefined,
    accessComparison: state.accessComparison || undefined
  };
}

@Component({
  selector: 'os-dspace-center',
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
    SortableHeaderComponent,
    StatusBadgeComponent
  ],
  templateUrl: './dspace-center.component.html',
  styleUrl: './dspace-center.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DspaceCenterComponent {
  private readonly api = inject(DspaceApiService);
  private readonly i18n = inject(I18nService);
  private readonly notifications = inject(NotificationService);
  private readonly auth = inject(AuthService);
  private readonly syncApi = inject(SyncApiService);
  private readonly monitor = inject(SyncMonitorService);
  private lastFilesSyncId: string | null = null;
  readonly incidentStages = INCIDENT_STAGES;
  readonly confirmFiles = signal(false);
  readonly filesBusy = signal(false);
  readonly canAdminister = computed(() => this.auth.canAdminister('INTEGRATIONS'));
  readonly currentSync = computed(() => {
    const live = this.monitor.active('DSPACE');
    if (live && isActiveSyncStatus(live.status)) {
      return live;
    }
    const latest = this.monitor.latest('DSPACE');
    if (latest && isActiveSyncStatus(latest.status)) {
      return latest;
    }
    return live ?? this.monitor.dspace() ?? latest ?? null;
  });
  readonly dspaceRunning = computed(() => isActiveSyncStatus(this.currentSync()?.status));
  readonly filesRunning = computed(
    () => this.dspaceRunning() && dspaceIsBitstreamBackfill(this.currentSync())
  );
  readonly pageSizes: number[] = [10, 25, 50];

  readonly search = new FormControl('', { nonNullable: true });
  readonly tab = signal<DspaceTab>('items');
  readonly page = signal(1);
  readonly pageSize = signal(DEFAULT_PAGE_SIZE);
  readonly reloadTick = signal(0);
  readonly sortField = signal<DspaceSort>('syncedAt');
  readonly sortDirection = signal<SortDirection>('desc');
  readonly explicitSort = signal(false);
  readonly outcome = signal('FOUND');
  readonly accessStatus = signal('');
  readonly matchStatus = signal('');
  readonly matchMethod = signal('');
  readonly collectionUuid = signal('');
  readonly communityUuid = signal('');
  readonly doi = signal('');
  readonly sharedDoi = signal(false);
  readonly hasFile = signal('');
  readonly accessComparison = signal('');
  readonly incidentStage = signal('');
  readonly detail = signal<DspaceItemDetail | null>(null);
  readonly detailError = signal(false);
  readonly downloadError = signal(false);
  readonly selectedIds = signal<string[]>([]);
  readonly exportBusy = signal(false);
  readonly statsUpdating = signal(false);
  private statsRequestId = 0;

  readonly catalogStats = toSignal(
    toObservable(this.reloadTick).pipe(
      switchMap(() => this.api.stats().pipe(catchError(() => of(null))))
    ),
    { initialValue: null as DspaceStats | null }
  );
  readonly canRefreshFiles = computed(() =>
    dspaceCanRefreshFiles(
      this.canAdminister(),
      this.catalogStats()?.enabled !== false,
      this.catalogStats()?.configured !== false,
      this.dspaceRunning() || this.filesBusy()
    )
  );
  readonly filesProgress = computed(() => {
    const run = this.currentSync();
    if (!run || !isActiveSyncStatus(run.status)) {
      return null;
    }
    return {
      processed: run.processed ?? 0,
      total: run.totalExpected ?? 0,
      errors: run.errors ?? 0,
      files: dspaceIsBitstreamBackfill(run)
    };
  });
  readonly canCancelFiles = computed(
    () => this.canAdminister() && this.filesRunning() && !this.filesBusy()
  );

  readonly viewStats = toSignal(
    combineLatest({
      search: this.search.valueChanges.pipe(debounceTime(400), startWith(this.search.value)),
      outcome: toObservable(this.outcome),
      accessStatus: toObservable(this.accessStatus),
      matchStatus: toObservable(this.matchStatus),
      matchMethod: toObservable(this.matchMethod),
      collectionUuid: toObservable(this.collectionUuid),
      communityUuid: toObservable(this.communityUuid),
      doi: toObservable(this.doi),
      sharedDoi: toObservable(this.sharedDoi),
      hasFile: toObservable(this.hasFile),
      accessComparison: toObservable(this.accessComparison),
      reload: toObservable(this.reloadTick),
      tab: toObservable(this.tab)
    }).pipe(
      debounceTime(0),
      switchMap((state): Observable<DspaceStats | null> => {
        const requestId = ++this.statsRequestId;
        queueMicrotask(() => {
          if (requestId === this.statsRequestId) {
            this.statsUpdating.set(true);
          }
        });
        const search = state.tab === 'items' || state.tab === 'matches' ? state.search : '';
        return this.api.stats(dspaceStatsQuery({ ...state, search })).pipe(
          map((stats): DspaceStats | null => stats),
          catchError((): Observable<DspaceStats | null> => EMPTY),
          finalize(() => {
            if (requestId === this.statsRequestId) {
              this.statsUpdating.set(false);
            }
          })
        );
      })
    ),
    { initialValue: null as DspaceStats | null }
  );

  readonly stats = computed(() => this.viewStats());

  readonly catalogCommunities = toSignal(
    toObservable(this.reloadTick).pipe(
      switchMap(() =>
        this.api.communities({ page: 1, pageSize: 200, sort: 'name', direction: 'asc' }).pipe(
          map((page) => page.items),
          catchError(() => of([] as DspaceCommunityList[]))
        )
      )
    ),
    { initialValue: [] as DspaceCommunityList[] }
  );

  readonly catalogCollections = toSignal(
    toObservable(this.reloadTick).pipe(
      switchMap(() =>
        this.api.collections({ page: 1, pageSize: 200, sort: 'name', direction: 'asc' }).pipe(
          map((page) => page.items),
          catchError(() => of([] as DspaceCollectionList[]))
        )
      )
    ),
    { initialValue: [] as DspaceCollectionList[] }
  );

  readonly accessInventoryKnown = computed(() => dspaceAccessInventoryKnown(this.stats()));
  readonly fileInventoryKnown = computed(() => dspaceFileInventoryKnown(this.stats()));

  readonly history = toSignal(
    toObservable(this.reloadTick).pipe(
      switchMap(() => this.api.history().pipe(catchError(() => of([] as SyncRun[]))))
    ),
    { initialValue: [] as SyncRun[] }
  );

  readonly itemsResult = toSignal(
    combineLatest({
      search: this.search.valueChanges.pipe(debounceTime(400), startWith(this.search.value)),
      outcome: toObservable(this.outcome),
      accessStatus: toObservable(this.accessStatus),
      matchStatus: toObservable(this.matchStatus),
      matchMethod: toObservable(this.matchMethod),
      collectionUuid: toObservable(this.collectionUuid),
      communityUuid: toObservable(this.communityUuid),
      doi: toObservable(this.doi),
      sharedDoi: toObservable(this.sharedDoi),
      hasFile: toObservable(this.hasFile),
      accessComparison: toObservable(this.accessComparison),
      sortField: toObservable(this.sortField),
      sortDirection: toObservable(this.sortDirection),
      page: toObservable(this.page),
      pageSize: toObservable(this.pageSize),
      reload: toObservable(this.reloadTick),
      tab: toObservable(this.tab)
    }).pipe(
      debounceTime(0),
      switchMap((state) => {
        if (state.tab !== 'items' && state.tab !== 'matches') {
          return of({ status: 'success' as const, data: emptyPage<DspaceItemList>(state.pageSize) });
        }
        const query = this.toItemQuery(state);
        const source = state.tab === 'matches' ? this.api.matches(query) : this.api.items(query);
        return toPageViewState(source, state.pageSize);
      })
    ),
    { initialValue: { status: 'loading' as const, data: emptyPage<DspaceItemList>(DEFAULT_PAGE_SIZE) } }
  );

  readonly collectionsResult = toSignal(
    combineLatest({
      search: this.search.valueChanges.pipe(debounceTime(400), startWith(this.search.value)),
      sortField: toObservable(this.sortField),
      sortDirection: toObservable(this.sortDirection),
      page: toObservable(this.page),
      pageSize: toObservable(this.pageSize),
      reload: toObservable(this.reloadTick),
      tab: toObservable(this.tab)
    }).pipe(
      debounceTime(0),
      switchMap((state) =>
        state.tab === 'collections'
          ? toPageViewState(this.api.collections(this.toCatalogQuery(state)), state.pageSize)
          : of({ status: 'success' as const, data: emptyPage<DspaceCollectionList>(state.pageSize) })
      )
    ),
    { initialValue: { status: 'loading' as const, data: emptyPage<DspaceCollectionList>(DEFAULT_PAGE_SIZE) } }
  );

  readonly communitiesResult = toSignal(
    combineLatest({
      search: this.search.valueChanges.pipe(debounceTime(400), startWith(this.search.value)),
      sortField: toObservable(this.sortField),
      sortDirection: toObservable(this.sortDirection),
      page: toObservable(this.page),
      pageSize: toObservable(this.pageSize),
      reload: toObservable(this.reloadTick),
      tab: toObservable(this.tab)
    }).pipe(
      debounceTime(0),
      switchMap((state) =>
        state.tab === 'communities'
          ? toPageViewState(this.api.communities(this.toCatalogQuery(state)), state.pageSize)
          : of({ status: 'success' as const, data: emptyPage<DspaceCommunityList>(state.pageSize) })
      )
    ),
    { initialValue: { status: 'loading' as const, data: emptyPage<DspaceCommunityList>(DEFAULT_PAGE_SIZE) } }
  );

  readonly incidentsResult = toSignal(
    combineLatest({
      stage: toObservable(this.incidentStage),
      page: toObservable(this.page),
      pageSize: toObservable(this.pageSize),
      reload: toObservable(this.reloadTick),
      tab: toObservable(this.tab)
    }).pipe(
      debounceTime(0),
      switchMap((state) =>
        state.tab === 'incidents'
          ? toPageViewState(
              this.api.errors({ stage: state.stage, page: state.page, pageSize: state.pageSize }),
              state.pageSize
            )
          : of({ status: 'success' as const, data: emptyPage<DspaceIncident>(state.pageSize) })
      )
    ),
    { initialValue: { status: 'loading' as const, data: emptyPage<DspaceIncident>(DEFAULT_PAGE_SIZE) } }
  );

  readonly quickFilter = computed((): DspaceQuickFilter => {
    if (this.outcome() === 'MISSING') {
      return 'inactive';
    }
    if (this.outcome() === 'ERROR') {
      return 'errors';
    }
    if (this.sharedDoi()) {
      return 'sharedDoi';
    }
    if (this.accessComparison() === 'DIFFERENCE') {
      return 'accessDiff';
    }
    if (this.hasFile() === 'YES') {
      return 'withFile';
    }
    if (this.hasFile() === 'NO') {
      return 'withoutFile';
    }
    if (this.matchStatus() === 'LINKED') {
      return 'linked';
    }
    if (this.matchStatus() === 'UNMATCHED') {
      return 'unmatched';
    }
    if (this.matchStatus() === 'REVIEW') {
      return 'review';
    }
    if (this.accessStatus() === 'open.access') {
      return 'open';
    }
    if (this.accessStatus() === 'metadata.only') {
      return 'metadata';
    }
    return 'all';
  });

  readonly selectedCount = computed(() => this.selectedIds().length);
  readonly pageSelected = computed(() => {
    const items = this.itemsResult().data.items;
    return items.length > 0 && items.every((item) => this.selectedIds().includes(item.uuid));
  });

  constructor() {
    this.search.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.resetPage(true));
    effect(() => {
      const run = this.monitor.dspace() ?? this.monitor.latest('DSPACE') ?? this.monitor.lastCompletion();
      if (!run || !dspaceIsBitstreamBackfill(run) || run.syncId === this.lastFilesSyncId) {
        return;
      }
      if (
        run.status === 'COMPLETED'
        || run.status === 'COMPLETED_WITH_WARNINGS'
        || run.status === 'FAILED'
        || run.status === 'CANCELLED'
      ) {
        this.lastFilesSyncId = run.syncId;
        this.reloadTick.update((value) => value + 1);
      }
    });
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.confirmFiles()) {
      this.confirmFiles.set(false);
      return;
    }
    if (this.detail()) {
      this.closeDetail();
    }
  }

  readonly emptyKind = computed(() => {
    const tab = this.tab();
    const vm =
      tab === 'collections'
        ? this.collectionsResult()
        : tab === 'communities'
          ? this.communitiesResult()
          : tab === 'incidents'
            ? this.incidentsResult()
            : this.itemsResult();
    const catalog = this.catalogStats();
    return dspaceEmptyKind({
      loadError: vm.status === 'error',
      enabled: catalog?.enabled !== false,
      configured: catalog?.configured !== false,
      tablesAvailable: catalog?.tablesAvailable !== false,
      lastStatus: catalog?.lastStatus,
      itemCount: vm.data.items.length,
      filtered: this.hasActiveFilters()
    });
  });

  readonly hasActiveItemFilters = computed(() =>
    dspaceHasActiveItemFilters({
      search: this.tab() === 'items' || this.tab() === 'matches' ? this.search.value : '',
      outcome: this.outcome(),
      accessStatus: this.accessStatus(),
      matchStatus: this.matchStatus(),
      matchMethod: this.matchMethod(),
      collectionUuid: this.collectionUuid(),
      communityUuid: this.communityUuid(),
      doi: this.doi(),
      sharedDoi: this.sharedDoi(),
      hasFile: this.hasFile(),
      accessComparison: this.accessComparison()
    })
  );

  selectTab(tab: DspaceTab): void {
    this.tab.set(tab);
    this.resetPage(true);
    this.detail.set(null);
    this.sortField.set(tab === 'collections' || tab === 'communities' ? 'syncedAt' : 'syncedAt');
    this.sortDirection.set('desc');
    this.explicitSort.set(false);
  }

  applyQuick(filter: DspaceQuickFilter): void {
    this.resetPage(true);
    this.matchMethod.set('');
    this.doi.set('');
    this.sharedDoi.set(false);
    this.hasFile.set('');
    this.accessComparison.set('');
    if (filter === 'all') {
      this.collectionUuid.set('');
      this.communityUuid.set('');
    }
    switch (filter) {
      case 'linked':
        this.outcome.set('FOUND');
        this.matchStatus.set('LINKED');
        this.accessStatus.set('');
        break;
      case 'unmatched':
        this.outcome.set('FOUND');
        this.matchStatus.set('UNMATCHED');
        this.accessStatus.set('');
        break;
      case 'review':
        this.outcome.set('FOUND');
        this.matchStatus.set('REVIEW');
        this.accessStatus.set('');
        break;
      case 'open':
        this.outcome.set('FOUND');
        this.matchStatus.set('');
        this.accessStatus.set('open.access');
        break;
      case 'metadata':
        this.outcome.set('FOUND');
        this.matchStatus.set('');
        this.accessStatus.set('metadata.only');
        break;
      case 'sharedDoi':
        this.outcome.set('FOUND');
        this.matchStatus.set('');
        this.accessStatus.set('');
        this.sharedDoi.set(true);
        break;
      case 'accessDiff':
        this.outcome.set('FOUND');
        this.matchStatus.set('');
        this.accessStatus.set('');
        this.accessComparison.set('DIFFERENCE');
        break;
      case 'withFile':
        this.outcome.set('FOUND');
        this.matchStatus.set('');
        this.accessStatus.set('');
        this.hasFile.set('YES');
        break;
      case 'withoutFile':
        this.outcome.set('FOUND');
        this.matchStatus.set('');
        this.accessStatus.set('');
        this.hasFile.set('NO');
        break;
      case 'inactive':
        this.outcome.set('MISSING');
        this.matchStatus.set('');
        this.accessStatus.set('');
        break;
      case 'errors':
        this.outcome.set('ERROR');
        this.matchStatus.set('');
        this.accessStatus.set('');
        break;
      default:
        this.outcome.set('FOUND');
        this.matchStatus.set('');
        this.accessStatus.set('');
    }
  }

  clearFilters(): void {
    this.search.setValue('');
    this.incidentStage.set('');
    this.applyQuick('all');
  }

  onSort(field: string): void {
    const fallback: TableSort = { field: 'syncedAt', direction: 'desc' };
    const next = nextTableSort(
      { field: this.sortField(), direction: this.sortDirection() },
      field,
      fallback,
      this.explicitSort()
    );
    this.sortField.set(next.sort.field as DspaceSort);
    this.sortDirection.set(next.sort.direction);
    this.explicitSort.set(next.explicit);
    this.resetPage(false);
  }

  ariaSort(field: string): 'ascending' | 'descending' | 'none' {
    return ariaSortValue(field, this.sortField(), this.sortDirection());
  }

  goToPage(page: number): void {
    this.page.set(page);
  }

  setPageSize(size: number): void {
    if (!PAGE_SIZES.includes(size as (typeof PAGE_SIZES)[number])) {
      return;
    }
    this.pageSize.set(size);
    this.resetPage(false);
  }

  toggleSelect(uuid: string, checked: boolean): void {
    this.selectedIds.update((current) => {
      if (checked) {
        return current.includes(uuid) ? current : [...current, uuid];
      }
      return current.filter((id) => id !== uuid);
    });
  }

  togglePage(checked: boolean): void {
    const visible = this.itemsResult().data.items.map((item) => item.uuid);
    this.selectedIds.update((current) => {
      if (checked) {
        const next = new Set(current);
        visible.forEach((id) => next.add(id));
        return [...next];
      }
      return current.filter((id) => !visible.includes(id));
    });
  }

  isSelected(uuid: string): boolean {
    return this.selectedIds().includes(uuid);
  }

  exportExcel(): void {
    if (this.exportBusy() || (this.tab() !== 'items' && this.tab() !== 'matches')) {
      return;
    }
    this.exportBusy.set(true);
    const selected = this.selectedIds();
    const query = selected.length
      ? {
          uuids: selected,
          sort: this.sortField(),
          direction: this.sortDirection(),
          page: 1,
          pageSize: selected.length
        }
      : this.toItemQuery({
          search: this.search.value,
          outcome: this.outcome(),
          accessStatus: this.accessStatus(),
          matchStatus: this.matchStatus(),
          matchMethod: this.matchMethod(),
          collectionUuid: this.collectionUuid(),
          communityUuid: this.communityUuid(),
          doi: this.doi(),
          sharedDoi: this.sharedDoi(),
          hasFile: this.hasFile(),
          accessComparison: this.accessComparison(),
          sortField: this.sortField(),
          sortDirection: this.sortDirection(),
          page: 1,
          pageSize: 20000
        });
    this.api.export(query).subscribe({
      next: (response) => {
        this.exportBusy.set(false);
        const blob = response.body;
        if (!blob) {
          return;
        }
        downloadBlob(
          blob,
          filenameFromDisposition(response.headers.get('Content-Disposition'), 'openscience-dspace.xlsx')
        );
      },
      error: (error: HttpErrorResponse) => {
        this.exportBusy.set(false);
        const code = apiErrorCode(error);
        this.notifications.show(
          'error',
          this.i18n.translate('dspace.exportFailedTitle'),
          this.i18n.translate(code === 'EXPORT_EMPTY' ? 'dspace.exportEmpty' : 'dspace.exportFailed')
        );
      }
    });
  }

  openFilesConfirm(): void {
    if (!this.canRefreshFiles()) {
      return;
    }
    this.confirmFiles.set(true);
  }

  closeFilesConfirm(): void {
    if (this.filesBusy()) {
      return;
    }
    this.confirmFiles.set(false);
  }

  startFilesRefresh(): void {
    if (!this.canRefreshFiles() || this.filesBusy()) {
      return;
    }
    this.filesBusy.set(true);
    this.syncApi.startDspaceBitstreams().subscribe({
      next: (run) => {
        this.filesBusy.set(false);
        this.confirmFiles.set(false);
        this.monitor.watch(run);
        this.notifications.show(
          'warning',
          this.i18n.translate('dspace.files.action'),
          this.i18n.translate('dspace.files.started', {
            total: run.totalExpected ?? 0
          })
        );
      },
      error: (error: HttpErrorResponse) => {
        this.filesBusy.set(false);
        const code = apiErrorCode(error);
        if (error.status === 409 || code === 'SYNC_ALREADY_RUNNING') {
          this.confirmFiles.set(false);
          this.monitor.refreshRunning();
          this.notifications.show('warning', this.i18n.translate('dspace.title'), this.i18n.translate('sync.alreadyRunningDspace'));
          return;
        }
        const messageKey =
          code === 'DSPACE_DISABLED'
            ? 'sync.dspaceDisabled'
            : code === 'DSPACE_NOT_CONFIGURED'
              ? 'sync.dspaceNotConfigured'
              : code === 'DSPACE_TABLES_UNAVAILABLE'
                ? 'sync.dspaceTablesUnavailable'
                : 'dspace.files.startFailed';
        this.notifications.show('error', this.i18n.translate('dspace.files.action'), this.i18n.translate(messageKey));
      }
    });
  }

  cancelFilesRefresh(): void {
    const run = this.currentSync();
    if (!this.canAdminister() || !this.dspaceRunning() || this.filesBusy() || !run?.syncId) {
      return;
    }
    this.filesBusy.set(true);
    this.syncApi.cancel(run.syncId).subscribe({
      next: (updated) => {
        this.filesBusy.set(false);
        this.monitor.watch(updated);
      },
      error: (error: HttpErrorResponse) => {
        this.filesBusy.set(false);
        const code = apiErrorCode(error);
        this.notifications.show(
          'error',
          this.i18n.translate('dspace.files.action'),
          this.i18n.translate(code === 'SYNC_NOT_RUNNING' ? 'sync.cancelNotRunning' : 'sync.cancelFailed')
        );
      }
    });
  }

  openItem(row: DspaceItemList, event?: Event): void {
    event?.stopPropagation();
    this.detailError.set(false);
    this.downloadError.set(false);
    this.api.detail(row.uuid).subscribe({
      next: (detail) => this.detail.set(detail),
      error: () => {
        this.detail.set(null);
        this.detailError.set(true);
      }
    });
  }

  closeDetail(): void {
    this.detail.set(null);
    this.detailError.set(false);
    this.downloadError.set(false);
  }

  filterCollection(row: DspaceCollectionList): void {
    this.collectionUuid.set(row.uuid);
    this.communityUuid.set('');
    this.outcome.set('FOUND');
    this.tab.set('items');
    this.resetPage(true);
  }

  filterCommunity(row: DspaceCommunityList): void {
    this.communityUuid.set(row.uuid);
    this.collectionUuid.set('');
    this.outcome.set('FOUND');
    this.tab.set('items');
    this.resetPage(true);
  }

  clearStructureFilter(): void {
    this.collectionUuid.set('');
    this.communityUuid.set('');
    this.resetPage(true);
  }

  filterSharedDoi(doi: string, event?: Event): void {
    event?.stopPropagation();
    this.outcome.set('FOUND');
    this.matchStatus.set('');
    this.accessStatus.set('');
    this.sharedDoi.set(false);
    this.hasFile.set('');
    this.accessComparison.set('');
    this.doi.set(doi);
    this.tab.set('items');
    this.resetPage(true);
    this.detail.set(null);
  }

  clearDoiFilter(): void {
    this.doi.set('');
    this.resetPage(true);
  }

  setAccess(value: string): void {
    this.accessStatus.set(value);
    this.resetPage(true);
  }

  setCommunity(value: string): void {
    this.communityUuid.set(value);
    this.resetPage(true);
  }

  setCollection(value: string): void {
    this.collectionUuid.set(value);
    this.resetPage(true);
  }

  setMatchStatus(value: string): void {
    this.matchStatus.set(value);
    this.resetPage(true);
  }

  setMatchMethod(value: string): void {
    this.matchMethod.set(value);
    this.resetPage(true);
  }

  setHasFile(value: string): void {
    this.hasFile.set(value);
    this.resetPage(true);
  }

  setSharedDoi(value: string): void {
    this.sharedDoi.set(value === 'YES');
    this.resetPage(true);
  }

  setAccessComparison(value: string): void {
    this.accessComparison.set(value);
    this.resetPage(true);
  }

  setIncidentStage(value: string): void {
    this.incidentStage.set(value);
    this.resetPage(true);
  }

  private resetPage(clearSelection: boolean): void {
    this.page.set(1);
    if (clearSelection) {
      this.selectedIds.set([]);
    }
  }

  matchKind(status: string | null | undefined): DspaceMatchKind {
    return dspaceMatchKind(status);
  }

  matchTone(status: string | null | undefined): OsBadgeTone {
    switch (dspaceMatchKind(status)) {
      case 'LINKED':
        return 'success';
      case 'REVIEW':
        return 'review';
      default:
        return 'muted';
    }
  }

  matchLabel(status: string | null | undefined): string {
    return `dspace.match.${dspaceMatchKind(status)}`;
  }

  accessKey(status: string | null | undefined): string {
    return dspaceAccessKey(status);
  }

  canonicalAccessKey(canonical: string | null | undefined): string {
    return dspaceCanonicalAccessKey(canonical);
  }

  comparisonKey(value: string | null | undefined): string {
    return dspaceComparisonKey(value);
  }

  comparisonTone(value: string | null | undefined): OsBadgeTone {
    return dspaceComparisonTone(value);
  }

  originalFiles(files: DspaceBitstream[] | null | undefined): DspaceBitstream[] {
    return (files ?? []).filter((file) => (file.bundleName || '').toUpperCase() === 'ORIGINAL');
  }

  otherFiles(files: DspaceBitstream[] | null | undefined): DspaceBitstream[] {
    return (files ?? []).filter((file) => (file.bundleName || '').toUpperCase() !== 'ORIGINAL');
  }

  isPublicFile(file: DspaceBitstream): boolean {
    return dspaceIsPublicFile(file);
  }

  rightsMetadata(detail: DspaceItemDetail): DspaceMetadata[] {
    const fields = new Set(['dc.rights', 'dc.rights.uri', 'dcterms.accessRights', 'dc.rights.accessRights']);
    return (detail.metadata ?? []).filter((field) => field.field != null && fields.has(field.field));
  }

  downloadFile(event: Event, itemUuid: string, file: DspaceBitstream): void {
    event.stopPropagation();
    this.downloadError.set(false);
    this.api.downloadBitstream(itemUuid, file.uuid).subscribe({
      next: (response) => {
        const blob = response.body;
        if (!blob) {
          this.downloadError.set(true);
          return;
        }
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = file.name || 'bitstream';
        link.click();
        URL.revokeObjectURL(url);
      },
      error: () => this.downloadError.set(true)
    });
  }

  fulltextKey(availability: string | null | undefined): string {
    return dspaceFulltextKey(availability);
  }

  methodKey(method: string | null | undefined): string {
    if (!method) {
      return 'common.dash';
    }
    return `dspace.method.${method}`;
  }

  outcomeKey(outcome: string | null | undefined): string {
    if (outcome === 'FOUND') {
      return 'dspace.outcome.active';
    }
    if (outcome === 'MISSING') {
      return 'dspace.outcome.inactive';
    }
    if (outcome === 'ERROR') {
      return 'dspace.outcome.error';
    }
    return 'common.dash';
  }

  outcomeTone(outcome: string | null | undefined): OsBadgeTone {
    if (outcome === 'FOUND') {
      return 'ok';
    }
    if (outcome === 'MISSING') {
      return 'muted';
    }
    return 'danger';
  }

  statusKey(status: string | null | undefined): string {
    if (!status) {
      return 'sync.never';
    }
    return `sync.status.${status}`;
  }

  modeKey(mode: string | null | undefined): string {
    if (mode === 'INCREMENTAL') {
      return 'sync.mode.incremental';
    }
    if (mode === 'RECOVERY') {
      return 'sync.mode.recovery';
    }
    return 'sync.mode.full';
  }

  doiHref(doi: string | null | undefined): string | null {
    if (!doi) {
      return null;
    }
    return 'https://doi.org/' + doi.replace(/^https?:\/\/(dx\.)?doi\.org\//i, '');
  }

  formatBytes(value: number | null | undefined): string {
    if (value == null) {
      return '—';
    }
    if (value < 1024) {
      return `${value} B`;
    }
    if (value < 1024 * 1024) {
      return `${(value / 1024).toFixed(1)} KB`;
    }
    return `${(value / (1024 * 1024)).toFixed(1)} MB`;
  }

  private hasActiveFilters(): boolean {
    return (
      dspaceHasActiveItemFilters({
        search: this.search.value,
        outcome: this.outcome(),
        accessStatus: this.accessStatus(),
        matchStatus: this.matchStatus(),
        matchMethod: this.matchMethod(),
        collectionUuid: this.collectionUuid(),
        communityUuid: this.communityUuid(),
        doi: this.doi(),
        sharedDoi: this.sharedDoi(),
        hasFile: this.hasFile(),
        accessComparison: this.accessComparison()
      }) || !!this.incidentStage()
    );
  }

  private toItemQuery(state: {
    search: string;
    outcome: string;
    accessStatus: string;
    matchStatus: string;
    matchMethod: string;
    collectionUuid: string;
    communityUuid: string;
    doi: string;
    sharedDoi: boolean;
    hasFile: string;
    accessComparison: string;
    sortField: DspaceSort;
    sortDirection: SortDirection;
    page: number;
    pageSize: number;
  }): DspaceItemQuery {
    return {
      ...dspaceStatsQuery(state),
      sort: state.sortField,
      direction: state.sortDirection,
      page: state.page,
      pageSize: state.pageSize
    };
  }

  private toCatalogQuery(state: {
    search: string;
    sortField: DspaceSort;
    sortDirection: SortDirection;
    page: number;
    pageSize: number;
  }): DspaceItemQuery {
    return {
      search: state.search || undefined,
      sort: state.sortField,
      direction: state.sortDirection,
      page: state.page,
      pageSize: state.pageSize
    };
  }
}
