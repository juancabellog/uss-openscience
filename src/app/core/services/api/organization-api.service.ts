import { HttpClient, HttpHeaders, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  OrganizationDetail,
  OrganizationFilters,
  OrganizationItem,
  OrganizationPerson,
  OrganizationProject,
  OrganizationQuery,
  OrganizationStats,
  PagedResult
} from '../../models/openscience.models';
import { ApiPage } from './api.types';

interface ApiOrganizationSummary {
  id: number;
  pureId?: number | null;
  uuid?: string | null;
  name?: string | null;
  nameEs?: string | null;
  nameEn?: string | null;
  type?: string | null;
  typeEs?: string | null;
  typeEn?: string | null;
  typeUri?: string | null;
  parentUuid?: string | null;
  parentName?: string | null;
  parentNameEs?: string | null;
  parentNameEn?: string | null;
  activeInPure?: boolean | null;
  modifiedDatePure?: string | null;
  createdBy?: string | null;
  modifiedBy?: string | null;
}

interface ApiOrganizationDetail extends ApiOrganizationSummary {
  typeDiscriminator?: string | null;
  createdDatePure?: string | null;
  version?: string | null;
  systemName?: string | null;
  parentId?: number | null;
  parentResolved?: boolean | null;
  parentActiveInPure?: boolean | null;
}

interface ApiOrganizationFilters {
  types?: Array<{ uri?: string | null; typeEn?: string | null; typeEs?: string | null }> | null;
}

interface ApiOrganizationStats {
  total?: number;
  active?: number;
  inactive?: number;
  withParent?: number;
}

interface ApiOrganizationPerson {
  personId?: number | null;
  personPureId?: number | null;
  personUuid?: string | null;
  firstName?: string | null;
  lastName?: string | null;
  displayName?: string | null;
  jobTitle?: string | null;
  employmentType?: string | null;
  fte?: number | null;
  startDate?: string | null;
  endDate?: string | null;
  primaryAssociation?: boolean | null;
  personActiveInPure?: boolean | null;
}

interface ApiOrganizationProject {
  projectId?: number | null;
  projectPureId?: number | null;
  projectUuid?: string | null;
  title?: string | null;
  titleEs?: string | null;
  titleEn?: string | null;
  relationType?: string | null;
  projectActiveInPure?: boolean | null;
}

@Injectable({ providedIn: 'root' })
export class OrganizationApiService {
  private readonly http = inject(HttpClient);

  list(query: OrganizationQuery): Observable<PagedResult<OrganizationItem>> {
    const page = Math.max((query.page ?? 1) - 1, 0);
    const size = query.pageSize ?? 25;
    const params = organizationFilterParams(query).set('page', String(page)).set('size', String(size));
    return this.http
      .get<ApiPage<ApiOrganizationSummary>>(`${environment.apiBaseUrl}/organizations`, { params })
      .pipe(map(mapOrganizationsPage));
  }

  getById(id: number): Observable<OrganizationDetail> {
    return this.http
      .get<ApiOrganizationDetail>(`${environment.apiBaseUrl}/organizations/${id}`)
      .pipe(map(mapOrganizationDetail));
  }

  filters(): Observable<OrganizationFilters> {
    return this.http.get<ApiOrganizationFilters>(`${environment.apiBaseUrl}/organizations/filters`).pipe(
      map((dto) => ({
        types: (dto.types ?? [])
          .filter((item) => item.uri)
          .map((item) => ({
            uri: item.uri as string,
            typeEn: item.typeEn ?? null,
            typeEs: item.typeEs ?? null
          }))
      }))
    );
  }

  stats(query?: OrganizationQuery): Observable<OrganizationStats> {
    const params = query
      ? organizationFilterParams({ ...query, sort: undefined, direction: undefined })
      : undefined;
    return this.http.get<ApiOrganizationStats>(`${environment.apiBaseUrl}/organizations/stats`, { params }).pipe(
      map((dto) => ({
        total: dto.total ?? 0,
        active: dto.active ?? 0,
        inactive: dto.inactive ?? 0,
        withParent: dto.withParent ?? 0
      }))
    );
  }

  relatedPersons(id: number, page = 1, pageSize = 25): Observable<PagedResult<OrganizationPerson>> {
    const params = new HttpParams()
      .set('page', String(Math.max(page - 1, 0)))
      .set('size', String(pageSize));
    return this.http
      .get<ApiPage<ApiOrganizationPerson>>(`${environment.apiBaseUrl}/organizations/${id}/persons`, { params })
      .pipe(map(mapPersonsPage));
  }

  relatedProjects(id: number, page = 1, pageSize = 25): Observable<PagedResult<OrganizationProject>> {
    const params = new HttpParams()
      .set('page', String(Math.max(page - 1, 0)))
      .set('size', String(pageSize));
    return this.http
      .get<ApiPage<ApiOrganizationProject>>(`${environment.apiBaseUrl}/organizations/${id}/projects`, { params })
      .pipe(map(mapProjectsPage));
  }

  export(query: OrganizationQuery): Observable<HttpResponse<Blob>> {
    return this.http.get(`${environment.apiBaseUrl}/organizations/export.xlsx`, {
      params: organizationFilterParams(query),
      headers: new HttpHeaders({
        Accept: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
      }),
      responseType: 'blob',
      observe: 'response'
    });
  }
}

export function organizationFilterParams(query: OrganizationQuery): HttpParams {
  let params = new HttpParams();
  if (query.search?.trim()) {
    params = params.set('search', query.search.trim());
  }
  if (query.typeUri) {
    params = params.set('typeUri', query.typeUri);
  }
  if (query.presence) {
    params = params.set('presence', query.presence);
  }
  if (query.hasParent) {
    params = params.set('hasParent', query.hasParent);
  }
  if (query.sort) {
    params = params.set('sort', query.sort);
  }
  if (query.direction) {
    params = params.set('direction', query.direction.toUpperCase());
  }
  return params;
}

function mapOrganizationsPage(page: ApiPage<ApiOrganizationSummary>): PagedResult<OrganizationItem> {
  return {
    items: (page.items ?? []).map(mapOrganization),
    total: page.totalElements ?? 0,
    page: (page.page ?? 0) + 1,
    pageSize: page.size ?? 0
  };
}

function mapOrganization(dto: ApiOrganizationSummary): OrganizationItem {
  return {
    id: dto.id,
    pureId: dto.pureId ?? null,
    uuid: dto.uuid ?? null,
    name: dto.name ?? null,
    nameEs: dto.nameEs ?? null,
    nameEn: dto.nameEn ?? null,
    type: dto.type ?? null,
    typeEs: dto.typeEs ?? null,
    typeEn: dto.typeEn ?? null,
    typeUri: dto.typeUri ?? null,
    parentUuid: dto.parentUuid ?? null,
    parentName: dto.parentName ?? null,
    parentNameEs: dto.parentNameEs ?? null,
    parentNameEn: dto.parentNameEn ?? null,
    activeInPure: dto.activeInPure ?? null,
    modifiedDatePure: dto.modifiedDatePure ?? null,
    createdBy: dto.createdBy ?? null,
    modifiedBy: dto.modifiedBy ?? null
  };
}

function mapOrganizationDetail(dto: ApiOrganizationDetail): OrganizationDetail {
  return {
    ...mapOrganization(dto),
    typeDiscriminator: dto.typeDiscriminator ?? null,
    createdDatePure: dto.createdDatePure ?? null,
    version: dto.version ?? null,
    systemName: dto.systemName ?? null,
    parentId: dto.parentId ?? null,
    parentResolved: dto.parentResolved ?? null,
    parentActiveInPure: dto.parentActiveInPure ?? null
  };
}

function mapPersonsPage(page: ApiPage<ApiOrganizationPerson>): PagedResult<OrganizationPerson> {
  return {
    items: (page.items ?? []).map((item) => ({
      personId: item.personId ?? null,
      personPureId: item.personPureId ?? null,
      personUuid: item.personUuid ?? null,
      firstName: item.firstName ?? null,
      lastName: item.lastName ?? null,
      displayName: item.displayName ?? null,
      jobTitle: item.jobTitle ?? null,
      employmentType: item.employmentType ?? null,
      fte: item.fte ?? null,
      startDate: item.startDate ?? null,
      endDate: item.endDate ?? null,
      primaryAssociation: item.primaryAssociation ?? null,
      personActiveInPure: item.personActiveInPure ?? null
    })),
    total: page.totalElements ?? 0,
    page: (page.page ?? 0) + 1,
    pageSize: page.size ?? 0
  };
}

function mapProjectsPage(page: ApiPage<ApiOrganizationProject>): PagedResult<OrganizationProject> {
  return {
    items: (page.items ?? []).map((item) => ({
      projectId: item.projectId ?? null,
      projectPureId: item.projectPureId ?? null,
      projectUuid: item.projectUuid ?? null,
      title: item.title ?? null,
      titleEs: item.titleEs ?? null,
      titleEn: item.titleEn ?? null,
      relationType: item.relationType ?? null,
      projectActiveInPure: item.projectActiveInPure ?? null
    })),
    total: page.totalElements ?? 0,
    page: (page.page ?? 0) + 1,
    pageSize: page.size ?? 0
  };
}
