import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map, shareReplay } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { OperationalMetric } from '../../models/openscience.models';
import { MetricsService } from '../platform-services';
import { mapDashboardMetrics } from './api-mappers';
import { ApiDashboard } from './api.types';

@Injectable()
export class ApiMetricsService extends MetricsService {
  private readonly http = inject(HttpClient);
  private readonly dashboard$ = this.http
    .get<ApiDashboard>(`${environment.apiBaseUrl}/dashboard`)
    .pipe(shareReplay(1));

  override operationalMetrics(): Observable<OperationalMetric[]> {
    return this.dashboard$.pipe(map(mapDashboardMetrics));
  }

  override lastUpdated(): Observable<string> {
    return this.dashboard$.pipe(map((dashboard) => dashboard.lastUpdated));
  }
}

