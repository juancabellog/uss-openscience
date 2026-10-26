import { Injectable, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { MOCK_ISSUES } from '../../mock-data/openscience.mock';
import { includesText, mockNotFound, mockOf, paginate } from '../../mock-data/mock-rx';
import {
  DataQualityIssue,
  DataQualityIssueStatus,
  DataQualityQuery,
  PagedResult
} from '../../models/openscience.models';
import { DataQualityService } from '../platform-services';

@Injectable()
export class MockDataQualityService extends DataQualityService {
  private readonly issues = signal<DataQualityIssue[]>(MOCK_ISSUES.map((item) => ({ ...item })));

  override list(query: DataQualityQuery): Observable<PagedResult<DataQualityIssue>> {
    const search = query.search ?? '';
    const status = query.status ?? 'all';
    const filtered = this.issues().filter((item) => {
      const haystack = `${item.code} ${item.rule} ${item.entityLabel} ${item.description}`;
      return (!search || includesText(haystack, search)) && (status === 'all' || item.status === status);
    });
    return mockOf(paginate(filtered, query.page, query.pageSize));
  }

  override getById(id: string): Observable<DataQualityIssue> {
    const issue = this.issues().find((item) => item.id === id);
    return issue ? mockOf(issue) : mockNotFound(`Hallazgo ${id} no encontrado`);
  }

  override counts(): Observable<Record<DataQualityIssueStatus, number>> {
    const current = this.issues();
    const counts: Record<DataQualityIssueStatus, number> = {
      open: 0,
      in_review: 0,
      validated: 0,
      corrected: 0,
      dismissed: 0
    };
    for (const issue of current) {
      counts[issue.status] += 1;
    }
    return mockOf(counts);
  }

  override updateStatus(id: string, status: DataQualityIssueStatus): Observable<DataQualityIssue> {
    const current = this.issues();
    const index = current.findIndex((item) => item.id === id);
    if (index < 0) {
      return mockNotFound(`Hallazgo ${id} no encontrado`);
    }
    const updated = { ...current[index], status };
    this.issues.update((items) => items.map((item, i) => (i === index ? updated : item)));
    return mockOf(updated);
  }
}
