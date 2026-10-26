import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  HostListener,
  OnInit,
  computed,
  effect,
  inject,
  input,
  output,
  signal
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AppModuleCode, CrossrefQuery, JournalQuery, OpenAlexMatchQuery, OrganizationQuery, PersonQuery, ProjectQuery, ResearchOutputQuery, UnpaywallQuery } from '../../../core/models/openscience.models';
import { JournalApiService } from '../../../core/services/api/journal-api.service';
import { ProjectApiService } from '../../../core/services/api/project-api.service';
import { OrganizationApiService } from '../../../core/services/api/organization-api.service';
import { PersonApiService } from '../../../core/services/api/person-api.service';
import { ResearchOutputApiService } from '../../../core/services/api/research-output-api.service';
import { UnpaywallApiService } from '../../../core/services/api/unpaywall-api.service';
import { CrossrefApiService } from '../../../core/services/api/crossref-api.service';
import { OpenAlexApiService } from '../../../core/services/api/openalex-api.service';
import { SyncApiService, SyncConflictError, SyncEntity, SyncRun, sameSyncEntity } from '../../../core/services/api/sync-api.service';
import {
  canOfferSyncCancel,
  canStartSync,
  conservativeSyncPolicy,
  isSyncControlDisabled,
  mapSyncModePolicy,
  menuSyncModes,
  openAlexFullConfirmBodyKey,
  metricsMenuHelpKey,
  metricsMenuTitleKey,
  primarySyncActionKey,
  primarySyncMode,
  shouldConfirmSyncMode,
  showsSyncModeMenu,
  syncModeLabelKey,
  syncModeMenuHelpKey,
  syncModeMenuTitleKey,
  type SyncMode,
  type SyncModePolicy
} from '../../../core/sync/sync-mode.util';
import { isMetricsSyncEntity } from '../../../core/utils/pure-metrics.util';
import { I18nService } from '../../../core/i18n/i18n.service';
import { AuthService } from '../../../core/services/auth.service';
import { SyncMonitorService } from '../../../core/services/sync-monitor.service';
import { downloadBlob, filenameFromDisposition } from '../../../core/utils/download';
import { IconComponent } from '../icon/icon.component';
import { I18N_HOST, I18N_IMPORTS } from '../../i18n/i18n.imports';

@Component({
  selector: 'os-sync-export-bar',
  hostDirectives: [...I18N_HOST],
  imports: [...I18N_IMPORTS, IconComponent],
  templateUrl: './sync-export-bar.component.html',
  styleUrl: './sync-export-bar.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SyncExportBarComponent implements OnInit {
  private readonly peopleApi = inject(PersonApiService);
  private readonly outputsApi = inject(ResearchOutputApiService);
  private readonly journalsApi = inject(JournalApiService);
  private readonly projectsApi = inject(ProjectApiService);
  private readonly organizationsApi = inject(OrganizationApiService);
  private readonly unpaywallApi = inject(UnpaywallApiService);
  private readonly crossrefApi = inject(CrossrefApiService);
  private readonly openAlexApi = inject(OpenAlexApiService);
  private readonly syncApi = inject(SyncApiService);
  private readonly monitor = inject(SyncMonitorService);
  private readonly i18n = inject(I18nService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly auth = inject(AuthService);
  private lastEmittedId: string | null = null;

  readonly entity = input.required<SyncEntity>();
  readonly metricsEntity = input<SyncEntity | null>(null);
  readonly peopleQuery = input<PersonQuery>({});
  readonly outputQuery = input<ResearchOutputQuery>({});
  readonly journalQuery = input<JournalQuery>({});
  readonly projectQuery = input<ProjectQuery>({});
  readonly organizationQuery = input<OrganizationQuery>({});
  readonly unpaywallQuery = input<UnpaywallQuery>({});
  readonly crossrefQuery = input<CrossrefQuery>({});
  readonly openalexQuery = input<OpenAlexMatchQuery>({});
  readonly syncEnabled = input(true);
  readonly exportEnabled = input(true);
  readonly confirmEstimate = input<number | null>(null);
  readonly policyHint = input<{
    supportedSyncModes?: string[] | null;
    availableSyncModes?: string[] | null;
    hasValidBaseline?: boolean | null;
    recommendedSyncMode?: string | null;
    lastSuccessfulFullSyncAt?: string | null;
    lastFullSyncAt?: string | null;
  } | null>(null);
  readonly completed = output<SyncRun>();

  readonly confirmOpen = signal(false);
  readonly cancelConfirmOpen = signal(false);
  readonly confirmScope = signal<'entity' | 'metrics'>('entity');
  readonly cancelScope = signal<'entity' | 'metrics'>('entity');
  readonly menuOpen = signal(false);
  readonly metricsMenuOpen = signal(false);
  readonly pendingMode = signal<SyncMode>('FULL');
  readonly exportBusy = signal(false);
  readonly startBusy = signal(false);
  readonly metricsStartBusy = signal(false);
  readonly cancelBusy = signal(false);
  readonly message = signal<string | null>(null);
  readonly messageKind = signal<'success' | 'error' | 'info'>('info');

  readonly currentSync = computed(() => this.monitor.active(this.entity()));
  readonly lastSync = computed(() => this.monitor.latest(this.entity()));
  readonly running = computed(() => this.monitor.isRunning(this.entity()));
  readonly canSync = computed(() => canStartSync(this.auth.canAdminister(this.moduleOfEntity()), this.syncEnabled()));
  readonly canExport = computed(() => this.exportEnabled() && this.auth.canExport(this.moduleOfEntity()));
  readonly policy = computed(() => {
    const entity = this.entity();
    if (this.monitor.hasLoadedPolicy(entity)) {
      return this.monitor.policy(entity);
    }
    const hint = this.policyHint();
    if (hint && (hint.recommendedSyncMode != null || hint.hasValidBaseline != null)) {
      return mapSyncModePolicy(hint, entity);
    }
    return conservativeSyncPolicy(entity);
  });
  readonly dualMode = computed(() => showsSyncModeMenu(this.policy()));
  readonly primaryMode = computed(() => primarySyncMode(this.policy()));
  readonly menuModes = computed(() => menuSyncModes(this.policy()));
  readonly controlDisabled = computed(() =>
    isSyncControlDisabled(this.running(), this.canSync(), this.startBusy())
  );
  readonly canCancel = computed(() =>
    canOfferSyncCancel(this.canSync(), this.currentSync()?.status)
  );
  readonly cancelling = computed(() => this.currentSync()?.status === 'CANCEL_REQUESTED');
  readonly metricsActive = computed(() => this.metricsEntity() != null);
  readonly currentMetricsSync = computed(() => {
    const entity = this.metricsEntity();
    return entity ? this.monitor.active(entity) : null;
  });
  readonly lastMetricsSync = computed(() => {
    const entity = this.metricsEntity();
    return entity ? this.monitor.latest(entity) : null;
  });
  readonly metricsRunning = computed(() => {
    const entity = this.metricsEntity();
    return entity ? this.monitor.isRunning(entity) : false;
  });
  readonly metricsPolicy = computed(() => {
    const entity = this.metricsEntity();
    if (!entity) {
      return conservativeSyncPolicy('PERSON_METRICS');
    }
    if (this.monitor.hasLoadedPolicy(entity)) {
      return this.monitor.policy(entity);
    }
    return conservativeSyncPolicy(entity);
  });
  readonly metricsDualMode = computed(() => showsSyncModeMenu(this.metricsPolicy()));
  readonly metricsPrimaryMode = computed(() => primarySyncMode(this.metricsPolicy()));
  readonly metricsMenuModes = computed(() => menuSyncModes(this.metricsPolicy()));
  readonly metricsControlDisabled = computed(() =>
    isSyncControlDisabled(this.metricsRunning(), this.canSync(), this.metricsStartBusy())
  );
  readonly metricsCanCancel = computed(() =>
    canOfferSyncCancel(this.canSync(), this.currentMetricsSync()?.status)
  );
  readonly metricsCancelling = computed(() => this.currentMetricsSync()?.status === 'CANCEL_REQUESTED');

  private moduleOfEntity(): AppModuleCode {
    const entity = this.entity();
    if (entity === 'PEOPLE' || entity === 'PERSON_METRICS') {
      return 'PEOPLE';
    }
    if (entity === 'JOURNALS' || entity === 'JOURNAL_METRICS') {
      return 'JOURNALS';
    }
    if (entity === 'RESEARCH_OUTPUT_METRICS') {
      return 'RESEARCH_OUTPUTS';
    }
    if (entity === 'PROJECTS') {
      return 'PROJECTS';
    }
    if (entity === 'ORGANIZATIONS') {
      return 'ORGANIZATIONS';
    }
    if (entity === 'ORCID' || entity === 'UNPAYWALL' || entity === 'CROSSREF' || entity === 'OPENALEX' || entity === 'DSPACE') {
      return 'INTEGRATIONS';
    }
    return 'RESEARCH_OUTPUTS';
  }

  readonly cancelTarget = computed(() =>
    this.cancelScope() === 'metrics' ? this.currentMetricsSync() : this.currentSync()
  );
  readonly confirmBusy = computed(() => this.startBusy() || this.metricsStartBusy());

  constructor() {
    effect(() => {
      const run = this.monitor.lastCompletion();
      if (!run) {
        return;
      }
      const metricsEntity = this.metricsEntity();
      const matches =
        sameSyncEntity(run.entity, this.entity())
        || (metricsEntity != null && sameSyncEntity(run.entity, metricsEntity));
      if (!matches || run.syncId === this.lastEmittedId) {
        return;
      }
      if (
        run.status === 'COMPLETED'
        || run.status === 'COMPLETED_WITH_WARNINGS'
        || run.status === 'FAILED'
        || run.status === 'CANCELLED'
      ) {
        this.lastEmittedId = run.syncId;
        this.completed.emit(run);
      }
    });
  }

  ngOnInit(): void {
    this.monitor.refreshLatest(this.entity());
    const metricsEntity = this.metricsEntity();
    if (metricsEntity) {
      this.monitor.refreshLatest(metricsEntity);
    }
    this.lastEmittedId = this.monitor.lastCompletion()?.syncId
      ?? this.monitor.latest(this.entity())?.syncId
      ?? (metricsEntity ? this.monitor.latest(metricsEntity)?.syncId : null)
      ?? null;
  }

  openConfirm(): void {
    this.requestSync(this.primaryMode());
  }

  openMetricsConfirm(): void {
    this.requestMetricsSync(this.metricsPrimaryMode());
  }

  requestSync(mode: SyncMode): void {
    this.message.set(null);
    this.menuOpen.set(false);
    this.metricsMenuOpen.set(false);
    if (this.running() || !this.canSync()) {
      return;
    }
    if (!this.policy().supportedSyncModes.includes(mode)) {
      return;
    }
    this.confirmScope.set('entity');
    this.pendingMode.set(mode);
    if (shouldConfirmSyncMode(mode, this.policy())) {
      this.confirmOpen.set(true);
      return;
    }
    this.startSync();
  }

  requestMetricsSync(mode: SyncMode): void {
    this.message.set(null);
    this.menuOpen.set(false);
    this.metricsMenuOpen.set(false);
    if (!this.metricsEntity() || this.metricsRunning() || !this.canSync()) {
      return;
    }
    if (!this.metricsPolicy().supportedSyncModes.includes(mode)) {
      return;
    }
    this.confirmScope.set('metrics');
    this.pendingMode.set(mode);
    if (shouldConfirmSyncMode(mode, this.metricsPolicy())) {
      this.confirmOpen.set(true);
      return;
    }
    this.startSync();
  }

  openCancelConfirm(): void {
    this.openCancelConfirmFor('entity');
  }

  openCancelConfirmFor(scope: 'entity' | 'metrics'): void {
    const canCancel = scope === 'metrics' ? this.metricsCanCancel() : this.canCancel();
    if (!canCancel || this.cancelBusy()) {
      return;
    }
    this.cancelScope.set(scope);
    this.cancelConfirmOpen.set(true);
  }

  closeCancelConfirm(): void {
    if (this.cancelBusy()) {
      return;
    }
    this.cancelConfirmOpen.set(false);
  }

  toggleMenu(event: Event): void {
    event.stopPropagation();
    if (this.running() || !this.canSync()) {
      return;
    }
    this.metricsMenuOpen.set(false);
    this.menuOpen.update((open) => !open);
  }

  toggleMetricsMenu(event: Event): void {
    event.stopPropagation();
    if (this.metricsRunning() || !this.canSync()) {
      return;
    }
    this.menuOpen.set(false);
    this.metricsMenuOpen.update((open) => !open);
  }

  closeMenu(): void {
    this.menuOpen.set(false);
    this.metricsMenuOpen.set(false);
  }

  cancelConfirm(): void {
    this.confirmOpen.set(false);
  }

  startSync(): void {
    const scope = this.confirmScope();
    const target = scope === 'metrics' ? this.metricsEntity() : this.entity();
    const running = scope === 'metrics' ? this.metricsRunning() : this.running();
    const busy = scope === 'metrics' ? this.metricsStartBusy() : this.startBusy();
    if (!target || busy || running || !this.canSync()) {
      return;
    }
    this.confirmOpen.set(false);
    if (scope === 'metrics') {
      this.metricsStartBusy.set(true);
    } else {
      this.startBusy.set(true);
    }
    const mode = this.pendingMode();
    const request = this.startRequestFor(target, mode);
    request.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (run) => {
        this.startBusy.set(false);
        this.metricsStartBusy.set(false);
        this.monitor.watch(run);
        this.message.set(null);
      },
      error: (error: HttpErrorResponse) => {
        this.startBusy.set(false);
        this.metricsStartBusy.set(false);
        const code = this.errorCode(error);
        if (error.status === 409 || code === 'SYNC_ALREADY_RUNNING') {
          this.monitor.adoptAlreadyRunning(target, this.conflictPayload(error));
          this.setMessage('info', this.alreadyRunningKey(target));
          return;
        }
        if (code === 'PURE_NOT_CONFIGURED') {
          this.setMessage('error', 'sync.pureNotConfigured');
          return;
        }
        if (code === 'ORCID_NOT_CONFIGURED') {
          this.setMessage('error', 'sync.orcidNotConfigured');
          return;
        }
        if (code === 'ORCID_TABLES_UNAVAILABLE') {
          this.setMessage('error', 'sync.orcidTablesUnavailable');
          return;
        }
        if (code === 'UNPAYWALL_NOT_CONFIGURED') {
          this.setMessage('error', 'sync.unpaywallNotConfigured');
          return;
        }
        if (code === 'UNPAYWALL_TABLES_UNAVAILABLE') {
          this.setMessage('error', 'sync.unpaywallTablesUnavailable');
          return;
        }
        if (code === 'CROSSREF_DISABLED') {
          this.setMessage('error', 'sync.crossrefDisabled');
          return;
        }
        if (code === 'CROSSREF_NOT_CONFIGURED') {
          this.setMessage('error', 'sync.crossrefNotConfigured');
          return;
        }
        if (code === 'CROSSREF_TABLES_UNAVAILABLE') {
          this.setMessage('error', 'sync.crossrefTablesUnavailable');
          return;
        }
        if (code === 'OPENALEX_DISABLED') {
          this.setMessage('error', 'sync.openalexDisabled');
          return;
        }
        if (code === 'OPENALEX_NOT_CONFIGURED') {
          this.setMessage('error', 'sync.openalexNotConfigured');
          return;
        }
        if (code === 'OPENALEX_TABLES_UNAVAILABLE') {
          this.setMessage('error', 'sync.openalexTablesUnavailable');
          return;
        }
        if (code === 'DSPACE_DISABLED') {
          this.setMessage('error', 'sync.dspaceDisabled');
          return;
        }
        if (code === 'DSPACE_NOT_CONFIGURED') {
          this.setMessage('error', 'sync.dspaceNotConfigured');
          return;
        }
        if (code === 'DSPACE_TABLES_UNAVAILABLE') {
          this.setMessage('error', 'sync.dspaceTablesUnavailable');
          return;
        }
        if (code === 'SYNC_MODE_NOT_SUPPORTED') {
          this.setMessage('error', 'sync.modeUnsupported');
          return;
        }
        if (code === 'SYNC_BASELINE_REQUIRED') {
          this.setMessage('error', 'sync.baselineRequired');
          return;
        }
        if (code === 'INVALID_SYNC_MODE') {
          this.setMessage('error', 'sync.modeUnsupported');
          return;
        }
        if (code === 'SYNC_LOCK_UNAVAILABLE') {
          this.setMessage('error', 'sync.lockUnavailable');
          return;
        }
        this.setMessage('error', 'sync.failed');
      }
    });
  }

  exportExcel(): void {
    if (this.exportBusy() || !this.canExport()) {
      return;
    }
    this.exportBusy.set(true);
    this.message.set(null);
    const request =
      this.entity() === 'PEOPLE'
        ? this.peopleApi.export(this.peopleQuery())
        : this.entity() === 'JOURNALS'
          ? this.journalsApi.export(this.journalQuery())
          : this.entity() === 'PROJECTS'
            ? this.projectsApi.export(this.projectQuery())
          : this.entity() === 'ORGANIZATIONS'
            ? this.organizationsApi.export(this.organizationQuery())
          : this.entity() === 'UNPAYWALL'
            ? this.unpaywallApi.export(this.unpaywallQuery())
            : this.entity() === 'CROSSREF'
              ? this.crossrefApi.export(this.crossrefQuery())
              : this.entity() === 'OPENALEX'
                ? this.openAlexApi.export(this.openalexQuery())
                : this.outputsApi.export(this.outputQuery());
    request.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (response) => this.saveExport(response),
      error: (error: HttpErrorResponse) => {
        this.exportBusy.set(false);
        if (error.status === 422 || this.errorCode(error) === 'EXPORT_EMPTY') {
          this.setMessage('info', 'sync.exportEmpty');
          return;
        }
        this.setMessage('error', 'sync.exportFailed');
      }
    });
  }

  confirmCancel(): void {
    const run = this.cancelTarget();
    const canCancel = this.cancelScope() === 'metrics' ? this.metricsCanCancel() : this.canCancel();
    if (!run || this.cancelBusy() || !canCancel) {
      return;
    }
    this.cancelBusy.set(true);
    this.syncApi.cancel(run.syncId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (updated) => {
        this.cancelBusy.set(false);
        this.cancelConfirmOpen.set(false);
        this.monitor.watch(updated);
        this.setMessage('info', 'sync.cancelRequested');
      },
      error: (error: HttpErrorResponse) => {
        this.cancelBusy.set(false);
        const code = this.errorCode(error);
        if (error.status === 409 || code === 'SYNC_NOT_RUNNING') {
          this.cancelConfirmOpen.set(false);
          this.setMessage('info', 'sync.cancelNotRunning');
          this.monitor.refreshRunning();
          return;
        }
        this.setMessage('error', 'sync.cancelFailed');
      }
    });
  }

  confirmTitleKey(): string {
    const entity = this.confirmTargetEntity();
    if (this.pendingMode() === 'INCREMENTAL' && !isMetricsSyncEntity(entity)) {
      return entity === 'OPENALEX'
        ? 'sync.confirmOpenAlexIncrementalTitle'
        : 'sync.confirmIncrementalTitle';
    }
    if (entity === 'PERSON_METRICS') {
      return 'sync.confirmPersonMetricsTitle';
    }
    if (entity === 'JOURNAL_METRICS') {
      return 'sync.confirmJournalMetricsTitle';
    }
    if (entity === 'RESEARCH_OUTPUT_METRICS') {
      return 'sync.confirmOutputMetricsTitle';
    }
    if (this.pendingMode() === 'FULL' && this.policy().supportedSyncModes.includes('INCREMENTAL')) {
      return 'sync.confirmFullTitle';
    }
    if (this.entity() === 'PEOPLE') {
      return 'sync.confirmPeopleTitle';
    }
    if (this.entity() === 'JOURNALS') {
      return 'sync.confirmJournalsTitle';
    }
    if (this.entity() === 'PROJECTS') {
      return 'sync.confirmProjectsTitle';
    }
    if (this.entity() === 'ORGANIZATIONS') {
      return 'sync.confirmOrganizationsTitle';
    }
    return this.entity() === 'ORCID'
      ? 'sync.confirmOrcidTitle'
      : this.entity() === 'UNPAYWALL'
        ? 'sync.confirmUnpaywallTitle'
        : this.entity() === 'CROSSREF'
          ? 'sync.confirmCrossrefTitle'
          : this.entity() === 'OPENALEX'
            ? 'sync.confirmOpenAlexTitle'
            : this.entity() === 'DSPACE'
              ? 'sync.confirmDspaceTitle'
            : 'sync.confirmOutputsTitle';
  }

  confirmBodyKey(): string {
    const entity = this.confirmTargetEntity();
    if (this.pendingMode() === 'INCREMENTAL' && !isMetricsSyncEntity(entity)) {
      return entity === 'OPENALEX'
        ? 'sync.confirmOpenAlexIncrementalBody'
        : 'sync.confirmIncrementalBody';
    }
    if (entity === 'PERSON_METRICS') {
      return 'sync.confirmPersonMetricsBody';
    }
    if (entity === 'JOURNAL_METRICS') {
      return 'sync.confirmJournalMetricsBody';
    }
    if (entity === 'RESEARCH_OUTPUT_METRICS') {
      return 'sync.confirmOutputMetricsBody';
    }
    if (this.pendingMode() === 'FULL' && entity === 'OPENALEX') {
      return openAlexFullConfirmBodyKey(this.confirmEstimate());
    }
    if (this.pendingMode() === 'FULL' && this.policy().supportedSyncModes.includes('INCREMENTAL')) {
      return 'sync.confirmFullBody';
    }
    if (this.entity() === 'PEOPLE') {
      return 'sync.confirmPeopleBody';
    }
    if (this.entity() === 'JOURNALS') {
      return 'sync.confirmJournalsBody';
    }
    if (this.entity() === 'PROJECTS') {
      return 'sync.confirmProjectsBody';
    }
    if (this.entity() === 'ORGANIZATIONS') {
      return 'sync.confirmOrganizationsBody';
    }
    return this.entity() === 'ORCID'
      ? 'sync.confirmOrcidBody'
      : this.entity() === 'UNPAYWALL'
        ? 'sync.confirmUnpaywallBody'
        : this.entity() === 'CROSSREF'
          ? 'sync.confirmCrossrefBody'
          : this.entity() === 'OPENALEX'
            ? 'sync.confirmOpenAlexBody'
            : this.entity() === 'DSPACE'
              ? 'sync.confirmDspaceBody'
            : 'sync.confirmOutputsBody';
  }

  confirmActionKey(): string {
    return this.pendingMode() === 'FULL' ? 'sync.confirmFullAction' : 'sync.startAction';
  }

  confirmBodyParams(): Record<string, string | number> {
    const count = this.confirmEstimate();
    if (this.pendingMode() === 'FULL' && this.entity() === 'OPENALEX' && count != null && count > 0) {
      return { count: this.formatCount(count) };
    }
    return {};
  }

  primaryActionKey(): string {
    return primarySyncActionKey(this.policy(), this.running());
  }

  primaryHelpKey(): string {
    return this.dualMode() && this.primaryMode() === 'INCREMENTAL'
      ? 'sync.mode.incrementalHelp'
      : this.dualMode()
        ? 'sync.mode.fullHelp'
        : this.fullOnlyTitleKey();
  }

  metricsPrimaryActionKey(): string {
    return primarySyncActionKey(this.metricsPolicy(), this.metricsRunning());
  }

  metricsPrimaryHelpKey(): string {
    return this.metricsPrimaryMode() === 'INCREMENTAL'
      ? 'sync.metrics.incrementalHelp'
      : 'sync.metrics.fullHelp';
  }

  menuTitleKey(mode: SyncMode): string {
    return syncModeMenuTitleKey(mode);
  }

  menuHelpKey(mode: SyncMode): string {
    return syncModeMenuHelpKey(mode);
  }

  metricsModeTitleKey(mode: SyncMode): string {
    return metricsMenuTitleKey(mode);
  }

  metricsModeHelpKey(mode: SyncMode): string {
    return metricsMenuHelpKey(mode);
  }

  fullOnlyTitleKey(): string {
    return 'sync.fullOnlyHint';
  }

  lastModeKey(run: SyncRun): string | null {
    return syncModeLabelKey(run.syncMode);
  }

  @HostListener('document:click')
  onDocumentClick(): void {
    this.closeMenu();
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.closeMenu();
    if (this.cancelConfirmOpen()) {
      this.closeCancelConfirm();
      return;
    }
    if (this.confirmOpen()) {
      this.cancelConfirm();
    }
  }

  hasVisibleChanges(run: SyncRun): boolean {
    return this.monitor.hasVisibleChanges(run);
  }

  lastSyncSummary(run: SyncRun, entity: SyncEntity = this.entity()): string {
    if (entity === 'ORCID') {
      return this.i18n.translate('sync.orcidLastSummary', {
        processed: this.formatCount(run.processed),
        errors: this.formatCount(run.errors)
      });
    }
    if (entity === 'UNPAYWALL') {
      return this.i18n.translate('sync.toast.unpaywallSummary', {
        processed: this.formatCount(run.processed),
        created: this.formatCount(run.created),
        updated: this.formatCount(run.updated),
        unchanged: this.formatCount(run.unchanged),
        notFound: this.formatCount(run.notFound ?? 0),
        errors: this.formatCount(run.errors)
      });
    }
    if (entity === 'CROSSREF') {
      return this.i18n.translate('sync.toast.crossrefSummary', {
        processed: this.formatCount(run.processed),
        created: this.formatCount(run.created),
        updated: this.formatCount(run.updated),
        unchanged: this.formatCount(run.unchanged),
        found: this.formatCount(run.oa ?? 0),
        otherAgency: this.formatCount(run.nonOa ?? 0),
        errors: this.formatCount(run.errors)
      });
    }
    if (entity === 'OPENALEX') {
      return this.i18n.translate('sync.toast.openalexSummary', {
        processed: this.formatCount(run.processed),
        created: this.formatCount(run.created),
        updated: this.formatCount(run.updated),
        unchanged: this.formatCount(run.unchanged),
        found: this.formatCount(run.oa ?? 0),
        errors: this.formatCount(run.errors)
      });
    }
    if (isMetricsSyncEntity(entity)) {
      return this.i18n.translate(this.metricsSummaryKey(entity), {
        processed: this.formatCount(run.processed),
        found: this.formatCount(run.created),
        empty: this.formatCount(run.unchanged),
        errors: this.formatCount(run.errors)
      });
    }
    if (entity === 'DSPACE') {
      return this.i18n.translate('sync.toast.dspaceSummary', {
        processed: this.formatCount(run.processed),
        created: this.formatCount(run.created),
        updated: this.formatCount(run.updated),
        unchanged: this.formatCount(run.unchanged),
        found: this.formatCount(run.oa ?? 0),
        errors: this.formatCount(run.errors)
      });
    }
    const parts: string[] = [];
    if (run.created > 0) {
      parts.push(this.i18n.translate('sync.lastSummary.created', { count: this.formatCount(run.created) }));
    }
    if (run.updated > 0) {
      parts.push(this.i18n.translate('sync.lastSummary.updated', { count: this.formatCount(run.updated) }));
    }
    if (run.reactivated > 0) {
      parts.push(this.i18n.translate('sync.lastSummary.reactivated', { count: this.formatCount(run.reactivated) }));
    }
    if (run.markedInactive > 0) {
      parts.push(this.i18n.translate('sync.lastSummary.inactive', { count: this.formatCount(run.markedInactive) }));
    }
    return parts.join(' · ');
  }

  globalErrorLine(run: SyncRun): string | null {
    if (this.entity() !== 'ORCID' || run.status !== 'FAILED') {
      return null;
    }
    const code = run.errorCode?.trim();
    if (code) {
      const specific = this.i18n.translate(`sync.humanError.${code}`);
      if (specific && specific !== `sync.humanError.${code}`) {
        return specific;
      }
    }
    return this.i18n.translate('sync.humanError.FAILED');
  }

  openDetail(syncId: string): void {
    this.monitor.openDetail(syncId);
  }

  formatCount(value: number): string {
    return new Intl.NumberFormat(this.i18n.locale()).format(value ?? 0);
  }

  confirmTargetEntity(): SyncEntity {
    return this.confirmScope() === 'metrics' ? (this.metricsEntity() ?? this.entity()) : this.entity();
  }

  metricsSummaryKey(entity: SyncEntity | string | null = this.metricsEntity()): string {
    if (entity === 'PERSON_METRICS') {
      return 'sync.metrics.personSummary';
    }
    if (entity === 'RESEARCH_OUTPUT_METRICS') {
      return 'sync.metrics.outputSummary';
    }
    if (entity === 'JOURNAL_METRICS') {
      return 'sync.metrics.journalSummary';
    }
    return 'sync.metrics.summary';
  }

  startRequestFor(entity: SyncEntity, mode: SyncMode) {
    if (entity === 'JOURNAL_METRICS') {
      return this.syncApi.startJournalMetrics(mode);
    }
    if (entity === 'RESEARCH_OUTPUT_METRICS') {
      return this.syncApi.startResearchOutputMetrics(mode);
    }
    if (entity === 'PERSON_METRICS') {
      return this.syncApi.startPersonMetrics(mode);
    }
    if (entity === 'PEOPLE') {
      return this.syncApi.startPeople(mode);
    }
    if (entity === 'JOURNALS') {
      return this.syncApi.startJournals(mode);
    }
    if (entity === 'PROJECTS') {
      return this.syncApi.startProjects(mode);
    }
    if (entity === 'ORGANIZATIONS') {
      return this.syncApi.startOrganizations(mode);
    }
    if (entity === 'ORCID') {
      return this.syncApi.startOrcid(mode);
    }
    if (entity === 'UNPAYWALL') {
      return this.syncApi.startUnpaywall(mode);
    }
    if (entity === 'CROSSREF') {
      return this.syncApi.startCrossref(mode);
    }
    if (entity === 'OPENALEX') {
      return this.syncApi.startOpenAlex(mode);
    }
    if (entity === 'DSPACE') {
      return this.syncApi.startDspace(mode);
    }
    return this.syncApi.startResearchOutputs(mode);
  }

  alreadyRunningKey(entity: SyncEntity | string = this.entity()): string {
    const value = entity;
    if (value === 'PERSON_METRICS') {
      return 'sync.alreadyRunningPersonMetrics';
    }
    if (value === 'JOURNAL_METRICS') {
      return 'sync.alreadyRunningJournalMetrics';
    }
    if (value === 'RESEARCH_OUTPUT_METRICS') {
      return 'sync.alreadyRunningOutputMetrics';
    }
    if (value === 'PEOPLE') {
      return 'sync.alreadyRunningPeople';
    }
    if (value === 'JOURNALS') {
      return 'sync.alreadyRunningJournals';
    }
    if (value === 'PROJECTS') {
      return 'sync.alreadyRunningProjects';
    }
    if (value === 'ORGANIZATIONS') {
      return 'sync.alreadyRunningOrganizations';
    }
    return value === 'ORCID'
      ? 'sync.alreadyRunningOrcid'
      : value === 'UNPAYWALL'
        ? 'sync.alreadyRunningUnpaywall'
        : value === 'CROSSREF'
          ? 'sync.alreadyRunningCrossref'
          : value === 'OPENALEX'
            ? 'sync.alreadyRunningOpenAlex'
            : value === 'DSPACE'
              ? 'sync.alreadyRunningDspace'
            : 'sync.alreadyRunningOutputs';
  }

  fallbackFilename(): string {
    if (this.entity() === 'PEOPLE') {
      return 'openscience-personas.xlsx';
    }
    if (this.entity() === 'JOURNALS') {
      return 'openscience-revistas.xlsx';
    }
    if (this.entity() === 'PROJECTS') {
      return 'openscience-proyectos.xlsx';
    }
    if (this.entity() === 'ORGANIZATIONS') {
      return 'openscience-organizaciones.xlsx';
    }
    return this.entity() === 'UNPAYWALL'
      ? 'openscience-unpaywall.xlsx'
      : this.entity() === 'CROSSREF'
        ? 'openscience-crossref.xlsx'
        : this.entity() === 'OPENALEX'
          ? 'openscience-openalex.xlsx'
          : 'openscience-produccion-cientifica.xlsx';
  }

  private saveExport(response: HttpResponse<Blob>): void {
    this.exportBusy.set(false);
    const blob = response.body;
    if (!blob) {
      this.setMessage('error', 'sync.exportFailed');
      return;
    }
    downloadBlob(blob, filenameFromDisposition(response.headers.get('Content-Disposition'), this.fallbackFilename()));
  }

  private setMessage(kind: 'success' | 'error' | 'info', key: string): void {
    this.messageKind.set(kind);
    this.message.set(key);
  }

  private errorCode(error: HttpErrorResponse): string | null {
    return this.conflictPayload(error)?.code ?? null;
  }

  private conflictPayload(error: HttpErrorResponse): SyncConflictError | null {
    const payload = error.error;
    if (payload && typeof payload === 'object') {
      return payload as SyncConflictError;
    }
    return null;
  }
}
