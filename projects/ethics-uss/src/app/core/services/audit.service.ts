import { Injectable } from '@angular/core';
import { DemoAuthService } from '../auth/demo-auth.service';
import { AuditEvent, Role } from '../models/ethics.models';
import { EthicsStore } from './ethics-store.service';
import { nowIso, uid } from './ids';

@Injectable({ providedIn: 'root' })
export class AuditService {
  constructor(
    private readonly store: EthicsStore,
    private readonly auth: DemoAuthService
  ) {}

  list(projectId?: string): AuditEvent[] {
    const items = this.store.snapshot().audit;
    return (projectId ? items.filter((item) => item.projectId === projectId) : items)
      .slice()
      .sort((a, b) => b.date.localeCompare(a.date));
  }

  record(partial: Omit<AuditEvent, 'id' | 'actorId' | 'actorName' | 'role' | 'date'> & { date?: string }): AuditEvent {
    const user = this.auth.currentUser();
    const event: AuditEvent = {
      id: uid('aud'),
      actorId: user?.id ?? 'system',
      actorName: user?.displayName ?? 'Sistema',
      role: (user?.role ?? 'ADMINISTRATOR') as Role,
      date: partial.date ?? nowIso(),
      action: partial.action,
      objectType: partial.objectType,
      objectId: partial.objectId,
      description: partial.description,
      oldValue: partial.oldValue,
      newValue: partial.newValue,
      reason: partial.reason,
      projectId: partial.projectId,
      caseId: partial.caseId
    };
    this.store.update((state) => ({ ...state, audit: [event, ...state.audit] }));
    return event;
  }
}
