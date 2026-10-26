import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, HostListener, computed, effect, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { ApiPage } from '../../../core/services/api/api.types';
import {
  SyncApiService,
  SyncChangeGroup,
  SyncChangeLine,
  SyncChangeTypeFilter,
  SyncEntity,
  SyncErrorPage,
  SyncRun,
  normalizeSyncEntity,
  syncEntityLabelKey
} from '../../../core/services/api/sync-api.service';
import { SyncMonitorService } from '../../../core/services/sync-monitor.service';
import { I18nService } from '../../../core/i18n/i18n.service';
import { SyncDetailService } from '../../../core/services/sync-detail.service';
import { presentSyncChange, withOrganizationContext } from '../../../core/utils/sync-change-copy';
import { PaginationComponent } from '../pagination/pagination.component';
import { StatusBadgeComponent } from '../status-badge/status-badge.component';
import { I18N_HOST, I18N_IMPORTS } from '../../i18n/i18n.imports';
import { openAlexStaleMessageKey } from '../../../core/sync/openalex-history.util';
import {
  canShowOpenAlexAuthorRecovery,
  canViewOpenAlexAuthorRecovery,
  openAlexInvalidCount,
  openAlexPhaseLabelKey,
  openAlexRecoveryBlockedReason
} from '../../../core/sync/openalex-recovery.util';
import { syncModeLabelKey } from '../../../core/sync/sync-mode.util';
import { isMetricsSyncEntity } from '../../../core/utils/pure-metrics.util';

@Component({
  selector: 'os-sync-detail-dialog',
  hostDirectives: [...I18N_HOST],
  imports: [...I18N_IMPORTS, RouterLink, PaginationComponent, StatusBadgeComponent],
  templateUrl: './sync-detail-dialog.component.html',
  styleUrl: './sync-detail-dialog.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SyncDetailDialogComponent {
  private readonly syncApi = inject(SyncApiService);
  private readonly i18n = inject(I18nService);
  private readonly monitor = inject(SyncMonitorService);
  readonly auth = inject(AuthService);
  readonly detail = inject(SyncDetailService);

  readonly run = signal<SyncRun | null>(null);
  readonly page = signal<ApiPage<SyncChangeGroup> | null>(null);
  readonly loading = signal(false);
  readonly error = signal(false);
  readonly filter = signal<SyncChangeTypeFilter>('ALL');
  readonly currentPage = signal(0);
  readonly showErrors = signal(false);
  readonly errorPage = signal<SyncErrorPage | null>(null);
  readonly errorsLoading = signal(false);
  readonly errorListPage = signal(0);

  readonly open = this.detail.open;
  readonly people = computed(() => this.run()?.entity === 'PEOPLE');
  readonly orcid = computed(() => normalizeSyncEntity(this.run()?.entity) === 'ORCID');
  readonly unpaywall = computed(() => normalizeSyncEntity(this.run()?.entity) === 'UNPAYWALL');
  readonly crossref = computed(() => normalizeSyncEntity(this.run()?.entity) === 'CROSSREF');
  readonly openalex = computed(() => normalizeSyncEntity(this.run()?.entity) === 'OPENALEX');
  readonly metrics = computed(() => isMetricsSyncEntity(this.run()?.entity));
  readonly failed = computed(() => this.run()?.status === 'FAILED');
  readonly recoverConfirmOpen = signal(false);
  readonly recoverBusy = signal(false);
  readonly recoverError = signal<string | null>(null);
  readonly canRecoverAuthors = computed(() =>
    canShowOpenAlexAuthorRecovery(this.run(), this.auth.canAdminister('INTEGRATIONS'))
  );
  readonly canViewAuthorRecovery = computed(() =>
    canViewOpenAlexAuthorRecovery(this.run(), this.auth.canAdminister('INTEGRATIONS'))
  );
  readonly recoveryBlockedReason = computed(() => openAlexRecoveryBlockedReason(this.run()));
  readonly lastValidFullAt = computed(() => {
    const entity = normalizeSyncEntity(this.run()?.entity) as SyncEntity | '';
    if (
      entity !== 'ORCID'
      && entity !== 'UNPAYWALL'
      && entity !== 'CROSSREF'
      && entity !== 'OPENALEX'
      && entity !== 'DSPACE'
      && entity !== 'PERSON_METRICS'
      && entity !== 'RESEARCH_OUTPUT_METRICS'
      && entity !== 'JOURNAL_METRICS'
    ) {
      return null;
    }
    return this.monitor.policy(entity).lastSuccessfulFullSyncAt ?? null;
  });
  readonly hasChanges = computed(() => hasVisibleChanges(this.run()));
  readonly tabs = computed(() => {
    const run = this.run();
    if (!run || isMetricsSyncEntity(run.entity)) {
      return [];
    }
    return [
      { id: 'ALL' as const, labelKey: 'common.all', count: visibleTotal(run) },
      { id: 'CREATED' as const, labelKey: 'sync.detail.badge.created', count: run.created },
      { id: 'UPDATED' as const, labelKey: 'sync.detail.badge.updated', count: run.updated },
      { id: 'REACTIVATED' as const, labelKey: 'sync.detail.badge.reactivated', count: run.reactivated },
      { id: 'INACTIVATED' as const, labelKey: 'sync.detail.badge.inactivated', count: run.markedInactive }
    ].filter((tab) => tab.id === 'ALL' || tab.count > 0);
  });

  constructor() {
    effect(() => {
      const syncId = this.detail.currentSyncId();
      if (!syncId) {
        this.run.set(null);
        this.page.set(null);
        this.filter.set('ALL');
        this.currentPage.set(0);
        this.showErrors.set(false);
        this.errorPage.set(null);
        this.errorListPage.set(0);
        return;
      }
      this.filter.set('ALL');
      this.currentPage.set(0);
      this.showErrors.set(false);
      this.errorPage.set(null);
      this.errorListPage.set(0);
      this.recoverConfirmOpen.set(false);
      this.recoverBusy.set(false);
      this.recoverError.set(null);
      this.load(syncId, 'ALL', 0);
    });
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.open()) {
      this.detail.close();
    }
  }

  selectFilter(filter: SyncChangeTypeFilter): void {
    this.filter.set(filter);
    this.currentPage.set(0);
    const syncId = this.detail.currentSyncId();
    if (syncId) {
      this.load(syncId, filter, 0, this.run());
    }
  }

  goToPage(page: number): void {
    const syncId = this.detail.currentSyncId();
    if (!syncId) {
      return;
    }
    this.currentPage.set(page - 1);
    this.load(syncId, this.filter(), page - 1, this.run());
  }

  entityLabel(): string {
    return this.i18n.translate(syncEntityLabelKey(this.run()?.entity));
  }

  modeLabel(run: SyncRun): string | null {
    const key = syncModeLabelKey(run.syncMode);
    return key ? this.i18n.translate(key) : null;
  }

  phaseLabel(run: SyncRun): string | null {
    const key = openAlexPhaseLabelKey(run.phase);
    return key ? this.i18n.translate(key) : null;
  }

  displayMessage(run: SyncRun): string | null {
    const staleKey = openAlexStaleMessageKey(run.entity, run.errorCode);
    if (staleKey) {
      return this.i18n.translate(staleKey);
    }
    return run.message?.trim() || null;
  }

  invalidCount(run: SyncRun): number {
    return openAlexInvalidCount(run);
  }

  openRecoverConfirm(): void {
    if (!this.canRecoverAuthors()) {
      return;
    }
    this.recoverError.set(null);
    this.recoverConfirmOpen.set(true);
  }

  cancelRecover(): void {
    this.recoverConfirmOpen.set(false);
  }

  confirmRecover(): void {
    const source = this.run();
    if (!source || this.recoverBusy()) {
      return;
    }
    this.recoverBusy.set(true);
    this.recoverError.set(null);
    this.syncApi.recoverOpenAlexAuthors(source.syncId).subscribe({
      next: (result) => {
        this.recoverBusy.set(false);
        this.recoverConfirmOpen.set(false);
        this.syncApi.get(result.recoveryExecutionId).subscribe({
          next: (run) => {
            this.monitor.watch(run);
            this.detail.show(run.syncId);
          },
          error: () => {
            this.detail.show(result.recoveryExecutionId);
          }
        });
      },
      error: (error: HttpErrorResponse) => {
        this.recoverBusy.set(false);
        const code = error.error?.code;
        if (error.status === 409 && code === 'SYNC_ALREADY_RUNNING') {
          this.recoverError.set('sync.detail.openalexRecoverBlocked');
          return;
        }
        this.recoverError.set(error.error?.message || error.message || 'sync.detail.loadError');
      }
    });
  }

  badgeKey(type?: string | null): string {
    switch (type) {
      case 'CREATED':
        return 'sync.detail.badge.created';
      case 'REACTIVATED':
        return 'sync.detail.badge.reactivated';
      case 'INACTIVATED':
        return 'sync.detail.badge.inactivated';
      default:
        return 'sync.detail.badge.updated';
    }
  }

  badgeTone(type?: string | null): 'success' | 'info' | 'review' | 'muted' {
    switch (type) {
      case 'CREATED':
        return 'success';
      case 'REACTIVATED':
        return 'info';
      case 'INACTIVATED':
        return 'muted';
      default:
        return 'review';
    }
  }

  introKey(type?: string | null): string | null {
    switch (type) {
      case 'CREATED':
        return 'sync.detail.createdBody';
      case 'REACTIVATED':
        return 'sync.detail.reactivatedBody';
      case 'INACTIVATED':
        return 'sync.detail.inactivatedBody';
      default:
        return null;
    }
  }

  lines(group: SyncChangeGroup) {
    return withOrganizationContext(
      (group.changes ?? [])
        .map((change: SyncChangeLine) => presentSyncChange(change, this.i18n))
        .filter((item): item is NonNullable<typeof item> => item != null),
      this.i18n
    );
  }

  toggleErrors(): void {
    const next = !this.showErrors();
    this.showErrors.set(next);
    if (next && this.detail.currentSyncId()) {
      this.loadErrors(this.detail.currentSyncId()!, 0);
    }
  }

  goToErrorPage(page: number): void {
    const syncId = this.detail.currentSyncId();
    if (!syncId) {
      return;
    }
    this.errorListPage.set(page - 1);
    this.loadErrors(syncId, page - 1);
  }

  stageLabel(stage?: string | null): string {
    if (!stage) {
      return this.i18n.translate('common.dash');
    }
    const key = 'sync.detail.stage.' + stage;
    const translated = this.i18n.translate(key);
    return translated === key ? stage : translated;
  }

  fetchedCount(run: SyncRun): number {
    return run.fetched ?? run.processed + run.errors;
  }

  formatCount(value: number | null | undefined): string {
    return new Intl.NumberFormat(this.i18n.locale()).format(value ?? 0);
  }

  workCount(run: SyncRun): number {
    return run.created + run.updated + run.unchanged + run.reactivated;
  }

  durationLabel(run: SyncRun): string | null {
    if (!run.startedAt || !run.finishedAt) {
      return null;
    }
    const start = Date.parse(run.startedAt);
    const end = Date.parse(run.finishedAt);
    if (Number.isNaN(start) || Number.isNaN(end) || end < start) {
      return null;
    }
    const totalSeconds = Math.round((end - start) / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    if (minutes <= 0) {
      return this.i18n.translate('sync.detail.durationSeconds', { seconds: this.formatCount(seconds) });
    }
    return this.i18n.translate('sync.detail.durationMinutes', {
      minutes: this.formatCount(minutes),
      seconds: this.formatCount(seconds)
    });
  }

  orcidWorks(run: SyncRun): number {
    return run.created + run.updated + run.unchanged + run.reactivated;
  }

  duration(run: SyncRun): string | null {
    if (!run.startedAt || !run.finishedAt) {
      return null;
    }
    const start = Date.parse(run.startedAt);
    const end = Date.parse(run.finishedAt);
    if (Number.isNaN(start) || Number.isNaN(end) || end < start) {
      return null;
    }
    const totalSeconds = Math.round((end - start) / 1000);
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;
    if (hours > 0) {
      return this.i18n.translate('sync.detail.durationHms', { hours, minutes, seconds });
    }
    if (minutes > 0) {
      return this.i18n.translate('sync.detail.durationMs', { minutes, seconds });
    }
    return this.i18n.translate('sync.detail.durationS', { seconds });
  }

  private load(syncId: string, filter: SyncChangeTypeFilter, page: number, currentRun?: SyncRun | null): void {
    this.loading.set(true);
    this.error.set(false);
    if (currentRun) {
      this.syncApi.changes(syncId, filter, page, 25).subscribe({
        next: (result) => {
          this.page.set(result);
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          this.error.set(true);
        }
      });
      return;
    }
    this.syncApi.get(syncId).subscribe({
      next: (run) => {
        this.run.set(run);
        this.syncApi.changes(syncId, filter, page, 25).subscribe({
          next: (result) => {
            this.page.set(result);
            this.loading.set(false);
          },
          error: () => {
            this.loading.set(false);
            this.error.set(true);
          }
        });
      },
      error: (_error: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(true);
      }
    });
  }

  private loadErrors(syncId: string, page: number): void {
    this.errorsLoading.set(true);
    this.syncApi.errors(syncId, page, 25).subscribe({
      next: (result) => {
        this.errorPage.set(result);
        this.errorsLoading.set(false);
      },
      error: () => {
        this.errorsLoading.set(false);
        this.errorPage.set({ items: [], groups: [], page, size: 25, totalElements: 0 });
      }
    });
  }
}

function hasVisibleChanges(run: SyncRun | null): boolean {
  if (!run || isMetricsSyncEntity(run.entity)) {
    return false;
  }
  return run.created > 0 || run.updated > 0 || run.reactivated > 0 || run.markedInactive > 0;
}

function visibleTotal(run: SyncRun): number {
  return run.created + run.updated + run.reactivated + run.markedInactive;
}
