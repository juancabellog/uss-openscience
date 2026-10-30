import { Injectable } from '@angular/core';
import { requirementsFor } from '../../mocks/committee-document-requirements.mock';
import {
  ClassificationCode,
  CommitteeCase,
  CommitteeCaseStatus,
  EthicsCommittee,
  ProcessKind
} from '../models/ethics.models';
import { CommunicationService } from './communication.service';
import { AuditService } from './audit.service';
import { EthicsStore } from './ethics-store.service';
import { NotificationService } from './notification.service';
import { holds, hasScope } from '../workflow/role-capabilities';
import { nowIso, uid } from './ids';

@Injectable({ providedIn: 'root' })
export class CommitteeCaseService {
  constructor(
    private readonly store: EthicsStore,
    private readonly audit: AuditService,
    private readonly notifications: NotificationService,
    private readonly communications: CommunicationService
  ) {}

  list(): CommitteeCase[] {
    return this.store.snapshot().cases.slice().sort((a, b) => b.receivedAt.localeCompare(a.receivedAt));
  }

  byCommittee(committees: EthicsCommittee[]): CommitteeCase[] {
    return this.list().filter((item) => committees.includes(item.committee));
  }

  byProject(projectId: string): CommitteeCase[] {
    return this.list().filter((item) => item.projectId === projectId);
  }

  get(id: string): CommitteeCase | undefined {
    return this.store.snapshot().cases.find((item) => item.id === id);
  }

  derive(submissionId: string, codes: ClassificationCode[]): CommitteeCase[] {
    const submission = this.store.snapshot().submissions.find((item) => item.id === submissionId);
    if (!submission) {
      throw new Error('Solicitud no encontrada');
    }
    const created: CommitteeCase[] = [];
    const stamp = nowIso();
    this.store.update((state) => {
      const next = { ...state, cases: [...state.cases] };
      for (const code of codes) {
        if (code === 'NO_REQUIERE_COMITE') {
          continue;
        }
        const exists = next.cases.some((item) => item.submissionId === submissionId && item.committee === code);
        if (exists) {
          continue;
        }
        const item: CommitteeCase = {
          id: uid('case'),
          code: `${code}-${new Date().getFullYear()}-${String(next.cases.length + 1).padStart(3, '0')}`,
          projectId: submission.projectId,
          submissionId,
          committee: code,
          status: 'PENDING_DOCUMENTS',
          receivedAt: stamp,
          reviewerIds: [],
          currentCycle: 1,
          packageFrozen: false
        };
        created.push(item);
        next.cases.unshift(item);
      }
      return next;
    });
    created.forEach((item) => {
      this.audit.record({
        action: 'CREATE_CASE',
        objectType: 'CommitteeCase',
        objectId: item.id,
        description: `Derivó expediente ${item.committee}`,
        projectId: item.projectId
      });
    });
    const project = this.store.snapshot().projects.find((item) => item.id === submission.projectId);
    if (project) {
      this.notifications.notify(project.researcherId, {
        type: 'CLASSIFICATION_DONE',
        title: 'Clasificación realizada',
        body: 'Ya puede cargar antecedentes por comité.',
        link: `/proyectos/${project.id}`
      });
    }
    return created;
  }

  notifyCommittee(caseId: string, message?: string): void {
    const current = this.get(caseId);
    if (!current) {
      return;
    }
    const body = message?.trim() || 'Informo que se han cargado los antecedentes requeridos para la revisión del proyecto.';
    this.patch(caseId, { status: 'ADMISSIBILITY', notifiedAt: nowIso(), packageFrozen: true, processStatus: current.processStatus === 'DRAFT' ? 'SUBMITTED' : current.processStatus });
    this.audit.record({
      action: 'COMMITTEE_NOTIFIED',
      objectType: 'CommitteeCase',
      objectId: caseId,
      description: `Notificó ${current.committee}`,
      projectId: current.projectId
    });
    this.communications.record({
      caseId,
      projectId: current.projectId,
      kind: current.processKind === 'AMENDMENT' ? 'AMENDMENT' : current.processKind === 'FOLLOW_UP' || current.processKind === 'CLOSURE_FOLLOW_UP' ? 'FOLLOW_UP' : 'COMMITTEE_NOTIFICATION',
      body,
      auditAction: 'COMMITTEE_NOTIFIED',
      auditDescription: `Comunicó la notificación a ${current.committee}`,
      notifyUserIds: []
    });
    this.notifyOfficers(current, {
      type: 'DOCUMENTS_SENT',
      title: 'Antecedentes enviados',
      body,
      params: { caseCode: current.code }
    });
  }

  admissibility(caseId: string, accept: boolean, reason?: string): void {
    this.patch(caseId, { status: accept ? 'PENDING_ASSIGNMENT' : 'RETURNED', packageFrozen: accept });
    const current = this.get(caseId)!;
    this.audit.record({
      action: accept ? 'ADMISSIBILITY_ACCEPTED' : 'RETURN_CASE',
      objectType: 'CommitteeCase',
      objectId: caseId,
      description: accept ? 'Declaró expediente completo' : 'Devolvió para subsanar',
      reason: accept ? undefined : reason,
      projectId: current.projectId
    });
  }

  setStatus(caseId: string, status: CommitteeCaseStatus): void {
    this.patch(caseId, { status });
  }

  /** Cierra la respuesta del investigador y vuelve a dejar el paquete congelado. */
  receiveResearcherResponse(caseId: string): void {
    this.patch(caseId, { status: 'IN_REVIEW', packageFrozen: true });
  }

  requiredDocs(committee: EthicsCommittee): Array<{ key: string; label: string; required: boolean; versionable: boolean }> {
    return requirementsFor(committee).map((item) => ({
      key: item.id,
      label: item.nameKey,
      required: item.required,
      versionable: item.versionable
    }));
  }

  completion(caseId: string): { done: number; total: number } {
    const item = this.get(caseId);
    if (!item) {
      return { done: 0, total: 0 };
    }
    const required = requirementsFor(item.committee, true);
    const docs = this.store.snapshot().documents.filter((doc) => doc.ownerId === caseId);
    const done = required.filter((req) => docs.some((doc) => doc.requirementKey === req.id)).length;
    return { done, total: required.length };
  }

  startProcess(caseId: string, kind: ProcessKind, justification: string): void {
    const current = this.get(caseId);
    if (!current || !justification.trim()) {
      return;
    }
    this.patch(caseId, { processKind: kind, processStatus: 'DRAFT', packageFrozen: false });
    this.audit.record({
      action: kind === 'AMENDMENT' ? 'AMENDMENT_REQUESTED' : 'FOLLOW_UP_REQUESTED',
      objectType: 'CommitteeCase',
      objectId: caseId,
      description: justification.trim(),
      reason: justification.trim(),
      projectId: current.projectId
    });
  }

  editingLocked(caseId: string): boolean {
    const item = this.get(caseId);
    if (!item) {
      return false;
    }
    const closed = item.status === 'APPROVED' || item.status === 'REJECTED' || item.status === 'EXEMPT';
    return closed && item.processStatus !== 'DRAFT';
  }

  private patch(caseId: string, changes: Partial<CommitteeCase>): void {
    this.store.update((state) => ({
      ...state,
      cases: state.cases.map((item) => (item.id === caseId ? { ...item, ...changes } : item))
    }));
  }

  private notifyOfficers(item: CommitteeCase, draft: { type: string; title: string; body: string; params?: Record<string, string | number> }): void {
    this.store
      .snapshot()
      .users.filter((user) => hasScope(user, 'COMMITTEE') && holds(user, 'VIEW_COMMITTEE_SESSIONS') && user.committees.includes(item.committee))
      .forEach((user) => this.notifications.notify(user.id, { ...draft, link: `/expedientes/${item.id}` }));
  }
}
