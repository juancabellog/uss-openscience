import { Injectable } from '@angular/core';
import { DemoAuthService } from '../auth/demo-auth.service';
import { EthicsCommittee, ReviewerAssignment, User } from '../models/ethics.models';
import { canReassignReviewer, isAdvancedReviewerReassignment, requiredReassignCapability } from '../workflow/reviewer-assignment.policy';
import { rolesOf } from '../workflow/role-capabilities';
import { AuditService } from './audit.service';
import { CommitteeCaseService } from './committee-case.service';
import { EthicsStore } from './ethics-store.service';
import { nowIso, uid } from './ids';
import { NotificationService } from './notification.service';
import { PermissionService } from './permission.service';

const CLOSED = new Set(['APPROVED', 'REJECTED', 'EXEMPT', 'WITHDRAWN']);

export type ReassignOutcome = 'ok' | 'invalid' | 'forbidden' | 'closed' | 'same' | 'duplicate';

@Injectable({ providedIn: 'root' })
export class ReviewerService {
  constructor(
    private readonly store: EthicsStore,
    private readonly audit: AuditService,
    private readonly notifications: NotificationService,
    private readonly cases: CommitteeCaseService,
    private readonly auth: DemoAuthService,
    private readonly permissions: PermissionService
  ) {}

  /** Personas con rol REVIEWER. La composición del comité no autoriza a revisar. */
  candidates(_committee: EthicsCommittee): User[] {
    return this.pool();
  }

  catalog(committee: EthicsCommittee): User[] {
    return this.candidates(committee);
  }

  /** Personas que pueden revisar. El comité al que pertenecen es metadata. */
  pool(): User[] {
    return this.store
      .snapshot()
      .users.filter((user) => user.ethicsAccess !== 'INACTIVE' && rolesOf(user).includes('REVIEWER'))
      .sort((a, b) => a.displayName.localeCompare(b.displayName, 'es'));
  }

  assignmentsFor(reviewerId: string): ReviewerAssignment[] {
    return this.store.snapshot().assignments.filter((item) => item.reviewerId === reviewerId);
  }

  byCase(caseId: string): ReviewerAssignment[] {
    return this.store.snapshot().assignments.filter((item) => item.caseId === caseId);
  }

  currentBySlot(caseId: string): { slot: 1 | 2; assignment?: ReviewerAssignment }[] {
    const items = this.byCase(caseId);
    const latest = (slot: 1 | 2) =>
      items
        .filter((item) => item.slot === slot && !item.supersededAt)
        .sort((a, b) => b.assignedAt.localeCompare(a.assignedAt))[0];
    return [
      { slot: 1, assignment: latest(1) },
      { slot: 2, assignment: latest(2) }
    ];
  }

  /** Revisiones vigentes: slot actual, no rechazado, en un caso que sigue abierto. */
  activeReviewCount(userId: string): number {
    const state = this.store.snapshot();
    return state.cases.filter((item) => {
      if (CLOSED.has(item.status)) {
        return false;
      }
      return this.currentBySlot(item.id).some((slot) => {
        const assignment = slot.assignment;
        return !!assignment && assignment.reviewerId === userId && assignment.status !== 'REJECTED' && assignment.status !== 'CONFLICT';
      });
    }).length;
  }

  getAssignment(id: string): ReviewerAssignment | undefined {
    return this.store.snapshot().assignments.find((item) => item.id === id);
  }

  assign(caseId: string, reviewer1: string, reviewer2: string, sessionId?: string): void {
    if (!reviewer1 || !reviewer2 || reviewer1 === reviewer2) {
      return;
    }
    const current = this.cases.get(caseId);
    const user = this.auth.currentUser();
    if (!current || !user || !this.permissions.can(user, 'ASSIGN_REVIEWERS', this.context(caseId))) {
      return;
    }
    const stamp = nowIso();
    const first = this.entry(caseId, reviewer1, 1, stamp, user.id, sessionId);
    const second = this.entry(caseId, reviewer2, 2, stamp, user.id, sessionId);
    this.store.update((state) => ({
      ...state,
      assignments: [first, second, ...state.assignments],
      cases: state.cases.map((item) =>
        item.id === caseId
          ? { ...item, reviewerIds: [reviewer1, reviewer2], sessionId: sessionId ?? item.sessionId, status: 'PENDING_ACCEPTANCE' }
          : item
      )
    }));
    this.audit.record({
      action: 'ASSIGN_REVIEWERS',
      objectType: 'ReviewerAssignment',
      objectId: caseId,
      description: 'Asignó dos revisores',
      projectId: current.projectId,
      caseId
    });
    this.auditSlot('REVIEWER_ASSIGNED', current.projectId, caseId, 1, reviewer1);
    this.auditSlot('REVIEWER_ASSIGNED', current.projectId, caseId, 2, reviewer2);
    [first, second].forEach((assignment) =>
      this.notifications.notify(assignment.reviewerId, {
        type: 'REVIEW_ASSIGNED',
        title: 'Asignación de revisor',
        body: `Tiene una nueva revisión en ${current.code}.`,
        params: { caseCode: current.code },
        link: `/revisiones/${assignment.id}`
      })
    );
  }

  reviewWasSubmitted(assignmentId: string): boolean {
    return this.store.snapshot().reviews.some((item) => item.assignmentId === assignmentId && !!item.submittedAt);
  }

  /** Capability más estado de la asignación y del caso. El template no redecide esto. */
  canReassign(caseId: string, assignment?: ReviewerAssignment): boolean {
    const current = this.cases.get(caseId);
    const user = this.auth.currentUser();
    if (!current || !user || !assignment) {
      return false;
    }
    return canReassignReviewer({
      hasOrdinaryCapability: this.permissions.can(user, 'REASSIGN_REVIEWER', this.context(caseId)),
      hasAdvancedCapability: this.permissions.can(user, 'REASSIGN_REVIEWER_ADVANCED_STAGE', this.context(caseId)),
      status: assignment.status,
      reviewSubmitted: this.reviewWasSubmitted(assignment.id),
      caseStatus: current.status
    });
  }

  /** La plantilla no compara estados. Esta función es la misma regla que la autorización. */
  isAdvancedReassignment(caseId: string, assignment?: ReviewerAssignment): boolean {
    const current = this.cases.get(caseId);
    if (!current || !assignment) {
      return false;
    }
    return isAdvancedReviewerReassignment({
      caseStatus: current.status,
      assignmentStatus: assignment.status,
      reviewSubmitted: this.reviewWasSubmitted(assignment.id)
    });
  }

  /**
   * La ronda siguiente no reabre la asignación cerrada.
   * Deja otra, del mismo slot y la misma persona, para que el proceso continúe
   * sin una recomendación ni un cambio de revisor.
   */
  openAssignmentsForNewRound(caseId: string): void {
    const stamp = nowIso();
    const created: ReviewerAssignment[] = [];
    const superseded = new Set<string>();
    for (const slot of this.currentBySlot(caseId)) {
      const assignment = slot.assignment;
      if (!assignment || assignment.status !== 'COMPLETED') {
        continue;
      }
      superseded.add(assignment.id);
      created.push({
        ...this.entry(caseId, assignment.reviewerId, slot.slot, stamp, assignment.assignedBy ?? 'system', assignment.sessionId),
        status: 'ACCEPTED',
        decidedAt: stamp
      });
    }
    if (!created.length) {
      return;
    }
    this.store.update((state) => ({
      ...state,
      assignments: [
        ...created,
        ...state.assignments.map((item) => (superseded.has(item.id) ? { ...item, supersededAt: stamp } : item))
      ]
    }));
  }

  reassign(caseId: string, slot: 1 | 2, reviewerId: string, reason: string): ReassignOutcome {
    const motive = reason.trim();
    if (!reviewerId || !motive) {
      return 'invalid';
    }
    const current = this.cases.get(caseId);
    const user = this.auth.currentUser();
    if (!current || !user) {
      return 'forbidden';
    }
    const slots = this.currentBySlot(caseId);
    const previous = slots.find((item) => item.slot === slot)?.assignment;
    const other = slots.find((item) => item.slot !== slot)?.assignment;
    const required = previous
      ? requiredReassignCapability({
          caseStatus: current.status,
          status: previous.status,
          reviewSubmitted: this.reviewWasSubmitted(previous.id)
        })
      : null;
    if (!previous || !required) {
      return 'closed';
    }
    if (!this.permissions.can(user, required, this.context(caseId))) {
      return 'forbidden';
    }
    if (previous.reviewerId === reviewerId) {
      return 'same';
    }
    if (other && other.reviewerId === reviewerId && other.status !== 'REJECTED' && other.status !== 'CONFLICT') {
      return 'duplicate';
    }
    const stamp = nowIso();
    const next = this.entry(caseId, reviewerId, slot, stamp, user.id, current.sessionId);
    const ids = slots
      .map((item) => (item.slot === slot ? next : item.assignment))
      .filter((item): item is ReviewerAssignment => !!item && item.status !== 'REJECTED' && item.status !== 'CONFLICT')
      .map((item) => item.reviewerId);
    const stillOpen = current.status === 'PENDING_ASSIGNMENT' || current.status === 'PENDING_ACCEPTANCE';
    this.store.update((state) => ({
      ...state,
      assignments: [
        next,
        ...state.assignments.map((item) => (item.id === previous.id ? { ...item, supersededAt: stamp } : item))
      ],
      cases: state.cases.map((item) =>
        item.id === caseId
          ? {
              ...item,
              reviewerIds: ids,
              status: stillOpen ? (ids.length === 2 ? 'PENDING_ACCEPTANCE' : 'PENDING_ASSIGNMENT') : item.status
            }
          : item
      )
    }));
    this.audit.record({
      action: 'REVIEWER_REASSIGNED',
      objectType: 'ReviewerAssignment',
      objectId: next.id,
      description: `Reasignación ${required === 'REASSIGN_REVIEWER_ADVANCED_STAGE' ? 'avanzada' : 'ordinaria'}. Estado previo ${previous.status}. Etapa ${current.status}.`,
      oldValue: this.label(previous.slot, previous.reviewerId),
      newValue: this.label(slot, reviewerId),
      reason: motive,
      projectId: current.projectId,
      caseId
    });
    this.notifications.notify(reviewerId, {
      type: 'REVIEW_ASSIGNED',
      title: 'Asignación de revisor',
      body: `Tiene una nueva revisión en ${current.code}.`,
      params: { caseCode: current.code },
      link: `/revisiones/${next.id}`
    });
    return 'ok';
  }

  decide(assignmentId: string, accept: boolean, reason?: string, conflict?: boolean): void {
    const assignment = this.getAssignment(assignmentId);
    const user = this.auth.currentUser();
    if (!assignment || !user || user.id !== assignment.reviewerId || assignment.status !== 'PENDING') {
      return;
    }
    const status = !accept ? 'REJECTED' : conflict ? 'CONFLICT' : 'ACCEPTED';
    this.store.update((state) => ({
      ...state,
      assignments: state.assignments.map((item) =>
        item.id === assignmentId
          ? { ...item, status, rejectReason: reason, conflictDeclared: conflict, decidedAt: nowIso() }
          : item
      )
    }));
    const current = this.cases.get(assignment.caseId);
    const action = accept ? (conflict ? 'DECLARE_CONFLICT' : 'REVIEWER_ACCEPTED') : 'REVIEWER_REJECTED';
    this.audit.record({
      action,
      objectType: 'ReviewerAssignment',
      objectId: assignmentId,
      description: accept ? (conflict ? 'Declaró conflicto de interés' : 'Aceptó la asignación') : 'Rechazó la asignación',
      newValue: this.label(assignment.slot, assignment.reviewerId),
      reason,
      projectId: current?.projectId,
      caseId: assignment.caseId
    });
    if (status !== 'ACCEPTED') {
      this.cases.setStatus(assignment.caseId, 'PENDING_ASSIGNMENT');
      this.syncReviewerIds(assignment.caseId);
      return;
    }
    const active = this.currentBySlot(assignment.caseId)
      .map((item) => item.assignment)
      .filter((item): item is ReviewerAssignment => !!item && item.status !== 'REJECTED' && item.status !== 'CONFLICT');
    const bothAccepted = active.length === 2 && active.every((item) => item.status === 'ACCEPTED' || item.status === 'COMPLETED');
    if (bothAccepted && (current?.status === 'PENDING_ACCEPTANCE' || current?.status === 'PENDING_ASSIGNMENT')) {
      this.cases.setStatus(assignment.caseId, 'IN_REVIEW');
    }
  }

  private syncReviewerIds(caseId: string): void {
    const ids = this.currentBySlot(caseId)
      .map((item) => item.assignment)
      .filter((item): item is ReviewerAssignment => !!item && item.status !== 'REJECTED' && item.status !== 'CONFLICT')
      .map((item) => item.reviewerId);
    this.store.update((state) => ({
      ...state,
      cases: state.cases.map((item) => (item.id === caseId ? { ...item, reviewerIds: ids } : item))
    }));
  }

  private entry(caseId: string, reviewerId: string, slot: 1 | 2, stamp: string, assignedBy: string, sessionId?: string): ReviewerAssignment {
    return { id: uid('asg'), caseId, reviewerId, slot, status: 'PENDING', assignedAt: stamp, assignedBy, sessionId };
  }

  private auditSlot(action: string, projectId: string, caseId: string, slot: 1 | 2, reviewerId: string): void {
    this.audit.record({
      action,
      objectType: 'ReviewerAssignment',
      objectId: caseId,
      description: 'Asignó un revisor',
      newValue: this.label(slot, reviewerId),
      projectId,
      caseId
    });
  }

  private label(slot: 1 | 2, reviewerId: string): string {
    const name = this.store.snapshot().users.find((user) => user.id === reviewerId)?.displayName ?? reviewerId;
    return `${slot}: ${name}`;
  }

  private context(caseId: string) {
    const item = this.cases.get(caseId);
    return {
      projectId: item?.projectId,
      committeeCaseId: caseId,
      committeeType: item?.committee,
      workflowStatus: item?.status
    };
  }
}
