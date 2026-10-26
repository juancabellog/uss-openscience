import { HttpClient, HttpHeaders, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  ProjectDetail,
  ProjectFilters,
  ProjectItem,
  ProjectQuery,
  ProjectStats,
  PagedResult
} from '../../models/openscience.models';
import { SyncRun } from './sync-api.service';
import { ApiPage } from './api.types';

interface ApiProjectSummary {
  id: number;
  pureId?: number | null;
  uuid?: string | null;
  title?: string | null;
  titleEs?: string | null;
  titleEn?: string | null;
  acronym?: string | null;
  projectId?: string | null;
  funderReference?: string | null;
  type?: string | null;
  typeEs?: string | null;
  typeEn?: string | null;
  periodStartDate?: string | null;
  periodEndDate?: string | null;
  principalInvestigatorName?: string | null;
  principalInvestigatorPersonId?: number | null;
  principalInvestigatorPersonUuid?: string | null;
  managingOrganizationUuid?: string | null;
  managingOrganizationId?: number | null;
  managingOrganizationName?: string | null;
  managingOrganizationNameEs?: string | null;
  managingOrganizationNameEn?: string | null;
  managingOrganizationResolved?: boolean | null;
  workflowStep?: string | null;
  workflowEs?: string | null;
  workflowEn?: string | null;
  visibilityKey?: string | null;
  visibilityEs?: string | null;
  visibilityEn?: string | null;
  activeInPure?: boolean | null;
  portalUrl?: string | null;
}

interface ApiProjectOrganization {
  organizationUuid?: string | null;
  organizationSystemName?: string | null;
  managing?: boolean | null;
  organizationId?: number | null;
  nameEs?: string | null;
  nameEn?: string | null;
  typeEs?: string | null;
  typeEn?: string | null;
  activeInPure?: boolean | null;
  resolved?: boolean | null;
}

interface ApiProjectParticipant {
  id?: number | null;
  pureId?: number | null;
  typeDiscriminator?: string | null;
  firstName?: string | null;
  lastName?: string | null;
  displayName?: string | null;
  roleUri?: string | null;
  roleTermEn?: string | null;
  roleTermEs?: string | null;
  associationHidden?: boolean | null;
  personUuid?: string | null;
  personId?: number | null;
  organizations?: ApiProjectOrganization[] | null;
}

interface ApiProjectDetail extends ApiProjectSummary {
  typeDiscriminator?: string | null;
  createdBy?: string | null;
  createdDatePure?: string | null;
  modifiedBy?: string | null;
  modifiedDatePure?: string | null;
  version?: string | null;
  typeUri?: string | null;
  workflowDescriptionEn?: string | null;
  workflowDescriptionEs?: string | null;
  systemName?: string | null;
  prettyUrlIdentifiers?: string[] | null;
  participants?: ApiProjectParticipant[] | null;
  identifiers?: Array<{
    pureId?: number | null;
    typeDiscriminator?: string | null;
    value?: string | null;
    typeUri?: string | null;
    typeEn?: string | null;
    typeEs?: string | null;
  }> | null;
  descriptions?: Array<{
    pureId?: number | null;
    typeUri?: string | null;
    typeEn?: string | null;
    typeEs?: string | null;
  }> | null;
  natureTypes?: Array<{ uri?: string | null; termEn?: string | null; termEs?: string | null }> | null;
  organizations?: ApiProjectOrganization[] | null;
}

interface ApiProjectFilters {
  types?: Array<{ uri?: string | null; typeEn?: string | null; typeEs?: string | null }> | null;
  workflows?: Array<{ step?: string | null; labelEn?: string | null; labelEs?: string | null }> | null;
  visibilities?: Array<{ key?: string | null; labelEn?: string | null; labelEs?: string | null }> | null;
  startYears?: number[] | null;
}

interface ApiProjectStats {
  total?: number;
  withProjectId?: number;
  withFunderReference?: number;
  withPrincipalInvestigator?: number;
}

@Injectable({ providedIn: 'root' })
export class ProjectApiService {
  private readonly http = inject(HttpClient);

  list(query: ProjectQuery): Observable<PagedResult<ProjectItem>> {
    const page = Math.max((query.page ?? 1) - 1, 0);
    const size = query.pageSize ?? 25;
    const params = projectFilterParams(query).set('page', String(page)).set('size', String(size));
    return this.http
      .get<ApiPage<ApiProjectSummary>>(`${environment.apiBaseUrl}/projects`, { params })
      .pipe(map(mapProjectsPage));
  }

  getById(id: string | number): Observable<ProjectDetail> {
    return this.http
      .get<ApiProjectDetail>(`${environment.apiBaseUrl}/projects/${encodeURIComponent(String(id))}`)
      .pipe(map(mapProjectDetail));
  }

  filters(): Observable<ProjectFilters> {
    return this.http.get<ApiProjectFilters>(`${environment.apiBaseUrl}/projects/filters`).pipe(
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
          })),
        visibilities: (dto.visibilities ?? [])
          .filter((item) => item.key)
          .map((item) => ({
            key: item.key as string,
            labelEn: item.labelEn ?? null,
            labelEs: item.labelEs ?? null
          })),
        startYears: dto.startYears ?? []
      }))
    );
  }

  stats(query?: ProjectQuery): Observable<ProjectStats> {
    const params = query
      ? projectFilterParams({ ...query, sort: undefined, direction: undefined })
      : undefined;
    return this.http.get<ApiProjectStats>(`${environment.apiBaseUrl}/projects/stats`, { params }).pipe(
      map((dto) => ({
        total: dto.total ?? 0,
        withProjectId: dto.withProjectId ?? 0,
        withFunderReference: dto.withFunderReference ?? 0,
        withPrincipalInvestigator: dto.withPrincipalInvestigator ?? 0
      }))
    );
  }

  history(): Observable<SyncRun[]> {
    return this.http.get<SyncRun[]>(`${environment.apiBaseUrl}/projects/history`);
  }

  export(query: ProjectQuery): Observable<HttpResponse<Blob>> {
    return this.http.get(`${environment.apiBaseUrl}/projects/export.xlsx`, {
      params: projectFilterParams(query),
      headers: new HttpHeaders({
        Accept: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
      }),
      responseType: 'blob',
      observe: 'response'
    });
  }
}

function mapProjectsPage(page: ApiPage<ApiProjectSummary>): PagedResult<ProjectItem> {
  return {
    items: (page.items ?? []).map(mapProject),
    total: page.totalElements ?? 0,
    page: (page.page ?? 0) + 1,
    pageSize: page.size ?? 0
  };
}

function mapProject(dto: ApiProjectSummary): ProjectItem {
  return {
    id: dto.id,
    pureId: dto.pureId ?? null,
    uuid: dto.uuid ?? null,
    title: dto.title ?? null,
    titleEs: dto.titleEs ?? null,
    titleEn: dto.titleEn ?? null,
    acronym: dto.acronym ?? null,
    projectId: dto.projectId ?? null,
    funderReference: dto.funderReference ?? null,
    type: dto.type ?? null,
    typeEs: dto.typeEs ?? null,
    typeEn: dto.typeEn ?? null,
    periodStartDate: dto.periodStartDate ?? null,
    periodEndDate: dto.periodEndDate ?? null,
    principalInvestigatorName: dto.principalInvestigatorName ?? null,
    principalInvestigatorPersonId: dto.principalInvestigatorPersonId ?? null,
    principalInvestigatorPersonUuid: dto.principalInvestigatorPersonUuid ?? null,
    managingOrganizationUuid: dto.managingOrganizationUuid ?? null,
    managingOrganizationId: dto.managingOrganizationId ?? null,
    managingOrganizationName: dto.managingOrganizationName ?? null,
    managingOrganizationNameEs: dto.managingOrganizationNameEs ?? null,
    managingOrganizationNameEn: dto.managingOrganizationNameEn ?? null,
    managingOrganizationResolved: dto.managingOrganizationResolved === true,
    workflowStep: dto.workflowStep ?? null,
    workflowEs: dto.workflowEs ?? null,
    workflowEn: dto.workflowEn ?? null,
    visibilityKey: dto.visibilityKey ?? null,
    visibilityEs: dto.visibilityEs ?? null,
    visibilityEn: dto.visibilityEn ?? null,
    activeInPure: dto.activeInPure ?? null,
    portalUrl: dto.portalUrl ?? null
  };
}

function mapOrganization(item: ApiProjectOrganization): ProjectDetail['organizations'][number] {
  return {
    organizationUuid: item.organizationUuid ?? null,
    organizationSystemName: item.organizationSystemName ?? null,
    managing: item.managing === true,
    organizationId: item.organizationId ?? null,
    nameEs: item.nameEs ?? null,
    nameEn: item.nameEn ?? null,
    typeEs: item.typeEs ?? null,
    typeEn: item.typeEn ?? null,
    activeInPure: item.activeInPure ?? null,
    resolved: item.resolved === true
  };
}

function mapProjectDetail(dto: ApiProjectDetail): ProjectDetail {
  return {
    ...mapProject(dto),
    typeDiscriminator: dto.typeDiscriminator ?? null,
    createdBy: dto.createdBy ?? null,
    createdDatePure: dto.createdDatePure ?? null,
    modifiedBy: dto.modifiedBy ?? null,
    modifiedDatePure: dto.modifiedDatePure ?? null,
    version: dto.version ?? null,
    typeUri: dto.typeUri ?? null,
    workflowDescriptionEn: dto.workflowDescriptionEn ?? null,
    workflowDescriptionEs: dto.workflowDescriptionEs ?? null,
    systemName: dto.systemName ?? null,
    prettyUrlIdentifiers: dto.prettyUrlIdentifiers ?? [],
    participants: (dto.participants ?? []).map((item) => ({
      id: item.id ?? null,
      pureId: item.pureId ?? null,
      typeDiscriminator: item.typeDiscriminator ?? null,
      firstName: item.firstName ?? null,
      lastName: item.lastName ?? null,
      displayName: item.displayName ?? null,
      roleUri: item.roleUri ?? null,
      roleTermEn: item.roleTermEn ?? null,
      roleTermEs: item.roleTermEs ?? null,
      associationHidden: item.associationHidden ?? null,
      personUuid: item.personUuid ?? null,
      personId: item.personId ?? null,
      organizations: (item.organizations ?? []).map(mapOrganization)
    })),
    identifiers: (dto.identifiers ?? []).map((item) => ({
      pureId: item.pureId ?? null,
      typeDiscriminator: item.typeDiscriminator ?? null,
      value: item.value ?? null,
      typeUri: item.typeUri ?? null,
      typeEn: item.typeEn ?? null,
      typeEs: item.typeEs ?? null
    })),
    descriptions: (dto.descriptions ?? []).map((item) => ({
      pureId: item.pureId ?? null,
      typeUri: item.typeUri ?? null,
      typeEn: item.typeEn ?? null,
      typeEs: item.typeEs ?? null
    })),
    natureTypes: (dto.natureTypes ?? []).map((item) => ({
      uri: item.uri ?? null,
      termEn: item.termEn ?? null,
      termEs: item.termEs ?? null
    })),
    organizations: (dto.organizations ?? []).map(mapOrganization)
  };
}

export function projectFilterParams(query: ProjectQuery): HttpParams {
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
  if (query.visibilityKey) {
    params = params.set('visibilityKey', query.visibilityKey);
  }
  if (query.presence) {
    params = params.set('presence', query.presence);
  }
  if (query.startYear != null) {
    params = params.set('startYear', String(query.startYear));
  }
  if (query.sort) {
    params = params.set('sort', query.sort);
  }
  if (query.direction) {
    params = params.set('direction', query.direction);
  }
  return params;
}
