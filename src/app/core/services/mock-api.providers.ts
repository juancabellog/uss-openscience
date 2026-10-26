import { Provider } from '@angular/core';
import { ApiAuditService } from './api/api-audit.service';
import { MockDataQualityService } from './mock/mock-data-quality.service';
import { MockIndicatorService } from './mock/mock-indicator.service';
import { ApiIntegrationService } from './api/api-integration.service';
import { MockMetricsService } from './mock/mock-metrics.service';
import { MockResearchOutputService } from './mock/mock-research-output.service';
import { MockResearcherService } from './mock/mock-researcher.service';
import {
  AuditService,
  DataQualityService,
  IndicatorService,
  IntegrationService,
  MetricsService,
  ResearcherService,
  ResearchOutputService
} from './platform-services';

export const MOCK_API_PROVIDERS: Provider[] = [
  { provide: ResearcherService, useClass: MockResearcherService },
  { provide: ResearchOutputService, useClass: MockResearchOutputService },
  { provide: DataQualityService, useClass: MockDataQualityService },
  { provide: MetricsService, useClass: MockMetricsService },
  { provide: IndicatorService, useClass: MockIndicatorService },
  { provide: IntegrationService, useClass: ApiIntegrationService },
  { provide: AuditService, useClass: ApiAuditService }
];
