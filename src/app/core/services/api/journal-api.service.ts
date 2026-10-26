import { HttpClient, HttpHeaders, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map, of, tap } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  JournalDetail,
  JournalFilters,
  JournalIssnItem,
  JournalIssns,
  JournalItem,
  JournalMetrics,
  JournalQuery,
  JournalStats,
  PagedResult
} from '../../models/openscience.models';
import { ApiPage } from './api.types';

interface ApiJournalSummary {
  id: number;
  pureId?: number | null;
  uuid?: string | null;
  title?: string | null;
  issns?: string[] | null;
  issnCount?: number | null;
  publicationCount?: number | null;
  scopusId?: string | null;
  type?: string | null;
  typeEs?: string | null;
  typeEn?: string | null;
  workflowStep?: string | null;
  workflowEs?: string | null;
  workflowEn?: string | null;
  activeInPure?: boolean | null;
  createdBy?: string | null;
  modifiedBy?: string | null;
  citeScore?: number | null;
  citeScoreYear?: number | null;
  quartilesCompact?: string | null;
  quartileYear?: number | null;
  quartileCount?: number | null;
}

interface ApiJournalDetail extends ApiJournalSummary {
  publisherUuid?: string | null;
  publisherSystemName?: string | null;
  createdBy?: string | null;
  createdDatePure?: string | null;
  modifiedBy?: string | null;
  modifiedDatePure?: string | null;
  version?: string | null;
  systemName?: string | null;
  titles?: Array<{ pureId?: number | null; title?: string | null }> | null;
  identifiers?: Array<{
    pureId?: number | null;
    typeDiscriminator?: string | null;
    idSource?: string | null;
    value?: string | null;
    typeUri?: string | null;
    typeEn?: string | null;
    typeEs?: string | null;
  }> | null;
  keywordGroups?: Array<{
    pureId?: number | null;
    typeDiscriminator?: string | null;
    logicalName?: string | null;
    nameEn?: string | null;
    nameEs?: string | null;
    classifications?: Array<{ uri?: string | null; termEn?: string | null; termEs?: string | null }> | null;
  }> | null;
}

interface ApiJournalFilters {
  types?: Array<{ uri?: string | null; typeEn?: string | null; typeEs?: string | null }> | null;
  workflows?: Array<{ step?: string | null; labelEn?: string | null; labelEs?: string | null }> | null;
}

interface ApiJournalStats {
  total?: number;
  withoutIssn?: number;
  oneIssn?: number;
  twoOrMoreIssn?: number;
  withScopusId?: number;
  withoutPublications?: number;
  unresolvedResearchOutputRefs?: number;
  withCiteScore?: number;
  withQuartile?: number;
}

interface ApiJournalIssns {
  journalId?: number;
  journalPureId?: number | null;
  journalUuid?: string | null;
  count?: number;
  items?: Array<{ id?: number | null; pureId?: number | null; issn?: string | null }> | null;
}

@Injectable({ providedIn: 'root' })
export class JournalApiService {
  private readonly http = inject(HttpClient);
  private readonly issnCache = new Map<number, JournalIssns>();

  list(query: JournalQuery): Observable<PagedResult<JournalItem>> {
    const page = Math.max((query.page ?? 1) - 1, 0);
    const size = query.pageSize ?? 25;
    const params = journalFilterParams(query).set('page', String(page)).set('size', String(size));
    return this.http
      .get<ApiPage<ApiJournalSummary>>(`${environment.apiBaseUrl}/journals`, { params })
      .pipe(map(mapJournalsPage));
  }

  getById(id: number): Observable<JournalDetail> {
    return this.http
      .get<ApiJournalDetail>(`${environment.apiBaseUrl}/journals/${id}`)
      .pipe(map(mapJournalDetail));
  }

  filters(): Observable<JournalFilters> {
    return this.http.get<ApiJournalFilters>(`${environment.apiBaseUrl}/journals/filters`).pipe(
      map((dto) => ({
        types: (dto.types ?? [])
          .filter((item) => item.uri)
          .map((item) => ({
            uri: item.uri as string,
            typeEn: item.typeEn ?? null,
            typeEs: item.typeEs ?? null
          })),
        workflows: (dto.workflows ?? [])
          .filter((item) => item.step)
          .map((item) => ({
            step: item.step as string,
            labelEn: item.labelEn ?? null,
            labelEs: item.labelEs ?? null
          }))
      }))
    );
  }

  stats(query?: JournalQuery): Observable<JournalStats> {
    const params = query
      ? journalFilterParams({ ...query, sort: undefined, direction: undefined })
      : undefined;
    return this.http.get<ApiJournalStats>(`${environment.apiBaseUrl}/journals/stats`, { params }).pipe(
      map((dto) => ({
        total: dto.total ?? 0,
        withoutIssn: dto.withoutIssn ?? 0,
        oneIssn: dto.oneIssn ?? 0,
        twoOrMoreIssn: dto.twoOrMoreIssn ?? 0,
        withScopusId: dto.withScopusId ?? 0,
        withoutPublications: dto.withoutPublications ?? 0,
        unresolvedResearchOutputRefs: dto.unresolvedResearchOutputRefs ?? 0,
        withCiteScore: dto.withCiteScore ?? 0,
        withQuartile: dto.withQuartile ?? 0
      }))
    );
  }

  getMetrics(id: number): Observable<JournalMetrics> {
    return this.http.get<JournalMetrics>(`${environment.apiBaseUrl}/journals/${id}/metrics`);
  }

  getIssns(id: number): Observable<JournalIssns> {
    const cached = this.issnCache.get(id);
    if (cached) {
      return of(cached);
    }
    return this.http.get<ApiJournalIssns>(`${environment.apiBaseUrl}/journals/${id}/issns`).pipe(
      map(mapIssns),
      tap((result) => this.issnCache.set(id, result))
    );
  }

  export(query: JournalQuery): Observable<HttpResponse<Blob>> {
    return this.http.get(`${environment.apiBaseUrl}/journals/export.xlsx`, {
      params: journalFilterParams(query),
      headers: new HttpHeaders({
        Accept: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
      }),
      responseType: 'blob',
      observe: 'response'
    });
  }
}

function mapJournalsPage(page: ApiPage<ApiJournalSummary>): PagedResult<JournalItem> {
  return {
    items: (page.items ?? []).map(mapJournal),
    total: page.totalElements ?? 0,
    page: (page.page ?? 0) + 1,
    pageSize: page.size ?? 0
  };
}

function mapJournal(dto: ApiJournalSummary): JournalItem {
  const issns = dto.issns ?? [];
  return {
    id: dto.id,
    pureId: dto.pureId ?? null,
    uuid: dto.uuid ?? null,
    title: dto.title ?? null,
    issns,
    issnCount: dto.issnCount ?? issns.length,
    publicationCount: dto.publicationCount ?? 0,
    scopusId: dto.scopusId ?? null,
    type: dto.type ?? null,
    typeEs: dto.typeEs ?? null,
    typeEn: dto.typeEn ?? null,
    workflowStep: dto.workflowStep ?? null,
    workflowEs: dto.workflowEs ?? null,
    workflowEn: dto.workflowEn ?? null,
    activeInPure: dto.activeInPure ?? null,
    createdBy: dto.createdBy ?? null,
    modifiedBy: dto.modifiedBy ?? null,
    citeScore: dto.citeScore ?? null,
    citeScoreYear: dto.citeScoreYear ?? null,
    quartilesCompact: dto.quartilesCompact ?? null,
    quartileYear: dto.quartileYear ?? null,
    quartileCount: dto.quartileCount ?? null
  };
}

function mapJournalDetail(dto: ApiJournalDetail): JournalDetail {
  return {
    ...mapJournal(dto),
    publisherUuid: dto.publisherUuid ?? null,
    publisherSystemName: dto.publisherSystemName ?? null,
    createdBy: dto.createdBy ?? null,
    createdDatePure: dto.createdDatePure ?? null,
    modifiedBy: dto.modifiedBy ?? null,
    modifiedDatePure: dto.modifiedDatePure ?? null,
    version: dto.version ?? null,
    systemName: dto.systemName ?? null,
    titles: (dto.titles ?? []).map((item) => ({
      pureId: item.pureId ?? null,
      title: item.title ?? null
    })),
    identifiers: (dto.identifiers ?? []).map((item) => ({
      pureId: item.pureId ?? null,
      typeDiscriminator: item.typeDiscriminator ?? null,
      idSource: item.idSource ?? null,
      value: item.value ?? null,
      typeUri: item.typeUri ?? null,
      typeEn: item.typeEn ?? null,
      typeEs: item.typeEs ?? null
    })),
    keywordGroups: (dto.keywordGroups ?? []).map((item) => ({
      pureId: item.pureId ?? null,
      typeDiscriminator: item.typeDiscriminator ?? null,
      logicalName: item.logicalName ?? null,
      nameEn: item.nameEn ?? null,
      nameEs: item.nameEs ?? null,
      classifications: (item.classifications ?? []).map((classification) => ({
        uri: classification.uri ?? null,
        termEn: classification.termEn ?? null,
        termEs: classification.termEs ?? null
      }))
    }))
  };
}

function mapIssns(dto: ApiJournalIssns): JournalIssns {
  const items: JournalIssnItem[] = (dto.items ?? []).map((item) => ({
    id: item.id ?? null,
    pureId: item.pureId ?? null,
    issn: item.issn ?? null
  }));
  return {
    journalId: dto.journalId ?? 0,
    journalPureId: dto.journalPureId ?? null,
    journalUuid: dto.journalUuid ?? null,
    count: dto.count ?? items.length,
    items
  };
}

export function journalFilterParams(query: JournalQuery): HttpParams {
  let params = new HttpParams();
  const search = query.search?.trim();
  if (search) {
    params = params.set('search', search);
  }
  if (query.typeUri) {
    params = params.set('typeUri', query.typeUri);
  }
  if (query.workflowStep) {
    params = params.set('workflowStep', query.workflowStep);
  }
  if (query.issnCount) {
    params = params.set('issnCount', query.issnCount);
  }
  if (query.publications) {
    params = params.set('publications', query.publications);
  }
  if (query.citescore) {
    params = params.set('citescore', query.citescore);
  }
  if (query.quartile) {
    params = params.set('quartile', query.quartile);
  }
  if (query.sort) {
    params = params.set('sort', query.sort);
  }
  if (query.direction) {
    params = params.set('direction', query.direction);
  }
  return params;
}
