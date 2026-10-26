import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, combineLatest, debounceTime, of, startWith, switchMap } from 'rxjs';
import {
  SortDirection,
  UnpaywallFilters,
  UnpaywallQuery,
  UnpaywallSort,
  UnpaywallStats,
  UnpaywallWorkDetail,
  UnpaywallWorkSummary
} from '../../../core/models/openscience.models';
import { UnpaywallApiService } from '../../../core/services/api/unpaywall-api.service';
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
const EMPTY_FILTERS: UnpaywallFilters = { oaStatuses: [], hostTypes: [], licenses: [], versions: [] };

@Component({
  selector: 'os-unpaywall-center',
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
  templateUrl: './unpaywall-center.component.html',
  styleUrl: './unpaywall-center.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class UnpaywallCenterComponent {
  private readonly api = inject(UnpaywallApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly monitor = inject(SyncMonitorService);

  readonly search = new FormControl('', { nonNullable: true });
  readonly oaStatus = new FormControl('', { nonNullable: true });
  readonly hostType = new FormControl('', { nonNullable: true });
  readonly license = new FormControl('', { nonNullable: true });
  readonly version = new FormControl('', { nonNullable: true });
  readonly repository = new FormControl('', { nonNullable: true });
  readonly isOa = new FormControl('', { nonNullable: true });
  readonly outcome = new FormControl('', { nonNullable: true });
  readonly page = signal(1);
  readonly reloadTick = signal(0);
  readonly sortField = signal<UnpaywallSort>('retrievedAt');
  readonly sortDirection = signal<SortDirection>('desc');
  readonly explicitSort = signal(false);
  readonly detail = signal<UnpaywallWorkDetail | null>(null);
  readonly running = this.monitor.unpaywall;

  readonly stats = toSignal(
    toObservable(this.reloadTick).pipe(
      switchMap(() => this.api.stats().pipe(catchError(() => of(null))))
    ),
    { initialValue: null as UnpaywallStats | null }
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
      oaStatus: this.oaStatus.valueChanges.pipe(startWith(this.oaStatus.value)),
      hostType: this.hostType.valueChanges.pipe(startWith(this.hostType.value)),
      license: this.license.valueChanges.pipe(startWith(this.license.value)),
      version: this.version.valueChanges.pipe(startWith(this.version.value)),
      repository: this.repository.valueChanges.pipe(startWith(this.repository.value)),
      isOa: this.isOa.valueChanges.pipe(startWith(this.isOa.value)),
      outcome: this.outcome.valueChanges.pipe(startWith(this.outcome.value)),
      sortField: toObservable(this.sortField),
      sortDirection: toObservable(this.sortDirection),
      page: toObservable(this.page),
      reload: toObservable(this.reloadTick)
    }).pipe(
      debounceTime(0),
      switchMap((state) =>
        toPageViewState(this.api.list(this.toQuery(state)), PAGE_SIZE)
      )
    ),
    { initialValue: { status: 'loading' as const, data: emptyPage<UnpaywallWorkSummary>(PAGE_SIZE) } }
  );

  constructor() {
    const params = this.route.snapshot.queryParamMap;
    if (params.get('isOa') === 'true') {
      this.isOa.setValue('yes');
      this.outcome.setValue('FOUND');
    }
    if (params.get('isOa') === 'false') {
      this.isOa.setValue('no');
      this.outcome.setValue('FOUND');
    }
    if (params.get('repositoryCopy') === 'true') {
      this.repository.setValue('yes');
    }
    if (params.get('outcome') === 'NOT_FOUND') {
      this.outcome.setValue('NOT_FOUND');
    }
  }

  currentQuery(): UnpaywallQuery {
    return this.toQuery({
      search: this.search.value,
      oaStatus: this.oaStatus.value,
      hostType: this.hostType.value,
      license: this.license.value,
      version: this.version.value,
      repository: this.repository.value,
      isOa: this.isOa.value,
      outcome: this.outcome.value,
      sortField: this.sortField(),
      sortDirection: this.sortDirection(),
      page: this.page()
    });
  }

  onSort(field: string): void {
    const fallback: TableSort = { field: 'retrievedAt', direction: 'desc' };
    const next = nextTableSort(
      { field: this.sortField(), direction: this.sortDirection() },
      field,
      fallback,
      this.explicitSort()
    );
    this.sortField.set(next.sort.field as UnpaywallSort);
    this.sortDirection.set(next.sort.direction);
    this.explicitSort.set(next.explicit);
    this.page.set(1);
  }

  goToPage(page: number): void {
    this.page.set(page);
  }

  reload(): void {
    this.monitor.refreshLatest('UNPAYWALL');
    this.reloadTick.update((value) => value + 1);
  }

  openDetail(row: UnpaywallWorkSummary): void {
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

  oaLabel(row: UnpaywallWorkSummary): string {
    if (row.lastOutcome === 'NOT_FOUND') {
      return 'unpaywall.notEvaluated';
    }
    if (row.isOa === true) {
      return 'unpaywall.openAccess';
    }
    if (row.isOa === false) {
      return 'unpaywall.noOpenAccess';
    }
    return 'unpaywall.notEvaluated';
  }

  private toQuery(state: {
    search: string;
    oaStatus: string;
    hostType: string;
    license: string;
    version: string;
    repository: string;
    isOa: string;
    outcome: string;
    sortField: UnpaywallSort;
    sortDirection: SortDirection;
    page: number;
  }): UnpaywallQuery {
    return {
      search: state.search || undefined,
      oaStatus: state.oaStatus || undefined,
      hostType: state.hostType || undefined,
      license: state.license || undefined,
      version: state.version || undefined,
      repositoryCopy: state.repository === 'yes' ? true : state.repository === 'no' ? false : undefined,
      isOa: state.isOa === 'yes' ? true : state.isOa === 'no' ? false : undefined,
      outcome: state.outcome || undefined,
      sort: state.sortField,
      direction: state.sortDirection,
      page: state.page,
      pageSize: PAGE_SIZE
    };
  }
}
