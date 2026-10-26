import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, combineLatest, debounceTime, EMPTY, finalize, map, of, startWith, switchMap, type Observable } from 'rxjs';
import {
  Person,
  PersonFilters,
  PersonOrcidStatus,
  PersonPopulation,
  PersonQuery,
  PersonScopusStatus,
  PersonSort,
  PersonStats,
  SortDirection
} from '../../core/models/openscience.models';
import { PersonApiService } from '../../core/services/api/person-api.service';
import { SyncApiService, SyncEntity, SyncRun, syncEntityLabelKey } from '../../core/services/api/sync-api.service';
import { SyncMonitorService } from '../../core/services/sync-monitor.service';
import { syncModeLabelKey } from '../../core/sync/sync-mode.util';
import { emptyPage, toPageViewState } from '../../core/utils/page-view-state';
import { DataTableShellComponent } from '../../shared/components/data-table-shell/data-table-shell.component';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state/error-state.component';
import { FilterBarComponent } from '../../shared/components/filter-bar/filter-bar.component';
import { InfoHintComponent } from '../../shared/components/info-hint/info-hint.component';
import { LoadingStateComponent } from '../../shared/components/loading-state/loading-state.component';
import { MiniStatComponent } from '../../shared/components/mini-stat/mini-stat.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { PaginationComponent } from '../../shared/components/pagination/pagination.component';
import { SearchBoxComponent } from '../../shared/components/search-box/search-box.component';
import { ScopusIdsCellComponent } from '../../shared/components/scopus-ids-cell/scopus-ids-cell.component';
import { SortableHeaderComponent } from '../../shared/components/sortable-header/sortable-header.component';
import { SyncExportBarComponent } from '../../shared/components/sync-export-bar/sync-export-bar.component';
import { I18N_HOST, I18N_IMPORTS } from '../../shared/i18n/i18n.imports';
import { ariaSortValue, nextTableSort, TableSort } from '../../shared/table/table-sort';

const PAGE_SIZE = 25;
export const PEOPLE_HISTORY_ENTITIES: SyncEntity[] = ['PEOPLE', 'PERSON_METRICS'];
const EMPTY_FILTERS: PersonFilters = {
  positions: [],
  withoutPositionAvailable: false,
  scopusIdCounts: []
};

export function peopleHasActiveFilters(state: {
  search?: string;
  cargo?: string;
  orcidStatus?: PersonOrcidStatus;
  scopusStatus?: PersonScopusStatus;
  scopusCount?: string | number | null;
  minDistinctScopusCount?: number | null;
  population?: PersonPopulation;
}): boolean {
  const scopusCount = state.scopusCount;
  return !!(
    state.search?.trim() ||
    state.cargo ||
    (state.orcidStatus && state.orcidStatus !== 'ALL') ||
    (state.scopusStatus && state.scopusStatus !== 'ALL') ||
    (scopusCount !== '' && scopusCount != null) ||
    (state.minDistinctScopusCount != null && state.minDistinctScopusCount > 0) ||
    (state.population && state.population !== 'ALL')
  );
}

export function peopleStatsQuery(state: {
  search?: string;
  cargo?: string;
  orcidStatus?: PersonOrcidStatus;
  scopusStatus?: PersonScopusStatus;
  scopusCount?: number | null;
  minDistinctScopusCount?: number | null;
  population?: PersonPopulation;
  sort?: PersonSort;
  direction?: SortDirection;
  page?: number;
  pageSize?: number;
}): PersonQuery {
  return {
    search: state.search || undefined,
    cargo: state.cargo || undefined,
    population: state.population,
    orcidStatus: state.orcidStatus,
    scopusStatus: state.scopusStatus,
    scopusCount: state.scopusCount ?? undefined,
    minDistinctScopusCount: state.minDistinctScopusCount ?? undefined
  };
}

@Component({
  selector: 'os-people-list',
  hostDirectives: [...I18N_HOST],
  imports: [
    ...I18N_IMPORTS,
    ReactiveFormsModule,
    RouterLink,
    DataTableShellComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    FilterBarComponent,
    InfoHintComponent,
    LoadingStateComponent,
    MiniStatComponent,
    PageHeaderComponent,
    PaginationComponent,
    SearchBoxComponent,
    ScopusIdsCellComponent,
    SortableHeaderComponent,
    SyncExportBarComponent
  ],
  templateUrl: './people-list.component.html',
  styleUrl: './people-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PeopleListComponent {
  private readonly peopleApi = inject(PersonApiService);
  private readonly syncApi = inject(SyncApiService);
  private readonly monitor = inject(SyncMonitorService);
  private readonly route = inject(ActivatedRoute);
  readonly tab = signal<'catalog' | 'history'>('catalog');

  readonly search = new FormControl('', { nonNullable: true });
  readonly cargo = new FormControl('', { nonNullable: true });
  readonly orcidFilter = new FormControl<PersonOrcidStatus>('ALL', { nonNullable: true });
  readonly scopusStatus = new FormControl<PersonScopusStatus>('ALL', { nonNullable: true });
  readonly scopusCount = new FormControl('', { nonNullable: true });
  readonly minDistinctScopusCount = signal<number | null>(null);
  readonly population = new FormControl<PersonPopulation>('ALL', { nonNullable: true });
  readonly populationValue = toSignal(
    this.population.valueChanges.pipe(startWith(this.population.value)),
    { initialValue: this.population.value }
  );
  readonly page = signal(1);
  readonly reloadTick = signal(0);
  readonly sortField = signal<PersonSort>('name');
  readonly sortDirection = signal<SortDirection>('asc');
  readonly explicitSort = signal(false);
  readonly statsUpdating = signal(false);
  private statsRequestId = 0;
  private readonly defaultSort: TableSort = { field: 'name', direction: 'asc' };

  readonly filters = toSignal(
    toObservable(this.reloadTick).pipe(
      switchMap(() => this.peopleApi.positions().pipe(catchError(() => of(EMPTY_FILTERS))))
    ),
    { initialValue: EMPTY_FILTERS }
  );

  readonly viewStats = toSignal(
    combineLatest({
      search: this.search.valueChanges.pipe(debounceTime(400), startWith(this.search.value)),
      cargo: this.cargo.valueChanges.pipe(startWith(this.cargo.value)),
      orcidStatus: this.orcidFilter.valueChanges.pipe(startWith(this.orcidFilter.value)),
      scopusStatus: this.scopusStatus.valueChanges.pipe(startWith(this.scopusStatus.value)),
      scopusCount: this.scopusCount.valueChanges.pipe(startWith(this.scopusCount.value)),
      minDistinctScopusCount: toObservable(this.minDistinctScopusCount),
      population: this.population.valueChanges.pipe(startWith(this.population.value)),
      reload: toObservable(this.reloadTick)
    }).pipe(
      debounceTime(0),
      switchMap((state): Observable<PersonStats | null> => {
        const requestId = ++this.statsRequestId;
        queueMicrotask(() => {
          if (requestId === this.statsRequestId) {
            this.statsUpdating.set(true);
          }
        });
        return this.peopleApi
          .stats(
            peopleStatsQuery({
              ...state,
              cargo: state.cargo || undefined,
              scopusCount: this.parseScopusCount(state.scopusCount)
            })
          )
          .pipe(
            map((stats): PersonStats | null => stats),
            catchError((): Observable<PersonStats | null> => EMPTY),
            finalize(() => {
              if (requestId === this.statsRequestId) {
                this.statsUpdating.set(false);
              }
            })
          );
      })
    ),
    { initialValue: null as PersonStats | null }
  );

  readonly stats = computed(() => this.viewStats());
  readonly hasActiveFilters = computed(() => {
    this.stats();
    this.statsUpdating();
    return peopleHasActiveFilters({
      search: this.search.value,
      cargo: this.cargo.value,
      orcidStatus: this.orcidFilter.value,
      scopusStatus: this.scopusStatus.value,
      scopusCount: this.scopusCount.value,
      minDistinctScopusCount: this.minDistinctScopusCount(),
      population: this.population.value
    });
  });

  readonly result = toSignal(
    combineLatest({
      search: this.search.valueChanges.pipe(debounceTime(400), startWith(this.search.value)),
      cargo: this.cargo.valueChanges.pipe(startWith(this.cargo.value)),
      orcidStatus: this.orcidFilter.valueChanges.pipe(startWith(this.orcidFilter.value)),
      scopusStatus: this.scopusStatus.valueChanges.pipe(startWith(this.scopusStatus.value)),
      scopusCount: this.scopusCount.valueChanges.pipe(startWith(this.scopusCount.value)),
      minDistinctScopusCount: toObservable(this.minDistinctScopusCount),
      population: this.population.valueChanges.pipe(startWith(this.population.value)),
      sortField: toObservable(this.sortField),
      sortDirection: toObservable(this.sortDirection),
      page: toObservable(this.page),
      reload: toObservable(this.reloadTick)
    }).pipe(
      switchMap(({ search, cargo, orcidStatus, scopusStatus, scopusCount, minDistinctScopusCount, population, sortField, sortDirection, page }) =>
        toPageViewState(
          this.peopleApi.list({
            search,
            cargo: cargo || undefined,
            population,
            orcidStatus,
            scopusStatus,
            scopusCount: this.parseScopusCount(scopusCount),
            minDistinctScopusCount: minDistinctScopusCount ?? undefined,
            sort: sortField,
            direction: sortDirection,
            page,
            pageSize: PAGE_SIZE
          }),
          PAGE_SIZE
        )
      )
    ),
    { initialValue: { status: 'loading' as const, data: emptyPage<Person>(PAGE_SIZE) } }
  );

  readonly historyState = toSignal(
    toObservable(this.reloadTick).pipe(
      switchMap(() =>
        this.syncApi.history(PEOPLE_HISTORY_ENTITIES).pipe(
          map((items) => ({ status: 'success' as const, items })),
          catchError(() => of({ status: 'error' as const, items: [] as SyncRun[] })),
          startWith({ status: 'loading' as const, items: [] as SyncRun[] })
        )
      )
    ),
    { initialValue: { status: 'loading' as const, items: [] as SyncRun[] } }
  );

  constructor() {
    const params = this.route.snapshot.queryParamMap;
    const scopusStatus = params.get('scopusStatus');
    if (scopusStatus === 'WITH_SCOPUS' || scopusStatus === 'WITHOUT_SCOPUS') {
      this.scopusStatus.setValue(scopusStatus);
    }
    const minDistinct = params.get('minDistinctScopusCount');
    if (minDistinct) {
      const parsed = Number(minDistinct);
      if (Number.isInteger(parsed) && parsed > 0) {
        this.minDistinctScopusCount.set(parsed);
      }
    }
    combineLatest([
      this.search.valueChanges,
      this.cargo.valueChanges,
      this.orcidFilter.valueChanges,
      this.scopusStatus.valueChanges,
      this.scopusCount.valueChanges,
      this.population.valueChanges
    ])
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.page.set(1));
  }

  currentQuery(): PersonQuery {
    return {
      search: this.search.value,
      cargo: this.cargo.value || undefined,
      population: this.population.value,
      orcidStatus: this.orcidFilter.value,
      scopusStatus: this.scopusStatus.value,
      scopusCount: this.parseScopusCount(this.scopusCount.value),
      minDistinctScopusCount: this.minDistinctScopusCount() ?? undefined,
      sort: this.sortField(),
      direction: this.sortDirection()
    };
  }

  onPageChange(page: number): void {
    this.page.set(page);
  }

  onSort(field: string): void {
    const next = nextTableSort(
      { field: this.sortField(), direction: this.sortDirection() },
      field,
      this.defaultSort,
      this.explicitSort()
    );
    this.sortField.set(next.sort.field as PersonSort);
    this.sortDirection.set(next.sort.direction);
    this.explicitSort.set(next.explicit);
    this.page.set(1);
  }

  ariaSort(field: string): 'ascending' | 'descending' | 'none' {
    return ariaSortValue(field, this.sortField(), this.sortDirection());
  }

  reload(): void {
    this.reloadTick.update((value) => value + 1);
  }

  openHistoryDetail(syncId: string): void {
    this.monitor.openDetail(syncId);
  }

  operationKey(entity?: string | null): string {
    return syncEntityLabelKey(entity);
  }

  modeKey(mode?: string | null): string {
    return syncModeLabelKey(mode) ?? 'common.dash';
  }

  statusKey(status?: string | null): string {
    return status ? `sync.status.${status}` : 'common.dash';
  }

  clearFilters(): void {
    this.search.setValue('');
    this.cargo.setValue('');
    this.orcidFilter.setValue('ALL');
    this.scopusStatus.setValue('ALL');
    this.scopusCount.setValue('');
    this.minDistinctScopusCount.set(null);
    this.population.setValue('ALL');
    this.page.set(1);
  }

  setPopulation(value: PersonPopulation): void {
    this.population.setValue(value);
  }

  onOrcidStatusChange(): void {
    this.page.set(1);
  }

  onScopusStatusChange(): void {
    this.scopusCount.setValue('');
    this.minDistinctScopusCount.set(null);
    this.page.set(1);
  }

  onScopusCountChange(): void {
    this.scopusStatus.setValue('ALL');
    this.minDistinctScopusCount.set(null);
    this.page.set(1);
  }

  parseScopusCount(value: string): number | null {
    if (value === '' || value == null) {
      return null;
    }
    const parsed = Number(value);
    return Number.isInteger(parsed) && parsed >= 0 ? parsed : null;
  }

  scopusIdCount(person: Person): number {
    return person.scopusIdCount ?? person.scopusIds.length;
  }

  scopusCountOptions(): number[] {
    const catalog = this.filters().scopusIdCounts;
    if (catalog.length > 0) {
      return catalog;
    }
    return (this.stats()?.scopus?.distribution ?? []).map((bucket) => bucket.count);
  }

  displayName(person: Person): string {
    const last = person.lastName?.trim() ?? '';
    const first = person.firstName?.trim() ?? '';
    if (last && first) {
      return `${last}, ${first}`;
    }
    const fullName = person.fullName?.trim();
    if (fullName) {
      return fullName;
    }
    return last || first || '';
  }

  orcidHref(orcid: string): string {
    return `https://orcid.org/${orcid}`;
  }

  hasPublicationCount(items: Person[]): boolean {
    return items.some((item) => item.publicationCount != null);
  }
}
