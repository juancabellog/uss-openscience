import { Injectable } from '@angular/core';
import { COMMITTEE_LABELS } from '../../mocks/catalogs';
import {
  CommitteeCase,
  EthicsCommittee,
  GlobalProjectStatus,
  Project,
  User
} from '../models/ethics.models';
import { DemoAuthService } from '../auth/demo-auth.service';
import { EthicsStore } from './ethics-store.service';
import { addYears, daysUntil } from './ids';

export interface ProjectRow {
  project: Project;
  researcher: User | undefined;
  globalStatus: GlobalProjectStatus;
  cases: CommitteeCase[];
  lastActivity: string;
}

@Injectable({ providedIn: 'root' })
export class ProjectService {
  constructor(
    private readonly store: EthicsStore,
    private readonly auth: DemoAuthService
  ) {}

  listVisible(): ProjectRow[] {
    const state = this.store.snapshot();
    const user = this.auth.currentUser();
    return state.projects
      .filter((project) => this.canSee(project, user, state.cases))
      .map((project) => this.toRow(project))
      .sort((a, b) => b.lastActivity.localeCompare(a.lastActivity));
  }

  get(id: string): ProjectRow | undefined {
    const state = this.store.snapshot();
    const project = state.projects.find((item) => item.id === id);
    if (!project || !this.canSee(project, this.auth.currentUser(), state.cases)) {
      return undefined;
    }
    return this.toRow(project);
  }

  researcherOf(project: Project): User | undefined {
    return this.store.snapshot().users.find((user) => user.id === project.researcherId);
  }

  globalStatus(projectId: string): GlobalProjectStatus {
    const state = this.store.snapshot();
    const submission = state.submissions.find((item) => item.projectId === projectId);
    const cases = state.cases.filter((item) => item.projectId === projectId);
    if (!submission) {
      return 'DRAFT';
    }
    if (submission.status === 'DRAFT') {
      return 'DRAFT';
    }
    if (submission.status === 'SUBMITTED' && !submission.classification.validated) {
      return 'PENDING_CLASSIFICATION';
    }
    if (cases.some((item) => item.status === 'WITH_OBSERVATIONS' || item.status === 'AWAITING_RESEARCHER')) {
      return 'WITH_OBSERVATIONS';
    }
    if (cases.length && cases.every((item) => item.status === 'APPROVED')) {
      return 'APPROVED';
    }
    if (cases.length && cases.every((item) => item.status === 'EXEMPT')) {
      return 'EXEMPT';
    }
    if (cases.some((item) => item.status === 'REJECTED') && cases.every((item) => ['REJECTED', 'APPROVED', 'EXEMPT'].includes(item.status))) {
      return 'REJECTED';
    }
    if (cases.some((item) => item.status === 'APPROVED') && cases.some((item) => !['APPROVED', 'EXEMPT', 'REJECTED'].includes(item.status))) {
      return 'MIXED';
    }
    return 'IN_EVALUATION';
  }

  committeeChip(committee: EthicsCommittee | 'NO_REQUIERE_COMITE', status?: string): { label: string; status: string } {
    return { label: COMMITTEE_LABELS[committee], status: status ?? 'No aplica' };
  }

  expiryInfo(item: CommitteeCase): { daysLeft: number; semaphore: 'VIGENTE' | 'PROXIMO' | 'VENCIDO' } | null {
    if (!item.approvalExpiresAt) {
      return null;
    }
    const daysLeft = daysUntil(item.approvalExpiresAt);
    return {
      daysLeft,
      semaphore: daysLeft < 0 ? 'VENCIDO' : daysLeft <= 90 ? 'PROXIMO' : 'VIGENTE'
    };
  }

  computeExpiry(issuedAt: string): string {
    return addYears(issuedAt, 1);
  }

  private toRow(project: Project): ProjectRow {
    const state = this.store.snapshot();
    const cases = state.cases.filter((item) => item.projectId === project.id);
    const audit = state.audit.filter((item) => item.projectId === project.id)[0];
    return {
      project,
      researcher: state.users.find((user) => user.id === project.researcherId),
      globalStatus: this.globalStatus(project.id),
      cases,
      lastActivity: audit?.date ?? project.updatedAt
    };
  }

  private canSee(project: Project, user: User | null, cases: CommitteeCase[]): boolean {
    if (!user) {
      return false;
    }
    if (user.role === 'ADMINISTRATOR') {
      return true;
    }
    if (user.role === 'RESEARCHER') {
      return project.researcherId === user.id;
    }
    const related = cases.filter((item) => item.projectId === project.id);
    if (user.role === 'COMMITTEE_PRESIDENT' || user.role === 'EXECUTIVE_SECRETARY') {
      return related.some((item) => user.committees.includes(item.committee));
    }
    return related.some((item) => item.reviewerIds.includes(user.id));
  }
}
