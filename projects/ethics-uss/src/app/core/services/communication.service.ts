import { Injectable } from '@angular/core';
import { DemoAuthService } from '../auth/demo-auth.service';
import { CommitteeCommunication } from '../models/ethics.models';
import { AuditService } from './audit.service';
import { EthicsStore } from './ethics-store.service';
import { NotificationService } from './notification.service';
import { nowIso, uid } from './ids';

@Injectable({ providedIn: 'root' })
export class CommunicationService {
  constructor(
    private readonly store: EthicsStore,
    private readonly audit: AuditService,
    private readonly notifications: NotificationService,
    private readonly auth: DemoAuthService
  ) {}

  byCase(caseId: string): CommitteeCommunication[] {
    return this.store.snapshot().communications
      .filter((item) => item.caseId === caseId)
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  }

  record(input: Omit<CommitteeCommunication, 'id' | 'createdAt' | 'emailSimulated' | 'actorId' | 'actorName'> & {
    actorId?: string;
    actorName?: string;
    notifyUserIds?: string[];
    notification?: { type: string; title: string; body: string; link?: string; params?: Record<string, string | number> };
    auditAction: string;
    auditDescription: string;
  }): CommitteeCommunication {
    const actor = this.store.snapshot().users.find((user) => user.id === input.actorId) ?? this.auth.currentUser();
    const item: CommitteeCommunication = {
      id: uid('com'),
      caseId: input.caseId,
      projectId: input.projectId,
      actorId: actor?.id ?? 'system',
      actorName: input.actorName ?? actor?.displayName ?? 'Sistema',
      kind: input.kind,
      body: input.body,
      createdAt: nowIso(),
      emailSimulated: true
    };
    this.store.update((state) => ({ ...state, communications: [item, ...(state.communications ?? [])] }));
    this.audit.record({
      action: input.auditAction,
      objectType: 'Communication',
      objectId: item.id,
      caseId: input.caseId,
      description: input.auditDescription,
      projectId: input.projectId
    });
    if (input.notification) {
      for (const userId of input.notifyUserIds ?? []) {
        this.notifications.notify(userId, { ...input.notification, link: input.notification.link });
      }
    }
    return item;
  }
}
