import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';

export interface EventsModuleStatus {
  module: string;
  backend: string;
  catalog: string;
  tablesAvailable: boolean;
  directoryAvailable: boolean;
  publishedCount: number;
  message: string | null;
}

export interface EventPublicSummary {
  id: number;
  slug: string;
  title: string;
  titleEn?: string | null;
  descriptionEn?: string | null;
  primaryLanguage?: string | null;
  eventType?: string | null;
  eventTypeLabel?: string | null;
  startsAt: string | null;
  endsAt?: string | null;
  timezone?: string | null;
  modality?: string | null;
  modalityLabel?: string | null;
  locationName?: string | null;
  registrationOpen: boolean;
  publicationStatus: string | null;
}

export interface EventPublicDetail extends EventPublicSummary {
  description?: string | null;
  timezone?: string | null;
  campusName?: string | null;
  virtualAccess?: string | null;
  organizingUnit?: string | null;
  organizingUnitEn?: string | null;
  organizingUnitCode?: string | null;
  registrationOpensAt?: string | null;
  registrationClosesAt?: string | null;
  capacity?: number | null;
  remainingCapacity?: number | null;
  registrationUrl?: string | null;
}

export interface EventsCatalog {
  module: EventsModuleStatus;
  items: EventPublicSummary[];
}

export interface EventWrite {
  title: string;
  titleEn?: string | null;
  description?: string | null;
  descriptionEn?: string | null;
  primaryLanguage?: string | null;
  eventType?: string | null;
  startsAt?: string | null;
  endsAt?: string | null;
  timezone?: string | null;
  modality?: string | null;
  locationName?: string | null;
  campusName?: string | null;
  virtualAccess?: string | null;
  organizingUnit?: string | null;
  organizingUnitCode?: string | null;
  publicationStatus?: string | null;
  registrationOpensAt?: string | null;
  registrationClosesAt?: string | null;
  allowRegistrationDuringEvent?: boolean;
  capacity?: number | null;
  blankSignatureRows?: number | null;
  publicSlug?: string | null;
}

export interface EventAdmin extends EventPublicDetail {
  publicSlug: string;
  publicUrl?: string | null;
  blankSignatureRows: number;
  allowRegistrationDuringEvent?: boolean;
  confirmedCount?: number | null;
  createdBy?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface EventOrganizingUnit {
  code: string;
  nameEs: string;
  nameEn: string;
  other: boolean;
  active: boolean;
  sortOrder: number;
}

export interface EventAdminList {
  items: EventAdmin[];
  total: number;
}

export interface EventRegistration {
  id: number;
  publicCode: string;
  seqNo: number;
  status: string;
  email: string;
  displayName?: string | null;
  facultyOrInstitution?: string | null;
  campusName?: string | null;
  positionTitle?: string | null;
  affiliationOrigin?: string | null;
  directoryPersonUuid?: string | null;
  createdAt?: string | null;
}

export interface EventRegistrationList {
  eventId: number;
  title: string;
  confirmedCount: number;
  capacity?: number | null;
  items: EventRegistration[];
}

export interface EventRegistrationProfile {
  email: string;
  sessionToken?: string | null;
  verified: boolean;
  directoryMatch: boolean;
  alreadyRegistered: boolean;
  registrationOpen: boolean;
  remainingCapacity?: number | null;
  displayName?: string | null;
  facultyOrInstitution?: string | null;
  campusName?: string | null;
  positionTitle?: string | null;
  affiliationOrigin?: string | null;
}

export interface EventRegistrationReceipt {
  publicCode: string;
  eventTitle: string;
  email: string;
  displayName?: string | null;
  affiliationOrigin?: string | null;
  directoryMatch: boolean;
  message?: string | null;
}

export interface EventArtifact {
  id: number;
  versionNo: number;
  filename: string;
  sha256: string;
  byteSize: number;
  blankRows: number;
  generatedBy?: string | null;
  generatedAt?: string | null;
  language?: string | null;
  downloadPath: string;
}

export interface EventDocument {
  id: number;
  kind: string;
  filename: string;
  contentType: string;
  sha256: string;
  byteSize: number;
  note?: string | null;
  uploadedBy?: string | null;
  uploadedAt?: string | null;
  downloadPath: string;
}

export interface EventAttendance {
  id?: number | null;
  registrationId: number;
  publicCode: string;
  seqNo: number;
  status: string;
  statusLabel?: string | null;
  source?: string | null;
  walkIn: boolean;
  displayName?: string | null;
  email?: string | null;
  facultyOrInstitution?: string | null;
  positionTitle?: string | null;
  notes?: string | null;
  confirmedBy?: string | null;
  confirmedAt?: string | null;
}

export interface EventAttendanceImportRequest {
  artifactVersion?: number | null;
  source?: string | null;
  rows: Array<{
    rowNumber?: number;
    registrationCode?: string;
    artifactVersion?: number;
    proposedStatus: string;
    displayName?: string;
    notes?: string;
  }>;
}

export interface EventAttendanceImport {
  id: number;
  artifactVersion?: number | null;
  source: string;
  status: string;
  filename?: string | null;
  matched: number;
  unmatched: number;
  ambiguous: number;
  rows: Array<{
    rowNumber: number;
    registrationCode?: string | null;
    proposedStatus: string;
    matchStatus: string;
    displayName?: string | null;
    notes?: string | null;
  }>;
}

export interface EventIndicators {
  totalEvents: number;
  publishedEvents: number;
  advanceRegistrations: number;
  effectiveAttendees: number;
  walkIns: number;
  confirmedAbsences: number;
  registeredAttendanceRate?: number | null;
  uniquePeople: number;
  recurringPeople: number;
  pureLinked: number;
  attendanceTablesAvailable: boolean;
  byFaculty: Array<{ label: string; total: number }>;
  byPosition: Array<{ label: string; total: number }>;
}

export interface AuthUser {
  id: string;
  displayName?: string;
  email?: string;
  effectivePermissions?: Array<{ module?: string; permission?: string }>;
}

export const EVENT_TYPES = [
  { value: 'TALK' },
  { value: 'SEMINAR' },
  { value: 'WORK_TABLE' },
  { value: 'WORKSHOP' },
  { value: 'JOURNEY' },
  { value: 'OTHER' }
];

export const EVENT_MODALITIES = [
  { value: 'PRESENTIAL' },
  { value: 'VIRTUAL' },
  { value: 'HYBRID' }
];

export const EVENT_STATUSES = [
  { value: 'DRAFT' },
  { value: 'PUBLISHED' },
  { value: 'FINISHED' },
  { value: 'CANCELLED' }
];

@Injectable({ providedIn: 'root' })
export class EventsApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  health(): Observable<EventsModuleStatus> {
    return this.http.get<EventsModuleStatus>(`${this.base}/events/health`);
  }

  catalog(): Observable<EventsCatalog> {
    return this.http.get<EventsCatalog>(`${this.base}/events`);
  }

  getBySlug(slug: string): Observable<EventPublicDetail> {
    return this.http.get<EventPublicDetail>(`${this.base}/events/${slug}`);
  }

  requestRegistration(
    slug: string,
    email: string,
    locale?: string
  ): Observable<{ code?: string; message?: string; emailSent?: boolean }> {
    return this.http.post<{ code?: string; message?: string; emailSent?: boolean }>(
      `${this.base}/events/${slug}/register/request`,
      { email, locale, language: locale }
    );
  }

  consumeVerification(slug: string, token: string): Observable<EventRegistrationProfile> {
    return this.http
      .post<EventRegistrationProfile>(`${this.base}/events/${slug}/register/verify`, { token })
      .pipe(tap((profile) => this.storeVerified(slug, profile.sessionToken)));
  }

  registrationProfile(slug: string): Observable<EventRegistrationProfile> {
    return this.http.get<EventRegistrationProfile>(`${this.base}/events/${slug}/register/profile`, {
      headers: this.verifiedHeaders(slug)
    });
  }

  confirmRegistration(
    slug: string,
    body: {
      displayName?: string;
      facultyOrInstitution?: string;
      campusName?: string;
      positionTitle?: string;
      language?: string;
      locale?: string;
    }
  ): Observable<EventRegistrationReceipt> {
    return this.http.post<EventRegistrationReceipt>(`${this.base}/events/${slug}/register/confirm`, body, {
      headers: this.verifiedHeaders(slug)
    });
  }

  adminStatus(): Observable<EventsModuleStatus> {
    return this.http.get<EventsModuleStatus>(`${this.base}/events/admin/status`);
  }

  adminList(filters?: { status?: string; type?: string; q?: string }): Observable<EventAdminList> {
    let params = new HttpParams();
    if (filters?.status) {
      params = params.set('status', filters.status);
    }
    if (filters?.type) {
      params = params.set('type', filters.type);
    }
    if (filters?.q) {
      params = params.set('q', filters.q);
    }
    return this.http.get<EventAdminList>(`${this.base}/events/admin`, { params });
  }

  adminCampuses(): Observable<string[]> {
    return this.http.get<string[]>(`${this.base}/events/admin/campuses`);
  }

  adminOrganizingUnits(): Observable<EventOrganizingUnit[]> {
    return this.http.get<EventOrganizingUnit[]>(`${this.base}/events/admin/organizing-units`);
  }

  adminGet(id: number): Observable<EventAdmin> {
    return this.http.get<EventAdmin>(`${this.base}/events/admin/${id}`);
  }

  adminCreate(body: EventWrite): Observable<EventAdmin> {
    return this.http.post<EventAdmin>(`${this.base}/events/admin`, body);
  }

  adminUpdate(id: number, body: EventWrite): Observable<EventAdmin> {
    return this.http.put<EventAdmin>(`${this.base}/events/admin/${id}`, body);
  }

  adminPublish(id: number): Observable<EventAdmin> {
    return this.http.post<EventAdmin>(`${this.base}/events/admin/${id}/publish`, {});
  }

  adminUnpublish(id: number): Observable<EventAdmin> {
    return this.http.post<EventAdmin>(`${this.base}/events/admin/${id}/unpublish`, {});
  }

  adminRegistrations(id: number): Observable<EventRegistrationList> {
    return this.http.get<EventRegistrationList>(`${this.base}/events/admin/${id}/registrations`);
  }

  adminFinish(id: number): Observable<EventAdmin> {
    return this.http.post<EventAdmin>(`${this.base}/events/admin/${id}/finish`, {});
  }

  adminCancel(id: number): Observable<EventAdmin> {
    return this.http.post<EventAdmin>(`${this.base}/events/admin/${id}/cancel`, {});
  }

  adminAddRegistration(
    id: number,
    body: {
      email: string;
      displayName?: string;
      facultyOrInstitution?: string;
      campusName?: string;
      positionTitle?: string;
      walkIn?: boolean;
    }
  ): Observable<EventRegistrationReceipt> {
    return this.http.post<EventRegistrationReceipt>(`${this.base}/events/admin/${id}/registrations`, body);
  }

  adminMinutes(id: number): Observable<EventArtifact[]> {
    return this.http.get<EventArtifact[]>(`${this.base}/events/admin/${id}/minutes`);
  }

  adminGenerateMinutes(id: number, blankRows?: number, language?: string): Observable<EventArtifact> {
    let params = new HttpParams();
    if (blankRows != null) {
      params = params.set('blankRows', String(blankRows));
    }
    if (language) {
      params = params.set('language', language);
    }
    return this.http.post<EventArtifact>(`${this.base}/events/admin/${id}/minutes`, {}, { params });
  }

  adminDocuments(id: number): Observable<EventDocument[]> {
    return this.http.get<EventDocument[]>(`${this.base}/events/admin/${id}/documents`);
  }

  adminUploadDocument(id: number, file: File, kind: string, note?: string): Observable<EventDocument> {
    return this.http.post<EventDocument>(`${this.base}/events/admin/${id}/documents`, file, {
      headers: new HttpHeaders({
        'Content-Type': file.type || 'application/octet-stream',
        'X-Filename': file.name,
        'X-Document-Kind': kind,
        'X-Document-Note': note || ''
      })
    });
  }

  adminAttendance(id: number): Observable<EventAttendance[]> {
    return this.http.get<EventAttendance[]>(`${this.base}/events/admin/${id}/attendance`);
  }

  adminSetAttendance(id: number, body: { registrationId?: number; publicCode?: string; status: string; notes?: string }): Observable<EventAttendance> {
    return this.http.post<EventAttendance>(`${this.base}/events/admin/${id}/attendance`, body);
  }

  adminWalkIn(
    id: number,
    body: { email: string; displayName?: string; facultyOrInstitution?: string; campusName?: string; positionTitle?: string }
  ): Observable<EventRegistrationReceipt> {
    return this.http.post<EventRegistrationReceipt>(`${this.base}/events/admin/${id}/attendance/walk-in`, {
      ...body,
      walkIn: true
    });
  }

  adminPreviewImport(id: number, body: EventAttendanceImportRequest): Observable<EventAttendanceImport> {
    return this.http.post<EventAttendanceImport>(`${this.base}/events/admin/${id}/attendance/imports`, body);
  }

  adminPreviewImportFile(id: number, file: File, artifactVersion?: number): Observable<EventAttendanceImport> {
    let params = new HttpParams();
    if (artifactVersion != null) {
      params = params.set('artifactVersion', String(artifactVersion));
    }
    return this.http.post<EventAttendanceImport>(`${this.base}/events/admin/${id}/attendance/imports/file`, file, {
      headers: new HttpHeaders({
        'Content-Type': file.type || 'application/octet-stream',
        'X-Filename': file.name
      }),
      params
    });
  }

  adminConfirmImport(id: number, importId: number): Observable<EventAttendanceImport> {
    return this.http.post<EventAttendanceImport>(`${this.base}/events/admin/${id}/attendance/imports/${importId}/confirm`, {});
  }

  adminCloseDocumentation(id: number): Observable<EventAdmin> {
    return this.http.post<EventAdmin>(`${this.base}/events/admin/${id}/close-documentation`, {});
  }

  adminIndicators(from?: string, to?: string): Observable<EventIndicators> {
    let params = new HttpParams();
    if (from) {
      params = params.set('from', from);
    }
    if (to) {
      params = params.set('to', to);
    }
    return this.http.get<EventIndicators>(`${this.base}/events/admin/indicators`, { params });
  }

  downloadUrl(path: string): string {
    return `${this.base}${path.startsWith('/') ? path : '/' + path}`;
  }

  login(email: string, password: string): Observable<AuthUser> {
    return this.http.post<AuthUser>(`${this.base}/auth/login`, { email, password });
  }

  me(): Observable<AuthUser> {
    return this.http.get<AuthUser>(`${this.base}/auth/me`);
  }

  logout(): Observable<unknown> {
    return this.http.post(`${this.base}/auth/logout`, {});
  }

  hasEventsPermission(user: AuthUser | null | undefined): boolean {
    return !!user?.effectivePermissions?.some((item) => item.module === 'EVENTS');
  }

  storeVerified(slug: string, token?: string | null): void {
    if (!token) {
      return;
    }
    sessionStorage.setItem(`ose-verified:${slug}`, token);
  }

  private verifiedHeaders(slug: string): HttpHeaders {
    const token = sessionStorage.getItem(`ose-verified:${slug}`);
    return token ? new HttpHeaders({ 'X-Event-Verified-Session': token }) : new HttpHeaders();
  }
}
