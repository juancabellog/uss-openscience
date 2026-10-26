import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  PagedResult,
  Researcher,
  ResearcherDetail,
  ResearcherQuery
} from '../../models/openscience.models';
import { ResearcherService } from '../platform-services';
import { mapResearcherDetail, mapResearchersPage } from './api-mappers';
import { ApiPage, ApiResearcherDetail, ApiResearcherSummary } from './api.types';

@Injectable()
export class ApiResearcherService extends ResearcherService {
  private readonly http = inject(HttpClient);

  override list(query: ResearcherQuery): Observable<PagedResult<Researcher>> {
    const page = Math.max((query.page ?? 1) - 1, 0);
    const size = query.pageSize ?? 25;
    let params = new HttpParams().set('page', String(page)).set('size', String(size));
    if (query.search) {
      params = params.set('search', query.search);
    }
    return this.http
      .get<ApiPage<ApiResearcherSummary>>(`${environment.apiBaseUrl}/researchers`, { params })
      .pipe(map(mapResearchersPage));
  }

  override getById(id: number): Observable<ResearcherDetail> {
    return this.http
      .get<ApiResearcherDetail>(`${environment.apiBaseUrl}/researchers/${id}`)
      .pipe(map(mapResearcherDetail));
  }
}
