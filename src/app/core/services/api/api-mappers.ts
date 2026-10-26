import {
  DataQualityStatus,
  IntegrationHealth,
  IntegrationStatus,
  OperationalMetric,
  PagedResult,
  Researcher,
  ResearcherDetail,
  ResearchOutputSummary
} from '../../models/openscience.models';
import {
  ApiDashboard,
  ApiIntegrationStatus,
  ApiMetric,
  ApiPage,
  ApiResearcherDetail,
  ApiResearcherSummary,
  ApiResearchOutputSummary
} from './api.types';

export function mapDashboardMetrics(dashboard: ApiDashboard): OperationalMetric[] {
  return [
    mapMetricCard('persons', dashboard.metrics.registeredPersons),
    mapMetricCard('researchers', dashboard.metrics.researchers),
    mapMetricCard('orcid', dashboard.metrics.personsWithOrcid),
    mapMetricCard('researchersOrcid', dashboard.metrics.researchersWithOrcid),
    mapMetricCard('scopus', dashboard.metrics.personsWithScopusId),
    mapMetricCard('findings', dashboard.metrics.pendingDataQualityIssues)
  ];
}

function mapMetricCard(id: string, metric: ApiMetric | undefined): OperationalMetric {
  const available = metric?.available === true && metric.value != null;
  return {
    id,
    label: id,
    value: available ? String(metric.value) : '—',
    hint: metric?.unavailableReason ?? '',
    demo: false
  };
}

export function mapResearchersPage(page: ApiPage<ApiResearcherSummary>): PagedResult<Researcher> {
  return {
    items: (page.items ?? []).map(mapResearcher),
    total: page.totalElements ?? 0,
    page: (page.page ?? 0) + 1,
    pageSize: page.size ?? 0
  };
}

export function mapResearcher(dto: ApiResearcherSummary): Researcher {
  return {
    id: dto.id,
    uuid: dto.uuid ?? '',
    firstName: dto.firstName ?? '',
    lastName: dto.lastName ?? '',
    email: dto.email ?? '',
    position: dto.position ?? undefined,
    faculty: dto.faculty ?? undefined,
    orcid: dto.orcid ?? undefined,
    orcidVerified: dto.orcidAuthenticated ?? undefined,
    scopusIds: dto.scopusIds ?? [],
    scopusAffiliations: [],
    publicationCount: dto.publicationCount ?? null,
    dataQualityStatus: mapQualityStatus(dto.dataQualityStatus)
  };
}

export function mapResearcherDetail(dto: ApiResearcherDetail): ResearcherDetail {
  return {
    ...mapResearcher(dto),
    primaryId: dto.primaryId ?? undefined,
    publications: (dto.publications ?? []).map(mapOutputSummary),
    qualityIssueIds: [],
    auditEntryIds: []
  };
}

function mapOutputSummary(dto: ApiResearchOutputSummary): ResearchOutputSummary {
  return {
    id: String(dto.id),
    title: dto.title ?? '',
    year: dto.year ?? null,
    type: dto.type ?? '',
    doi: dto.doi ?? undefined,
    journal: dto.journal ?? undefined,
    contributorCount: dto.contributorCount ?? null
  };
}

function mapQualityStatus(value: string | null | undefined): DataQualityStatus | undefined {
  if (value === 'ok' || value === 'review' || value === 'pending') {
    return value;
  }
  return undefined;
}

export function mapIntegrations(items: ApiIntegrationStatus[]): IntegrationStatus[] {
  return items.map((item) => ({
    id: item.code.toLowerCase(),
    name: item.code,
    description: item.code,
    health: mapIntegrationHealth(item.status),
    healthReason: item.statusReason ?? null,
    lastSync: item.lastSuccessfulExecution ?? undefined,
    lastRun: item.lastExecution ?? undefined,
    lastSyncMode: item.lastSyncMode ?? null,
    lastFullSyncAt: item.lastFullSyncAt ?? null,
    lastSuccessfulFullSyncAt: item.lastSuccessfulFullSyncAt ?? null,
    supportedSyncModes: item.supportedSyncModes ?? null,
    availableSyncModes: item.availableSyncModes ?? null,
    hasValidBaseline: item.hasValidBaseline ?? null,
    recommendedSyncMode: item.recommendedSyncMode ?? null,
    processedCount: item.processed ?? undefined,
    errorCount: item.errors ?? undefined,
    entities: (item.entities ?? []).map((row) => ({
      entity: row.entity,
      latestExecution: row.latestExecution ?? null
    }))
  }));
}

function mapIntegrationHealth(status: string): IntegrationHealth {
  switch (status) {
    case 'OPERATIONAL':
      return 'operational';
    case 'WARNING':
      return 'warning';
    case 'ERROR':
      return 'error';
    case 'DISABLED':
      return 'disabled';
    default:
      return 'unconfigured';
  }
}
