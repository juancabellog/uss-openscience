import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, combineLatest, debounceTime, EMPTY, finalize, map, of, startWith, switchMap, type Observable } from 'rxjs';
import {
  OrganizationFilters,
  OrganizationItem,
  OrganizationParentFilter,
  OrganizationPresenceFilter,
  OrganizationQuery,
  OrganizationSort,
  OrganizationStats,
  SortDirection
} from '../../core/models/openscience.models';
import { I18nService } from '../../core/i18n/i18n.service';
import { OrganizationApiService } from '../../core/services/api/organization-api.service';
import { SyncApiService, SyncEntity, SyncRun, syncEntityLabelKey } from '../../core/services/api/sync-api.service';
import { SyncMonitorService } from '../../core/services/sync-monitor.service';
import { syncModeLabelKey } from '../../core/sync/sync-mode.util';
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
import { SortableHeaderComponent } from '../../shared/components/sortable-header/sortable-header.component';
import { SyncExportBarComponent } from '../../shared/components/sync-export-bar/sync-export-bar.component';
import { I18N_HOST, I18N_IMPORTS } from '../../shared/i18n/i18n.imports';
import { ariaSortValue, nextTableSort, TableSort } from '../../shared/table/table-sort';

const PAGE_SIZE = 25;
export const ORGANIZATION_HISTORY_ENTITIES: SyncEntity[] = ['ORGANIZATIONS'];
const EMPTY_FILTERS: OrganizationFilters = { types: [] };
const DEFAULT_SORT: TableSort = { field: 'name', direction: 'asc' };

export function organizationHasActiveFilters(state: {
  search?: string;
  typeUri?: string;
  presence?: string;
  hasParent?: string;
}): boolean {
  return !!(
    state.search?.trim() ||
    state.typeUri ||
    (state.presence && state.presence !== 'ACTIVE') ||
    state.hasParent
  );
}

export function organizationStatsQuery(state: {
  search?: string;
  typeUri?: string;
  presence?: OrganizationPresenceFilter;
  hasParent?: OrganizationParentFilter;
  sort?: OrganizationSort;
  direction?: SortDirection;
  page?: number;
  pageSize?: number;
}): OrganizationQuery {
  return {
    search: state.search || undefined,
    typeUri: state.typeUri || undefined,
    presence: state.presence || undefined,
    hasParent: state.hasParent || undefined
  };
}

@Component({
  selector: 'os-organization-list',
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
  templateUrl: './organization-list.component.html',
  styleUrl: './organization-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class OrganizationListComponent {
  private readonly organizationsApi = inject(OrganizationApiService);
  private readonly syncApi = inject(SyncApiService);
  private readonly monitor = inject(SyncMonitorService);
  private readonly i18n = inject(I18nService);
  readonly tab = signal<'catalog' | 'history'>('catalog');

  readonly search = new FormControl('', { nonNullable: true });
  readonly typeUri = new FormControl('', { nonNullable: true });
  readonly presence = new FormControl<OrganizationPresenceFilter>('ACTIVE', { nonNullable: true });
  readonly hasParent = new FormControl<OrganizationParentFilter>('', { nonNullable: true });
  readonly page = signal(1);
  readonly reloadTick = signal(0);
  readonly sortField = signal<OrganizationSort>('name');
  readonly sortDirection = signal<SortDirection>('asc');
  readonly explicitSort = signal(false);
  readonly visualSortField = computed(() => (this.explicitSort() ? this.sortField() : ''));
  readonly statsUpdating = signal(false);
  private statsRequestId = 0;

  readonly filters = toSignal(
    toObservable(this.reloadTick).pipe(
      switchMap(() => this.organizationsApi.filters().pipe(catchError(() => of(EMPTY_FILTERS))))
    ),
    { initialValue: EMPTY_FILTERS }
  );

  readonly viewStats = toSignal(
    combineLatest({
      search: this.search.valueChanges.pipe(debounceTime(400), startWith(this.search.value)),
      typeUri: this.typeUri.valueChanges.pipe(startWith(this.typeUri.value)),
      presence: this.presence.valueChanges.pipe(startWith(this.presence.value)),
      hasParent: this.hasParent.valueChanges.pipe(startWith(this.hasParent.value)),
      reload: toObservable(this.reloadTick)
    }).pipe(
      debounceTime(0),
      switchMap((state): Observable<OrganizationStats | null> => {
        const requestId = ++this.statsRequestId;
        queueMicrotask(() => {
          if (requestId === this.statsRequestId) {
            this.statsUpdating.set(true);
          }
        });
        return this.organizationsApi.stats(organizationStatsQuery(state)).pipe(
          map((stats): OrganizationStats | null => stats),
          catchError((): Observable<OrganizationStats | null> => EMPTY),
          finalize(() => {
            if (requestId === this.statsRequestId) {
              this.statsUpdating.set(false);
            }
          })
        );
      })
    ),
    { initialValue: null as OrganizationStats | null }
  );

  readonly stats = computed(() => this.viewStats());
  readonly hasActiveFilters = computed(() => {
    this.stats();
    this.statsUpdating();
    return organizationHasActiveFilters({
      search: this.search.value,
      typeUri: this.typeUri.value,
      presence: this.presence.value,
      hasParent: this.hasParent.value
    });
  });

  readonly result = toSignal(
    combineLatest({
      search: this.search.valueChanges.pipe(debounceTime(400), startWith(this.search.value)),
      typeUri: this.typeUri.valueChanges.pipe(startWith(this.typeUri.value)),
      presence: this.presence.valueChanges.pipe(startWith(this.presence.value)),
      hasParent: this.hasParent.valueChanges.pipe(startWith(this.hasParent.value)),
      sortField: toObservable(this.sortField),
      sortDirection: toObservable(this.sortDirection),
      page: toObservable(this.page),
      reload: toObservable(this.reloadTick)
    }).pipe(
      switchMap(({ search, typeUri, presence, hasParent, sortField, sortDirection, page }) =>
        toPageViewState(
          this.organizationsApi.list({
            search,
            typeUri: typeUri || undefined,
            presence,
            hasParent: hasParent || undefined,
            sort: sortField,
            direction: sortDirection,
            page,
            pageSize: PAGE_SIZE
          }),
          PAGE_SIZE
        )
      )
    ),
    { initialValue: { status: 'loading' as const, data: emptyPage<OrganizationItem>(PAGE_SIZE) } }
  );

  readonly language = this.i18n.currentLanguage;

  readonly historyState = toSignal(
    toObservable(this.reloadTick).pipe(
      switchMap(() =>
        this.syncApi.history(ORGANIZATION_HISTORY_ENTITIES).pipe(
          map((items) => ({ status: 'success' as const, items })),
          catchError(() => of({ status: 'error' as const, items: [] as SyncRun[] })),
          startWith({ status: 'loading' as const, items: [] as SyncRun[] })
        )
      )
    ),
    { initialValue: { status: 'loading' as const, items: [] as SyncRun[] } }
  );

  constructor() {
    combineLatest([this.search.valueChanges, this.typeUri.valueChanges, this.presence.valueChanges, this.hasParent.valueChanges])
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.page.set(1));
  }

  currentQuery(): OrganizationQuery {
    return {
      search: this.search.value,
      typeUri: this.typeUri.value || undefined,
      presence: this.presence.value,
      hasParent: this.hasParent.value || undefined,
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
      DEFAULT_SORT,
      this.explicitSort()
    );
    this.sortField.set(next.sort.field as OrganizationSort);
    this.sortDirection.set(next.sort.direction);
    this.explicitSort.set(next.explicit);
    this.page.set(1);
  }

  ariaSort(field: string): 'ascending' | 'descending' | 'none' {
    if (!this.explicitSort()) {
      return 'none';
    }
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
    this.typeUri.setValue('');
    this.presence.setValue('ACTIVE');
    this.hasParent.setValue('');
    this.page.set(1);
  }

  nameLabel(item: { name?: string | null; nameEs?: string | null; nameEn?: string | null }): string {
    return localized(item.nameEs, item.nameEn, item.name, this.language());
  }

  typeLabel(item: { type?: string | null; typeEs?: string | null; typeEn?: string | null }): string {
    return localized(item.typeEs, item.typeEn, item.type, this.language());
  }

  parentLabel(item: OrganizationItem): string {
    return localized(item.parentNameEs, item.parentNameEn, item.parentName || item.parentUuid, this.language());
  }
}

function localized(
  es: string | null | undefined,
  en: string | null | undefined,
  fallback: string | null | undefined,
  language: string
): string {
  const preferred = language === 'en' ? en || es : es || en;
  return preferred || fallback || '';
}
