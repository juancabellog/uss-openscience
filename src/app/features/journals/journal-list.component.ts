import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, combineLatest, debounceTime, EMPTY, finalize, map, of, startWith, switchMap, type Observable } from 'rxjs';
import {
  JournalCiteScoreFilter,
  JournalFilters,
  JournalIssnCountFilter,
  JournalItem,
  JournalPublicationFilter,
  JournalQuartileFilter,
  JournalQuery,
  JournalSort,
  JournalStats,
  SortDirection
} from '../../core/models/openscience.models';
import { I18nService } from '../../core/i18n/i18n.service';
import { JournalApiService } from '../../core/services/api/journal-api.service';
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
import { IssnCellComponent } from '../../shared/components/issn-cell/issn-cell.component';
import { I18N_HOST, I18N_IMPORTS } from '../../shared/i18n/i18n.imports';
import { ariaSortValue, nextTableSort, TableSort } from '../../shared/table/table-sort';

const PAGE_SIZE = 25;
export const JOURNAL_HISTORY_ENTITIES: SyncEntity[] = ['JOURNALS', 'JOURNAL_METRICS'];
const EMPTY_FILTERS: JournalFilters = { types: [], workflows: [] };
const DEFAULT_SORT: TableSort = { field: 'title', direction: 'asc' };

export function journalHasActiveFilters(state: {
  search?: string;
  typeUri?: string;
  workflowStep?: string;
  issnCount?: JournalIssnCountFilter;
  publications?: JournalPublicationFilter;
  citescore?: JournalCiteScoreFilter;
  quartile?: JournalQuartileFilter;
}): boolean {
  return !!(
    state.search?.trim() ||
    state.typeUri ||
    state.workflowStep ||
    state.issnCount ||
    state.publications ||
    state.citescore ||
    state.quartile
  );
}

export function journalStatsQuery(state: {
  search?: string;
  typeUri?: string;
  workflowStep?: string;
  issnCount?: JournalIssnCountFilter;
  publications?: JournalPublicationFilter;
  citescore?: JournalCiteScoreFilter;
  quartile?: JournalQuartileFilter;
  sort?: JournalSort;
  direction?: SortDirection;
  page?: number;
  pageSize?: number;
}): JournalQuery {
  return {
    search: state.search || undefined,
    typeUri: state.typeUri || undefined,
    workflowStep: state.workflowStep || undefined,
    issnCount: state.issnCount || undefined,
    publications: state.publications || undefined,
    citescore: state.citescore || undefined,
    quartile: state.quartile || undefined
  };
}

@Component({
  selector: 'os-journal-list',
  hostDirectives: [...I18N_HOST],
  imports: [
    ...I18N_IMPORTS,
    ReactiveFormsModule,
    RouterLink,
    DataTableShellComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    FilterBarComponent,
    IssnCellComponent,
    LoadingStateComponent,
    MiniStatComponent,
    PageHeaderComponent,
    PaginationComponent,
    SearchBoxComponent,
    SortableHeaderComponent,
    SyncExportBarComponent
  ],
  templateUrl: './journal-list.component.html',
  styleUrl: './journal-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class JournalListComponent {
  private readonly journalsApi = inject(JournalApiService);
  private readonly syncApi = inject(SyncApiService);
  private readonly monitor = inject(SyncMonitorService);
  private readonly i18n = inject(I18nService);
  readonly tab = signal<'catalog' | 'history'>('catalog');

  readonly search = new FormControl('', { nonNullable: true });
  readonly typeUri = new FormControl('', { nonNullable: true });
  readonly workflowStep = new FormControl('', { nonNullable: true });
  readonly issnCount = new FormControl<JournalIssnCountFilter>('', { nonNullable: true });
  readonly publications = new FormControl<JournalPublicationFilter>('', { nonNullable: true });
  readonly citescore = new FormControl<JournalCiteScoreFilter>('', { nonNullable: true });
  readonly quartile = new FormControl<JournalQuartileFilter>('', { nonNullable: true });
  readonly page = signal(1);
  readonly reloadTick = signal(0);
  readonly sortField = signal<JournalSort>('title');
  readonly sortDirection = signal<SortDirection>('asc');
  readonly explicitSort = signal(false);
  readonly visualSortField = computed(() => (this.explicitSort() ? this.sortField() : ''));
  readonly statsUpdating = signal(false);
  private statsRequestId = 0;

  readonly filters = toSignal(
    toObservable(this.reloadTick).pipe(
      switchMap(() => this.journalsApi.filters().pipe(catchError(() => of(EMPTY_FILTERS))))
    ),
    { initialValue: EMPTY_FILTERS }
  );

  readonly viewStats = toSignal(
    combineLatest({
      search: this.search.valueChanges.pipe(debounceTime(400), startWith(this.search.value)),
      typeUri: this.typeUri.valueChanges.pipe(startWith(this.typeUri.value)),
      workflowStep: this.workflowStep.valueChanges.pipe(startWith(this.workflowStep.value)),
      issnCount: this.issnCount.valueChanges.pipe(startWith(this.issnCount.value)),
      publications: this.publications.valueChanges.pipe(startWith(this.publications.value)),
      citescore: this.citescore.valueChanges.pipe(startWith(this.citescore.value)),
      quartile: this.quartile.valueChanges.pipe(startWith(this.quartile.value)),
      reload: toObservable(this.reloadTick)
    }).pipe(
      debounceTime(0),
      switchMap((state): Observable<JournalStats | null> => {
        const requestId = ++this.statsRequestId;
        queueMicrotask(() => {
          if (requestId === this.statsRequestId) {
            this.statsUpdating.set(true);
          }
        });
        return this.journalsApi.stats(journalStatsQuery(state)).pipe(
          map((stats): JournalStats | null => stats),
          catchError((): Observable<JournalStats | null> => EMPTY),
          finalize(() => {
            if (requestId === this.statsRequestId) {
              this.statsUpdating.set(false);
            }
          })
        );
      })
    ),
    { initialValue: null as JournalStats | null }
  );

  readonly stats = computed(() => this.viewStats());
  readonly hasActiveFilters = computed(() => {
    this.stats();
    this.statsUpdating();
    return journalHasActiveFilters({
      search: this.search.value,
      typeUri: this.typeUri.value,
      workflowStep: this.workflowStep.value,
      issnCount: this.issnCount.value,
      publications: this.publications.value,
      citescore: this.citescore.value,
      quartile: this.quartile.value
    });
  });

  readonly result = toSignal(
    combineLatest({
      search: this.search.valueChanges.pipe(debounceTime(400), startWith(this.search.value)),
      typeUri: this.typeUri.valueChanges.pipe(startWith(this.typeUri.value)),
      workflowStep: this.workflowStep.valueChanges.pipe(startWith(this.workflowStep.value)),
      issnCount: this.issnCount.valueChanges.pipe(startWith(this.issnCount.value)),
      publications: this.publications.valueChanges.pipe(startWith(this.publications.value)),
      citescore: this.citescore.valueChanges.pipe(startWith(this.citescore.value)),
      quartile: this.quartile.valueChanges.pipe(startWith(this.quartile.value)),
      sortField: toObservable(this.sortField),
      sortDirection: toObservable(this.sortDirection),
      page: toObservable(this.page),
      reload: toObservable(this.reloadTick)
    }).pipe(
      switchMap(({ search, typeUri, workflowStep, issnCount, publications, citescore, quartile, sortField, sortDirection, page }) =>
        toPageViewState(
          this.journalsApi.list({
            search,
            typeUri: typeUri || undefined,
            workflowStep: workflowStep || undefined,
            issnCount: issnCount || undefined,
            publications: publications || undefined,
            citescore: citescore || undefined,
            quartile: quartile || undefined,
            sort: sortField,
            direction: sortDirection,
            page,
            pageSize: PAGE_SIZE
          }),
          PAGE_SIZE
        )
      )
    ),
    { initialValue: { status: 'loading' as const, data: emptyPage<JournalItem>(PAGE_SIZE) } }
  );

  readonly language = this.i18n.currentLanguage;
  readonly showUnresolved = computed(() => (this.stats()?.unresolvedResearchOutputRefs ?? 0) > 0);

  readonly historyState = toSignal(
    toObservable(this.reloadTick).pipe(
      switchMap(() =>
        this.syncApi.history(JOURNAL_HISTORY_ENTITIES).pipe(
          map((items) => ({ status: 'success' as const, items })),
          catchError(() => of({ status: 'error' as const, items: [] as SyncRun[] })),
          startWith({ status: 'loading' as const, items: [] as SyncRun[] })
        )
      )
    ),
    { initialValue: { status: 'loading' as const, items: [] as SyncRun[] } }
  );

  constructor() {
    combineLatest([
      this.search.valueChanges,
      this.typeUri.valueChanges,
      this.workflowStep.valueChanges,
      this.issnCount.valueChanges,
      this.publications.valueChanges,
      this.citescore.valueChanges,
      this.quartile.valueChanges
    ])
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.page.set(1));
  }

  currentQuery(): JournalQuery {
    return {
      search: this.search.value,
      typeUri: this.typeUri.value || undefined,
      workflowStep: this.workflowStep.value || undefined,
      issnCount: this.issnCount.value || undefined,
      publications: this.publications.value || undefined,
      citescore: this.citescore.value || undefined,
      quartile: this.quartile.value || undefined,
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
    this.sortField.set(next.sort.field as JournalSort);
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
    this.workflowStep.setValue('');
    this.issnCount.setValue('');
    this.publications.setValue('');
    this.citescore.setValue('');
    this.quartile.setValue('');
    this.page.set(1);
  }

  typeLabel(item: { type?: string | null; typeEs?: string | null; typeEn?: string | null }): string {
    return localized(item.typeEs, item.typeEn, item.type, this.language());
  }

  workflowLabel(item: {
    workflowStep?: string | null;
    workflowEs?: string | null;
    workflowEn?: string | null;
    step?: string | null;
    labelEs?: string | null;
    labelEn?: string | null;
  }): string {
    return localized(
      item.workflowEs ?? item.labelEs,
      item.workflowEn ?? item.labelEn,
      item.workflowStep ?? item.step,
      this.language()
    );
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
