import { ChangeDetectionStrategy, Component, computed, HostListener, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  catchError,
  combineLatest,
  debounceTime,
  filter,
  interval,
  of,
  startWith,
  switchMap,
  tap
} from 'rxjs';
import { AuditChange, AuditEvent, AuditSyncError } from '../../core/models/openscience.models';
import { AuditService } from '../../core/services/platform-services';
import { emptyPage, toPageViewState } from '../../core/utils/page-view-state';
import { DataTableShellComponent } from '../../shared/components/data-table-shell/data-table-shell.component';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state/error-state.component';
import { FilterBarComponent } from '../../shared/components/filter-bar/filter-bar.component';
import { LoadingStateComponent } from '../../shared/components/loading-state/loading-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { PaginationComponent } from '../../shared/components/pagination/pagination.component';
import { SearchBoxComponent } from '../../shared/components/search-box/search-box.component';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';
import { I18N_HOST, I18N_IMPORTS } from '../../shared/i18n/i18n.imports';
import {
  AUDIT_ENTITIES,
  AUDIT_SOURCES,
  AUDIT_STATUSES,
  auditActorLabel,
  auditEmptyKind,
  auditEntityKey,
  auditHasActiveFilters,
  auditIsActive,
  auditModeKey,
  auditOperationKey,
  auditOutcomeKey,
  auditSourceKey,
  auditStatusTone,
  auditTypeKey,
  auditDurationLabel,
  formatAuditValue
} from './audit.util';
import { openAlexAuditShowsRecovery, openAlexStaleMessageKey } from '../../core/sync/openalex-history.util';

const PAGE_SIZE = 25;
const POLL_MS = 8000;

@Component({
  selector: 'os-audit',
  hostDirectives: [...I18N_HOST],
  imports: [
    ...I18N_IMPORTS,
    ReactiveFormsModule,
    DataTableShellComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    FilterBarComponent,
    LoadingStateComponent,
    PageHeaderComponent,
    PaginationComponent,
    SearchBoxComponent,
    StatusBadgeComponent,
    RouterLink
  ],
  templateUrl: './audit.component.html',
  styleUrl: './audit.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AuditComponent {
  private readonly audit = inject(AuditService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly search = new FormControl('', { nonNullable: true });
  readonly from = new FormControl('', { nonNullable: true });
  readonly to = new FormControl('', { nonNullable: true });
  readonly eventType = new FormControl('SYNC', { nonNullable: true });
  readonly source = new FormControl('', { nonNullable: true });
  readonly entity = new FormControl('', { nonNullable: true });
  readonly status = new FormControl('', { nonNullable: true });
  readonly syncMode = new FormControl('', { nonNullable: true });
  readonly actor = new FormControl('', { nonNullable: true });
  readonly page = signal(1);
  readonly reloadTick = signal(0);
  readonly selectedId = signal<string | null>(null);
  readonly detailTab = signal<'changes' | 'errors'>('changes');
  readonly changePage = signal(1);
  readonly errorPage = signal(1);
  readonly expandedChangeId = signal<number | null>(null);

  readonly sources = AUDIT_SOURCES;
  readonly entities = AUDIT_ENTITIES;
  readonly statuses = AUDIT_STATUSES;
  readonly sourceKey = auditSourceKey;
  readonly operationKey = auditOperationKey;
  readonly entityKey = auditEntityKey;
  readonly typeKey = auditTypeKey;
  readonly outcomeKey = auditOutcomeKey;
  readonly statusTone = auditStatusTone;
  readonly modeKey = auditModeKey;
  readonly actorLabel = auditActorLabel;
  readonly formatValue = formatAuditValue;
  readonly durationLabel = auditDurationLabel;
  readonly staleMessageKey = openAlexStaleMessageKey;
  readonly showsOpenAlexRecovery = openAlexAuditShowsRecovery;

  readonly result = toSignal(
    combineLatest({
      search: this.search.valueChanges.pipe(startWith(this.search.value), debounceTime(200)),
      from: this.from.valueChanges.pipe(startWith(this.from.value)),
      to: this.to.valueChanges.pipe(startWith(this.to.value)),
      eventType: this.eventType.valueChanges.pipe(startWith(this.eventType.value)),
      source: this.source.valueChanges.pipe(startWith(this.source.value)),
      entity: this.entity.valueChanges.pipe(startWith(this.entity.value)),
      status: this.status.valueChanges.pipe(startWith(this.status.value)),
      syncMode: this.syncMode.valueChanges.pipe(startWith(this.syncMode.value)),
      actor: this.actor.valueChanges.pipe(startWith(this.actor.value), debounceTime(200)),
      page: toObservable(this.page),
      reload: toObservable(this.reloadTick)
    }).pipe(
      switchMap((query) =>
        toPageViewState(
          this.audit.list({
            search: query.search.trim() || undefined,
            from: query.from || undefined,
            to: query.to || undefined,
            eventType: query.eventType || undefined,
            source: query.source || undefined,
            entity: query.entity || undefined,
            status: query.status || undefined,
            syncMode: query.syncMode || undefined,
            actor: query.actor.trim() || undefined,
            page: query.page,
            pageSize: PAGE_SIZE,
            sort: 'startedAt',
            direction: 'desc'
          }),
          PAGE_SIZE
        )
      )
    ),
    { initialValue: { status: 'loading' as const, data: emptyPage<AuditEvent>(PAGE_SIZE) } }
  );

  readonly selected = toSignal(
    combineLatest({
      id: toObservable(this.selectedId),
      reload: toObservable(this.reloadTick)
    }).pipe(
      switchMap(({ id }) => (id ? this.audit.getById(id).pipe(catchError(() => of(null))) : of(null)))
    ),
    { initialValue: null }
  );

  readonly changes = toSignal(
    combineLatest({
      id: toObservable(this.selectedId),
      tab: toObservable(this.detailTab),
      page: toObservable(this.changePage),
      reload: toObservable(this.reloadTick)
    }).pipe(
      switchMap(({ id, tab, page }) =>
        id && tab === 'changes'
          ? toPageViewState(this.audit.changes(id, page, PAGE_SIZE), PAGE_SIZE)
          : of({ status: 'success' as const, data: emptyPage<AuditChange>(PAGE_SIZE) })
      )
    ),
    { initialValue: { status: 'loading' as const, data: emptyPage<AuditChange>(PAGE_SIZE) } }
  );

  readonly errors = toSignal(
    combineLatest({
      id: toObservable(this.selectedId),
      tab: toObservable(this.detailTab),
      page: toObservable(this.errorPage),
      reload: toObservable(this.reloadTick)
    }).pipe(
      switchMap(({ id, tab, page }) =>
        id && tab === 'errors'
          ? toPageViewState(this.audit.errors(id, page, PAGE_SIZE), PAGE_SIZE)
          : of({ status: 'success' as const, data: emptyPage<AuditSyncError>(PAGE_SIZE) })
      )
    ),
    { initialValue: { status: 'loading' as const, data: emptyPage<AuditSyncError>(PAGE_SIZE) } }
  );

  readonly emptyKind = computed(() =>
    auditEmptyKind({
      status: this.result().status,
      total: this.result().data.total,
      filtered: this.hasFilters()
    })
  );

  constructor() {
    combineLatest([
      this.search.valueChanges,
      this.from.valueChanges,
      this.to.valueChanges,
      this.eventType.valueChanges,
      this.source.valueChanges,
      this.entity.valueChanges,
      this.status.valueChanges,
      this.syncMode.valueChanges,
      this.actor.valueChanges
    ])
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.page.set(1));

    this.route.queryParamMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      const executionId = params.get('executionId');
      if (executionId) {
        this.selectedId.set(executionId);
        this.detailTab.set('changes');
        this.changePage.set(1);
        this.errorPage.set(1);
      }
    });

    interval(POLL_MS)
      .pipe(
        takeUntilDestroyed(),
        filter(() => this.shouldPoll()),
        tap(() => this.reloadTick.update((value) => value + 1))
      )
      .subscribe();
  }

  hasFilters(): boolean {
    return auditHasActiveFilters({
      search: this.search.value.trim(),
      from: this.from.value,
      to: this.to.value,
      eventType: this.eventType.value,
      source: this.source.value,
      entity: this.entity.value,
      status: this.status.value,
      syncMode: this.syncMode.value,
      actor: this.actor.value.trim()
    });
  }

  select(event: AuditEvent): void {
    this.selectedId.set(event.eventId);
    this.detailTab.set('changes');
    this.changePage.set(1);
    this.errorPage.set(1);
    this.expandedChangeId.set(null);
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { executionId: event.executionId },
      queryParamsHandling: 'merge'
    });
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.selectedId()) {
      this.closeDetail();
    }
  }

  closeDetail(): void {
    this.selectedId.set(null);
    this.expandedChangeId.set(null);
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { executionId: null },
      queryParamsHandling: 'merge'
    });
  }

  onPageChange(page: number): void {
    this.page.set(page);
  }

  onChangePage(page: number): void {
    this.changePage.set(page);
  }

  onErrorPage(page: number): void {
    this.errorPage.set(page);
  }

  retry(): void {
    this.reloadTick.update((value) => value + 1);
  }

  resetFilters(): void {
    this.search.setValue('');
    this.from.setValue('');
    this.to.setValue('');
    this.eventType.setValue('SYNC');
    this.source.setValue('');
    this.entity.setValue('');
    this.status.setValue('');
    this.syncMode.setValue('');
    this.actor.setValue('');
    this.page.set(1);
  }

  setTab(tab: 'changes' | 'errors'): void {
    this.detailTab.set(tab);
  }

  toggleChange(id: number): void {
    this.expandedChangeId.set(this.expandedChangeId() === id ? null : id);
  }

  private shouldPoll(): boolean {
    const items = this.result().data.items;
    if (items.some((item) => auditIsActive(item.status))) {
      return true;
    }
    const selected = this.selected();
    return selected != null && auditIsActive(selected.status);
  }
}
