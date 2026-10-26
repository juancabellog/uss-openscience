import { Provider } from '@angular/core';
import { ApiIntegrationService } from './api/api-integration.service';
import { ApiMetricsService } from './api/api-metrics.service';
import { ApiResearcherService } from './api/api-researcher.service';
import { MOCK_API_PROVIDERS } from './mock-api.providers';
import { ApiAuditService } from './api/api-audit.service';
import { MockDataQualityService } from './mock/mock-data-quality.service';
import { MockIndicatorService } from './mock/mock-indicator.service';
import { MockResearchOutputService } from './mock/mock-research-output.service';
import {
  AuditService,
  DataQualityService,
  IndicatorService,
  IntegrationService,
  MetricsService,
  ResearcherService,
  ResearchOutputService
} from './platform-services';

/**
 * Conexión real a OpenLiberty para Dashboard, Integraciones y Auditoría.
 * Personas, Producción científica, Integraciones y Auditoría usan API real aunque useMockData sea true.
 * El resto permanece en mock hasta que existan tablas y cálculos.
 */
export const REAL_API_PROVIDERS: Provider[] = [
  { provide: ResearcherService, useClass: ApiResearcherService },
  { provide: MetricsService, useClass: ApiMetricsService },
  { provide: IntegrationService, useClass: ApiIntegrationService },
  { provide: ResearchOutputService, useClass: MockResearchOutputService },
  { provide: DataQualityService, useClass: MockDataQualityService },
  { provide: IndicatorService, useClass: MockIndicatorService },
  { provide: AuditService, useClass: ApiAuditService }
];

export function platformApiProviders(useMockData: boolean): Provider[] {
  return useMockData ? MOCK_API_PROVIDERS : REAL_API_PROVIDERS;
}
