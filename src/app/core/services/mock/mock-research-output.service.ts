import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { MOCK_OUTPUTS } from '../../mock-data/openscience.mock';
import { includesText, mockOf, paginate } from '../../mock-data/mock-rx';
import { PagedResult, ResearchOutput, ResearchOutputQuery } from '../../models/openscience.models';
import { ResearchOutputService } from '../platform-services';

@Injectable()
export class MockResearchOutputService extends ResearchOutputService {
  override list(query: ResearchOutputQuery): Observable<PagedResult<ResearchOutput>> {
    const search = query.search ?? '';
    const type = query.type ?? 'all';
    const status = query.status ?? 'all';
    const filtered = MOCK_OUTPUTS.filter((item) => {
      const haystack = `${item.title} ${item.doi ?? ''} ${item.journal ?? ''}`;
      return (
        (!search || includesText(haystack, search)) &&
        (type === 'all' || item.type === type) &&
        (status === 'all' || item.metadataStatus === status)
      );
    });
    return mockOf(paginate(filtered, query.page, query.pageSize));
  }
}
