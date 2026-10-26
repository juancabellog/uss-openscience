import { HttpClient, HttpHeaders, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map, of, tap } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  PagedResult,
  ResearchOutputDetailItem,
  ResearchOutputDoiVersions,
  ResearchOutputElectronicVersionItem,
  ResearchOutputFilters,
  ResearchOutputItem,
  ResearchOutputMetrics,
  ResearchOutputQuery,
  ResearchOutputStats,
  PureCorrectionResult
} from '../../models/openscience.models';
import {
  ApiPage,
  ApiResearchOutputDetail,
  ApiResearchOutputDoiVersions,
  ApiResearchOutputElectronicVersion,
  ApiResearchOutputFilters,
  ApiResearchOutputSummary
} from './api.types';

@Injectable({ providedIn: 'root' })
export class ResearchOutputApiService {
  private readonly http = inject(HttpClient);
  private readonly doiVersionsCache = new Map<number, ResearchOutputDoiVersions>();

  list(query: ResearchOutputQuery): Observable<PagedResult<ResearchOutputItem>> {
    const page = Math.max((query.page ?? 1) - 1, 0);
    const size = query.pageSize ?? 25;
    const params = outputFilterParams(query)
      .set('page', String(page))
      .set('size', String(size));
    return this.http
      .get<ApiPage<ApiResearchOutputSummary>>(`${environment.apiBaseUrl}/research-output`, { params })
      .pipe(map(mapOutputsPage));
  }

  export(query: ResearchOutputQuery): Observable<HttpResponse<Blob>> {
    return this.http.get(`${environment.apiBaseUrl}/research-output/export.xlsx`, {
      params: outputFilterParams(query),
      headers: excelAcceptHeaders(),
      responseType: 'blob',
      observe: 'response'
    });
  }

  getDoiVersions(id: number): Observable<ResearchOutputDoiVersions> {
    const cached = this.doiVersionsCache.get(id);
    if (cached) {
      return of(cached);
    }
    return this.http
      .get<ApiResearchOutputDoiVersions>(`${environment.apiBaseUrl}/research-output/${id}/doi-versions`)
      .pipe(
        map(mapDoiVersions),
        tap((result) => this.doiVersionsCache.set(id, result))
      );
  }

  invalidateDoiVersions(id: number): void {
    this.doiVersionsCache.delete(id);
  }

  removeDoiVersion(
    researchOutputId: number,
    versionPureId: number,
    doi: string | null
  ): Observable<PureCorrectionResult> {
    let params = new HttpParams();
    if (doi) {
      params = params.set('doi', doi);
    }
    return this.http
      .delete<PureCorrectionResult>(
        `${environment.apiBaseUrl}/research-output/${researchOutputId}/doi-versions/${versionPureId}`,
        { params }
      )
      .pipe(tap(() => this.doiVersionsCache.delete(researchOutputId)));
  }

  reconcileLocalDoiVersion(
    researchOutputId: number,
    versionPureId: number
  ): Observable<PureCorrectionResult> {
    return this.http
      .post<PureCorrectionResult>(
        `${environment.apiBaseUrl}/research-output/${researchOutputId}/doi-versions/${versionPureId}/reconcile-local`,
        {}
      )
      .pipe(tap((result) => {
        if (result.status !== 'REMOTE_SUCCESS_LOCAL_SYNC_FAILED') {
          this.doiVersionsCache.delete(researchOutputId);
        }
      }));
  }

  getMetrics(id: number): Observable<ResearchOutputMetrics> {
    return this.http.get<ResearchOutputMetrics>(`${environment.apiBaseUrl}/research-output/${id}/metrics`);
  }

  getById(id: number): Observable<ResearchOutputDetailItem> {
    return this.http
      .get<ApiResearchOutputDetail>(`${environment.apiBaseUrl}/research-output/${id}`)
      .pipe(map(mapOutputDetail));
  }

  filters(): Observable<ResearchOutputFilters> {
    return this.http
      .get<ApiResearchOutputFilters>(`${environment.apiBaseUrl}/research-output/filters`)
      .pipe(map(mapOutputFilters));
  }

  stats(query?: ResearchOutputQuery): Observable<ResearchOutputStats> {
    const params = query
      ? outputFilterParams({ ...query, sort: undefined, direction: undefined })
      : undefined;
    return this.http
      .get<ResearchOutputStats>(`${environment.apiBaseUrl}/research-output/stats`, { params })
      .pipe(
        map((dto) => ({
          total: dto.total ?? 0,
          withDoi: dto.withDoi ?? 0,
          withoutDoi: dto.withoutDoi ?? 0,
          withMultipleDoiVersions: dto.withMultipleDoiVersions ?? 0,
          withSharedDoi: dto.withSharedDoi ?? 0,
          sharedDoiValues: dto.sharedDoiValues ?? 0,
          doiElectronicVersionsWithoutDoi: dto.doiElectronicVersionsWithoutDoi ?? 0,
          withFwci: dto.withFwci ?? 0
        }))
      );
  }
}

function mapOutputsPage(page: ApiPage<ApiResearchOutputSummary>): PagedResult<ResearchOutputItem> {
  return {
    items: (page.items ?? []).map(mapOutput),
    total: page.totalElements ?? 0,
    page: (page.page ?? 0) + 1,
    pageSize: page.size ?? 0
  };
}

function mapOutput(dto: ApiResearchOutputSummary): ResearchOutputItem {
  return {
    id: dto.id,
    pureId: dto.pureId ?? null,
    uuid: dto.uuid ?? null,
    title: dto.title ?? null,
    year: dto.year ?? null,
    type: dto.type ?? null,
    doi: dto.doi ?? null,
    journal: dto.journal ?? null,
    journalUuid: dto.journalUuid ?? null,
    contributorCount: dto.contributorCount ?? null,
    doiVersionCount: dto.doiVersionCount ?? null,
    activeInPure: dto.activeInPure ?? null,
    workflowStep: dto.workflowStep ?? null,
    workflowEs: dto.workflowEs ?? null,
    workflowEn: dto.workflowEn ?? null,
    visibilityKey: dto.visibilityKey ?? null,
    visibilityEs: dto.visibilityEs ?? null,
    visibilityEn: dto.visibilityEn ?? null,
    createdBy: dto.createdBy ?? null,
    modifiedBy: dto.modifiedBy ?? null,
    fwci: dto.fwci ?? null,
    fwciYear: dto.fwciYear ?? null
  };
}

function excelAcceptHeaders(): HttpHeaders {
  return new HttpHeaders({
    Accept: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
  });
}

export function outputFilterParams(query: ResearchOutputQuery): HttpParams {
  let params = new HttpParams();
  const search = query.search?.trim();
  if (search) {
    params = params.set('search', search);
  }
  if (query.type) {
    params = params.set('type', query.type);
  }
  if (query.year != null) {
    params = params.set('year', String(query.year));
  }
  if (query.doiStatus && query.doiStatus !== 'ALL') {
    params = params.set('doiStatus', query.doiStatus);
  } else if (query.hasDoi != null) {
    params = params.set('hasDoi', String(query.hasDoi));
  }
  if (query.workflowStep) {
    params = params.set('workflow', query.workflowStep);
  }
  if (query.fwciFrom != null) {
    params = params.set('fwciFrom', String(query.fwciFrom));
  }
  if (query.fwciTo != null) {
    params = params.set('fwciTo', String(query.fwciTo));
  }
  if (query.sort) {
    params = params.set('sort', query.sort);
  }
  if (query.direction) {
    params = params.set('direction', query.direction.toUpperCase());
  }
  return params;
}

function mapOutputDetail(dto: ApiResearchOutputDetail): ResearchOutputDetailItem {
  return {
    ...mapOutput(dto),
    typeEs: dto.typeEs ?? null,
    typeEn: dto.typeEn ?? null,
    submissionYear: dto.submissionYear ?? null,
    volume: dto.volume ?? null,
    issue: dto.issue ?? null,
    pages: dto.pages ?? null,
    numberOfPages: dto.numberOfPages ?? null,
    journalDetail: dto.journalDetail
      ? {
          uuid: dto.journalDetail.uuid ?? null,
          title: dto.journalDetail.title ?? null,
          issn: dto.journalDetail.issn ?? null,
          synced: dto.journalDetail.synced ?? null
        }
      : null,
    publicationStatuses: (dto.publicationStatuses ?? []).map((item) => ({
      current: item.current ?? null,
      statusEs: item.statusEs ?? null,
      statusEn: item.statusEn ?? null,
      publicationYear: item.publicationYear ?? null,
      publicationMonth: item.publicationMonth ?? null,
      publicationDay: item.publicationDay ?? null
    })),
    identifiers: (dto.identifiers ?? []).map((item) => ({
      typeDiscriminator: item.typeDiscriminator ?? null,
      idSource: item.idSource ?? null,
      value: item.value ?? null
    })),
    electronicVersions: (dto.electronicVersions ?? []).map(mapElectronicVersion),
    contributors: (dto.contributors ?? []).map((item) => ({
      id: item.id,
      personId: item.personId ?? null,
      personUuid: item.personUuid ?? null,
      firstName: item.firstName ?? null,
      lastName: item.lastName ?? null,
      fullName: item.fullName ?? null,
      role: item.role ?? null,
      roleEs: item.roleEs ?? null,
      roleEn: item.roleEn ?? null,
      external: item.external ?? null,
      correspondingAuthor: item.correspondingAuthor ?? null,
      organizationUuids: item.organizationUuids ?? []
    })),
    keywordGroups: (dto.keywordGroups ?? []).map((group) => ({
      name: group.name ?? null,
      nameEs: group.nameEs ?? null,
      nameEn: group.nameEn ?? null,
      keywords: (group.keywords ?? []).map((keyword) => ({
        type: keyword.type ?? null,
        value: keyword.value ?? null,
        locale: keyword.locale ?? null
      }))
    })),
    links: (dto.links ?? []).map((item) => ({
      url: item.url ?? null,
      descriptionEs: item.descriptionEs ?? null,
      descriptionEn: item.descriptionEn ?? null,
      linkTypeEs: item.linkTypeEs ?? null,
      linkTypeEn: item.linkTypeEn ?? null
    })),
    externalOrganizationUuids: dto.externalOrganizationUuids ?? [],
    doiElectronicVersionCount: dto.doiElectronicVersionCount ?? null,
    distinctDoiCount: dto.distinctDoiCount ?? null
  };
}

function mapDoiVersions(dto: ApiResearchOutputDoiVersions): ResearchOutputDoiVersions {
  const items = (dto.items ?? []).map(mapElectronicVersion);
  return {
    researchOutputId: dto.researchOutputId ?? 0,
    count: dto.count ?? items.length,
    distinctCount: dto.distinctCount ?? 0,
    items
  };
}

function mapElectronicVersion(item: ApiResearchOutputElectronicVersion): ResearchOutputElectronicVersionItem {
  return {
    id: item.id ?? null,
    pureId: item.pureId ?? null,
    typeDiscriminator: item.typeDiscriminator ?? null,
    doi: item.doi ?? null,
    accessTypeEs: item.accessTypeEs ?? null,
    accessTypeEn: item.accessTypeEn ?? null,
    versionTypeEs: item.versionTypeEs ?? null,
    versionTypeEn: item.versionTypeEn ?? null
  };
}

function mapOutputFilters(dto: ApiResearchOutputFilters): ResearchOutputFilters {
  return {
    types: (dto.types ?? []).map((item) => ({
      code: item.code,
      typeEn: item.typeEn ?? null,
      typeEs: item.typeEs ?? null
    })),
    years: dto.years ?? [],
    workflows: (dto.workflows ?? [])
      .filter((item): item is { step: string; labelEn?: string | null; labelEs?: string | null } => !!item.step)
      .map((item) => ({
        step: item.step,
        labelEn: item.labelEn ?? null,
        labelEs: item.labelEs ?? null
      }))
  };
}
