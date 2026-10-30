import { Injectable } from '@angular/core';
import { DemoAuthService } from '../auth/demo-auth.service';
import { reviewFormFor } from '../forms/review-form.definition';
import { Review, Role } from '../models/ethics.models';
import { AuditService } from './audit.service';
import { CommitteeCaseService } from './committee-case.service';
import { EthicsStore } from './ethics-store.service';
import { nowIso, uid } from './ids';
import { PermissionService } from './permission.service';
import { ReviewerService } from './reviewer.service';

@Injectable({ providedIn: 'root' })
export class ReviewService {
  constructor(
    private readonly store: EthicsStore,
    private readonly audit: AuditService,
    private readonly assignments: ReviewerService,
    private readonly cases: CommitteeCaseService,
    private readonly permissions: PermissionService,
    private readonly auth: DemoAuthService
  ) {}

  visibleTo(assignmentId: string, userId: string, _role: Role): Review | undefined {
    const assignment = this.assignments.getAssignment(assignmentId);
    if (!assignment) {
      return undefined;
    }
    const review = this.store.snapshot().reviews.find((item) => item.assignmentId === assignmentId);
    if (!review || review.reviewerId !== assignment.reviewerId) {
      return undefined;
    }
    return this.canReadReview(userId, review.caseId, review.reviewerId) ? review : undefined;
  }

  listForCase(caseId: string, userId: string, _role: Role, cycleNumber?: number): Review[] {
    const items = this.store.snapshot().reviews.filter((item) =>
      item.caseId === caseId && (cycleNumber === undefined || item.cycleNumber === cycleNumber)
    );
    const user = this.store.snapshot().users.find((item) => item.id === userId);
    const item = this.cases.get(caseId);
    const context = {
      projectId: item?.projectId,
      committeeCaseId: caseId,
      committeeType: item?.committee,
      workflowStatus: item?.status
    };
    if (user && this.permissions.can(user, 'VIEW_ALL_REVIEWS', context)) {
      return items.filter((review) => !!review.submittedAt);
    }
    if (user && this.permissions.can(user, 'VIEW_OWN_REVIEW', context)) {
      return items.filter((review) => review.reviewerId === userId);
    }
    return [];
  }

  private canReadReview(userId: string, caseId: string, reviewerId: string): boolean {
    const user = this.store.snapshot().users.find((item) => item.id === userId);
    const item = this.cases.get(caseId);
    if (!user || !item) {
      return false;
    }
    const context = {
      projectId: item.projectId,
      committeeCaseId: caseId,
      committeeType: item.committee,
      workflowStatus: item.status
    };
    if (this.permissions.can(user, 'VIEW_ALL_REVIEWS', context)) {
      return true;
    }
    if (reviewerId !== userId) {
      return false;
    }
    return this.store.snapshot().assignments.some((row) =>
      row.caseId === caseId && row.reviewerId === userId && row.status !== 'REJECTED' && row.status !== 'CONFLICT'
    );
  }

  save(assignmentId: string, criteria: Record<string, string>, submit: boolean): Review {
    const assignment = this.assignments.getAssignment(assignmentId);
    const user = this.auth.currentUser();
    if (!assignment || !user || user.id !== assignment.reviewerId || assignment.status !== 'ACCEPTED') {
      throw new Error('Asignación no encontrada');
    }
    const currentCase = this.cases.get(assignment.caseId);
    const allowed = this.permissions.can(user, submit ? 'SUBMIT_OWN_REVIEW' : 'EDIT_OWN_REVIEW', {
      projectId: currentCase?.projectId,
      committeeCaseId: assignment.caseId,
      committeeType: currentCase?.committee,
      workflowStatus: currentCase?.status
    });
    if (!allowed) {
      throw new Error('Asignación no encontrada');
    }
    const current = this.cases.get(assignment.caseId);
    const existing = this.store.snapshot().reviews.find((item) =>
      item.assignmentId === assignmentId && item.cycleNumber === (current?.currentCycle ?? 1) && !item.submittedAt
    ) ?? this.store.snapshot().reviews.find((item) =>
      item.assignmentId === assignmentId && item.cycleNumber === (current?.currentCycle ?? 1)
    );
    const review: Review = {
      id: existing?.id ?? uid('rev'),
      assignmentId,
      caseId: assignment.caseId,
      reviewerId: assignment.reviewerId,
      cycleNumber: current?.currentCycle ?? 1,
      criteria,
      submittedAt: submit ? nowIso() : existing?.submittedAt,
      privacy: 'COMMITTEE_ONLY',
      formDefinitionId: current ? reviewFormFor(current.committee).id : 'CEC_REVIEW_V1'
    };
    this.store.update((state) => ({
      ...state,
      reviews: [review, ...state.reviews.filter((item) => item.id !== review.id)],
      assignments: submit
        ? state.assignments.map((item) => (item.id === assignmentId ? { ...item, status: 'COMPLETED' } : item))
        : state.assignments
    }));
    if (submit) {
      this.audit.record({
        action: 'REVIEW_SUBMITTED',
        objectType: 'Review',
        objectId: review.id,
        description: 'Envió revisión',
        projectId: this.cases.get(assignment.caseId)?.projectId
      });
      const active = this.assignments.currentBySlot(assignment.caseId)
        .map((item) => item.assignment)
        .filter((item) => !!item && item.status !== 'REJECTED' && item.status !== 'CONFLICT');
      const done = active.length === 2 && active.every((item) => item!.status === 'COMPLETED' || item!.id === assignmentId);
      if (done) {
        this.cases.setStatus(assignment.caseId, 'IN_REVIEW');
      }
    }
    return review;
  }
}
