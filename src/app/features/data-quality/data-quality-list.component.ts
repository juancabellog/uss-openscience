import { DOCUMENT } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, HostListener, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable, Subscription, catchError, combineLatest, debounceTime, interval, map, of, startWith, switchMap } from 'rxjs';
import { I18nService } from '../../core/i18n/i18n.service';
import { AuthService } from '../../core/services/auth.service';
import {
  DataQualityApplyResult,
  DataQualityExecution,
  DataQualityFinding,
  DataQualityFindingEvidence,
  DataQualityRule,
  DataQualityRuleCount,
  DataQualityStats,
  SortDirection
} from '../../core/models/openscience.models';
import { DataQualityApiService } from '../../core/services/api/data-quality-api.service';
import { NotificationService } from '../../core/services/notification.service';
import { apiErrorCode } from '../../core/utils/api-error';
import { downloadBlob, filenameFromDisposition } from '../../core/utils/download';
import { emptyPage, PageViewState } from '../../core/utils/page-view-state';
import { DataTableShellComponent } from '../../shared/components/data-table-shell/data-table-shell.component';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state/error-state.component';
import { FilterBarComponent } from '../../shared/components/filter-bar/filter-bar.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { LoadingStateComponent } from '../../shared/components/loading-state/loading-state.component';
import { MiniStatComponent } from '../../shared/components/mini-stat/mini-stat.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { PaginationComponent } from '../../shared/components/pagination/pagination.component';
import { SearchBoxComponent } from '../../shared/components/search-box/search-box.component';
import { SortableHeaderComponent } from '../../shared/components/sortable-header/sortable-header.component';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';
import { I18N_HOST, I18N_IMPORTS } from '../../shared/i18n/i18n.imports';
import { OsBadgeTone } from '../../shared/components/status-badge/status-badge.component';
import { ariaSortValue, nextTableSort, TableSort } from '../../shared/table/table-sort';

const PAGE_SIZE = 25;
const POLL_MS = 5000;
export const DQ_RULE_SOURCE_FAMILIES = new Set(['PURE', 'CROSSREF', 'OPENALEX', 'EXTERNAL']);
export const CROSSREF_ORCID_PREVIEW = 50;
const CROSSREF_SUGGESTED_MAX = 240;

export type CrossrefOrcidEvidence = {
  doi: string;
  externalOrcidCount: number;
  externalOrcids: string[];
};

export function crossrefOrcidSuggestedText(suggested: string | null | undefined, summary: string | null | undefined): string {
  const value = suggested ?? '';
  if (value && value.length <= CROSSREF_SUGGESTED_MAX) {
    return value;
  }
  return summary || '';
}

export function parseCrossrefOrcidEvidence(
  ruleCode: string | null | undefined,
  evidenceJson: string | null | undefined
): CrossrefOrcidEvidence | null {
  if (ruleCode !== 'CROSSREF-007' || !evidenceJson) {
    return null;
  }
  try {
    const parsed = JSON.parse(evidenceJson) as {
      doi?: string;
      externalOrcidCount?: number;
      externalOrcids?: unknown;
    };
    if (!Array.isArray(parsed.externalOrcids)) {
      return null;
    }
    const orcids = parsed.externalOrcids.filter((item): item is string => typeof item === 'string');
    return {
      doi: parsed.doi ?? '',
      externalOrcidCount: Number(parsed.externalOrcidCount) || orcids.length,
      externalOrcids: orcids
    };
  } catch {
    return null;
  }
}

export function dataQualityRulesForSource(
  counts: DataQualityRuleCount[],
  rules: DataQualityRule[],
  source: string
): DataQualityRuleCount[] {
  if (!source) {
    return counts;
  }
  if (DQ_RULE_SOURCE_FAMILIES.has(source)) {
    const codes = new Set(rules.filter((rule) => rule.sourceFamily === source).map((rule) => rule.code));
    return counts.filter((item) => codes.has(item.code) || item.sourceFamily === source);
  }
  return counts.filter((item) => item.count > 0);
}

export function previewCrossrefOrcids(orcids: string[], shown: number): string[] {
  return orcids.slice(0, Math.max(0, shown));
}

@Component({
  selector: 'os-data-quality-list',
  hostDirectives: [...I18N_HOST],
  imports: [
    ...I18N_IMPORTS,
    ReactiveFormsModule,
    RouterLink,
    DataTableShellComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    FilterBarComponent,
    IconComponent,
    LoadingStateComponent,
    MiniStatComponent,
    PageHeaderComponent,
    PaginationComponent,
    SearchBoxComponent,
    SortableHeaderComponent,
    StatusBadgeComponent
  ],
  templateUrl: './data-quality-list.component.html',
  styleUrl: './data-quality-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DataQualityListComponent {
  private readonly api = inject(DataQualityApiService);
  private readonly i18n = inject(I18nService);
  private readonly auth = inject(AuthService);
  private readonly notifications = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly document = inject(DOCUMENT);
  private readonly route = inject(ActivatedRoute);

  readonly tab = signal<'findings' | 'rules' | 'history'>('findings');
  readonly search = new FormControl('', { nonNullable: true });
  readonly category = new FormControl('', { nonNullable: true });
  readonly rule = new FormControl('', { nonNullable: true });
  readonly entity = new FormControl('', { nonNullable: true });
  readonly action = new FormControl('', { nonNullable: true });
  readonly status = new FormControl('OPEN', { nonNullable: true });
  readonly source = new FormControl('', { nonNullable: true });
  readonly page = signal(1);
  readonly tableTick = signal(0);
  readonly sortField = signal('lastDetectedAt');
  readonly sortDirection = signal<SortDirection>('desc');
  readonly selectedIds = signal<number[]>([]);
  readonly detail = signal<DataQualityFinding | null>(null);
  readonly ruleDetail = signal<DataQualityRule | null>(null);
  readonly historyDetail = signal<DataQualityExecution | null>(null);
  readonly stats = signal<DataQualityStats | null>(null);
  readonly execution = signal<DataQualityExecution | null>(null);
  readonly rules = signal<DataQualityRule[]>([]);
  readonly history = signal<DataQualityExecution[]>([]);
  readonly confirmRun = signal(false);
  readonly confirmApply = signal(false);
  readonly confirmNotApplicable = signal(false);
  readonly busy = signal(false);
  readonly applying = signal(false);
  readonly exportBusy = signal(false);
  readonly applyResult = signal<DataQualityApplyResult | null>(null);
  readonly editValue = signal('');
  readonly crossrefOrcidShown = signal(CROSSREF_ORCID_PREVIEW);
  readonly justification = signal('');
  readonly notApplicableId = signal<number | null>(null);
  private pollSub: Subscription | null = null;
  private lastNotifiedStatus: string | null = null;
  private readonly defaultSort: TableSort = { field: 'lastDetectedAt', direction: 'desc' };
  private explicitSort = false;

  private readonly resultState = toSignal(
    combineLatest({
      search: this.search.valueChanges.pipe(debounceTime(400), startWith(this.search.value)),
      category: this.category.valueChanges.pipe(startWith(this.category.value)),
      rule: this.rule.valueChanges.pipe(startWith(this.rule.value)),
      entity: this.entity.valueChanges.pipe(startWith(this.entity.value)),
      action: this.action.valueChanges.pipe(startWith(this.action.value)),
      status: this.status.valueChanges.pipe(startWith(this.status.value)),
      source: this.source.valueChanges.pipe(startWith(this.source.value)),
      sortField: toObservable(this.sortField),
      sortDirection: toObservable(this.sortDirection),
      page: toObservable(this.page),
      tableTick: toObservable(this.tableTick)
    }).pipe(
      switchMap((filters): Observable<PageViewState<DataQualityFinding>> =>
        this.api.list(this.toQuery(filters)).pipe(
          map((data): PageViewState<DataQualityFinding> => ({ status: 'success', data })),
          catchError(() => of({ status: 'error' as const, data: emptyPage<DataQualityFinding>(PAGE_SIZE) }))
        )
      )
    ),
    { initialValue: { status: 'loading', data: emptyPage<DataQualityFinding>(PAGE_SIZE) } }
  );

  readonly table = computed((): PageViewState<DataQualityFinding> => this.resultState() ?? {
    status: 'loading',
    data: emptyPage<DataQualityFinding>(PAGE_SIZE)
  });

  readonly running = computed(() => isRunning(this.execution()?.status));
  readonly selectedFindings = computed(() => {
    const selected = new Set(this.selectedIds());
    return this.table().data.items.filter((item) => selected.has(item.id));
  });
  readonly bulkReady = computed(() => {
    const items = this.selectedFindings();
    if (items.length === 0) {
      return false;
    }
    const first = items[0];
    return items.every(
      (item) =>
        item.ruleCode === first.ruleCode
        && item.correctionMode === first.correctionMode
        && item.issueKind === first.issueKind
        && item.correctionMode === 'SAFE_SUGGESTION'
        && !!item.suggestedValue
    );
  });

  readonly filterValues = toSignal(
    combineLatest({
      search: this.search.valueChanges.pipe(startWith(this.search.value)),
      category: this.category.valueChanges.pipe(startWith(this.category.value)),
      rule: this.rule.valueChanges.pipe(startWith(this.rule.value)),
      entity: this.entity.valueChanges.pipe(startWith(this.entity.value)),
      action: this.action.valueChanges.pipe(startWith(this.action.value)),
      status: this.status.valueChanges.pipe(startWith(this.status.value)),
      source: this.source.valueChanges.pipe(startWith(this.source.value))
    }),
    {
      initialValue: {
        search: this.search.value,
        category: this.category.value,
        rule: this.rule.value,
        entity: this.entity.value,
        action: this.action.value,
        status: this.status.value,
        source: this.source.value
      }
    }
  );

  readonly hasExecutionUniverse = computed(
    () => this.execution() != null || this.history().length > 0
  );

  readonly filtersDirty = computed(() => {
    const filters = this.filterValues();
    return Boolean(
      filters.search.trim()
      || filters.category
      || filters.rule
      || filters.entity
      || filters.action
      || filters.status !== 'OPEN'
      || filters.source
    );
  });

  readonly canExport = computed(() => this.table().data.total > 0);
  readonly canAdminister = computed(() => this.auth.canAdminister('DATA_QUALITY'));
  readonly visibleRuleCounts = computed(() =>
    dataQualityRulesForSource(this.stats()?.ruleCounts ?? [], this.rules(), this.filterValues().source)
  );

  readonly findingsEmptyKind = computed((): 'never' | 'zero' | 'filtered' | null => {
    if (this.running()) {
      return null;
    }
    const table = this.table();
    if (table.status === 'loading' || table.status === 'error' || table.data.items.length > 0) {
      return null;
    }
    if (!this.hasExecutionUniverse()) {
      return 'never';
    }
    const last = this.execution();
    if (last?.status === 'COMPLETED' && last.findingCount === 0) {
      return 'zero';
    }
    const anyFindings = (last?.findingCount ?? 0) > 0
      || this.history().some((item) => item.findingCount > 0);
    return anyFindings ? 'filtered' : 'zero';
  });

  constructor() {
    const rule = this.route.snapshot.queryParamMap.get('rule');
    if (rule) {
      this.rule.setValue(rule);
    }
    combineLatest([
      this.search.valueChanges,
      this.category.valueChanges,
      this.rule.valueChanges,
      this.entity.valueChanges,
      this.action.valueChanges,
      this.status.valueChanges,
      this.source.valueChanges
    ])
      .pipe(takeUntilDestroyed())
      .subscribe(() => {
        const allowed = new Set(this.visibleRuleCounts().map((item) => item.code));
        if (this.rule.value && this.source.value && !allowed.has(this.rule.value)) {
          this.rule.setValue('');
          return;
        }
        this.page.set(1);
        this.selectedIds.set([]);
        this.reloadStats();
      });
    this.reloadStats();
    this.reloadRules();
    this.reloadHistory();
    this.api
      .latest()
      .pipe(catchError(() => of(null)), takeUntilDestroyed())
      .subscribe((run) => {
        this.execution.set(run);
        if (isRunning(run?.status)) {
          this.startProgressPoll();
        }
      });
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.applyResult()) {
      this.applyResult.set(null);
      return;
    }
    if (this.detail()) {
      this.closeFinding();
      return;
    }
    this.ruleDetail.set(null);
    this.historyDetail.set(null);
  }

  onPageChange(page: number): void {
    this.page.set(page);
    this.selectedIds.set([]);
  }

  onSort(field: string): void {
    const next = nextTableSort(
      { field: this.sortField(), direction: this.sortDirection() },
      field,
      this.defaultSort,
      this.explicitSort
    );
    this.sortField.set(next.sort.field);
    this.sortDirection.set(next.sort.direction);
    this.explicitSort = next.explicit;
    this.page.set(1);
  }

  ariaSort(field: string): 'ascending' | 'descending' | 'none' {
    return ariaSortValue(field, this.sortField(), this.sortDirection());
  }

  toggleRow(id: number, checked: boolean): void {
    this.selectedIds.update((current) => {
      const next = new Set(current);
      if (checked) {
        next.add(id);
      } else {
        next.delete(id);
      }
      return [...next];
    });
  }

  toggleAll(checked: boolean): void {
    this.selectedIds.set(checked ? this.table().data.items.map((item) => item.id) : []);
  }

  isSelected(id: number): boolean {
    return this.selectedIds().includes(id);
  }

  onFindingRowClick(event: Event, finding: DataQualityFinding): void {
    const target = event.target;
    if (!(target instanceof Element)) {
      return;
    }
    if (target.closest('button, a, input, label, textarea, select')) {
      return;
    }
    this.openFinding(finding);
  }

  openFinding(finding: DataQualityFinding): void {
    this.detail.set(finding);
    this.crossrefOrcidShown.set(CROSSREF_ORCID_PREVIEW);
    this.editValue.set(finding.suggestedValue || finding.currentValue || '');
    this.api.finding(finding.id).pipe(catchError(() => of(finding))).subscribe((item) => {
      this.detail.set(item);
      this.editValue.set(item.suggestedValue || item.currentValue || '');
    });
  }

  closeFinding(): void {
    const id = this.detail()?.id ?? null;
    this.detail.set(null);
    this.crossrefOrcidShown.set(CROSSREF_ORCID_PREVIEW);
    if (id == null) {
      return;
    }
    queueMicrotask(() => {
      this.document.getElementById('dq-finding-open-' + id)?.focus();
    });
  }

  startValidation(): void {
    this.confirmRun.set(false);
    this.busy.set(true);
    this.api.start().subscribe({
      next: (run) => {
        this.busy.set(false);
        this.execution.set(run);
        this.lastNotifiedStatus = run.status;
        this.startProgressPoll();
      },
      error: (error: HttpErrorResponse) => {
        this.busy.set(false);
        const code = apiErrorCode(error);
        this.notifications.show(
          'error',
          this.i18n.translate('dataQuality.toast.startFailedTitle'),
          this.i18n.translate(code === 'DATA_QUALITY_RUNNING' ? 'dataQuality.alreadyRunning' : 'dataQuality.toast.startFailed')
        );
      }
    });
  }

  applySuggestion(finding: DataQualityFinding): void {
    this.runApply(() => this.api.apply(finding.id, { applySuggestion: true }));
  }

  applyManual(finding: DataQualityFinding): void {
    this.runApply(() => this.api.apply(finding.id, { newValue: this.editValue() }));
  }

  applyBulk(): void {
    this.confirmApply.set(false);
    this.runApply(() => this.api.applyBulk(this.selectedIds()));
  }

  markInReview(finding: DataQualityFinding): void {
    this.api.markInReview(finding.id).subscribe({
      next: (updated) => {
        this.detail.set(updated);
        this.refreshTable();
      },
      error: () => this.notifications.show('error', this.i18n.translate('dataQuality.toast.actionFailedTitle'), this.i18n.translate('dataQuality.toast.actionFailed'))
    });
  }

  askNotApplicable(finding: DataQualityFinding): void {
    this.notApplicableId.set(finding.id);
    this.justification.set('');
    this.confirmNotApplicable.set(true);
  }

  confirmNotApplicableSubmit(): void {
    const id = this.notApplicableId();
    if (!id || !this.justification().trim()) {
      return;
    }
    this.confirmNotApplicable.set(false);
    this.api.markNotApplicable(id, this.justification().trim()).subscribe({
      next: (updated) => {
        this.detail.set(updated);
        this.refreshTable();
      },
      error: () => this.notifications.show('error', this.i18n.translate('dataQuality.toast.actionFailedTitle'), this.i18n.translate('dataQuality.toast.actionFailed'))
    });
  }

  retryLocal(finding: DataQualityFinding): void {
    this.api.retryLocal(finding.id).subscribe({
      next: (result) => {
        this.notifications.show(
          result.status === 'LOCAL_RECONCILED' ? 'success' : 'warning',
          this.i18n.translate('dataQuality.toast.retryTitle'),
          result.message || this.i18n.translate('dataQuality.toast.retryDone')
        );
        this.refreshTable();
      },
      error: () => this.notifications.show('error', this.i18n.translate('dataQuality.toast.actionFailedTitle'), this.i18n.translate('dataQuality.toast.actionFailed'))
    });
  }

  clearFilters(): void {
    this.search.setValue('');
    this.category.setValue('');
    this.rule.setValue('');
    this.entity.setValue('');
    this.action.setValue('');
    this.status.setValue('OPEN');
    this.source.setValue('');
  }

  exportExcel(): void {
    if (!this.canExport() || this.exportBusy()) {
      return;
    }
    this.exportBusy.set(true);
    this.api.export(this.currentQuery()).subscribe({
      next: (response) => {
        this.exportBusy.set(false);
        const blob = response.body;
        if (!blob) {
          return;
        }
        downloadBlob(blob, filenameFromDisposition(response.headers.get('Content-Disposition'), 'openscience-calidad-datos.xlsx'));
      },
      error: (error: HttpErrorResponse) => {
        this.exportBusy.set(false);
        const code = apiErrorCode(error);
        this.notifications.show(
          'error',
          this.i18n.translate('dataQuality.toast.exportFailedTitle'),
          this.i18n.translate(code === 'EXPORT_EMPTY' ? 'dataQuality.exportEmpty' : 'dataQuality.toast.exportFailed')
        );
      }
    });
  }

  actionTone(mode: string): OsBadgeTone {
    if (mode === 'SAFE_SUGGESTION') return 'success';
    if (mode === 'MANUAL_REVIEW') return 'review';
    return 'info';
  }

  statusTone(status: string): OsBadgeTone {
    if (status === 'RESOLVED' || status === 'COMPLETED') return 'success';
    if (status === 'IN_REVIEW' || status === 'RUNNING') return 'review';
    if (status === 'FAILED') return 'danger';
    if (status === 'NOT_APPLICABLE') return 'muted';
    return 'open';
  }

  progressPercent(run: DataQualityExecution): number {
    if (!run.totalRecords) {
      return 0;
    }
    return Math.min(100, Math.round((run.evaluatedRecords / run.totalRecords) * 100));
  }

  crossrefOrcidSuggested(finding: DataQualityFinding): string {
    return crossrefOrcidSuggestedText(finding.suggestedValue, finding.summary);
  }

  crossrefOrcidEvidence(finding: DataQualityFinding): CrossrefOrcidEvidence | null {
    return parseCrossrefOrcidEvidence(finding.ruleCode, finding.evidenceJson);
  }

  crossrefOrcidPreview(finding: DataQualityFinding): string[] {
    const evidence = this.crossrefOrcidEvidence(finding);
    if (!evidence) {
      return [];
    }
    return previewCrossrefOrcids(evidence.externalOrcids, this.crossrefOrcidShown());
  }

  showMoreCrossrefOrcids(): void {
    this.crossrefOrcidShown.update((shown) => shown + CROSSREF_ORCID_PREVIEW);
  }

  evidenceRows(finding: DataQualityFinding): DataQualityFindingEvidence[] {
    if (finding.evidences?.length) {
      return finding.evidences;
    }
    try {
      const parsed = finding.evidenceJson
        ? JSON.parse(finding.evidenceJson) as {
            sources?: Array<{
              source?: string;
              sourceRecordId?: string;
              value?: string;
              result?: string;
              lastExternalSyncAt?: string;
            }>;
          }
        : null;
      return (parsed?.sources ?? []).map((item) => ({
        source: item.source ?? null,
        sourceRecordId: item.sourceRecordId ?? null,
        sourceField: null,
        externalValue: item.value ?? null,
        normalizedExternalValue: null,
        comparisonResult: item.result ?? null,
        externalLastSyncAt: item.lastExternalSyncAt ?? null,
        metadataJson: null
      }));
    } catch {
      return [];
    }
  }

  hasExternalEvidence(finding: DataQualityFinding): boolean {
    return this.evidenceRows(finding).length > 0;
  }

  hasExternalConflict(finding: DataQualityFinding): boolean {
    return finding.issueKind === 'EXTERNAL_SOURCE_CONFLICT';
  }

  comparisonTone(result: string | null | undefined): OsBadgeTone {
    if (result === 'CONSISTENT') return 'success';
    if (result === 'DIFFERENCE_DETECTED' || result === 'EXTERNAL_SOURCE_CONFLICT') return 'review';
    if (result === 'NOT_COMPARABLE') return 'muted';
    return 'info';
  }

  sourceLabel(source: string | null | undefined): string {
    const key = 'dataQuality.source.' + (source ?? '');
    const translated = this.i18n.translate(key);
    return translated === key ? (source ?? '') : translated;
  }

  evidenceChildIds(finding: DataQualityFinding): string {
    try {
      const evidence = finding.evidenceJson ? JSON.parse(finding.evidenceJson) as { removeChildPureIds?: number[] } : null;
      return (evidence?.removeChildPureIds ?? []).join(', ');
    } catch {
      return '';
    }
  }

  private runApply(request: () => Observable<DataQualityApplyResult>): void {
    this.applying.set(true);
    request().subscribe({
      next: (result) => {
        this.applying.set(false);
        this.applyResult.set(result);
        this.selectedIds.set([]);
        this.refreshTable();
        if (result.applied > 0 && result.failed === 0 && result.remoteChanged === 0 && result.localFailed === 0) {
          this.notifications.show(
            'success',
            this.i18n.translate('dataQuality.toast.appliedTitle'),
            this.i18n.translate('dataQuality.toast.appliedBody', { count: result.applied })
          );
        }
        if (this.detail()) {
          this.api.finding(this.detail()!.id).pipe(catchError(() => of(this.detail()!))).subscribe((item) => this.detail.set(item));
        }
      },
      error: () => {
        this.applying.set(false);
        this.notifications.show('error', this.i18n.translate('dataQuality.toast.actionFailedTitle'), this.i18n.translate('dataQuality.toast.actionFailed'));
      }
    });
  }

  private startProgressPoll(): void {
    this.pollSub?.unsubscribe();
    this.pollSub = interval(POLL_MS)
      .pipe(startWith(0), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        const current = this.execution();
        if (!current) {
          return;
        }
        this.api.execution(current.id).pipe(catchError(() => of(current))).subscribe((run) => {
          this.execution.set(run);
          if (!isRunning(run.status)) {
            this.pollSub?.unsubscribe();
            this.pollSub = null;
            if (this.lastNotifiedStatus !== run.status) {
              this.lastNotifiedStatus = run.status;
              if (run.status === 'COMPLETED') {
                this.notifications.show(
                  'success',
                  this.i18n.translate('dataQuality.toast.completedTitle'),
                  this.i18n.translate('dataQuality.toast.completedBody', {
                    findings: run.findingCount,
                    records: this.stats()?.affectedRecords ?? run.findingCount
                  })
                );
              } else if (run.status === 'FAILED') {
                this.notifications.show('error', this.i18n.translate('dataQuality.toast.failedTitle'), this.i18n.translate('dataQuality.toast.failedBody'));
              }
              this.refreshTable();
              this.reloadHistory();
              this.reloadRules();
            }
          }
        });
      });
  }

  private refreshTable(): void {
    this.tableTick.update((value) => value + 1);
    this.reloadStats();
  }

  private reloadStats(): void {
    this.api.stats(this.currentQuery()).pipe(catchError(() => of(null))).subscribe((stats) => this.stats.set(stats));
  }

  private reloadRules(): void {
    this.api.rules().pipe(catchError(() => of([]))).subscribe((rules) => this.rules.set(rules));
  }

  private reloadHistory(): void {
    this.api.history().pipe(catchError(() => of([]))).subscribe((items) => this.history.set(items));
  }

  private currentQuery() {
    return this.toQuery({
      search: this.search.value,
      category: this.category.value,
      rule: this.rule.value,
      entity: this.entity.value,
      action: this.action.value,
      status: this.status.value,
      source: this.source.value,
      sortField: this.sortField(),
      sortDirection: this.sortDirection(),
      page: this.page()
    });
  }

  private toQuery(filters: {
    search: string;
    category: string;
    rule: string;
    entity: string;
    action: string;
    status: string;
    source: string;
    sortField: string;
    sortDirection: SortDirection;
    page: number;
  }) {
    return {
      search: filters.search || undefined,
      category: filters.category || undefined,
      rule: filters.rule || undefined,
      entity: filters.entity || undefined,
      action: filters.action || undefined,
      status: filters.status || undefined,
      source: filters.source || undefined,
      sort: filters.sortField,
      direction: filters.sortDirection,
      page: filters.page,
      pageSize: PAGE_SIZE
    };
  }
}

function isRunning(status?: string | null): boolean {
  return status === 'RUNNING' || status === 'PENDING';
}
