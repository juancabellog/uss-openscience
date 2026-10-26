import { Injectable } from '@angular/core';
import { EthicsCommittee, ReviewerAssignment, User } from '../models/ethics.models';
import { AuditService } from './audit.service';
import { CommitteeCaseService } from './committee-case.service';
import { EthicsStore } from './ethics-store.service';
import { NotificationService } from './notification.service';
import { nowIso, uid } from './ids';

@Injectable({ providedIn: 'root' })
export class ReviewerService {
  constructor(
    private readonly store: EthicsStore,
    private readonly audit: AuditService,
    private readonly notifications: NotificationService,
    private readonly cases: CommitteeCaseService
  ) {}

  catalog(committee: EthicsCommittee): User[] {
    return this.store
      .snapshot()
      .users.filter((user) => user.role === 'COMMITTEE_MEMBER' && user.committees.includes(committee))
      .sort((a, b) => (a.activeLoad ?? 0) - (b.activeLoad ?? 0) || (a.specialty ?? '').localeCompare(b.specialty ?? ''));
  }

  assignmentsFor(reviewerId: string): ReviewerAssignment[] {
    return this.store.snapshot().assignments.filter((item) => item.reviewerId === reviewerId);
  }

  byCase(caseId: string): ReviewerAssignment[] {
    return this.store.snapshot().assignments.filter((item) => item.caseId === caseId);
  }

  getAssignment(id: string): ReviewerAssignment | undefined {
    return this.store.snapshot().assignments.find((item) => item.id === id);
  }

  assign(caseId: string, reviewer1: string, reviewer2: string, sessionId?: string): void {
    const stamp = nowIso();
    const first: ReviewerAssignment = {
      id: uid('asg'),
      caseId,
      reviewerId: reviewer1,
      slot: 1,
      status: 'PENDING',
      assignedAt: stamp,
      sessionId
    };
    const second: ReviewerAssignment = {
      ...first,
      id: uid('asg'),
      reviewerId: reviewer2,
      slot: 2
    };
    this.store.update((state) => ({
      ...state,
      assignments: [first, second, ...state.assignments.filter((item) => item.caseId !== caseId)],
      cases: state.cases.map((item) =>
        item.id === caseId
          ? { ...item, reviewerIds: [reviewer1, reviewer2], sessionId, status: 'PENDING_ACCEPTANCE' }
          : item
      )
    }));
    const current = this.cases.get(caseId);
    this.audit.record({
      action: 'ASSIGN_REVIEWERS',
      objectType: 'ReviewerAssignment',
      objectId: caseId,
      description: 'Asignó dos revisores',
      projectId: current?.projectId
    });
    [reviewer1, reviewer2].forEach((id) =>
      this.notifications.notify(id, {
        type: 'REVIEW_ASSIGNED',
        title: 'Asignación de revisor',
        body: `Tiene una nueva revisión en ${current?.code}.`,
        params: { caseCode: current?.code ?? '' },
        link: `/revisiones/${id === reviewer1 ? first.id : second.id}`
      })
    );
  }

  decide(assignmentId: string, accept: boolean, reason?: string, conflict?: boolean): void {
    const assignment = this.getAssignment(assignmentId);
    if (!assignment) {
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
    this.audit.record({
      action: accept ? (conflict ? 'DECLARE_CONFLICT' : 'ACCEPT_ASSIGNMENT') : 'REJECT_ASSIGNMENT',
      objectType: 'ReviewerAssignment',
      objectId: assignmentId,
      description: accept ? (conflict ? 'Declaró conflicto de interés' : 'Aceptó asignación') : 'Rechazó asignación',
      reason,
      projectId: current?.projectId
    });
    if (status !== 'ACCEPTED') {
      this.cases.setStatus(assignment.caseId, 'PENDING_ASSIGNMENT');
    } else {
      const bothAccepted = this.byCase(assignment.caseId).every((item) => item.status === 'ACCEPTED' || item.id === assignmentId);
      if (bothAccepted) {
        this.cases.setStatus(assignment.caseId, 'IN_REVIEW');
      }
    }
  }
}
