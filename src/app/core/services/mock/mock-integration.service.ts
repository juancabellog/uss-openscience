import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { MOCK_INTEGRATIONS } from '../../mock-data/openscience.mock';
import { mockOf } from '../../mock-data/mock-rx';
import { IntegrationStatus } from '../../models/openscience.models';
import { IntegrationService } from '../platform-services';

@Injectable()
export class MockIntegrationService extends IntegrationService {
  override list(): Observable<IntegrationStatus[]> {
    return mockOf(MOCK_INTEGRATIONS);
  }
}
