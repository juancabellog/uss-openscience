import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { emptyPage } from '../../utils/page-view-state';
import { mockNotFound, mockOf } from '../../mock-data/mock-rx';
import { AuditChange, AuditEvent, AuditQuery, AuditSyncError, PagedResult } from '../../models/openscience.models';
import { AuditService } from '../platform-services';

/** Retained for compile compatibility. Auditoría uses ApiAuditService. */
@Injectable()
export class MockAuditService extends AuditService {
  override list(_query: AuditQuery): Observable<PagedResult<AuditEvent>> {
    return mockOf(emptyPage<AuditEvent>(25));
  }

  override getById(id: string): Observable<AuditEvent> {
    return mockNotFound(`Evento ${id} no encontrado`);
  }

  override changes(_id: string, _page = 1, pageSize = 25): Observable<PagedResult<AuditChange>> {
    return mockOf(emptyPage<AuditChange>(pageSize));
  }

  override errors(_id: string, _page = 1, pageSize = 25): Observable<PagedResult<AuditSyncError>> {
    return mockOf(emptyPage<AuditSyncError>(pageSize));
  }
}
