import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { IntegrationStatus } from '../../core/models/openscience.models';
import { DashboardApiService } from '../../core/services/api/dashboard-api.service';
import { AuthService } from '../../core/services/auth.service';
import { ApiDataQualitySummary, ApiMetric, DashboardResponse } from '../../core/services/api/api.types';
import { mapIntegrations } from '../../core/services/api/api-mappers';
import { ErrorStateComponent } from '../../shared/components/error-state/error-state.component';
import { LoadingStateComponent } from '../../shared/components/loading-state/loading-state.component';
import { MetricCardComponent } from '../../shared/components/metric-card/metric-card.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { SectionCardComponent } from '../../shared/components/section-card/section-card.component';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';
import { environment } from '../../../environments/environment';
import { I18N_HOST, I18N_IMPORTS } from '../../shared/i18n/i18n.imports';
import { integrationIcon as iconForHealth, integrationKey, integrationTone } from '../../shared/utils/status.util';

@Component({
  selector: 'os-dashboard',
  hostDirectives: [...I18N_HOST],
  imports: [
    ...I18N_IMPORTS,
    RouterLink,
    LoadingStateComponent,
    ErrorStateComponent,
    MetricCardComponent,
    PageHeaderComponent,
    SectionCardComponent,
    StatusBadgeComponent
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DashboardComponent {
  private readonly dashboardApi = inject(DashboardApiService);
  readonly auth = inject(AuthService);
  private readonly reloadTick = signal(0);

  readonly integrationKey = integrationKey;
  readonly integrationTone = integrationTone;

  integrationIcon(health: IntegrationStatus['health']): string {
    return iconForHealth(health);
  }

  readonly vm = toSignal(
    toObservable(this.reloadTick).pipe(
      switchMap(() =>
        this.dashboardApi.getDashboard().pipe(
          map((response) => toDashboardVm(response)),
          catchError((error: unknown) => {
            logDashboardError(error);
            return of(emptyDashboard('error'));
          }),
          startWith(emptyDashboard('loading'))
        )
      )
    ),
    { initialValue: emptyDashboard('loading') }
  );

  reload(): void {
    this.reloadTick.update((value) => value + 1);
  }

  visibleMetrics(): DashboardMetricCard[] {
    return this.vm().metrics.filter((metric) => canSeeDashboardMetric(metric.id, this.auth));
  }

  canSeeQuality(): boolean {
    return this.auth.canConsult('DATA_QUALITY');
  }

  canSeeIntegrations(): boolean {
    return this.auth.canConsult('INTEGRATIONS');
  }

  canSeeIndicators(): boolean {
    return this.auth.canConsult('INDICATORS');
  }

  metricValue(metric: DashboardMetricCard): string {
    return metric.available && metric.value != null ? String(metric.value) : '';
  }

  hasCoverage(metric: DashboardMetricCard): boolean {
    return metric.coveragePercent != null && metric.coverageDenominator != null;
  }

  showsResearcherDefinition(metric: DashboardMetricCard): boolean {
    return metric.id === 'researchers' || metric.id === 'researchersOrcid';
  }

  qualityWidth(count: number | null, total: number): number {
    if (count == null || total <= 0) {
      return 0;
    }
    return Math.round((count / total) * 100);
  }

  qualityTotal(quality: DashboardQualityVm): number {
    return [quality.open, quality.inReview, quality.validated, quality.corrected]
      .filter((value): value is number => value != null)
      .reduce((sum, value) => sum + value, 0);
  }
}

interface DashboardMetricCard {
  id: 'persons' | 'researchers' | 'orcid' | 'researchersOrcid' | 'scopus' | 'findings';
  available: boolean;
  value: number | null;
  coveragePercent: number | null;
  coverageNumerator: number | null;
  coverageDenominator: number | null;
}

interface DashboardQualityVm {
  available: boolean;
  open: number | null;
  inReview: number | null;
  validated: number | null;
  corrected: number | null;
}

interface DashboardVm {
  status: 'loading' | 'success' | 'error';
  lastUpdated: string;
  metrics: DashboardMetricCard[];
  dataQuality: DashboardQualityVm;
  integrations: IntegrationStatus[];
}

function emptyDashboard(status: DashboardVm['status']): DashboardVm {
  return {
    status,
    lastUpdated: '',
    metrics: [],
    dataQuality: { available: false, open: null, inReview: null, validated: null, corrected: null },
    integrations: []
  };
}

function toDashboardVm(response: DashboardResponse): DashboardVm {
  const coverage = response.metrics.orcidCoveragePercent;
  return {
    status: 'success',
    lastUpdated: response.lastUpdated,
    metrics: [
      toCard('persons', response.metrics.registeredPersons),
      toCard('researchers', response.metrics.researchers),
      toCard('orcid', response.metrics.personsWithOrcid, coverage),
      toCard(
        'researchersOrcid',
        response.metrics.researchersWithOrcid,
        response.metrics.researchersWithOrcidPercent
      ),
      toCard('scopus', response.metrics.personsWithScopusId),
      toCard('findings', response.metrics.pendingDataQualityIssues)
    ],
    dataQuality: toQuality(response.dataQuality),
    integrations: mapIntegrations(response.integrations ?? [])
  };
}

function toCard(
  id: DashboardMetricCard['id'],
  metric: ApiMetric | undefined,
  coverage?: ApiMetric
): DashboardMetricCard {
  const available = metric?.available === true && metric.value != null;
  const coverageAvailable = coverage?.available === true && coverage.value != null;
  return {
    id,
    available,
    value: available ? Number(metric.value) : null,
    coveragePercent: coverageAvailable ? Number(coverage.value) : null,
    coverageNumerator: coverageAvailable ? coverage.numerator ?? null : null,
    coverageDenominator: coverageAvailable ? coverage.denominator ?? null : null
  };
}

function toQuality(summary: ApiDataQualitySummary | undefined): DashboardQualityVm {
  if (!summary?.available) {
    return { available: false, open: null, inReview: null, validated: null, corrected: null };
  }
  return {
    available: true,
    open: summary.open ?? null,
    inReview: summary.inReview ?? null,
    validated: summary.validated ?? null,
    corrected: summary.corrected ?? null
  };
}

export function canSeeDashboardMetric(
  id: DashboardMetricCard['id'],
  auth: Pick<AuthService, 'canConsult'>
): boolean {
  if (id === 'findings') {
    return auth.canConsult('DATA_QUALITY');
  }
  return auth.canConsult('PEOPLE');
}

function logDashboardError(error: unknown): void {
  const http = error instanceof HttpErrorResponse ? error : null;
  console.error('[OPENSCIENCE] Dashboard request failed', {
    url: `${environment.apiBaseUrl}/dashboard`,
    status: http?.status ?? 'unknown',
    statusText: http?.statusText,
    message: http?.message ?? String(error)
  });
}
