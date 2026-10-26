/**
 * Contratos de servicios de dominio.
 * Los componentes inyectan estas clases abstractas.
 * Hoy el Dashboard, Personas y Producción científica usan API real contra OpenLiberty.
 * Integraciones y Auditoría usan API real aunque useMockData sea true.
 * El resto de módulos permanece en Mock* mientras environment.useMockData sea true.
 */
import { Observable } from 'rxjs';
import {
  AuditChange,
  AuditEvent,
  AuditQuery,
  AuditSyncError,
  DataQualityIssue,
  DataQualityIssueStatus,
  DataQualityQuery,
  InstitutionalIndicator,
  IntegrationStatus,
  OperationalMetric,
  PagedResult,
  Researcher,
  ResearcherDetail,
  ResearcherQuery,
  ResearchOutput,
  ResearchOutputQuery
} from '../models/openscience.models';

export abstract class ResearcherService {
  abstract list(query: ResearcherQuery): Observable<PagedResult<Researcher>>;
  abstract getById(id: number): Observable<ResearcherDetail>;
}

export abstract class ResearchOutputService {
  abstract list(query: ResearchOutputQuery): Observable<PagedResult<ResearchOutput>>;
}

export abstract class DataQualityService {
  abstract list(query: DataQualityQuery): Observable<PagedResult<DataQualityIssue>>;
  abstract getById(id: string): Observable<DataQualityIssue>;
  abstract counts(): Observable<Record<DataQualityIssueStatus, number>>;
  abstract updateStatus(id: string, status: DataQualityIssueStatus): Observable<DataQualityIssue>;
}

export abstract class MetricsService {
  abstract operationalMetrics(): Observable<OperationalMetric[]>;
  abstract lastUpdated(): Observable<string>;
}

export abstract class IndicatorService {
  abstract list(): Observable<InstitutionalIndicator[]>;
}

export abstract class IntegrationService {
  abstract list(): Observable<IntegrationStatus[]>;
}

export abstract class AuditService {
  abstract list(query: AuditQuery): Observable<PagedResult<AuditEvent>>;
  abstract getById(id: string): Observable<AuditEvent>;
  abstract changes(id: string, page?: number, pageSize?: number): Observable<PagedResult<AuditChange>>;
  abstract errors(id: string, page?: number, pageSize?: number): Observable<PagedResult<AuditSyncError>>;
}
