import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, combineLatest, debounceTime, of, startWith, switchMap } from 'rxjs';
import {
  CrossrefFilters,
  CrossrefQuery,
  CrossrefSort,
  CrossrefStats,
  CrossrefWorkDetail,
  CrossrefWorkSummary,
  SortDirection
} from '../../../core/models/openscience.models';
import { AuthService } from '../../../core/services/auth.service';
import { CrossrefApiService } from '../../../core/services/api/crossref-api.service';
import { SyncMonitorService } from '../../../core/services/sync-monitor.service';
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
import { SyncExportBarComponent } from '../../../shared/components/sync-export-bar/sync-export-bar.component';
import { I18N_HOST, I18N_IMPORTS } from '../../../shared/i18n/i18n.imports';
import { nextTableSort, TableSort } from '../../../shared/table/table-sort';

const PAGE_SIZE = 25;
const EMPTY_FILTERS: CrossrefFilters = { statuses: [], workTypes: [] };

export type CrossrefEmptyKind = 'never' | 'none' | 'filtered' | 'disabled' | null;

export function crossrefEmptyKind(options: {
  loadError: boolean;
  enabled: boolean;
  configured: boolean;
  tablesAvailable: boolean;
  lastStatus: string | null | undefined;
  evaluated: number;
  itemCount: number;
  filtered: boolean;
}): CrossrefEmptyKind {
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
  if (!options.lastStatus && options.evaluated === 0) {
    return 'never';
  }
  return 'none';
}

export function canShowCrossrefSync(canAdminister: boolean, enabled: boolean, configured: boolean): boolean {
  return canAdminister && enabled && configured;
}

@Component({
  selector: 'os-crossref-center',
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
    SyncExportBarComponent
  ],
  templateUrl: './crossref-center.component.html',
  styleUrl: './crossref-center.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CrossrefCenterComponent {
  private readonly api = inject(CrossrefApiService);
  private readonly monitor = inject(SyncMonitorService);
  readonly auth = inject(AuthService);

  readonly search = new FormControl('', { nonNullable: true });
  readonly status = new FormControl('', { nonNullable: true });
  readonly workType = new FormControl('', { nonNullable: true });
  readonly hasOrcid = new FormControl('', { nonNullable: true });
  readonly hasAffiliation = new FormControl('', { nonNullable: true });
  readonly hasRor = new FormControl('', { nonNullable: true });
  readonly hasFunding = new FormControl('', { nonNullable: true });
  readonly hasLicense = new FormControl('', { nonNullable: true });
  readonly hasUpdate = new FormControl('', { nonNullable: true });
  readonly comparison = new FormControl('', { nonNullable: true });
  readonly page = signal(1);
  readonly reloadTick = signal(0);
  readonly sortField = signal<CrossrefSort>('syncedAt');
  readonly sortDirection = signal<SortDirection>('desc');
  readonly explicitSort = signal(false);
  readonly detail = signal<CrossrefWorkDetail | null>(null);
  readonly running = this.monitor.crossref;

  readonly stats = toSignal(
    toObservable(this.reloadTick).pipe(
      switchMap(() => this.api.stats().pipe(catchError(() => of(null))))
    ),
    { initialValue: null as CrossrefStats | null }
  );

  readonly filters = toSignal(
    toObservable(this.reloadTick).pipe(
      switchMap(() => this.api.filters().pipe(catchError(() => of(EMPTY_FILTERS))))
    ),
    { initialValue: EMPTY_FILTERS }
  );

  readonly result = toSignal(
    combineLatest({
      search: this.search.valueChanges.pipe(debounceTime(400), startWith(this.search.value)),
      status: this.status.valueChanges.pipe(startWith(this.status.value)),
      workType: this.workType.valueChanges.pipe(startWith(this.workType.value)),
      hasOrcid: this.hasOrcid.valueChanges.pipe(startWith(this.hasOrcid.value)),
      hasAffiliation: this.hasAffiliation.valueChanges.pipe(startWith(this.hasAffiliation.value)),
      hasRor: this.hasRor.valueChanges.pipe(startWith(this.hasRor.value)),
      hasFunding: this.hasFunding.valueChanges.pipe(startWith(this.hasFunding.value)),
      hasLicense: this.hasLicense.valueChanges.pipe(startWith(this.hasLicense.value)),
      hasUpdate: this.hasUpdate.valueChanges.pipe(startWith(this.hasUpdate.value)),
      comparison: this.comparison.valueChanges.pipe(startWith(this.comparison.value)),
      sortField: toObservable(this.sortField),
      sortDirection: toObservable(this.sortDirection),
      page: toObservable(this.page),
      reload: toObservable(this.reloadTick)
    }).pipe(
      debounceTime(0),
      switchMap((state) => toPageViewState(this.api.list(this.toQuery(state)), PAGE_SIZE))
    ),
    { initialValue: { status: 'loading' as const, data: emptyPage<CrossrefWorkSummary>(PAGE_SIZE) } }
  );

  readonly emptyKind = computed(() => {
    const vm = this.result();
    const stats = this.stats();
    return crossrefEmptyKind({
      loadError: vm.status === 'error',
      enabled: stats?.enabled !== false,
      configured: stats?.configured !== false,
      tablesAvailable: stats?.tablesAvailable !== false,
      lastStatus: stats?.lastStatus,
      evaluated: stats?.evaluated ?? 0,
      itemCount: vm.data.items.length,
      filtered: this.hasActiveFilters()
    });
  });

  currentQuery(): CrossrefQuery {
    return this.toQuery({
      search: this.search.value,
      status: this.status.value,
      workType: this.workType.value,
      hasOrcid: this.hasOrcid.value,
      hasAffiliation: this.hasAffiliation.value,
      hasRor: this.hasRor.value,
      hasFunding: this.hasFunding.value,
      hasLicense: this.hasLicense.value,
      hasUpdate: this.hasUpdate.value,
      comparison: this.comparison.value,
      sortField: this.sortField(),
      sortDirection: this.sortDirection(),
      page: this.page()
    });
  }

  onSort(field: string): void {
    const fallback: TableSort = { field: 'syncedAt', direction: 'desc' };
    const next = nextTableSort(
      { field: this.sortField(), direction: this.sortDirection() },
      field,
      fallback,
      this.explicitSort()
    );
    this.sortField.set(next.sort.field as CrossrefSort);
    this.sortDirection.set(next.sort.direction);
    this.explicitSort.set(next.explicit);
    this.page.set(1);
  }

  goToPage(page: number): void {
    this.page.set(page);
  }

  reload(): void {
    this.monitor.refreshLatest('CROSSREF');
    this.reloadTick.update((value) => value + 1);
  }

  openDetail(row: CrossrefWorkSummary): void {
    this.api.detail(row.id).subscribe({
      next: (detail) => this.detail.set(detail),
      error: () => this.detail.set(null)
    });
  }

  closeDetail(): void {
    this.detail.set(null);
  }

  doiHref(doi: string | null | undefined): string | null {
    if (!doi) {
      return null;
    }
    return 'https://doi.org/' + doi.replace(/^https?:\/\/(dx\.)?doi\.org\//i, '');
  }

  statusKey(status: string | null | undefined): string {
    switch (status) {
      case 'FOUND':
        return 'crossref.status.FOUND';
      case 'OTHER_AGENCY':
        return 'crossref.status.OTHER_AGENCY';
      case 'NOT_FOUND':
        return 'crossref.status.NOT_FOUND';
      case 'INVALID_DOI':
        return 'crossref.status.INVALID_DOI';
      case 'ERROR':
        return 'crossref.status.ERROR';
      case 'RATE_LIMITED':
        return 'crossref.status.RATE_LIMITED';
      default:
        return 'common.dash';
    }
  }

  private hasActiveFilters(): boolean {
    return !!(
      this.search.value
      || this.status.value
      || this.workType.value
      || this.hasOrcid.value
      || this.hasAffiliation.value
      || this.hasRor.value
      || this.hasFunding.value
      || this.hasLicense.value
      || this.hasUpdate.value
      || this.comparison.value
    );
  }

  private toQuery(state: {
    search: string;
    status: string;
    workType: string;
    hasOrcid: string;
    hasAffiliation: string;
    hasRor: string;
    hasFunding: string;
    hasLicense: string;
    hasUpdate: string;
    comparison: string;
    sortField: CrossrefSort;
    sortDirection: SortDirection;
    page: number;
  }): CrossrefQuery {
    const flag = (value: string) => (value === 'yes' ? true : value === 'no' ? false : undefined);
    return {
      search: state.search || undefined,
      status: state.status || undefined,
      workType: state.workType || undefined,
      hasOrcid: flag(state.hasOrcid),
      hasAffiliation: flag(state.hasAffiliation),
      hasRor: flag(state.hasRor),
      hasFunding: flag(state.hasFunding),
      hasLicense: flag(state.hasLicense),
      hasUpdate: flag(state.hasUpdate),
      comparison: state.comparison || undefined,
      sort: state.sortField,
      direction: state.sortDirection,
      page: state.page,
      pageSize: PAGE_SIZE
    };
  }
}
