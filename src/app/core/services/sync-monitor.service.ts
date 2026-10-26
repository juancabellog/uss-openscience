import { Injectable, computed, inject, signal } from '@angular/core';
import { Subscription, catchError, forkJoin, interval, of, switchMap } from 'rxjs';
import { I18nService } from '../i18n/i18n.service';
import { AuthService } from './auth.service';
import { NotificationService } from './notification.service';
import {
  ApiSyncModePolicy,
  SyncApiService,
  SyncConflictError,
  SyncEntity,
  SyncRun,
  normalizeSyncEntity
} from './api/sync-api.service';
import { SyncDetailService } from './sync-detail.service';
import { SyncModePolicy, conservativeSyncPolicy, mapSyncModePolicy } from '../sync/sync-mode.util';

const POLL_MS = 8000;

@Injectable({ providedIn: 'root' })
export class SyncMonitorService {
  private readonly syncApi = inject(SyncApiService);
  private readonly auth = inject(AuthService);
  private readonly notifications = inject(NotificationService);
  private readonly i18n = inject(I18nService);
  private readonly detail = inject(SyncDetailService);
  private readonly notified = new Set<string>();
  private pollSub: Subscription | null = null;

  readonly people = signal<SyncRun | null>(null);
  readonly researchOutput = signal<SyncRun | null>(null);
  readonly journals = signal<SyncRun | null>(null);
  readonly projects = signal<SyncRun | null>(null);
  readonly organizations = signal<SyncRun | null>(null);
  readonly orcid = signal<SyncRun | null>(null);
  readonly unpaywall = signal<SyncRun | null>(null);
  readonly crossref = signal<SyncRun | null>(null);
  readonly openalex = signal<SyncRun | null>(null);
  readonly dspace = signal<SyncRun | null>(null);
  readonly journalMetrics = signal<SyncRun | null>(null);
  readonly researchOutputMetrics = signal<SyncRun | null>(null);
  readonly personMetrics = signal<SyncRun | null>(null);
  readonly latestPeople = signal<SyncRun | null>(null);
  readonly latestResearchOutput = signal<SyncRun | null>(null);
  readonly latestJournals = signal<SyncRun | null>(null);
  readonly latestProjects = signal<SyncRun | null>(null);
  readonly latestOrganizations = signal<SyncRun | null>(null);
  readonly latestOrcid = signal<SyncRun | null>(null);
  readonly latestUnpaywall = signal<SyncRun | null>(null);
  readonly latestCrossref = signal<SyncRun | null>(null);
  readonly latestOpenAlex = signal<SyncRun | null>(null);
  readonly latestDspace = signal<SyncRun | null>(null);
  readonly latestJournalMetrics = signal<SyncRun | null>(null);
  readonly latestResearchOutputMetrics = signal<SyncRun | null>(null);
  readonly latestPersonMetrics = signal<SyncRun | null>(null);
  readonly lastCompletion = signal<SyncRun | null>(null);
  private readonly policies = signal<Partial<Record<SyncEntity, SyncModePolicy>>>({});

  readonly hasRunning = computed(
    () =>
      isActive(this.people())
      || isActive(this.researchOutput())
      || isActive(this.journals())
      || isActive(this.projects())
      || isActive(this.organizations())
      || isActive(this.orcid())
      || isActive(this.unpaywall())
      || isActive(this.crossref())
      || isActive(this.openalex())
      || isActive(this.dspace())
      || isActive(this.journalMetrics())
      || isActive(this.researchOutputMetrics())
      || isActive(this.personMetrics())
  );

  constructor() {
    this.bootstrap();
  }

  active(entity: SyncEntity): SyncRun | null {
    if (entity === 'PEOPLE') {
      return this.people();
    }
    if (entity === 'JOURNALS') {
      return this.journals();
    }
    if (entity === 'PROJECTS') {
      return this.projects();
    }
    if (entity === 'ORGANIZATIONS') {
      return this.organizations();
    }
    if (entity === 'ORCID') {
      return this.orcid();
    }
    if (entity === 'UNPAYWALL') {
      return this.unpaywall();
    }
    if (entity === 'CROSSREF') {
      return this.crossref();
    }
    if (entity === 'OPENALEX') {
      return this.openalex();
    }
    if (entity === 'DSPACE') {
      return this.dspace();
    }
    if (entity === 'JOURNAL_METRICS') {
      return this.journalMetrics();
    }
    if (entity === 'RESEARCH_OUTPUT_METRICS') {
      return this.researchOutputMetrics();
    }
    if (entity === 'PERSON_METRICS') {
      return this.personMetrics();
    }
    return this.researchOutput();
  }

  policy(entity: SyncEntity): SyncModePolicy {
    return this.policies()[entity] ?? conservativeSyncPolicy(entity);
  }

  hasLoadedPolicy(entity: SyncEntity): boolean {
    return this.policies()[entity] != null;
  }

  latest(entity: SyncEntity): SyncRun | null {
    if (entity === 'PEOPLE') {
      return this.latestPeople();
    }
    if (entity === 'JOURNALS') {
      return this.latestJournals();
    }
    if (entity === 'PROJECTS') {
      return this.latestProjects();
    }
    if (entity === 'ORGANIZATIONS') {
      return this.latestOrganizations();
    }
    if (entity === 'ORCID') {
      return this.latestOrcid();
    }
    if (entity === 'UNPAYWALL') {
      return this.latestUnpaywall();
    }
    if (entity === 'CROSSREF') {
      return this.latestCrossref();
    }
    if (entity === 'OPENALEX') {
      return this.latestOpenAlex();
    }
    if (entity === 'DSPACE') {
      return this.latestDspace();
    }
    if (entity === 'JOURNAL_METRICS') {
      return this.latestJournalMetrics();
    }
    if (entity === 'RESEARCH_OUTPUT_METRICS') {
      return this.latestResearchOutputMetrics();
    }
    if (entity === 'PERSON_METRICS') {
      return this.latestPersonMetrics();
    }
    return this.latestResearchOutput();
  }

  isRunning(entity: SyncEntity): boolean {
    return isActive(this.active(entity));
  }

  watch(run: SyncRun): void {
    this.applyActive(run);
    this.ensurePolling();
  }

  refreshRunning(): void {
    this.syncApi.running().pipe(catchError(() => of([]))).subscribe((running) => {
      this.refresh(running);
      if (running.some(isActive)) {
        this.ensurePolling();
      }
    });
  }

  refreshLatest(entity: SyncEntity): void {
    this.latestIfAllowed(entity).subscribe((run) => {
      if (run) {
        this.applyLatest(run);
      }
    });
    this.refreshPolicy(entity);
  }

  refreshPolicy(entity: SyncEntity): void {
    this.policyIfAllowed(entity).subscribe((raw) => {
      if (raw) {
        this.applyPolicy(entity, raw);
      }
    });
  }

  adoptAlreadyRunning(entity: SyncEntity, conflict?: SyncConflictError | null): void {
    if (conflict?.activeSyncId) {
      this.applyActive({
        syncId: conflict.activeSyncId,
        entity: conflict.entity ?? entity,
        status: 'RUNNING',
        startedAt: conflict.startedAt ?? null,
        startedBy: conflict.startedBy ?? null,
        processed: 0,
        created: 0,
        updated: 0,
        unchanged: 0,
        reactivated: 0,
        markedInactive: 0,
        errors: 0
      });
      this.ensurePolling();
      this.syncApi.get(conflict.activeSyncId).subscribe({
        next: (run) => this.watch(run),
        error: () => this.refreshRunning()
      });
      return;
    }
    this.refreshRunning();
  }

  hasVisibleChanges(run: SyncRun | null | undefined): boolean {
    return hasVisibleChanges(run);
  }

  openDetail(syncId: string): void {
    this.detail.show(syncId);
  }

  alreadyRunningMessage(entity: SyncEntity): string {
    const key =
      entity === 'PERSON_METRICS'
        ? 'sync.alreadyRunningPersonMetrics'
        : entity === 'JOURNAL_METRICS'
          ? 'sync.alreadyRunningJournalMetrics'
          : entity === 'RESEARCH_OUTPUT_METRICS'
            ? 'sync.alreadyRunningOutputMetrics'
        : entity === 'PEOPLE'
        ? 'sync.alreadyRunningPeople'
        : entity === 'JOURNALS'
          ? 'sync.alreadyRunningJournals'
          : entity === 'PROJECTS'
            ? 'sync.alreadyRunningProjects'
          : entity === 'ORGANIZATIONS'
            ? 'sync.alreadyRunningOrganizations'
          : entity === 'ORCID'
            ? 'sync.alreadyRunningOrcid'
            : entity === 'UNPAYWALL'
              ? 'sync.alreadyRunningUnpaywall'
              : entity === 'CROSSREF'
                ? 'sync.alreadyRunningCrossref'
                : entity === 'OPENALEX'
                  ? 'sync.alreadyRunningOpenAlex'
                  : entity === 'DSPACE'
                    ? 'sync.alreadyRunningDspace'
                : 'sync.alreadyRunningOutputs';
    return this.i18n.translate(key);
  }

  private bootstrap(): void {
    forkJoin({
      running: this.syncApi.running().pipe(catchError(() => of([]))),
      people: this.latestIfAllowed('PEOPLE'),
      outputs: this.latestIfAllowed('RESEARCH_OUTPUT'),
      journals: this.latestIfAllowed('JOURNALS'),
      projects: this.latestIfAllowed('PROJECTS'),
      organizations: this.latestIfAllowed('ORGANIZATIONS'),
      orcid: this.latestIfAllowed('ORCID'),
      unpaywall: this.latestIfAllowed('UNPAYWALL'),
      crossref: this.latestIfAllowed('CROSSREF'),
      openalex: this.latestIfAllowed('OPENALEX'),
      dspace: this.latestIfAllowed('DSPACE'),
      journalMetrics: this.latestIfAllowed('JOURNAL_METRICS'),
      researchOutputMetrics: this.latestIfAllowed('RESEARCH_OUTPUT_METRICS'),
      personMetrics: this.latestIfAllowed('PERSON_METRICS'),
      peoplePolicy: this.policyIfAllowed('PEOPLE'),
      outputsPolicy: this.policyIfAllowed('RESEARCH_OUTPUT'),
      journalsPolicy: this.policyIfAllowed('JOURNALS'),
      projectsPolicy: this.policyIfAllowed('PROJECTS'),
      organizationsPolicy: this.policyIfAllowed('ORGANIZATIONS'),
      orcidPolicy: this.policyIfAllowed('ORCID'),
      unpaywallPolicy: this.policyIfAllowed('UNPAYWALL'),
      crossrefPolicy: this.policyIfAllowed('CROSSREF'),
      openalexPolicy: this.policyIfAllowed('OPENALEX'),
      dspacePolicy: this.policyIfAllowed('DSPACE'),
      journalMetricsPolicy: this.policyIfAllowed('JOURNAL_METRICS'),
      researchOutputMetricsPolicy: this.policyIfAllowed('RESEARCH_OUTPUT_METRICS'),
      personMetricsPolicy: this.policyIfAllowed('PERSON_METRICS')
    }).subscribe(({
      running, people, outputs, journals, projects, organizations, orcid, unpaywall, crossref, openalex, dspace,
      journalMetrics, researchOutputMetrics, personMetrics,
      peoplePolicy, outputsPolicy, journalsPolicy, projectsPolicy, organizationsPolicy, orcidPolicy, unpaywallPolicy, crossrefPolicy, openalexPolicy, dspacePolicy,
      journalMetricsPolicy, researchOutputMetricsPolicy, personMetricsPolicy
    }) => {
      this.latestPeople.set(people);
      this.latestResearchOutput.set(outputs);
      this.latestJournals.set(journals);
      this.latestProjects.set(projects);
      this.latestOrganizations.set(organizations);
      this.latestOrcid.set(orcid);
      this.latestUnpaywall.set(unpaywall);
      this.latestCrossref.set(crossref);
      this.latestOpenAlex.set(openalex);
      this.latestDspace.set(dspace);
      this.latestJournalMetrics.set(journalMetrics);
      this.latestResearchOutputMetrics.set(researchOutputMetrics);
      this.latestPersonMetrics.set(personMetrics);
      this.applyPolicy('PEOPLE', peoplePolicy);
      this.applyPolicy('RESEARCH_OUTPUT', outputsPolicy);
      this.applyPolicy('JOURNALS', journalsPolicy);
      this.applyPolicy('PROJECTS', projectsPolicy);
      this.applyPolicy('ORGANIZATIONS', organizationsPolicy);
      this.applyPolicy('ORCID', orcidPolicy);
      this.applyPolicy('UNPAYWALL', unpaywallPolicy);
      this.applyPolicy('CROSSREF', crossrefPolicy);
      this.applyPolicy('OPENALEX', openalexPolicy);
      this.applyPolicy('DSPACE', dspacePolicy);
      this.applyPolicy('JOURNAL_METRICS', journalMetricsPolicy);
      this.applyPolicy('RESEARCH_OUTPUT_METRICS', researchOutputMetricsPolicy);
      this.applyPolicy('PERSON_METRICS', personMetricsPolicy);
      rememberFinished(this.notified, people, outputs, journals, projects, organizations, orcid, unpaywall, crossref, openalex, dspace, journalMetrics, researchOutputMetrics, personMetrics);
      running.forEach((run) => this.applyActive(run));
      if (running.some(isActive)) {
        this.ensurePolling();
      }
    });
  }

  private latestIfAllowed(entity: SyncEntity) {
    return this.canConsultEntity(entity)
      ? this.syncApi.latest(entity).pipe(catchError(() => of(null)))
      : of(null);
  }

  private policyIfAllowed(entity: SyncEntity) {
    return this.canConsultEntity(entity)
      ? this.syncApi.policy(entity).pipe(catchError(() => of(null)))
      : of(null);
  }

  applyPolicy(entity: SyncEntity, raw: ApiSyncModePolicy | null): void {
    if (!raw) {
      return;
    }
    this.policies.update((current) => ({
      ...current,
      [entity]: mapSyncModePolicy(raw, entity)
    }));
  }

  private canConsultEntity(entity: SyncEntity): boolean {
    if (entity === 'PEOPLE') {
      return this.auth.canConsult('PEOPLE');
    }
    if (entity === 'JOURNALS') {
      return this.auth.canConsult('JOURNALS');
    }
    if (entity === 'PROJECTS') {
      return this.auth.canConsult('PROJECTS');
    }
    if (entity === 'ORCID' || entity === 'UNPAYWALL' || entity === 'CROSSREF' || entity === 'OPENALEX' || entity === 'DSPACE') {
      return this.auth.canConsult('INTEGRATIONS');
    }
    if (entity === 'JOURNAL_METRICS') {
      return this.auth.canConsult('JOURNALS');
    }
    if (entity === 'PERSON_METRICS') {
      return this.auth.canConsult('PEOPLE');
    }
    return this.auth.canConsult('RESEARCH_OUTPUTS');
  }

  private ensurePolling(): void {
    if (this.pollSub) {
      return;
    }
    this.pollSub = interval(POLL_MS)
      .pipe(switchMap(() => this.syncApi.running()))
      .subscribe((running) => this.refresh(running));
  }

  private stopPolling(): void {
    this.pollSub?.unsubscribe();
    this.pollSub = null;
  }

  private refresh(running: SyncRun[]): void {
    const previous = [this.people(), this.researchOutput(), this.journals(), this.projects(), this.organizations(), this.orcid(), this.unpaywall(), this.crossref(), this.openalex(), this.dspace(), this.journalMetrics(), this.researchOutputMetrics(), this.personMetrics()].filter(
      (run): run is SyncRun => run != null
    );
    const runningIds = new Set(running.map((run) => run.syncId));
    running.forEach((run) => this.applyActive(run));
    if (!running.some((run) => normalizeEntity(run.entity) === 'PEOPLE')) {
      this.people.set(null);
    }
    if (!running.some((run) => normalizeEntity(run.entity) === 'RESEARCH_OUTPUT')) {
      this.researchOutput.set(null);
    }
    if (!running.some((run) => normalizeEntity(run.entity) === 'JOURNALS')) {
      this.journals.set(null);
    }
    if (!running.some((run) => normalizeEntity(run.entity) === 'PROJECTS')) {
      this.projects.set(null);
    }
    if (!running.some((run) => normalizeEntity(run.entity) === 'ORGANIZATIONS')) {
      this.organizations.set(null);
    }
    if (!running.some((run) => normalizeEntity(run.entity) === 'ORCID')) {
      this.orcid.set(null);
    }
    if (!running.some((run) => normalizeEntity(run.entity) === 'UNPAYWALL')) {
      this.unpaywall.set(null);
    }
    if (!running.some((run) => normalizeEntity(run.entity) === 'CROSSREF')) {
      this.crossref.set(null);
    }
    if (!running.some((run) => normalizeEntity(run.entity) === 'OPENALEX')) {
      this.openalex.set(null);
    }
    if (!running.some((run) => normalizeEntity(run.entity) === 'DSPACE')) {
      this.dspace.set(null);
    }
    if (!running.some((run) => normalizeEntity(run.entity) === 'JOURNAL_METRICS')) {
      this.journalMetrics.set(null);
    }
    if (!running.some((run) => normalizeEntity(run.entity) === 'RESEARCH_OUTPUT_METRICS')) {
      this.researchOutputMetrics.set(null);
    }
    if (!running.some((run) => normalizeEntity(run.entity) === 'PERSON_METRICS')) {
      this.personMetrics.set(null);
    }
    for (const run of previous) {
      if (isActive(run) && !runningIds.has(run.syncId)) {
        this.finish(run.syncId);
      }
    }
    if (!running.some(isActive)) {
      this.stopPolling();
    }
  }

  private finish(syncId: string): void {
    this.syncApi.get(syncId).subscribe((run) => {
      this.applyLatest(run);
      this.lastCompletion.set(run);
      this.notifyOnce(run);
      const entity = normalizeSyncEntity(run.entity) as SyncEntity;
      this.refreshPolicy(entity);
    });
  }

  private applyActive(run: SyncRun): void {
    const entity = normalizeEntity(run.entity);
    if (entity === 'PEOPLE') {
      this.people.set(run);
      if (!isActive(run)) {
        this.latestPeople.set(run);
      }
      return;
    }
    if (entity === 'JOURNALS') {
      this.journals.set(run);
      if (!isActive(run)) {
        this.latestJournals.set(run);
      }
      return;
    }
    if (entity === 'PROJECTS') {
      this.projects.set(run);
      if (!isActive(run)) {
        this.latestProjects.set(run);
      }
      return;
    }
    if (entity === 'ORGANIZATIONS') {
      this.organizations.set(run);
      if (!isActive(run)) {
        this.latestOrganizations.set(run);
      }
      return;
    }
    if (entity === 'ORCID') {
      this.orcid.set(run);
      if (!isActive(run)) {
        this.latestOrcid.set(run);
      }
      return;
    }
    if (entity === 'UNPAYWALL') {
      this.unpaywall.set(run);
      if (!isActive(run)) {
        this.latestUnpaywall.set(run);
      }
      return;
    }
    if (entity === 'CROSSREF') {
      this.crossref.set(run);
      if (!isActive(run)) {
        this.latestCrossref.set(run);
      }
      return;
    }
    if (entity === 'OPENALEX') {
      this.openalex.set(run);
      if (!isActive(run)) {
        this.latestOpenAlex.set(run);
      }
      return;
    }
    if (entity === 'DSPACE') {
      this.dspace.set(run);
      if (!isActive(run)) {
        this.latestDspace.set(run);
      }
      return;
    }
    if (entity === 'JOURNAL_METRICS') {
      this.journalMetrics.set(run);
      if (!isActive(run)) {
        this.latestJournalMetrics.set(run);
      }
      return;
    }
    if (entity === 'RESEARCH_OUTPUT_METRICS') {
      this.researchOutputMetrics.set(run);
      if (!isActive(run)) {
        this.latestResearchOutputMetrics.set(run);
      }
      return;
    }
    if (entity === 'PERSON_METRICS') {
      this.personMetrics.set(run);
      if (!isActive(run)) {
        this.latestPersonMetrics.set(run);
      }
      return;
    }
    if (entity === 'RESEARCH_OUTPUT') {
      this.researchOutput.set(run);
      if (!isActive(run)) {
        this.latestResearchOutput.set(run);
      }
    }
  }

  private applyLatest(run: SyncRun): void {
    const entity = normalizeEntity(run.entity);
    if (entity === 'PEOPLE') {
      this.latestPeople.set(run);
      if (!isActive(run)) {
        this.people.set(null);
      }
      return;
    }
    if (entity === 'JOURNALS') {
      this.latestJournals.set(run);
      if (!isActive(run)) {
        this.journals.set(null);
      }
      return;
    }
    if (entity === 'PROJECTS') {
      this.latestProjects.set(run);
      if (!isActive(run)) {
        this.projects.set(null);
      }
      return;
    }
    if (entity === 'ORGANIZATIONS') {
      this.latestOrganizations.set(run);
      if (!isActive(run)) {
        this.organizations.set(null);
      }
      return;
    }
    if (entity === 'ORCID') {
      this.latestOrcid.set(run);
      if (!isActive(run)) {
        this.orcid.set(null);
      }
      return;
    }
    if (entity === 'UNPAYWALL') {
      this.latestUnpaywall.set(run);
      if (!isActive(run)) {
        this.unpaywall.set(null);
      }
      return;
    }
    if (entity === 'CROSSREF') {
      this.latestCrossref.set(run);
      if (!isActive(run)) {
        this.crossref.set(null);
      }
      return;
    }
    if (entity === 'OPENALEX') {
      this.latestOpenAlex.set(run);
      if (!isActive(run)) {
        this.openalex.set(null);
      }
      return;
    }
    if (entity === 'DSPACE') {
      this.latestDspace.set(run);
      if (!isActive(run)) {
        this.dspace.set(null);
      }
      return;
    }
    if (entity === 'JOURNAL_METRICS') {
      this.latestJournalMetrics.set(run);
      if (!isActive(run)) {
        this.journalMetrics.set(null);
      }
      return;
    }
    if (entity === 'RESEARCH_OUTPUT_METRICS') {
      this.latestResearchOutputMetrics.set(run);
      if (!isActive(run)) {
        this.researchOutputMetrics.set(null);
      }
      return;
    }
    if (entity === 'PERSON_METRICS') {
      this.latestPersonMetrics.set(run);
      if (!isActive(run)) {
        this.personMetrics.set(null);
      }
      return;
    }
    if (entity === 'RESEARCH_OUTPUT') {
      this.latestResearchOutput.set(run);
      if (!isActive(run)) {
        this.researchOutput.set(null);
      }
    }
  }

  private notifyOnce(run: SyncRun): void {
    if (this.notified.has(run.syncId)) {
      return;
    }
    this.notified.add(run.syncId);
    if (run.status === 'COMPLETED') {
      this.notifications.show(
        'success',
        this.i18n.translate(
          normalizeEntity(run.entity) === 'ORCID'
            ? 'sync.toast.completedOrcidTitle'
            : normalizeEntity(run.entity) === 'UNPAYWALL'
              ? 'sync.toast.completedUnpaywallTitle'
              : normalizeEntity(run.entity) === 'CROSSREF'
                ? 'sync.toast.completedCrossrefTitle'
                : normalizeEntity(run.entity) === 'OPENALEX'
                  ? 'sync.toast.completedOpenAlexTitle'
                  : normalizeEntity(run.entity) === 'DSPACE'
                    ? 'sync.toast.completedDspaceTitle'
                : 'sync.toast.completedTitle'
        ),
        this.completedBody(run),
        null,
        this.detailAction(run)
      );
      return;
    }
    if (run.status === 'COMPLETED_WITH_WARNINGS') {
      this.notifications.show(
        'warning',
        this.i18n.translate('sync.toast.warningsTitle'),
        this.completedBody(run),
        this.i18n.translate('sync.toast.warningsBody'),
        this.detailAction(run)
      );
      return;
    }
    if (run.status === 'FAILED') {
      this.notifications.show(
        'error',
        this.i18n.translate('sync.toast.failedTitle'),
        this.failedBody(run),
        this.failedDetail(run),
        this.detailAction(run, true)
      );
      return;
    }
    if (run.status === 'CANCELLED') {
      this.notifications.show(
        'warning',
        this.i18n.translate('sync.toast.cancelledTitle'),
        this.i18n.translate('sync.toast.cancelledBody'),
        null,
        this.detailAction(run)
      );
    }
  }

  private completedBody(run: SyncRun): string {
    const processed = this.formatCount(run.processed);
    if (normalizeEntity(run.entity) === 'ORCID') {
      return this.i18n.translate('sync.toast.orcidSummary', {
        processed,
        works: this.formatCount(run.created + run.updated + run.unchanged + run.reactivated),
        errors: this.formatCount(run.errors)
      });
    }
    if (normalizeEntity(run.entity) === 'UNPAYWALL') {
      return this.i18n.translate('sync.toast.unpaywallSummary', {
        processed,
        created: this.formatCount(run.created),
        updated: this.formatCount(run.updated),
        unchanged: this.formatCount(run.unchanged),
        notFound: this.formatCount(run.notFound ?? 0),
        errors: this.formatCount(run.errors)
      });
    }
    if (normalizeEntity(run.entity) === 'CROSSREF') {
      return this.i18n.translate('sync.toast.crossrefSummary', {
        processed,
        created: this.formatCount(run.created),
        updated: this.formatCount(run.updated),
        unchanged: this.formatCount(run.unchanged),
        found: this.formatCount(run.oa ?? 0),
        otherAgency: this.formatCount(run.nonOa ?? 0),
        errors: this.formatCount(run.errors)
      });
    }
    if (normalizeEntity(run.entity) === 'OPENALEX') {
      return this.i18n.translate('sync.toast.openalexSummary', {
        processed,
        created: this.formatCount(run.created),
        updated: this.formatCount(run.updated),
        unchanged: this.formatCount(run.unchanged),
        found: this.formatCount(run.oa ?? 0),
        errors: this.formatCount(run.errors)
      });
    }
    if (normalizeEntity(run.entity) === 'JOURNAL_METRICS'
        || normalizeEntity(run.entity) === 'RESEARCH_OUTPUT_METRICS'
        || normalizeEntity(run.entity) === 'PERSON_METRICS') {
      return this.i18n.translate('sync.toast.metricsSummary', {
        processed,
        created: this.formatCount(run.created),
        unchanged: this.formatCount(run.unchanged),
        errors: this.formatCount(run.errors)
      });
    }
    if (normalizeEntity(run.entity) === 'DSPACE') {
      return this.i18n.translate('sync.toast.dspaceSummary', {
        processed,
        created: this.formatCount(run.created),
        updated: this.formatCount(run.updated),
        unchanged: this.formatCount(run.unchanged),
        found: this.formatCount(run.oa ?? 0),
        errors: this.formatCount(run.errors)
      });
    }
    if (!this.hasVisibleChanges(run)) {
      return this.i18n.translate(this.noChangesKey(run), { processed });
    }
    return this.i18n.translate(this.summaryKey(run), {
      processed,
      details: this.changeDetails(run)
    });
  }

  private changeDetails(run: SyncRun): string {
    const parts: string[] = [];
    if (run.created > 0) {
      parts.push(this.i18n.translate('sync.toast.detailCreated', { count: this.formatCount(run.created) }));
    }
    if (run.updated > 0) {
      parts.push(this.i18n.translate('sync.toast.detailUpdated', { count: this.formatCount(run.updated) }));
    }
    if (run.reactivated > 0) {
      parts.push(this.i18n.translate('sync.toast.detailReactivated', { count: this.formatCount(run.reactivated) }));
    }
    if (run.markedInactive > 0) {
      parts.push(this.i18n.translate('sync.toast.detailInactive', { count: this.formatCount(run.markedInactive) }));
    }
    return parts.join('');
  }

  private failedBody(run: SyncRun): string {
    const key = run.errorCode === 'PURE_INCOMPLETE'
      ? this.failedIncompleteKey(run)
      : this.failedKey(run);
    return this.i18n.translate(key, {
      succeeded: this.formatCount(run.processed),
      failed: this.formatCount(run.errors),
      fetched: this.formatCount(run.fetched ?? run.processed + run.errors),
      expected: this.formatCount(run.totalExpected ?? 0)
    });
  }

  private failedDetail(run: SyncRun): string {
    const parts: string[] = [];
    if (run.errorCode) {
      parts.push(run.errorCode);
    }
    if (run.syncId) {
      parts.push(this.i18n.translate('sync.executionId', { id: run.syncId }));
    }
    return parts.join(' · ');
  }

  private detailAction(run: SyncRun, force = false) {
    if (!run.syncId || (!force && !hasVisibleChanges(run))) {
      return null;
    }
    return {
      label: this.i18n.translate('sync.viewDetail'),
      run: () => this.detail.show(run.syncId)
    };
  }

  private noChangesKey(run: SyncRun): string {
    const entity = normalizeEntity(run.entity);
    if (entity === 'PEOPLE') {
      return 'sync.toast.noChangesPeople';
    }
    if (entity === 'JOURNALS') {
      return 'sync.toast.noChangesJournals';
    }
    if (entity === 'PROJECTS') {
      return 'sync.toast.noChangesProjects';
    }
    if (entity === 'ORGANIZATIONS') {
      return 'sync.toast.noChangesOrganizations';
    }
    if (entity === 'ORCID') {
      return 'sync.toast.noChangesOrcid';
    }
    if (entity === 'UNPAYWALL') {
      return 'sync.toast.noChangesUnpaywall';
    }
    if (entity === 'CROSSREF') {
      return 'sync.toast.noChangesCrossref';
    }
    if (entity === 'OPENALEX') {
      return 'sync.toast.noChangesOpenAlex';
    }
    if (entity === 'DSPACE') {
      return 'sync.toast.noChangesDspace';
    }
    if (entity === 'JOURNAL_METRICS' || entity === 'RESEARCH_OUTPUT_METRICS' || entity === 'PERSON_METRICS') {
      return 'sync.toast.noChangesMetrics';
    }
    return 'sync.toast.noChangesOutputs';
  }

  private summaryKey(run: SyncRun): string {
    const entity = normalizeEntity(run.entity);
    if (entity === 'PEOPLE') {
      return 'sync.toast.peopleSummary';
    }
    if (entity === 'JOURNALS') {
      return 'sync.toast.journalsSummary';
    }
    if (entity === 'PROJECTS') {
      return 'sync.toast.projectsSummary';
    }
    if (entity === 'ORGANIZATIONS') {
      return 'sync.toast.organizationsSummary';
    }
    if (entity === 'ORCID') {
      return 'sync.toast.orcidSummary';
    }
    if (entity === 'UNPAYWALL') {
      return 'sync.toast.unpaywallSummary';
    }
    if (entity === 'CROSSREF') {
      return 'sync.toast.crossrefSummary';
    }
    if (entity === 'OPENALEX') {
      return 'sync.toast.openalexSummary';
    }
    if (entity === 'DSPACE') {
      return 'sync.toast.dspaceSummary';
    }
    if (entity === 'JOURNAL_METRICS' || entity === 'RESEARCH_OUTPUT_METRICS' || entity === 'PERSON_METRICS') {
      return 'sync.toast.metricsSummary';
    }
    return 'sync.toast.outputsSummary';
  }

  private failedKey(run: SyncRun): string {
    const entity = normalizeEntity(run.entity);
    if (entity === 'PEOPLE') {
      return 'sync.toast.failedPeople';
    }
    if (entity === 'JOURNALS') {
      return 'sync.toast.failedJournals';
    }
    if (entity === 'PROJECTS') {
      return 'sync.toast.failedProjects';
    }
    if (entity === 'ORGANIZATIONS') {
      return 'sync.toast.failedOrganizations';
    }
    if (entity === 'ORCID') {
      return 'sync.toast.failedOrcid';
    }
    if (entity === 'UNPAYWALL') {
      return 'sync.toast.failedUnpaywall';
    }
    if (entity === 'CROSSREF') {
      return 'sync.toast.failedCrossref';
    }
    if (entity === 'OPENALEX') {
      return 'sync.toast.failedOpenAlex';
    }
    if (entity === 'DSPACE') {
      return 'sync.toast.failedDspace';
    }
    if (entity === 'JOURNAL_METRICS' || entity === 'RESEARCH_OUTPUT_METRICS' || entity === 'PERSON_METRICS') {
      return 'sync.toast.failedMetrics';
    }
    return 'sync.toast.failedOutputs';
  }

  private failedIncompleteKey(run: SyncRun): string {
    const entity = normalizeEntity(run.entity);
    if (entity === 'PEOPLE') {
      return 'sync.toast.failedIncompletePeople';
    }
    if (entity === 'JOURNALS') {
      return 'sync.toast.failedIncompleteJournals';
    }
    if (entity === 'PROJECTS') {
      return 'sync.toast.failedIncompleteProjects';
    }
    if (entity === 'ORCID') {
      return 'sync.toast.failedIncompleteOrcid';
    }
    if (entity === 'UNPAYWALL') {
      return 'sync.toast.failedUnpaywall';
    }
    if (entity === 'CROSSREF') {
      return 'sync.toast.failedCrossref';
    }
    if (entity === 'OPENALEX') {
      return 'sync.toast.failedOpenAlex';
    }
    if (entity === 'DSPACE') {
      return 'sync.toast.failedDspace';
    }
    if (entity === 'JOURNAL_METRICS' || entity === 'RESEARCH_OUTPUT_METRICS' || entity === 'PERSON_METRICS') {
      return 'sync.toast.failedMetrics';
    }
    return 'sync.toast.failedIncompleteOutputs';
  }

  private formatCount(value: number): string {
    return new Intl.NumberFormat(this.i18n.locale()).format(value ?? 0);
  }
}

function hasVisibleChanges(run: SyncRun | null | undefined): boolean {
  if (!run) {
    return false;
  }
  return run.created > 0 || run.updated > 0 || run.reactivated > 0 || run.markedInactive > 0;
}

function isActive(run: SyncRun | null | undefined): boolean {
  const status = (run?.status ?? '').trim().toUpperCase();
  return status === 'PENDING' || status === 'RUNNING' || status === 'CANCEL_REQUESTED';
}

function normalizeEntity(entity?: string | null): string {
  return normalizeSyncEntity(entity);
}

function rememberFinished(target: Set<string>, ...runs: Array<SyncRun | null | undefined>): void {
  for (const run of runs) {
    if (run?.syncId && !isActive(run)) {
      target.add(run.syncId);
    }
  }
}
