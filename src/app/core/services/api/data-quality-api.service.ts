import { HttpClient, HttpHeaders, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  DataQualityApplyResult,
  DataQualityExecution,
  DataQualityFinding,
  DataQualityFindingQuery,
  DataQualityRule,
  DataQualityStats,
  PagedResult
} from '../../models/openscience.models';
import {
  ApiDataQualityApplyResult,
  ApiDataQualityExecution,
  ApiDataQualityFinding,
  ApiDataQualityRule,
  ApiDataQualityStats,
  ApiPage
} from './api.types';

@Injectable({ providedIn: 'root' })
export class DataQualityApiService {
  private readonly http = inject(HttpClient);

  stats(query: DataQualityFindingQuery): Observable<DataQualityStats> {
    return this.http
      .get<ApiDataQualityStats>(`${environment.apiBaseUrl}/data-quality/stats`, {
        params: filterParams(query)
      })
      .pipe(map(mapStats));
  }

  list(query: DataQualityFindingQuery): Observable<PagedResult<DataQualityFinding>> {
    const page = Math.max((query.page ?? 1) - 1, 0);
    const size = query.pageSize ?? 25;
    const params = filterParams(query).set('page', String(page)).set('size', String(size));
    return this.http
      .get<ApiPage<ApiDataQualityFinding>>(`${environment.apiBaseUrl}/data-quality`, { params })
      .pipe(map(mapFindingPage));
  }

  export(query: DataQualityFindingQuery): Observable<HttpResponse<Blob>> {
    return this.http.get(`${environment.apiBaseUrl}/data-quality/export.xlsx`, {
      params: filterParams(query),
      headers: new HttpHeaders({ Accept: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }),
      responseType: 'blob',
      observe: 'response'
    });
  }

  rules(): Observable<DataQualityRule[]> {
    return this.http
      .get<ApiDataQualityRule[]>(`${environment.apiBaseUrl}/data-quality/rules`)
      .pipe(map((items) => (items ?? []).map(mapRule)));
  }

  history(): Observable<DataQualityExecution[]> {
    return this.http
      .get<ApiDataQualityExecution[]>(`${environment.apiBaseUrl}/data-quality/history`)
      .pipe(map((items) => (items ?? []).map(mapExecution)));
  }

  latest(): Observable<DataQualityExecution | null> {
    return this.http
      .get<ApiDataQualityExecution>(`${environment.apiBaseUrl}/data-quality/executions/latest`, {
        observe: 'response'
      })
      .pipe(map((response) => (response.status === 204 || !response.body ? null : mapExecution(response.body))));
  }

  execution(id: number): Observable<DataQualityExecution> {
    return this.http
      .get<ApiDataQualityExecution>(`${environment.apiBaseUrl}/data-quality/executions/${id}`)
      .pipe(map(mapExecution));
  }

  start(rule?: string): Observable<DataQualityExecution> {
    let params = new HttpParams();
    if (rule) {
      params = params.set('rule', rule);
    }
    return this.http
      .post<ApiDataQualityExecution>(`${environment.apiBaseUrl}/data-quality/executions`, {}, { params })
      .pipe(map(mapExecution));
  }

  finding(id: number): Observable<DataQualityFinding> {
    return this.http
      .get<ApiDataQualityFinding>(`${environment.apiBaseUrl}/data-quality/findings/${id}`)
      .pipe(map(mapFinding));
  }

  apply(id: number, body: { newValue?: string; applySuggestion?: boolean }): Observable<DataQualityApplyResult> {
    return this.http
      .post<ApiDataQualityApplyResult>(`${environment.apiBaseUrl}/data-quality/findings/${id}/apply`, body)
      .pipe(map(mapApplyResult));
  }

  applyBulk(findingIds: number[]): Observable<DataQualityApplyResult> {
    return this.http
      .post<ApiDataQualityApplyResult>(`${environment.apiBaseUrl}/data-quality/findings/apply-bulk`, { findingIds })
      .pipe(map(mapApplyResult));
  }

  markNotApplicable(id: number, justification: string): Observable<DataQualityFinding> {
    return this.http
      .post<ApiDataQualityFinding>(`${environment.apiBaseUrl}/data-quality/findings/${id}/not-applicable`, {
        justification
      })
      .pipe(map(mapFinding));
  }

  markInReview(id: number): Observable<DataQualityFinding> {
    return this.http
      .post<ApiDataQualityFinding>(`${environment.apiBaseUrl}/data-quality/findings/${id}/in-review`, {})
      .pipe(map(mapFinding));
  }

  retryLocal(id: number): Observable<{ status: string; message?: string | null }> {
    return this.http.post<{ status: string; message?: string | null }>(
      `${environment.apiBaseUrl}/data-quality/findings/${id}/retry-local`,
      {}
    );
  }
}

function filterParams(query: DataQualityFindingQuery): HttpParams {
  let params = new HttpParams();
  if (query.search) params = params.set('search', query.search);
  if (query.category) params = params.set('category', query.category);
  if (query.rule) params = params.set('rule', query.rule);
  if (query.entity) params = params.set('entity', query.entity);
  if (query.action) params = params.set('action', query.action);
  if (query.status) params = params.set('status', query.status);
  if (query.source) params = params.set('source', query.source);
  if (query.sort) params = params.set('sort', query.sort);
  if (query.direction) params = params.set('direction', query.direction);
  return params;
}

function mapFindingPage(page: ApiPage<ApiDataQualityFinding>): PagedResult<DataQualityFinding> {
  return {
    items: (page.items ?? []).map(mapFinding),
    total: page.totalElements ?? 0,
    page: (page.page ?? 0) + 1,
    pageSize: page.size ?? 25
  };
}

function mapFinding(dto: ApiDataQualityFinding): DataQualityFinding {
  return {
    id: dto.id,
    ruleId: dto.ruleId ?? 0,
    ruleCode: dto.ruleCode ?? '',
    ruleName: dto.ruleName ?? '',
    category: dto.category ?? '',
    entityType: dto.entityType ?? '',
    localRecordId: dto.localRecordId ?? 0,
    pureId: dto.pureId ?? null,
    recordUuid: dto.recordUuid ?? null,
    recordLabel: dto.recordLabel ?? null,
    fieldName: dto.fieldName ?? null,
    issueKind: dto.issueKind ?? '',
    correctionMode: dto.correctionMode ?? '',
    status: dto.status ?? 'OPEN',
    summary: dto.summary ?? '',
    currentValue: dto.currentValue ?? null,
    suggestedValue: dto.suggestedValue ?? null,
    evidenceJson: dto.evidenceJson ?? null,
    evidences: (dto.evidences ?? []).map((item) => ({
      id: item.id ?? null,
      source: item.source ?? null,
      sourceRecordId: item.sourceRecordId ?? null,
      sourceField: item.sourceField ?? null,
      externalValue: item.externalValue ?? null,
      normalizedExternalValue: item.normalizedExternalValue ?? null,
      comparisonResult: item.comparisonResult ?? null,
      externalLastSyncAt: item.externalLastSyncAt ?? null,
      metadataJson: item.metadataJson ?? null
    })),
    description: dto.description ?? null,
    ruleText: dto.ruleText ?? null,
    alertText: dto.alertText ?? null,
    firstDetectedAt: dto.firstDetectedAt ?? null,
    lastDetectedAt: dto.lastDetectedAt ?? null,
    resolvedAt: dto.resolvedAt ?? null,
    resolvedBy: dto.resolvedBy ?? null,
    resolutionNote: dto.resolutionNote ?? null,
    recordRoute: dto.recordRoute ?? null
  };
}

function mapRule(dto: ApiDataQualityRule): DataQualityRule {
  return {
    id: dto.id,
    code: dto.code ?? '',
    sourceFamily: dto.sourceFamily ?? 'PURE',
    entityType: dto.entityType ?? '',
    fieldName: dto.fieldName ?? null,
    name: dto.name ?? '',
    description: dto.description ?? '',
    ruleText: dto.ruleText ?? '',
    alertText: dto.alertText ?? null,
    bulkProcedure: dto.bulkProcedure ?? null,
    category: dto.category ?? '',
    correctionMode: dto.correctionMode ?? '',
    enabled: dto.enabled !== false,
    openFindingCount: dto.openFindingCount ?? 0
  };
}

function mapExecution(dto: ApiDataQualityExecution): DataQualityExecution {
  return {
    id: dto.id,
    status: dto.status ?? '',
    triggerType: dto.triggerType ?? '',
    scope: dto.scope ?? '',
    entityType: dto.entityType ?? null,
    localRecordId: dto.localRecordId ?? null,
    syncExecutionId: dto.syncExecutionId ?? null,
    startedAt: dto.startedAt ?? null,
    finishedAt: dto.finishedAt ?? null,
    totalRecords: dto.totalRecords ?? 0,
    evaluatedRecords: dto.evaluatedRecords ?? 0,
    findingCount: dto.findingCount ?? 0,
    resolvedCount: dto.resolvedCount ?? 0,
    errorCount: dto.errorCount ?? 0,
    startedBy: dto.startedBy ?? null,
    errorCode: dto.errorCode ?? null,
    errorMessage: dto.errorMessage ?? null,
    durationSeconds: dto.durationSeconds ?? null
  };
}

function mapStats(dto: ApiDataQualityStats): DataQualityStats {
  return {
    openFindings: dto.openFindings ?? 0,
    affectedRecords: dto.affectedRecords ?? 0,
    correctionAvailable: dto.correctionAvailable ?? 0,
    manualReview: dto.manualReview ?? 0,
    viewOnly: dto.viewOnly ?? 0,
    rulesWithFindings: dto.rulesWithFindings ?? 0,
    ruleCounts: (dto.ruleCounts ?? []).map((item) => ({
      code: item.code ?? '',
      name: item.name ?? '',
      sourceFamily: item.sourceFamily ?? undefined,
      count: item.count ?? 0
    }))
  };
}

function mapApplyResult(dto: ApiDataQualityApplyResult): DataQualityApplyResult {
  return {
    applied: dto.applied ?? 0,
    remoteChanged: dto.remoteChanged ?? 0,
    localFailed: dto.localFailed ?? 0,
    reviewRequired: dto.reviewRequired ?? 0,
    failed: dto.failed ?? 0,
    items: (dto.items ?? []).map((item) => ({
      findingId: item.findingId ?? 0,
      ruleCode: item.ruleCode ?? '',
      recordLabel: item.recordLabel ?? null,
      status: item.status ?? '',
      message: item.message ?? null,
      currentValue: item.currentValue ?? null,
      suggestedValue: item.suggestedValue ?? null
    }))
  };
}
