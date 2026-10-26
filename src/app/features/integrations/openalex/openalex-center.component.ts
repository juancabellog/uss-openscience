import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, combineLatest, debounceTime, distinctUntilChanged, map, of, startWith, switchMap } from 'rxjs';
import {
  OpenAlexAuthorQuery,
  OpenAlexAuthorSort,
  OpenAlexAuthorSummary,
  OpenAlexFilters,
  OpenAlexMatchDetail,
  OpenAlexMatchQuery,
  OpenAlexMatchSort,
  OpenAlexMatchSummary,
  OpenAlexMatchWork,
  OpenAlexMatchWorkEvidence,
  OpenAlexStats,
  OpenAlexWorkQuery,
  OpenAlexWorkSort,
  OpenAlexWorkSummary,
  SortDirection
} from '../../../core/models/openscience.models';
import { AuthService } from '../../../core/services/auth.service';
import { OpenAlexApiService } from '../../../core/services/api/openalex-api.service';
import { SyncRun } from '../../../core/services/api/sync-api.service';
import { SyncDetailService } from '../../../core/services/sync-detail.service';
import { SyncMonitorService } from '../../../core/services/sync-monitor.service';
import {
  OpenAlexTab,
  isOpenAlexTab,
  openAlexHistoryShowsRecovery,
  openAlexHistoryStatusKey,
  sortOpenAlexHistoryDesc
} from '../../../core/sync/openalex-history.util';
import { openAlexPhaseLabelKey } from '../../../core/sync/openalex-recovery.util';
import { syncModeLabelKey } from '../../../core/sync/sync-mode.util';
import { emptyPage, toPageViewState } from '../../../core/utils/page-view-state';
import { DataTableShellComponent } from '../../../shared/components/data-table-shell/data-table-shell.component';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { ErrorStateComponent } from '../../../shared/components/error-state/error-state.component';
import { EvidenceCodeBadgesComponent } from '../../../shared/components/evidence-code-badges/evidence-code-badges.component';
import { FilterBarComponent } from '../../../shared/components/filter-bar/filter-bar.component';
import { LoadingStateComponent } from '../../../shared/components/loading-state/loading-state.component';
import { MiniStatComponent } from '../../../shared/components/mini-stat/mini-stat.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { PaginationComponent } from '../../../shared/components/pagination/pagination.component';
import { SearchBoxComponent } from '../../../shared/components/search-box/search-box.component';
import { SortableHeaderComponent } from '../../../shared/components/sortable-header/sortable-header.component';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge.component';
import { SyncExportBarComponent } from '../../../shared/components/sync-export-bar/sync-export-bar.component';
import { I18N_HOST, I18N_IMPORTS } from '../../../shared/i18n/i18n.imports';
import { nextTableSort, TableSort } from '../../../shared/table/table-sort';
import {
  OPENALEX_DRAWER_CLOSE_BUTTON_CLASS,
  OPENALEX_EVIDENCE_PAGE_SIZE,
  displayOpenAlexValue,
  joinOpenAlexName,
  openAlexEvidenceBadges,
  openAlexEvidenceUnits,
  openAlexOrcidComparisonCode,
  openAlexOrcidComparisonKey,
  openAlexOrcidSyncAllowed,
  openAlexPrimaryAffiliation,
  openAlexStatusTone,
  showOpenAlexEvidenceSearch
} from './openalex-evidence.util';

export { OPENALEX_DRAWER_CLOSE_BUTTON_CLASS };

export const OPENALEX_PAGE_SIZE = 25;
const PAGE_SIZE = OPENALEX_PAGE_SIZE;
const EMPTY_FILTERS: OpenAlexFilters = { statuses: [], matchStatuses: [], workTypes: [], positions: [] };

type OpenAlexHistoryState = {
  status: 'loading' | 'ready' | 'error';
  items: SyncRun[];
};

const EMPTY_HISTORY: OpenAlexHistoryState = { status: 'loading', items: [] };

export type { OpenAlexTab };
export type OpenAlexEmptyKind = 'never' | 'none' | 'filtered' | 'disabled' | null;

export function canShowOpenAlexSync(canAdminister: boolean, enabled: boolean, configured: boolean): boolean {
  return canAdminister && enabled && configured;
}

export function openAlexEmptyKind(options: {
  loadError: boolean;
  enabled: boolean;
  configured: boolean;
  tablesAvailable: boolean;
  lastStatus: string | null;
  evaluated: number;
  itemCount: number;
  filtered: boolean;
}): OpenAlexEmptyKind {
  if (options.loadError) {
    return null;
  }
  if (!options.enabled || !options.configured || !options.tablesAvailable) {
    return 'disabled';
  }
  if (!options.lastStatus && options.evaluated === 0 && options.itemCount === 0) {
    return 'never';
  }
  if (options.itemCount === 0 && options.filtered) {
    return 'filtered';
  }
  if (options.itemCount === 0) {
    return 'none';
  }
  return null;
}

export function openAlexStatusLabel(status: string | null | undefined): string {
  if (status === 'CONFIRMED') {
    return 'openalex.status.CONFIRMED';
  }
  if (status === 'STRONG') {
    return 'openalex.status.STRONG';
  }
  if (status === 'MANUAL_REVIEW') {
    return 'openalex.status.MANUAL_REVIEW';
  }
  if (status === 'CONFLICT') {
    return 'openalex.status.CONFLICT';
  }
  if (status === 'REJECTED') {
    return 'openalex.status.REJECTED';
  }
  return 'common.dash';
}

@Component({
  selector: 'os-openalex-center',
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
    StatusBadgeComponent,
    EvidenceCodeBadgesComponent,
    SyncExportBarComponent
  ],
  templateUrl: './openalex-center.component.html',
  styleUrl: './openalex-center.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class OpenAlexCenterComponent {
  private readonly api = inject(OpenAlexApiService);
  private readonly monitor = inject(SyncMonitorService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly detailService = inject(SyncDetailService);
  readonly auth = inject(AuthService);

  readonly tab = signal<OpenAlexTab>('matches');
  readonly search = new FormControl('', { nonNullable: true });
  readonly status = new FormControl('', { nonNullable: true });
  readonly position = new FormControl('', { nonNullable: true });
  readonly hasOrcid = new FormControl('', { nonNullable: true });
  readonly ussAffiliation = new FormControl('', { nonNullable: true });
  readonly minWorks = new FormControl('', { nonNullable: true });
  readonly page = signal(1);
  readonly reloadTick = signal(0);
  readonly sortField = signal<OpenAlexMatchSort | OpenAlexWorkSort | OpenAlexAuthorSort>('works');
  readonly sortDirection = signal<SortDirection>('desc');
  readonly explicitSort = signal(false);
  readonly detail = signal<OpenAlexMatchDetail | null>(null);
  readonly evidenceSearch = new FormControl('', { nonNullable: true });
  readonly evidencePage = signal(1);
  readonly evidenceLoading = signal(false);
  readonly actionBusy = signal(false);
  readonly actionMessage = signal<string | null>(null);
  readonly orcidSyncConfirm = signal<OpenAlexMatchDetail | null>(null);
  readonly drawerCloseButtonClass = OPENALEX_DRAWER_CLOSE_BUTTON_CLASS;

  readonly stats = toSignal(
    toObservable(this.reloadTick).pipe(
      switchMap(() => this.api.stats().pipe(catchError(() => of(null))))
    ),
    { initialValue: null as OpenAlexStats | null }
  );

  readonly filters = toSignal(
    toObservable(this.reloadTick).pipe(
      switchMap(() => this.api.filters().pipe(catchError(() => of(EMPTY_FILTERS))))
    ),
    { initialValue: EMPTY_FILTERS }
  );

  readonly matches = toSignal(
    combineLatest({
      search: this.search.valueChanges.pipe(debounceTime(400), startWith(this.search.value)),
      status: this.status.valueChanges.pipe(startWith(this.status.value)),
      position: this.position.valueChanges.pipe(startWith(this.position.value)),
      hasOrcid: this.hasOrcid.valueChanges.pipe(startWith(this.hasOrcid.value)),
      ussAffiliation: this.ussAffiliation.valueChanges.pipe(startWith(this.ussAffiliation.value)),
      minWorks: this.minWorks.valueChanges.pipe(startWith(this.minWorks.value)),
      sortField: toObservable(this.sortField),
      sortDirection: toObservable(this.sortDirection),
      page: toObservable(this.page),
      reload: toObservable(this.reloadTick),
      tab: toObservable(this.tab)
    }).pipe(
      debounceTime(0),
      switchMap((state) =>
        state.tab === 'matches'
          ? toPageViewState(this.api.matches(this.toMatchQuery(state)), PAGE_SIZE)
          : of({ status: 'ready' as const, data: emptyPage<OpenAlexMatchSummary>(PAGE_SIZE) })
      )
    ),
    { initialValue: { status: 'loading' as const, data: emptyPage<OpenAlexMatchSummary>(PAGE_SIZE) } }
  );

  readonly works = toSignal(
    combineLatest({
      search: this.search.valueChanges.pipe(debounceTime(400), startWith(this.search.value)),
      status: this.status.valueChanges.pipe(startWith(this.status.value)),
      sortField: toObservable(this.sortField),
      sortDirection: toObservable(this.sortDirection),
      page: toObservable(this.page),
      reload: toObservable(this.reloadTick),
      tab: toObservable(this.tab)
    }).pipe(
      debounceTime(0),
      switchMap((state) =>
        state.tab === 'works'
          ? toPageViewState(this.api.works(this.toWorkQuery(state)), PAGE_SIZE)
          : of({ status: 'ready' as const, data: emptyPage<OpenAlexWorkSummary>(PAGE_SIZE) })
      )
    ),
    { initialValue: { status: 'loading' as const, data: emptyPage<OpenAlexWorkSummary>(PAGE_SIZE) } }
  );

  readonly authors = toSignal(
    combineLatest({
      search: this.search.valueChanges.pipe(debounceTime(400), startWith(this.search.value)),
      sortField: toObservable(this.sortField),
      sortDirection: toObservable(this.sortDirection),
      page: toObservable(this.page),
      reload: toObservable(this.reloadTick),
      tab: toObservable(this.tab)
    }).pipe(
      debounceTime(0),
      switchMap((state) =>
        state.tab === 'authors'
          ? toPageViewState(this.api.authors(this.toAuthorQuery(state)), PAGE_SIZE)
          : of({ status: 'ready' as const, data: emptyPage<OpenAlexAuthorSummary>(PAGE_SIZE) })
      )
    ),
    { initialValue: { status: 'loading' as const, data: emptyPage<OpenAlexAuthorSummary>(PAGE_SIZE) } }
  );

  readonly canAdminister = computed(() => this.auth.canAdminister('INTEGRATIONS'));
  readonly canCorrectPeople = computed(() => this.auth.canAdminister('PEOPLE'));
  readonly pendingRecoveries = computed(() => this.stats()?.pendingRecoveries ?? []);
  readonly historyState = toSignal(
    combineLatest({
      reload: toObservable(this.reloadTick),
      activeId: toObservable(this.monitor.openalex).pipe(
        map((run) => run?.syncId ?? ''),
        distinctUntilChanged()
      ),
      latestId: toObservable(this.monitor.latestOpenAlex).pipe(
        map((run) => run?.syncId ?? ''),
        distinctUntilChanged()
      )
    }).pipe(
      switchMap(() =>
        this.api.history().pipe(
          map((items): OpenAlexHistoryState => ({ status: 'ready', items: sortOpenAlexHistoryDesc(items) })),
          catchError(() => of<OpenAlexHistoryState>({ status: 'error', items: [] }))
        )
      )
    ),
    { initialValue: EMPTY_HISTORY }
  );
  readonly history = computed(() => this.historyState().items);
  readonly hasActiveFilters = computed(
    () =>
      !!this.search.value
      || !!this.status.value
      || !!this.position.value
      || !!this.hasOrcid.value
      || !!this.ussAffiliation.value
      || !!this.minWorks.value
  );
  readonly emptyKind = computed(() => {
    const stats = this.stats();
    const tab = this.tab();
    const vm = tab === 'works' ? this.works() : tab === 'authors' ? this.authors() : this.matches();
    return openAlexEmptyKind({
      loadError: vm.status === 'error',
      enabled: stats?.enabled !== false,
      configured: stats?.configured !== false,
      tablesAvailable: stats?.tablesAvailable !== false,
      lastStatus: stats?.lastStatus ?? null,
      evaluated: stats?.evaluated ?? 0,
      itemCount: vm.data.items.length,
      filtered: this.hasActiveFilters()
    });
  });

  constructor() {
    this.route.queryParamMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      const tab = params.get('tab');
      if (isOpenAlexTab(tab) && this.tab() !== tab) {
        this.tab.set(tab);
      }
      const syncId = params.get('syncId') || params.get('executionId');
      if (syncId && this.detailService.currentSyncId() !== syncId) {
        this.detailService.show(syncId);
      }
    });
    this.evidenceSearch.valueChanges.pipe(debounceTime(400), distinctUntilChanged(), takeUntilDestroyed()).subscribe(() => {
      if (!this.detail()) {
        return;
      }
      this.evidencePage.set(1);
      this.loadEvidence(this.detail()!.id);
    });
  }

  selectTab(tab: OpenAlexTab): void {
    this.tab.set(tab);
    this.page.set(1);
    this.detail.set(null);
    if (tab === 'works') {
      this.sortField.set('syncedAt');
    } else if (tab === 'authors') {
      this.sortField.set('syncedAt');
    } else {
      this.sortField.set('works');
    }
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { tab, syncId: tab === 'history' ? this.route.snapshot.queryParamMap.get('syncId') : null },
      queryParamsHandling: 'merge'
    });
  }

  currentMatchQuery(): OpenAlexMatchQuery {
    return this.toMatchQuery({
      search: this.search.value,
      status: this.status.value,
      position: this.position.value,
      hasOrcid: this.hasOrcid.value,
      ussAffiliation: this.ussAffiliation.value,
      minWorks: this.minWorks.value,
      sortField: this.sortField(),
      sortDirection: this.sortDirection(),
      page: this.page()
    });
  }

  onSort(field: string): void {
    const fallback: TableSort = { field: String(this.sortField()), direction: this.sortDirection() };
    const next = nextTableSort(
      { field: String(this.sortField()), direction: this.sortDirection() },
      field,
      fallback,
      this.explicitSort()
    );
    this.sortField.set(next.sort.field as OpenAlexMatchSort);
    this.sortDirection.set(next.sort.direction);
    this.explicitSort.set(next.explicit);
    this.page.set(1);
  }

  goToPage(page: number): void {
    this.page.set(page);
  }

  reload(): void {
    this.monitor.refreshLatest('OPENALEX');
    this.reloadTick.update((value) => value + 1);
  }

  openHistoryDetail(syncId: string): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { tab: 'history', syncId },
      queryParamsHandling: 'merge'
    });
    this.detailService.show(syncId);
  }

  modeKey(mode?: string | null): string {
    return syncModeLabelKey(mode) ?? 'common.dash';
  }

  historyStatusKey(row: { status?: string | null; errorCode?: string | null } | null | undefined): string {
    return openAlexHistoryStatusKey(row);
  }

  phaseKey(phase?: string | null): string | null {
    return openAlexPhaseLabelKey(phase);
  }

  showsRecovery(row: SyncRun): boolean {
    return openAlexHistoryShowsRecovery(row);
  }

  openEvidence(row: OpenAlexMatchSummary): void {
    this.evidenceSearch.setValue('', { emitEvent: false });
    this.evidencePage.set(1);
    this.detail.set({
      ...row,
      conclusion: row.status,
      works: [],
      worksTotal: row.matchingWorkCount,
      workPage: 1,
      workSize: OPENALEX_EVIDENCE_PAGE_SIZE
    });
    this.loadEvidence(row.id, true);
  }

  closeEvidence(): void {
    this.detail.set(null);
    this.orcidSyncConfirm.set(null);
    this.evidenceLoading.set(false);
    this.evidenceSearch.setValue('', { emitEvent: false });
    this.evidencePage.set(1);
  }

  goToEvidencePage(page: number): void {
    const current = this.detail();
    if (!current) {
      return;
    }
    this.evidencePage.set(page);
    this.loadEvidence(current.id);
  }

  confirm(row: OpenAlexMatchSummary | OpenAlexMatchDetail): void {
    if (!this.canAdminister() || this.actionBusy()) {
      return;
    }
    this.actionBusy.set(true);
    this.api.confirm(row.id).subscribe({
      next: (detail) => {
        this.actionBusy.set(false);
        this.evidenceSearch.setValue('', { emitEvent: false });
        this.evidencePage.set(1);
        this.detail.set(detail);
        this.reload();
      },
      error: (error: HttpErrorResponse) => {
        this.actionBusy.set(false);
        this.actionMessage.set(this.actionErrorKey(error, 'openalex.actionFailed'));
      }
    });
  }

  reject(row: OpenAlexMatchSummary | OpenAlexMatchDetail): void {
    if (!this.canAdminister() || this.actionBusy()) {
      return;
    }
    this.actionBusy.set(true);
    this.api.reject(row.id).subscribe({
      next: (detail) => {
        this.actionBusy.set(false);
        this.evidenceSearch.setValue('', { emitEvent: false });
        this.evidencePage.set(1);
        this.detail.set(detail);
        this.reload();
      },
      error: (error: HttpErrorResponse) => {
        this.actionBusy.set(false);
        this.actionMessage.set(this.actionErrorKey(error, 'openalex.actionFailed'));
      }
    });
  }

  canSyncOrcid(detail: OpenAlexMatchDetail): boolean {
    return this.canCorrectPeople() && openAlexOrcidSyncAllowed(detail);
  }

  requestOrcidSync(detail: OpenAlexMatchDetail): void {
    if (!this.canSyncOrcid(detail) || this.actionBusy()) {
      return;
    }
    this.orcidSyncConfirm.set(detail);
  }

  cancelOrcidSync(): void {
    if (!this.actionBusy()) {
      this.orcidSyncConfirm.set(null);
    }
  }

  confirmOrcidSync(): void {
    const current = this.orcidSyncConfirm();
    if (!current || !this.canSyncOrcid(current) || this.actionBusy()) {
      return;
    }
    this.actionBusy.set(true);
    this.actionMessage.set(null);
    this.api.syncOrcid(current.id).subscribe({
      next: (result) => {
        this.actionBusy.set(false);
        this.orcidSyncConfirm.set(null);
        this.actionMessage.set(
          result.status === 'ALREADY_PRESENT' ? 'openalex.orcidAlreadyPresent' : 'openalex.orcidSyncOk'
        );
        this.loadEvidence(current.id);
        this.reload();
      },
      error: (error: HttpErrorResponse) => {
        this.actionBusy.set(false);
        this.actionMessage.set(this.actionErrorKey(error, 'openalex.orcidSyncFailed'));
      }
    });
  }

  statusKey(status: string | null | undefined): string {
    return openAlexStatusLabel(status);
  }

  statusTone(status: string | null | undefined) {
    return openAlexStatusTone(status);
  }

  display(value: string | number | null | undefined): string | null {
    return displayOpenAlexValue(value);
  }

  contributorName(work: OpenAlexMatchWork): string | null {
    return joinOpenAlexName(work.contributorFirstName, work.contributorLastName);
  }

  evidenceUnits(work: OpenAlexMatchWork): OpenAlexMatchWorkEvidence[] {
    return openAlexEvidenceUnits(work);
  }

  evidenceBadges(source: { reasonCodeList?: string[] | null; reasonCodes?: string | null } | null | undefined) {
    return openAlexEvidenceBadges(source);
  }

  orcidValue(value: string | null | undefined): string | null {
    return displayOpenAlexValue(value);
  }

  orcidComparisonCode(detail: OpenAlexMatchDetail): string {
    return openAlexOrcidComparisonCode(detail.orcidComparison, detail.personOrcid, detail.openalexOrcid);
  }

  orcidComparisonKey(detail: OpenAlexMatchDetail): string {
    return openAlexOrcidComparisonKey(this.orcidComparisonCode(detail));
  }

  orcidComparisonTone(detail: OpenAlexMatchDetail) {
    const result = this.orcidComparisonCode(detail);
    if (result === 'MATCH') {
      return 'success' as const;
    }
    if (result === 'CONFLICT') {
      return 'warning' as const;
    }
    if (result === 'OPENALEX_ONLY') {
      return 'review' as const;
    }
    return 'muted' as const;
  }

  orcidComparisonIcon(detail: OpenAlexMatchDetail): string | null {
    const result = this.orcidComparisonCode(detail);
    if (result === 'MATCH') {
      return 'check';
    }
    if (result === 'CONFLICT') {
      return 'warning';
    }
    return null;
  }

  primaryAffiliation(detail: OpenAlexMatchDetail) {
    return openAlexPrimaryAffiliation(detail.works);
  }

  showEvidenceSearch(detail: OpenAlexMatchDetail): boolean {
    return showOpenAlexEvidenceSearch(detail.matchingWorkCount, this.evidenceSearch.value);
  }

  worksTotal(detail: OpenAlexMatchDetail): number {
    return detail.worksTotal ?? detail.works.length;
  }

  private actionErrorKey(error: HttpErrorResponse, fallback: string): string {
    const code = error.error && typeof error.error === 'object' ? error.error.code : null;
    if (code === 'OPENALEX_AUTHOR_ALREADY_CONFIRMED') {
      return 'openalex.authorAlreadyConfirmed';
    }
    if (code === 'ORCID_CONFLICT') {
      return 'openalex.orcidConflictWrite';
    }
    if (code === 'MATCH_NOT_CONFIRMED' || code === 'MATCH_REJECTED' || code === 'OPENALEX_ORCID_MISSING'
        || code === 'OPENALEX_ORCID_INVALID' || code === 'PURE_HAS_ORCID' || code === 'AUTH_FORBIDDEN') {
      return 'openalex.orcidSyncBlocked';
    }
    return fallback;
  }

  private loadEvidence(id: number, open = false): void {
    this.evidenceLoading.set(true);
    this.api
      .matchDetail(id, {
        workPage: this.evidencePage(),
        workSize: OPENALEX_EVIDENCE_PAGE_SIZE,
        workSearch: this.evidenceSearch.value || undefined
      })
      .subscribe({
        next: (detail) => {
          this.detail.set(detail);
          this.evidenceLoading.set(false);
        },
        error: () => {
          this.evidenceLoading.set(false);
          if (open) {
            this.detail.set(null);
          }
        }
      });
  }

  private toMatchQuery(state: {
    search: string;
    status: string;
    position: string;
    hasOrcid: string;
    ussAffiliation: string;
    minWorks: string;
    sortField: string;
    sortDirection: SortDirection;
    page: number;
  }): OpenAlexMatchQuery {
    return {
      search: state.search || undefined,
      status: state.status || undefined,
      position: state.position || undefined,
      hasOrcid: state.hasOrcid === 'yes' ? true : state.hasOrcid === 'no' ? false : undefined,
      ussAffiliation: state.ussAffiliation === 'yes' ? true : state.ussAffiliation === 'no' ? false : undefined,
      minWorks: state.minWorks ? Number(state.minWorks) : undefined,
      sort: state.sortField as OpenAlexMatchSort,
      direction: state.sortDirection,
      page: state.page,
      pageSize: PAGE_SIZE
    };
  }

  private toWorkQuery(state: {
    search: string;
    status: string;
    sortField: string;
    sortDirection: SortDirection;
    page: number;
  }): OpenAlexWorkQuery {
    return {
      search: state.search || undefined,
      status: state.status || undefined,
      sort: state.sortField as OpenAlexWorkSort,
      direction: state.sortDirection,
      page: state.page,
      pageSize: PAGE_SIZE
    };
  }

  private toAuthorQuery(state: {
    search: string;
    sortField: string;
    sortDirection: SortDirection;
    page: number;
  }): OpenAlexAuthorQuery {
    return {
      search: state.search || undefined,
      sort: state.sortField as OpenAlexAuthorSort,
      direction: state.sortDirection,
      page: state.page,
      pageSize: PAGE_SIZE
    };
  }
}
