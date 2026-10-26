import { HttpClient, HttpHeaders, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  IndicatorMeasure,
  OrcidAdoptionSeries,
  PagedResult,
  ResearcherIdentityCase,
  ResearcherIdentityCaseQuery,
  ResearcherIdentityIndicators,
  ResearcherIdentitySignatureDetail
} from '../../models/openscience.models';
import {
  ApiIndicatorMeasure,
  ApiOrcidAdoptionSeries,
  ApiPage,
  ApiResearcherIdentityCase,
  ApiResearcherIdentityIndicators,
  ApiResearcherIdentitySignatureDetail
} from './api.types';

@Injectable({ providedIn: 'root' })
export class ResearcherIdentityApiService {
  private readonly http = inject(HttpClient);

  summary(): Observable<ResearcherIdentityIndicators> {
    return this.http
      .get<ApiResearcherIdentityIndicators>(`${environment.apiBaseUrl}/indicators/researcher-identity`)
      .pipe(map(mapSummary));
  }

  orcidAdoption(from?: string, to?: string): Observable<OrcidAdoptionSeries> {
    let params = new HttpParams();
    if (from) {
      params = params.set('from', from);
    }
    if (to) {
      params = params.set('to', to);
    }
    return this.http
      .get<ApiOrcidAdoptionSeries>(`${environment.apiBaseUrl}/indicators/orcid-adoption`, { params })
      .pipe(map(mapOrcidAdoption));
  }

  cases(query: ResearcherIdentityCaseQuery): Observable<PagedResult<ResearcherIdentityCase>> {
    const page = Math.max((query.page ?? 1) - 1, 0);
    const size = query.pageSize ?? 25;
    let params = caseParams(query).set('page', String(page)).set('size', String(size));
    return this.http
      .get<ApiPage<ApiResearcherIdentityCase>>(`${environment.apiBaseUrl}/indicators/researcher-identity/cases`, {
        params
      })
      .pipe(map(mapCasePage));
  }

  export(query: ResearcherIdentityCaseQuery): Observable<HttpResponse<Blob>> {
    return this.http.get(`${environment.apiBaseUrl}/indicators/researcher-identity/cases/export.xlsx`, {
      params: caseParams(query),
      headers: new HttpHeaders({
        Accept: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
      }),
      responseType: 'blob',
      observe: 'response'
    });
  }

  signatureDetail(personId: number): Observable<ResearcherIdentitySignatureDetail> {
    return this.http
      .get<ApiResearcherIdentitySignatureDetail>(
        `${environment.apiBaseUrl}/indicators/researcher-identity/cases/${personId}/signatures`
      )
      .pipe(map(mapSignatureDetail));
  }
}

function caseParams(query: ResearcherIdentityCaseQuery): HttpParams {
  let params = new HttpParams().set('indicator', query.indicator);
  if (query.classification && query.classification !== 'ALL') {
    params = params.set('classification', query.classification);
  }
  const search = query.search?.trim();
  if (search) {
    params = params.set('search', search);
  }
  if (query.sort) {
    params = params.set('sort', query.sort);
  }
  if (query.direction) {
    params = params.set('direction', query.direction);
  }
  return params;
}

function mapSummary(dto: ApiResearcherIdentityIndicators): ResearcherIdentityIndicators {
  return {
    scopusCoverage: mapMeasure(dto.scopusCoverage),
    multipleScopusProfiles: mapMeasure(dto.multipleScopusProfiles),
    controlledSignature: mapMeasure(dto.controlledSignature),
    highSignatureFragmentation: mapMeasure(dto.highSignatureFragmentation),
    normalizedUssOrcidAffiliation: mapMeasure(dto.normalizedUssOrcidAffiliation),
    orcidNormalizedCount: dto.orcidNormalizedCount ?? 0,
    orcidVariantCount: dto.orcidVariantCount ?? 0,
    orcidNotFoundCount: dto.orcidNotFoundCount ?? 0,
    orcidNotEvaluableCount: dto.orcidNotEvaluableCount ?? 0
  };
}

function mapOrcidAdoption(dto: ApiOrcidAdoptionSeries): OrcidAdoptionSeries {
  return {
    metric: dto.metric ?? 'orcidAdoption',
    definition: dto.definition ?? 'CUMULATIVE_ADOPTION',
    universe: dto.universe ?? 'PERSONS_IN_TENURE',
    availableFrom: dto.availableFrom ?? null,
    from: dto.from ?? '',
    to: dto.to ?? '',
    months: (dto.months ?? []).map((month) => ({
      month: month.month ?? '',
      totalPersons: month.totalPersons ?? null,
      personsWithOrcid: month.personsWithOrcid ?? null,
      percentage: month.percentage ?? null,
      complete: month.complete === true
    }))
  };
}

function mapMeasure(dto: ApiIndicatorMeasure | undefined): IndicatorMeasure {
  return {
    numerator: dto?.numerator ?? 0,
    denominator: dto?.denominator ?? 0,
    percentage: dto?.percentage ?? null,
    universe: dto?.universe ?? null
  };
}

function mapCasePage(page: ApiPage<ApiResearcherIdentityCase>): PagedResult<ResearcherIdentityCase> {
  return {
    items: (page.items ?? []).map(mapCase),
    total: page.totalElements ?? 0,
    page: (page.page ?? 0) + 1,
    pageSize: page.size ?? 0
  };
}

function mapCase(dto: ApiResearcherIdentityCase): ResearcherIdentityCase {
  return {
    personId: dto.personId ?? 0,
    pureId: dto.pureId ?? null,
    firstName: dto.firstName ?? null,
    lastName: dto.lastName ?? null,
    fullName: dto.fullName?.trim() || [dto.lastName, dto.firstName].filter(Boolean).join(', '),
    position: dto.position ?? null,
    orcid: dto.orcid ?? null,
    scopusDistinctCount: dto.scopusDistinctCount ?? null,
    evaluatedPublications: dto.evaluatedPublications ?? null,
    distinctSignatureCount: dto.distinctSignatureCount ?? null,
    primarySignature: dto.primarySignature ?? null,
    classification: dto.classification ?? null,
    employmentOrganizationName: dto.employmentOrganizationName ?? null,
    employmentOrganizationNames: dto.employmentOrganizationNames ?? []
  };
}

function mapSignatureDetail(dto: ApiResearcherIdentitySignatureDetail): ResearcherIdentitySignatureDetail {
  return {
    personId: dto.personId ?? 0,
    fullName: dto.fullName ?? '',
    position: dto.position ?? null,
    evaluatedPublications: dto.evaluatedPublications ?? 0,
    distinctSignatureCount: dto.distinctSignatureCount ?? 0,
    variants: (dto.variants ?? []).map((item) => ({
      displaySignature: item.displaySignature ?? '',
      normalizedSignature: item.normalizedSignature ?? '',
      publicationCount: item.publicationCount ?? 0
    }))
  };
}
