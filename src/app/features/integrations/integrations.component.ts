import { ChangeDetectionStrategy, Component, effect, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, of, switchMap } from 'rxjs';
import {
  IntegrationEntityRun,
  IntegrationEntitySync,
  IntegrationStatus,
  OrcidStats,
  ResearcherIdentityIndicators,
  UnpaywallStats,
  CrossrefStats,
  OpenAlexStats,
  DspaceStats
} from '../../core/models/openscience.models';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { IntegrationService } from '../../core/services/platform-services';
import { OrcidApiService } from '../../core/services/api/orcid-api.service';
import { UnpaywallApiService } from '../../core/services/api/unpaywall-api.service';
import { CrossrefApiService } from '../../core/services/api/crossref-api.service';
import { OpenAlexApiService } from '../../core/services/api/openalex-api.service';
import { DspaceApiService } from '../../core/services/api/dspace-api.service';
import { ResearcherIdentityApiService } from '../../core/services/api/researcher-identity-api.service';
import { normalizeSyncEntity, pureEntityPath, SyncEntity, syncEntityLabelKey } from '../../core/services/api/sync-api.service';
import { SyncMonitorService } from '../../core/services/sync-monitor.service';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { LoadingStateComponent } from '../../shared/components/loading-state/loading-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';
import { SyncExportBarComponent } from '../../shared/components/sync-export-bar/sync-export-bar.component';
import { I18N_HOST, I18N_IMPORTS } from '../../shared/i18n/i18n.imports';
import { integrationIcon as iconForHealth, integrationKey, integrationTone } from '../../shared/utils/status.util';

@Component({
  selector: 'os-integrations',
  hostDirectives: [...I18N_HOST],
  imports: [
    ...I18N_IMPORTS,
    RouterLink,
    IconComponent,
    LoadingStateComponent,
    PageHeaderComponent,
    StatusBadgeComponent,
    SyncExportBarComponent
  ],
  templateUrl: './integrations.component.html',
  styleUrl: './integrations.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class IntegrationsComponent {
  readonly auth = inject(AuthService);
  private readonly integrationApi = inject(IntegrationService);
  private readonly orcidApi = inject(OrcidApiService);
  private readonly unpaywallApi = inject(UnpaywallApiService);
  private readonly crossrefApi = inject(CrossrefApiService);
  private readonly openAlexApi = inject(OpenAlexApiService);
  private readonly dspaceApi = inject(DspaceApiService);
  private readonly identityApi = inject(ResearcherIdentityApiService);
  private readonly monitor = inject(SyncMonitorService);
  private readonly router = inject(Router);
  private readonly reloadTick = signal(0);

  constructor() {
    effect(() => {
      for (const item of this.items()) {
        const entity = normalizeSyncEntity(item.name) as SyncEntity;
        if (
          (entity === 'ORCID'
            || entity === 'UNPAYWALL'
            || entity === 'CROSSREF'
            || entity === 'OPENALEX'
            || entity === 'DSPACE')
          && (item.recommendedSyncMode != null || item.hasValidBaseline != null)
        ) {
          this.monitor.applyPolicy(entity, item);
        }
      }
    });
  }

  readonly items = toSignal(
    toObservable(this.reloadTick).pipe(
      switchMap(() => this.integrationApi.list().pipe(catchError(() => of([] as IntegrationStatus[]))))
    ),
    { initialValue: [] as IntegrationStatus[] }
  );
  readonly orcidStats = toSignal(
    toObservable(this.reloadTick).pipe(
      switchMap(() => this.orcidApi.stats().pipe(catchError(() => of(null))))
    ),
    { initialValue: null as OrcidStats | null }
  );
  readonly unpaywallStats = toSignal(
    toObservable(this.reloadTick).pipe(
      switchMap(() => this.unpaywallApi.stats().pipe(catchError(() => of(null))))
    ),
    { initialValue: null as UnpaywallStats | null }
  );
  readonly crossrefStats = toSignal(
    toObservable(this.reloadTick).pipe(
      switchMap(() => this.crossrefApi.stats().pipe(catchError(() => of(null))))
    ),
    { initialValue: null as CrossrefStats | null }
  );
  readonly openAlexStats = toSignal(
    toObservable(this.reloadTick).pipe(
      switchMap(() => this.openAlexApi.stats().pipe(catchError(() => of(null))))
    ),
    { initialValue: null as OpenAlexStats | null }
  );
  readonly dspaceStats = toSignal(
    toObservable(this.reloadTick).pipe(
      switchMap(() => this.dspaceApi.stats().pipe(catchError(() => of(null))))
    ),
    { initialValue: null as DspaceStats | null }
  );
  readonly identity = toSignal(
    toObservable(this.reloadTick).pipe(
      switchMap(() =>
        this.auth.canConsult('INDICATORS')
          ? this.identityApi.summary().pipe(catchError(() => of(null)))
          : of(null)
      )
    ),
    { initialValue: null as ResearcherIdentityIndicators | null }
  );
  readonly integrationKey = integrationKey;
  readonly integrationTone = integrationTone;
  readonly syncEntityLabelKey = syncEntityLabelKey;
  readonly pureEntityPath = pureEntityPath;

  integrationIcon(health: IntegrationStatus['health']): string {
    return iconForHealth(health);
  }

  healthReasonKey(item: IntegrationStatus): string | null {
    if (!item.healthReason) {
      return null;
    }
    if (item.id === 'orcid') {
      return `integrations.orcid.health.${item.healthReason}`;
    }
    if (item.id === 'unpaywall') {
      return `integrations.unpaywall.health.${item.healthReason}`;
    }
    if (item.id === 'crossref') {
      return `integrations.crossref.health.${item.healthReason}`;
    }
    if (item.id === 'openalex') {
      return `integrations.openalex.health.${item.healthReason}`;
    }
    if (item.id === 'dspace') {
      return `integrations.dspace.health.${item.healthReason}`;
    }
    if (item.id === 'pure') {
      return `integrations.pure.health.${item.healthReason}`;
    }
    return null;
  }

  openHealthDetail(item: IntegrationStatus): void {
    if (item.id === 'orcid') {
      this.openExecution(this.monitor.latest('ORCID')?.syncId);
      return;
    }
    if (item.id === 'unpaywall') {
      this.openExecution(this.monitor.latest('UNPAYWALL')?.syncId);
      return;
    }
    if (item.id === 'crossref') {
      this.openExecution(this.monitor.latest('CROSSREF')?.syncId);
      return;
    }
    if (item.id === 'openalex') {
      this.openExecution(this.monitor.latest('OPENALEX')?.syncId);
      return;
    }
    if (item.id === 'dspace') {
      this.openExecution(this.monitor.latest('DSPACE')?.syncId);
      return;
    }
    if (item.id !== 'pure') {
      return;
    }
    const failed = item.entities?.find((row) => row.latestExecution?.status === 'FAILED');
    this.openExecution(failed?.latestExecution?.syncId ?? firstPureSyncId(item.entities));
  }

  openPureExecution(row: IntegrationEntitySync): void {
    this.openExecution(row.latestExecution?.syncId);
  }

  private openExecution(syncId?: string | null): void {
    if (!syncId) {
      return;
    }
    if (this.auth.canConsult('AUDIT')) {
      void this.router.navigate(['/audit'], { queryParams: { executionId: syncId } });
      return;
    }
    this.monitor.openDetail(syncId);
  }

  entityActivityAt(run: IntegrationEntityRun | null | undefined): string | null {
    if (!run) {
      return null;
    }
    return run.finishedAt ?? run.startedAt ?? null;
  }

  entityStatusKey(run: IntegrationEntityRun | null | undefined): string {
    if (!run?.status) {
      return 'integrations.pure.neverSynced';
    }
    if (run.status === 'RUNNING' || run.status === 'PENDING' || run.status === 'CANCEL_REQUESTED') {
      return 'integrations.pure.syncing';
    }
    return `sync.status.${run.status}`;
  }

  isActiveRun(run: IntegrationEntityRun | null | undefined): boolean {
    return run?.status === 'RUNNING' || run?.status === 'PENDING' || run?.status === 'CANCEL_REQUESTED';
  }

  reloadOrcid(): void {
    this.monitor.refreshLatest('ORCID');
    this.reloadTick.update((value) => value + 1);
  }

  reloadUnpaywall(): void {
    this.monitor.refreshLatest('UNPAYWALL');
    this.reloadTick.update((value) => value + 1);
  }

  reloadCrossref(): void {
    this.monitor.refreshLatest('CROSSREF');
    this.reloadTick.update((value) => value + 1);
  }

  reloadOpenAlex(): void {
    this.monitor.refreshLatest('OPENALEX');
    this.reloadTick.update((value) => value + 1);
  }

  reloadDspace(): void {
    this.monitor.refreshLatest('DSPACE');
    this.reloadTick.update((value) => value + 1);
  }
}

function firstPureSyncId(entities: IntegrationEntitySync[] | undefined): string | undefined {
  return entities?.find((row) => row.latestExecution?.syncId)?.latestExecution?.syncId ?? undefined;
}
