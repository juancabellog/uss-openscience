import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, HostListener, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable, Subscription, catchError, combineLatest, debounceTime, interval, map, of, startWith, switchMap } from 'rxjs';
import { I18nService } from '../../../core/i18n/i18n.service';
import {
  OrcidCandidate,
  OrcidCandidateEvidence,
  OrcidDiscoveryApplyItem,
  OrcidDiscoveryApplyResult,
  OrcidDiscoveryExecution,
  OrcidDiscoveryPerson,
  OrcidDiscoverySelectedItem,
  OrcidDiscoveryStats,
  ResearcherIdentityIndicators,
  SortDirection
} from '../../../core/models/openscience.models';
import { OrcidApiService } from '../../../core/services/api/orcid-api.service';
import { AuthService } from '../../../core/services/auth.service';
import { ResearcherIdentityApiService } from '../../../core/services/api/researcher-identity-api.service';
import { NotificationService } from '../../../core/services/notification.service';
import { apiErrorCode } from '../../../core/utils/api-error';
import { emptyPage, PageViewState } from '../../../core/utils/page-view-state';
import { DataTableShellComponent } from '../../../shared/components/data-table-shell/data-table-shell.component';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { ErrorStateComponent } from '../../../shared/components/error-state/error-state.component';
import { FilterBarComponent } from '../../../shared/components/filter-bar/filter-bar.component';
import { IconComponent } from '../../../shared/components/icon/icon.component';
import { LoadingStateComponent } from '../../../shared/components/loading-state/loading-state.component';
import { MiniStatComponent } from '../../../shared/components/mini-stat/mini-stat.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { PaginationComponent } from '../../../shared/components/pagination/pagination.component';
import { SearchBoxComponent } from '../../../shared/components/search-box/search-box.component';
import { SortableHeaderComponent } from '../../../shared/components/sortable-header/sortable-header.component';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge.component';
import { I18N_HOST, I18N_IMPORTS } from '../../../shared/i18n/i18n.imports';
import { ariaSortValue, nextTableSort, TableSort } from '../../../shared/table/table-sort';
import {
  applyButtonI18nKey,
  applyConfirmI18nKey,
  applyPersonIds,
  canToggleSyncSelection,
  isSelectedForSync as isQueuedPerson,
  pruneQueuedAfterApply,
  queuedItemFromPerson,
  queuedPersonIdSet,
  resolveCandidateForQueue,
  withQueuedPerson,
  withoutQueuedPerson
} from './orcid-discovery-selection';

const PAGE_SIZE = 25;
const POLL_MS = 5000;

@Component({
  selector: 'os-orcid-discovery',
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
  templateUrl: './orcid-discovery.component.html',
  styleUrl: './orcid-discovery.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class OrcidDiscoveryComponent {
  private readonly api = inject(OrcidApiService);
  readonly auth = inject(AuthService);
  private readonly identityApi = inject(ResearcherIdentityApiService);
  private readonly i18n = inject(I18nService);
  private readonly notifications = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);

  readonly search = new FormControl('', { nonNullable: true });
  readonly status = new FormControl('ALL', { nonNullable: true });
  readonly cargo = new FormControl('', { nonNullable: true });
  readonly page = signal(1);
  readonly tableTick = signal(0);
  readonly sortField = signal('name');
  readonly sortDirection = signal<SortDirection>('asc');
  readonly confirmSearch = signal(false);
  readonly confirmApply = signal(false);
  readonly queued = signal<OrcidDiscoverySelectedItem[]>([]);
  private readonly pendingPersonIds = signal<ReadonlySet<number>>(new Set());
  readonly confirmRecalculate = signal(false);
  readonly busy = signal(false);
  readonly applying = signal(false);
  readonly applyResult = signal<OrcidDiscoveryApplyResult | null>(null);
  readonly canAdminister = computed(() => this.auth.canAdminister('INTEGRATIONS'));
  readonly stats = signal<OrcidDiscoveryStats | null>(null);
  readonly identity = toSignal(
    this.auth.canConsult('INDICATORS')
      ? this.identityApi.summary().pipe(catchError(() => of(null)))
      : of(null),
    { initialValue: null as ResearcherIdentityIndicators | null }
  );
  readonly execution = signal<OrcidDiscoveryExecution | null>(null);
  readonly history = signal<OrcidDiscoveryExecution[]>([]);
  readonly cargos = signal<string[]>([]);
  readonly newResults = signal(false);
  readonly detailPerson = signal<OrcidDiscoveryPerson | null>(null);
  readonly evidenceCandidate = signal<OrcidCandidate | null>(null);
  readonly detailPersonId = signal<number | null>(null);
  private pollSub: Subscription | null = null;
  private lastNotifiedStatus: string | null = null;
  private readonly defaultSort: TableSort = { field: 'name', direction: 'asc' };
  private explicitSort = false;

  readonly executionId = computed(() => this.execution()?.id ?? null);

  private readonly resultState = toSignal(
    combineLatest({
      search: this.search.valueChanges.pipe(debounceTime(400), startWith(this.search.value)),
      status: this.status.valueChanges.pipe(startWith(this.status.value)),
      cargo: this.cargo.valueChanges.pipe(startWith(this.cargo.value)),
      sortField: toObservable(this.sortField),
      sortDirection: toObservable(this.sortDirection),
      page: toObservable(this.page),
      tableTick: toObservable(this.tableTick),
      executionId: toObservable(this.executionId)
    }).pipe(
      switchMap(({ search, status, cargo, sortField, sortDirection, page, executionId }): Observable<PageViewState<OrcidDiscoveryPerson>> => {
        if (!executionId) {
          return of({ status: 'success', data: emptyPage<OrcidDiscoveryPerson>(PAGE_SIZE) });
        }
        return this.api
          .discoveryPeople(executionId, {
            search,
            status,
            cargo: cargo || undefined,
            sort: sortField,
            direction: sortDirection,
            page,
            pageSize: PAGE_SIZE
          })
          .pipe(
            map((data): PageViewState<OrcidDiscoveryPerson> => ({ status: 'success', data })),
            catchError(() => of({
              status: 'error' as const,
              data: emptyPage<OrcidDiscoveryPerson>(PAGE_SIZE)
            }))
          );
      })
    ),
    { initialValue: emptyTableView() }
  );

  readonly table = computed((): PageViewState<OrcidDiscoveryPerson> => this.resultState() ?? emptyTableView());

  readonly running = computed(() => isDiscoveryRunning(this.execution()?.status));

  readonly selectedCount = computed(() => this.queued().length);
  readonly queuedIds = computed(() => queuedPersonIdSet(this.queued()));
  readonly applyButtonKey = computed(() => applyButtonI18nKey(this.selectedCount()));
  readonly applyConfirmKey = computed(() => applyConfirmI18nKey(this.selectedCount()));
  readonly queuedStrongCount = computed(
    () => this.queued().filter((item) => item.personStatus === 'STRONG' || item.selectionOrigin === 'AUTO_STRONG').length
  );
  readonly queuedManualCount = computed(
    () => this.selectedCount() - this.queuedStrongCount()
  );

  constructor() {
    combineLatest([this.search.valueChanges, this.status.valueChanges, this.cargo.valueChanges])
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.page.set(1));
    this.loadInventory();
    this.loadHistory();
    this.api
      .latestDiscovery()
      .pipe(catchError(() => of(null)), takeUntilDestroyed())
      .subscribe((run) => {
        this.execution.set(run);
        this.loadCargos(run?.id ?? null);
        this.loadQueued();
        if (isDiscoveryRunning(run?.status)) {
          this.startProgressPoll();
        }
      });
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.applyResult()) {
      this.closeApplyResult();
      return;
    }
    if (this.confirmApply()) {
      this.confirmApply.set(false);
      return;
    }
    if (this.confirmSearch()) {
      this.confirmSearch.set(false);
      return;
    }
    if (this.confirmRecalculate()) {
      this.confirmRecalculate.set(false);
      return;
    }
    if (this.detailPerson()) {
      this.closeDetail();
    }
  }

  onPageChange(page: number): void {
    this.page.set(page);
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

  orcidHref(orcid: string): string {
    return `https://orcid.org/${orcid}`;
  }

  statusKey(status: string): string {
    return `integrations.orcid.discovery.status.${this.principalStatus(status)}`;
  }

  principalStatus(status: string): string {
    return status === 'TOO_MANY_CANDIDATES' ? 'MANUAL_REVIEW' : status;
  }

  statusReason(person: OrcidDiscoveryPerson): string | null {
    return person.statusReason || (person.personStatus === 'TOO_MANY_CANDIDATES' ? 'TOO_MANY_CANDIDATES' : null);
  }

  statusTone(status: string): 'success' | 'warning' | 'review' | 'danger' | 'neutral' | 'info' {
    const principal = this.principalStatus(status);
    if (principal === 'STRONG' || principal === 'SYNCED' || principal === 'COMPLETED') {
      return 'success';
    }
    if (principal === 'MANUAL_REVIEW' || principal === 'COMPLETED_WITH_WARNINGS') {
      return 'review';
    }
    if (principal === 'CONFLICT' || principal === 'ALREADY_USED' || principal === 'REMOTE_CHANGED') {
      return 'warning';
    }
    if (principal === 'SYNC_FAILED' || principal === 'FAILED' || principal === 'ERROR') {
      return 'danger';
    }
    if (principal === 'RUNNING' || principal === 'PENDING') {
      return 'info';
    }
    return 'neutral';
  }

  markKey(value: boolean | null): string {
    if (value === true) {
      return 'integrations.orcid.discovery.matchYes';
    }
    if (value === false) {
      return 'integrations.orcid.discovery.matchNo';
    }
    return 'common.dash';
  }

  givenFamilyName(firstName: string | null | undefined, lastName: string | null | undefined): string {
    return [firstName, lastName].filter((part) => !!part && part.trim()).join(' ').trim();
  }

  hasReason(item: OrcidCandidateEvidence, reason: string): boolean {
    return (item.reasonCodes ?? []).includes(reason);
  }

  candidatePositionKey(candidate: OrcidCandidate): string {
    if (candidate.evidence.some((item) => this.hasReason(item, 'POSITION_MATCH'))) {
      return 'integrations.orcid.discovery.positionSame';
    }
    if (candidate.evidence.some((item) => this.hasReason(item, 'POSITION_DIFFERENT'))
      || candidate.positionMatch === false) {
      return 'integrations.orcid.discovery.positionDifferent';
    }
    if (candidate.evidence.some((item) => this.hasReason(item, 'POSITION_NOT_COMPARABLE'))
      || (candidate.matchLevel === 'STRONG' && candidate.positionMatch == null)) {
      return 'integrations.orcid.discovery.positionNotComparable';
    }
    return 'common.dash';
  }

  evidencePositionKey(item: OrcidCandidateEvidence): string | null {
    if (this.hasReason(item, 'POSITION_MATCH') || item.positionMatch === true) {
      return 'integrations.orcid.discovery.positionSame';
    }
    if (this.hasReason(item, 'POSITION_DIFFERENT') || item.positionMatch === false) {
      return 'integrations.orcid.discovery.positionDifferent';
    }
    if (this.hasReason(item, 'POSITION_NOT_COMPARABLE') || (item.nameMatch === true && item.positionMatch == null)) {
      return 'integrations.orcid.discovery.positionNotComparable';
    }
    return null;
  }

  candidateLabel(count: number): string {
    return this.i18n.translate(
      count === 1 ? 'integrations.orcid.discovery.candidateCountOne' : 'integrations.orcid.discovery.candidateCount',
      { count }
    );
  }

  canOpenCandidates(person: OrcidDiscoveryPerson): boolean {
    return this.hasStoredCandidates(person) || (this.isTooMany(person) && person.searchHitCount > 0);
  }

  candidatesAriaLabel(person: OrcidDiscoveryPerson): string {
    const count = this.displayedCandidateCount(person);
    const key = count === 1
      ? 'integrations.orcid.discovery.viewCandidateAria'
      : 'integrations.orcid.discovery.viewCandidatesAria';
    return this.i18n.translate(key, { count, name: person.fullName || '' });
  }

  evidenceAriaLabel(person: OrcidDiscoveryPerson): string {
    return this.i18n.translate('integrations.orcid.discovery.viewEvidenceAria', {
      name: person.fullName || ''
    });
  }

  displayedCandidateCount(person: OrcidDiscoveryPerson): number {
    if (this.statusReason(person) === 'TOO_MANY_CANDIDATES' && person.searchHitCount > 0) {
      return person.searchHitCount;
    }
    return person.candidateCount;
  }

  hasStoredCandidates(person: OrcidDiscoveryPerson): boolean {
    return person.candidateCount > 0;
  }

  hasEvidence(person: OrcidDiscoveryPerson): boolean {
    return person.doiMatchCount > 0;
  }

  isTooMany(person: OrcidDiscoveryPerson): boolean {
    return this.statusReason(person) === 'TOO_MANY_CANDIDATES';
  }

  tooManyHint(person: OrcidDiscoveryPerson): string {
    return this.i18n.translate('integrations.orcid.discovery.tooManyHint', {
      found: person.searchHitCount || person.candidateCount,
      limit: this.searchLimit()
    });
  }

  searchLimit(): number {
    return this.stats()?.searchMaxCandidates || 20;
  }

  progressPercent(run: OrcidDiscoveryExecution): number {
    if (!run.totalPeople) {
      return 0;
    }
    return Math.min(100, Math.round((run.processedPeople / run.totalPeople) * 100));
  }

  canToggle(person: OrcidDiscoveryPerson): boolean {
    return canToggleSyncSelection({
      canAdminister: this.canAdminister(),
      queued: this.isSelectedForSync(person),
      selectable: person.selectable,
      personStatus: person.personStatus
    });
  }

  isSelectedForSync(person: OrcidDiscoveryPerson): boolean {
    return isQueuedPerson(this.queuedIds(), person.personId);
  }

  isSelectionPending(personId: number): boolean {
    return this.pendingPersonIds().has(personId);
  }

  checkboxTitle(person: OrcidDiscoveryPerson): string {
    if (this.canToggle(person)) {
      return '';
    }
    const reason = person.selectionDisabledReason;
    const key = reason
      ? `integrations.orcid.discovery.disabled.${reason}`
      : 'integrations.orcid.discovery.disabled.CHOOSE_CANDIDATE';
    return this.i18n.translate(key);
  }

  displayedOrcid(person: OrcidDiscoveryPerson): string | null {
    return person.selectedOrcid || (person.personStatus === 'STRONG' ? person.foundOrcid : null);
  }

  togglePerson(person: OrcidDiscoveryPerson, event?: Event): void {
    const executionId = this.execution()?.id;
    const input = event?.target instanceof HTMLInputElement ? event.target : null;
    const restoreNative = (): void => {
      if (input) {
        input.checked = this.isSelectedForSync(person);
      }
    };
    if (!this.canAdminister() || !executionId || this.running() || this.applying() || this.isSelectionPending(person.personId)) {
      restoreNative();
      return;
    }
    if (this.isSelectedForSync(person)) {
      this.persistQueue(executionId, person, false, null);
      return;
    }
    const candidateId = resolveCandidateForQueue(person);
    if (candidateId == null) {
      restoreNative();
      this.openCandidates(person);
      return;
    }
    this.persistQueue(executionId, person, true, candidateId);
  }

  refreshResults(): void {
    this.newResults.set(false);
    this.tableTick.update((value) => value + 1);
  }

  retryTable(): void {
    this.tableTick.update((value) => value + 1);
  }

  openCandidates(person: OrcidDiscoveryPerson, focusEvidence = false): void {
    const executionId = this.execution()?.id;
    if (!executionId) {
      return;
    }
    this.detailPersonId.set(person.personId);
    this.api
      .discoveryPerson(executionId, person.personId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (detail) => {
          this.detailPerson.set(detail);
          const withEvidence = detail.candidates.find((item) => item.evidence.length > 0 || item.doiMatchCount > 0);
          this.evidenceCandidate.set(focusEvidence ? withEvidence ?? null : null);
        },
        error: () => this.notifyError('integrations.orcid.discovery.loadError')
      });
  }

  closeDetail(): void {
    this.detailPerson.set(null);
    this.evidenceCandidate.set(null);
    this.detailPersonId.set(null);
  }

  selectCandidate(person: OrcidDiscoveryPerson, candidate: OrcidCandidate): void {
    const executionId = this.execution()?.id;
    if (!this.canAdminister() || !executionId || candidate.alreadyUsedPersonId != null || candidate.matchLevel === 'CONFLICT') {
      return;
    }
    this.mutate(
      () => this.api.selectCandidate(executionId, person.personId, candidate.id, this.isSelectedForSync(person)),
      () => this.reloadOpenDetail()
    );
  }

  clearCandidate(person: OrcidDiscoveryPerson): void {
    const executionId = this.execution()?.id;
    if (!this.canAdminister() || !executionId) {
      return;
    }
    this.mutate(() => this.api.selectCandidate(executionId, person.personId, null), () => this.reloadOpenDetail());
  }

  isChosenCandidate(person: OrcidDiscoveryPerson, candidate: OrcidCandidate): boolean {
    return person.selectedCandidateId === candidate.id;
  }

  selectedManually(person: OrcidDiscoveryPerson): boolean {
    return person.selectedCandidateId != null
      && person.personStatus !== 'STRONG'
      && person.selectionOrigin !== 'AUTO_STRONG';
  }

  startSearch(): void {
    if (!this.canAdminister()) {
      return;
    }
    this.confirmSearch.set(false);
    this.busy.set(true);
    this.api
      .startDiscovery()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (run) => {
          this.busy.set(false);
          this.execution.set(run);
          this.newResults.set(false);
          this.lastNotifiedStatus = run.status;
          this.tableTick.update((value) => value + 1);
          this.loadCargos(run.id);
          this.startProgressPoll();
        },
        error: (error: HttpErrorResponse) => {
          this.busy.set(false);
          const code = (error.error?.code as string | undefined) ?? '';
          if (code === 'ORCID_DISCOVERY_RUNNING') {
            this.notifyError('integrations.orcid.discovery.alreadyRunning');
            this.api.latestDiscovery().pipe(catchError(() => of(null))).subscribe((run) => {
              this.execution.set(run);
              if (isDiscoveryRunning(run?.status)) {
                this.startProgressPoll();
              }
            });
            return;
          }
          if (code === 'ORCID_DISCOVERY_TABLES_UNAVAILABLE') {
            this.notifyError('integrations.orcid.discovery.tablesUnavailable');
            return;
          }
          this.notifyError('integrations.orcid.discovery.startError');
        }
      });
  }

  selectStrong(): void {
    const executionId = this.execution()?.id;
    if (!this.canAdminister() || !executionId) {
      return;
    }
    this.mutate(() => this.api.selectStrong(executionId), undefined, false);
  }

  recalculateMatches(): void {
    const executionId = this.execution()?.id;
    if (!this.canAdminister() || !executionId || this.running() || this.busy()) {
      return;
    }
    this.confirmRecalculate.set(false);
    this.busy.set(true);
    this.api
      .recalculateDiscovery(executionId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (run) => {
          this.busy.set(false);
          this.execution.set(run);
          this.tableTick.update((value) => value + 1);
          this.loadInventory();
          this.reloadOpenDetail();
          this.notifications.show(
            'success',
            this.i18n.translate('integrations.orcid.discovery.recalculateDoneTitle'),
            this.i18n.translate('integrations.orcid.discovery.recalculateDoneBody')
          );
        },
        error: (error: HttpErrorResponse) => {
          this.busy.set(false);
          const code = (error.error?.code as string | undefined) ?? '';
          if (code === 'ORCID_DISCOVERY_RUNNING') {
            this.notifyError('integrations.orcid.discovery.alreadyRunning');
            return;
          }
          this.notifyError('integrations.orcid.discovery.recalculateError');
        }
      });
  }

  applySelected(): void {
    const executionId = this.execution()?.id;
    if (!this.canAdminister() || !executionId || this.applying()) {
      return;
    }
    this.applying.set(true);
    this.confirmApply.set(false);
    this.busy.set(true);
    const personIds = applyPersonIds(this.queued());
    this.api
      .applySelected(executionId, personIds)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.busy.set(false);
          this.applying.set(false);
          this.queued.set(pruneQueuedAfterApply(this.queued(), result));
          this.tableTick.update((value) => value + 1);
          this.loadInventory();
          this.reloadOpenDetail();
          this.notifyApplyResult(result);
        },
        error: (error: HttpErrorResponse) => {
          this.busy.set(false);
          this.applying.set(false);
          this.notifyApplyHttpError(error);
        }
      });
  }

  closeApplyResult(): void {
    this.applyResult.set(null);
  }

  retryLocalById(personId: number): void {
    const executionId = this.execution()?.id;
    if (!executionId || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.api
      .reconcileLocal(executionId, personId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (item) => {
          this.busy.set(false);
          this.tableTick.update((value) => value + 1);
          this.loadInventory();
          const status = this.normalizeApplyStatus(item.status ?? '');
          if (status === 'REMOTE_SUCCESS_LOCAL_SYNC_FAILED') {
            this.notifications.show(
              'warning',
              this.i18n.translate('integrations.orcid.discovery.applyLocalPendingTitle'),
              this.i18n.translate('integrations.orcid.discovery.applyLocalPendingBody')
            );
            return;
          }
          this.patchApplyItemStatus(personId, 'UPDATED');
          this.notifications.show(
            'success',
            this.i18n.translate('integrations.orcid.discovery.applyLocalRetryTitle'),
            this.i18n.translate('integrations.orcid.discovery.applyLocalRetryBody')
          );
        },
        error: () => {
          this.busy.set(false);
          this.notifyError('integrations.orcid.discovery.actionError');
        }
      });
  }

  applyItemStatusKey(status: string): string {
    return `integrations.orcid.discovery.applyStatus.${this.normalizeApplyStatus(status)}`;
  }

  applyItemTone(status: string): 'success' | 'warning' | 'review' | 'danger' | 'neutral' {
    const value = this.normalizeApplyStatus(status);
    if (value === 'UPDATED') {
      return 'success';
    }
    if (value === 'REMOTE_SUCCESS_LOCAL_SYNC_FAILED' || value === 'CONFLICT_REMOTE_CHANGED' || value === 'ALREADY_USED' || value === 'PURE_VERIFICATION_FAILED') {
      return 'warning';
    }
    if (value === 'FAILED') {
      return 'danger';
    }
    return 'review';
  }

  applyItemMessage(item: OrcidDiscoveryApplyItem): string {
    return this.i18n.translate(`integrations.orcid.discovery.applyStatusMessage.${this.normalizeApplyStatus(item.status)}`);
  }

  canRetryLocal(item: OrcidDiscoveryApplyItem): boolean {
    return this.normalizeApplyStatus(item.status) === 'REMOTE_SUCCESS_LOCAL_SYNC_FAILED' && item.localParentId != null;
  }

  retryLocal(person: OrcidDiscoveryPerson): void {
    this.retryLocalById(person.personId);
  }

  private notifyApplyResult(result: OrcidDiscoveryApplyResult): void {
    const buckets = classifyApplyItems(result.items);
    const attention = buckets.localFailed.length
      + buckets.remoteChanged.length
      + buckets.alreadyUsed.length
      + buckets.unconfirmed.length
      + buckets.failed.length;
    const detailAction = result.items.length
      ? { label: this.i18n.translate('integrations.orcid.discovery.viewApplyDetail'), run: () => this.applyResult.set(result) }
      : null;
    if (buckets.succeeded.length > 0 && attention === 0) {
      this.notifications.show(
        'success',
        this.i18n.translate('integrations.orcid.discovery.applySuccessTitle'),
        this.applySuccessBody(buckets.succeeded)
      );
      return;
    }
    if (buckets.succeeded.length === 0 && buckets.localFailed.length === result.items.length && result.items.length > 0) {
      const only = buckets.localFailed[0];
      this.notifications.show(
        'warning',
        this.i18n.translate('integrations.orcid.discovery.applyLocalPendingTitle'),
        this.i18n.translate('integrations.orcid.discovery.applyLocalPendingBody'),
        null,
        result.items.length === 1 && only.localParentId != null
          ? {
              label: this.i18n.translate('integrations.orcid.discovery.retryLocal'),
              run: () => this.retryLocalById(only.localParentId!)
            }
          : detailAction
      );
      return;
    }
    if (buckets.succeeded.length === 0 && buckets.unconfirmed.length === result.items.length && result.items.length > 0) {
      this.notifications.show(
        'warning',
        this.i18n.translate('integrations.orcid.discovery.applyUnconfirmedTitle'),
        this.i18n.translate('integrations.orcid.discovery.applyUnconfirmedBody'),
        null,
        detailAction
      );
      return;
    }
    if (buckets.succeeded.length === 0 && buckets.remoteChanged.length === result.items.length && result.items.length > 0) {
      this.notifications.show(
        'warning',
        this.i18n.translate('integrations.orcid.discovery.applyRemoteChangedTitle'),
        this.i18n.translate('integrations.orcid.discovery.applyRemoteChanged'),
        null,
        detailAction
      );
      return;
    }
    if (buckets.succeeded.length === 0 && buckets.alreadyUsed.length === result.items.length && result.items.length > 0) {
      this.notifications.show(
        'warning',
        this.i18n.translate('integrations.orcid.discovery.applyAlreadyUsedTitle'),
        this.i18n.translate('integrations.orcid.discovery.applyAlreadyUsed'),
        null,
        detailAction
      );
      return;
    }
    if (buckets.succeeded.length === 0) {
      this.notifications.show(
        'error',
        this.i18n.translate('integrations.orcid.discovery.applyFailedTitle'),
        this.applyFailureBody(buckets),
        null,
        detailAction
      );
      return;
    }
    this.notifications.show(
      'warning',
      this.i18n.translate('integrations.orcid.discovery.applyPartialTitle'),
      this.applyPartialBody(buckets.succeeded.length, attention),
      null,
      detailAction
    );
  }

  private applySuccessBody(succeeded: OrcidDiscoveryApplyItem[]): string {
    if (succeeded.length === 1) {
      const name = succeeded[0].displayName?.trim();
      return name
        ? this.i18n.translate('integrations.orcid.discovery.applySuccessOneNamed', { name })
        : this.i18n.translate('integrations.orcid.discovery.applySuccessOne');
    }
    return this.i18n.translate('integrations.orcid.discovery.applySuccessMany', { count: succeeded.length });
  }

  private applyPartialBody(succeeded: number, attention: number): string {
    const succeededPhrase = succeeded === 1
      ? this.i18n.translate('integrations.orcid.discovery.applySucceededOne')
      : this.i18n.translate('integrations.orcid.discovery.applySucceededMany', { count: succeeded });
    const attentionPhrase = attention === 1
      ? this.i18n.translate('integrations.orcid.discovery.applyAttentionOne')
      : this.i18n.translate('integrations.orcid.discovery.applyAttentionMany', { count: attention });
    return `${succeededPhrase} · ${attentionPhrase}`;
  }

  private applyFailureBody(buckets: ApplyBuckets): string {
    if (buckets.remoteChanged.length === 1 && buckets.alreadyUsed.length === 0 && buckets.failed.length === 0 && buckets.localFailed.length === 0 && buckets.unconfirmed.length === 0) {
      return this.i18n.translate('integrations.orcid.discovery.applyRemoteChanged');
    }
    if (buckets.alreadyUsed.length === 1 && buckets.remoteChanged.length === 0 && buckets.failed.length === 0 && buckets.localFailed.length === 0 && buckets.unconfirmed.length === 0) {
      return this.i18n.translate('integrations.orcid.discovery.applyAlreadyUsed');
    }
    return this.i18n.translate('integrations.orcid.discovery.applyFailedBody');
  }

  private notifyApplyHttpError(error: HttpErrorResponse): void {
    const code = apiErrorCode(error);
    if (code === 'ORCID_NONE_SELECTED') {
      this.notifyError('integrations.orcid.discovery.applyNoneSelected');
      return;
    }
    if (code === 'PURE_VERIFICATION_FAILED' || error.status === 408 || error.status === 504 || error.status === 500) {
      this.notifications.show(
        'warning',
        this.i18n.translate('integrations.orcid.discovery.applyUnconfirmedTitle'),
        this.i18n.translate('integrations.orcid.discovery.applyUnconfirmedBody')
      );
      return;
    }
    this.notifyError('integrations.orcid.discovery.applyStartError');
  }

  private patchApplyItemStatus(personId: number, status: string): void {
    const current = this.applyResult();
    if (!current) {
      return;
    }
    this.applyResult.set({
      ...current,
      items: current.items.map((item) => item.localParentId === personId ? { ...item, status, message: null } : item)
    });
  }

  private normalizeApplyStatus(status: string): string {
    const value = (status ?? '').trim().toUpperCase();
    if (value === 'SUCCESS' || value === 'SYNCED' || value === 'LOCAL_RECONCILED') {
      return 'UPDATED';
    }
    if (value === 'REMOTE_CHANGED') {
      return 'CONFLICT_REMOTE_CHANGED';
    }
    return value || 'FAILED';
  }

  private startProgressPoll(): void {
    if (this.pollSub && !this.pollSub.closed) {
      return;
    }
    this.pollSub = interval(POLL_MS)
      .pipe(
        switchMap(() => this.api.latestDiscovery().pipe(catchError(() => of(this.execution())))),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((run) => this.applyProgress(run));
  }

  private stopProgressPoll(): void {
    this.pollSub?.unsubscribe();
    this.pollSub = null;
  }

  private applyProgress(run: OrcidDiscoveryExecution | null): void {
    if (!run) {
      return;
    }
    const previous = this.execution();
    this.execution.set(run);
    if (
      run
      && previous
      && isDiscoveryRunning(previous.status)
      && run.processedPeople !== previous.processedPeople
    ) {
      this.newResults.set(true);
    }
    if (isDiscoveryTerminal(run?.status)) {
      this.stopProgressPoll();
      if (isDiscoveryRunning(previous?.status)) {
        this.onJobFinished(run);
      }
    }
  }

  private onJobFinished(run: OrcidDiscoveryExecution | null): void {
    this.newResults.set(false);
    this.tableTick.update((value) => value + 1);
    this.loadInventory();
    this.loadHistory();
    this.loadCargos(run?.id ?? null);
    this.reloadOpenDetail();
    this.notifyIfFinished(run);
  }

  private reloadOpenDetail(): void {
    const executionId = this.execution()?.id;
    const personId = this.detailPersonId();
    if (!executionId || personId == null) {
      return;
    }
    this.api
      .discoveryPerson(executionId, personId)
      .pipe(catchError(() => of(null)), takeUntilDestroyed(this.destroyRef))
      .subscribe((detail) => {
        if (detail && this.detailPersonId() === personId) {
          this.detailPerson.set(detail);
        }
      });
  }

  private loadInventory(): void {
    this.api
      .discoveryStats()
      .pipe(catchError(() => of(null)), takeUntilDestroyed(this.destroyRef))
      .subscribe((stats) => this.stats.set(stats));
    this.loadQueued();
  }

  private loadHistory(): void {
    this.api
      .discoveryHistory()
      .pipe(catchError(() => of([])), takeUntilDestroyed(this.destroyRef))
      .subscribe((history) => this.history.set(history));
  }

  private loadCargos(executionId: number | null): void {
    if (!executionId) {
      this.cargos.set([]);
      return;
    }
    this.api
      .discoveryCargos(executionId)
      .pipe(catchError(() => of([])), takeUntilDestroyed(this.destroyRef))
      .subscribe((cargos) => this.cargos.set(cargos));
  }

  private loadQueued(): void {
    const executionId = this.execution()?.id;
    if (!executionId || this.running()) {
      this.queued.set([]);
      return;
    }
    this.api
      .queuedForApply(executionId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (items) => {
          if (this.pendingPersonIds().size > 0) {
            return;
          }
          this.queued.set(items);
        },
        error: () => this.notifyError(
          'integrations.orcid.discovery.queueLoadError',
          'integrations.orcid.discovery.actionError'
        )
      });
  }

  originLabelKey(item: OrcidDiscoverySelectedItem): string {
    if (item.personStatus === 'STRONG' || item.selectionOrigin === 'AUTO_STRONG') {
      return 'integrations.orcid.discovery.originStrong';
    }
    return 'integrations.orcid.discovery.originManual';
  }

  private persistQueue(
    executionId: number,
    person: OrcidDiscoveryPerson,
    queued: boolean,
    candidateId: number | null
  ): void {
    const snapshot = this.queued();
    this.markPending(person.personId, true);
    this.queued.set(
      queued
        ? withQueuedPerson(snapshot, queuedItemFromPerson(person, candidateId))
        : withoutQueuedPerson(snapshot, person.personId)
    );
    this.api
      .selectCandidate(executionId, person.personId, queued ? candidateId : null, queued)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.markPending(person.personId, false);
          this.loadQueued();
        },
        error: (error: HttpErrorResponse) => {
          this.queued.set(snapshot);
          this.markPending(person.personId, false);
          this.notifySelectionError(error);
        }
      });
  }

  private markPending(personId: number, pending: boolean): void {
    this.pendingPersonIds.update((current) => {
      const next = new Set(current);
      if (pending) {
        next.add(personId);
      } else {
        next.delete(personId);
      }
      return next;
    });
  }

  private mutate<T>(request: () => Observable<T>, after?: () => void, reloadTable = true): void {
    this.busy.set(true);
    request()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.busy.set(false);
          if (reloadTable) {
            this.tableTick.update((value) => value + 1);
          }
          this.loadQueued();
          after?.();
        },
        error: (error: HttpErrorResponse) => {
          this.busy.set(false);
          this.notifySelectionError(error);
        }
      });
  }

  private notifySelectionError(error: HttpErrorResponse): void {
    if (error.status === 403) {
      return;
    }
    const code = apiErrorCode(error) ?? (error.error?.code as string | undefined) ?? '';
    if (code === 'ORCID_ALREADY_USED') {
      this.notifications.show(
        'error',
        this.i18n.translate('integrations.orcid.discovery.actionError'),
        this.i18n.translate('integrations.orcid.discovery.alreadyUsed')
      );
      return;
    }
    this.notifications.show(
      'error',
      this.i18n.translate('integrations.orcid.discovery.actionError'),
      this.i18n.translate('integrations.orcid.discovery.actionErrorHint')
    );
  }

  private notifyIfFinished(run: OrcidDiscoveryExecution | null): void {
    if (!run || isDiscoveryRunning(run.status)) {
      this.lastNotifiedStatus = run?.status ?? null;
      return;
    }
    const key = `${run.id}:${run.status}`;
    if (this.lastNotifiedStatus === key) {
      return;
    }
    this.lastNotifiedStatus = key;
    this.notifications.show(
      run.status === 'FAILED' ? 'error' : run.status === 'COMPLETED_WITH_WARNINGS' ? 'warning' : 'success',
      this.i18n.translate('integrations.orcid.discovery.finishedTitle'),
      this.i18n.translate('integrations.orcid.discovery.finishedBody', {
        processed: String(run.processedPeople),
        total: String(run.totalPeople)
      })
    );
  }

  private notifyError(key: string, titleKey = 'common.errorTitle'): void {
    this.notifications.show('error', this.i18n.translate(titleKey), this.i18n.translate(key));
  }
}

function emptyTableView(): PageViewState<OrcidDiscoveryPerson> {
  return { status: 'loading', data: emptyPage<OrcidDiscoveryPerson>(PAGE_SIZE) };
}

function isDiscoveryRunning(status?: string | null): boolean {
  const value = (status ?? '').trim().toUpperCase();
  return value === 'PENDING' || value === 'RUNNING';
}

function isDiscoveryTerminal(status?: string | null): boolean {
  const value = (status ?? '').trim().toUpperCase();
  return value === 'COMPLETED'
    || value === 'COMPLETED_WITH_WARNINGS'
    || value === 'FAILED'
    || value === 'CANCELLED';
}

interface ApplyBuckets {
  succeeded: OrcidDiscoveryApplyItem[];
  localFailed: OrcidDiscoveryApplyItem[];
  remoteChanged: OrcidDiscoveryApplyItem[];
  alreadyUsed: OrcidDiscoveryApplyItem[];
  unconfirmed: OrcidDiscoveryApplyItem[];
  failed: OrcidDiscoveryApplyItem[];
}

function classifyApplyItems(items: OrcidDiscoveryApplyItem[]): ApplyBuckets {
  const buckets: ApplyBuckets = {
    succeeded: [],
    localFailed: [],
    remoteChanged: [],
    alreadyUsed: [],
    unconfirmed: [],
    failed: []
  };
  for (const item of items) {
    const status = (item.status ?? '').trim().toUpperCase();
    if (status === 'UPDATED' || status === 'SUCCESS' || status === 'SYNCED' || status === 'LOCAL_RECONCILED') {
      buckets.succeeded.push(item);
    } else if (status === 'REMOTE_SUCCESS_LOCAL_SYNC_FAILED') {
      buckets.localFailed.push(item);
    } else if (status === 'CONFLICT_REMOTE_CHANGED' || status === 'REMOTE_CHANGED') {
      buckets.remoteChanged.push(item);
    } else if (status === 'ALREADY_USED') {
      buckets.alreadyUsed.push(item);
    } else if (status === 'PURE_VERIFICATION_FAILED') {
      buckets.unconfirmed.push(item);
    } else {
      buckets.failed.push(item);
    }
  }
  return buckets;
}
