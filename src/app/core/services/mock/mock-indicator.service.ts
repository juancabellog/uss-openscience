import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { MOCK_INDICATORS } from '../../mock-data/openscience.mock';
import { mockOf } from '../../mock-data/mock-rx';
import { InstitutionalIndicator } from '../../models/openscience.models';
import { IndicatorService } from '../platform-services';

@Injectable()
export class MockIndicatorService extends IndicatorService {
  override list(): Observable<InstitutionalIndicator[]> {
    return mockOf(MOCK_INDICATORS);
  }
}
