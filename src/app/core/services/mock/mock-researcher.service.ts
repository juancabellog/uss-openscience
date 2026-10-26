import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { MOCK_OUTPUTS, MOCK_RESEARCHERS } from '../../mock-data/openscience.mock';
import { includesText, mockNotFound, mockOf, paginate } from '../../mock-data/mock-rx';
import {
  PagedResult,
  Researcher,
  ResearcherDetail,
  ResearcherQuery,
  ResearchOutputSummary
} from '../../models/openscience.models';
import { ResearcherService } from '../platform-services';

@Injectable()
export class MockResearcherService extends ResearcherService {
  override list(query: ResearcherQuery): Observable<PagedResult<Researcher>> {
    const search = query.search ?? '';
    const faculty = query.faculty ?? 'all';
    const status = query.status ?? 'all';
    const filtered = MOCK_RESEARCHERS.filter((item) => {
      const haystack = `${item.firstName} ${item.lastName} ${item.orcid ?? ''} ${item.scopusIds.join(' ')}`;
      const matchesSearch = !search || includesText(haystack, search);
      const matchesFaculty = faculty === 'all' || item.faculty === faculty;
      const matchesStatus = status === 'all' || item.dataQualityStatus === status;
      return matchesSearch && matchesFaculty && matchesStatus;
    });
    return mockOf(paginate(filtered, query.page, query.pageSize));
  }

  override getById(id: number): Observable<ResearcherDetail> {
    const researcher = MOCK_RESEARCHERS.find((item) => item.id === id);
    if (!researcher) {
      return mockNotFound(`Investigador ${id} no encontrado`);
    }
    const publications: ResearchOutputSummary[] = MOCK_OUTPUTS.filter((output) =>
      output.contributors.some((name) =>
        name.toLowerCase().includes(researcher.lastName.split(' ')[0].toLowerCase())
      )
    );
    return mockOf({
      ...researcher,
      publications,
      qualityIssueIds: [`person-${id}`],
      auditEntryIds: []
    });
  }
}
