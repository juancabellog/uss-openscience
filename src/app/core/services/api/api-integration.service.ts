import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { IntegrationStatus } from '../../models/openscience.models';
import { IntegrationService } from '../platform-services';
import { mapIntegrations } from './api-mappers';
import { ApiIntegrationStatus } from './api.types';

@Injectable()
export class ApiIntegrationService extends IntegrationService {
  private readonly http = inject(HttpClient);

  override list(): Observable<IntegrationStatus[]> {
    return this.http
      .get<ApiIntegrationStatus[]>(`${environment.apiBaseUrl}/integrations`)
      .pipe(map(mapIntegrations));
  }
}
