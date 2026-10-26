import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { MOCK_LAST_UPDATED, MOCK_OPERATIONAL_METRICS } from '../../mock-data/openscience.mock';
import { mockOf } from '../../mock-data/mock-rx';
import { OperationalMetric } from '../../models/openscience.models';
import { MetricsService } from '../platform-services';

@Injectable()
export class MockMetricsService extends MetricsService {
  override operationalMetrics(): Observable<OperationalMetric[]> {
    return mockOf(MOCK_OPERATIONAL_METRICS);
  }

  override lastUpdated(): Observable<string> {
    return mockOf(MOCK_LAST_UPDATED);
  }
}
