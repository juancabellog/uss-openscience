import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { COMMITTEE_LABELS, COMMITTEE_SHORT_LABELS } from '../../mocks/catalogs';
import { Capability, hasScope, profileRoleKey, rolesOf } from '../../core/workflow/role-capabilities';
import { DemoAuthService } from '../../core/auth/demo-auth.service';
import { AuditEvent, CommitteeCase, EthicsCommittee } from '../../core/models/ethics.models';
import { AuditService } from '../../core/services/audit.service';
import { CommitteeCaseService } from '../../core/services/committee-case.service';
import { ProjectService } from '../../core/services/project.service';
import { ReviewerService } from '../../core/services/reviewer.service';
import { EthicsSubmissionService } from '../../core/services/ethics-submission.service';
import { EthicsStore } from '../../core/services/ethics-store.service';
import { PermissionService } from '../../core/services/permission.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { historyEventVisible, workspaceVisibility } from '../../core/workflow/committee-workspace.policy';
import { projectBelongsToCommittees } from '../../core/workflow/committee-projects';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';
import { HomeSummaryTileComponent } from '../../shared/components/home-summary-tile.component';
import { INVENTORY_INDICATORS } from '../projects/project-inventory';
import { activeReviews, pendingReviews } from '../reviews/review-inbox.filter';
import { EXPIRY_ATTENTION, HOME_INDICATORS, OWN_EXPIRY_ATTENTION, attentionFor, committeeWorkHome, ownAttentionFor, ownsProjectHome } from './home-work';
import { operationalTiles, showCommitteeProjects } from './home-tiles';
import { reviewAttentionCopy } from './review-work';
import {
  attentionForCapability,
  capabilitiesForRoles,
  institutionalModules,
  matchesCaseQueue
} from './home-institutional';

export interface WorkIndicatorView {
  id: string;
  labelKey: string;
  count: number;
}

export interface AttentionView {
  id: string;
  projectId: string;
  title: string;
  committeeKey: string;
  status: CommitteeCase['status'];
  reasonKey: string;
  activity: { date: string; action: string; fallback: string } | null;
  priority: number;
}

export interface OwnAttentionView {
  id: string;
  projectId: string;
  title: string;
  committeeKey: string;
  reasonKey: string;
  date: string | null;
  priority: number;
}

export interface RecentActivityView {
  id: string;
  date: string;
  projectId: string;
  title: string;
  action: string;
  fallback: string;
}

export interface ReviewAttentionView {
  id: string;
  projectId: string;
  title: string;
  committeeKey: string;
  reasonKey: string;
  statusKey: string;
}

@Component({
  selector: 'eth-dashboard-page',
  imports: [RouterLink, StatusChipComponent, HomeSummaryTileComponent, ...I18N_IMPORTS],
  templateUrl: './dashboard.page.html',
  styleUrl: './dashboard.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DashboardPage {
  private readonly auth = inject(DemoAuthService);
  private readonly projects = inject(ProjectService);
  private readonly cases = inject(CommitteeCaseService);
  private readonly assignments = inject(ReviewerService);
  private readonly submissions = inject(EthicsSubmissionService);
  private readonly permissions = inject(PermissionService);
  private readonly audit = inject(AuditService);
  private readonly store = inject(EthicsStore);
  readonly i18n = inject(I18nService);
  readonly user = this.auth.currentUser;
  readonly committeeScope = computed(() => hasScope(this.user(), 'COMMITTEE'));
  readonly roleLabelKey = computed(() => {
    const user = this.user();
    return user ? profileRoleKey(user) : '';
  });
  readonly activeCommittee = this.auth.activeCommittee;
  readonly rows = computed(() => this.projects.listVisible());
  readonly workHome = computed(() => committeeWorkHome(this.user()));
  readonly ownHome = computed(() => ownsProjectHome(this.user()));
  readonly granted = computed(() => capabilitiesForRoles(rolesOf(this.user())));
  readonly institutionalHome = computed(() => !this.workHome() && !this.ownHome() && institutionalModules(this.granted()).length > 0);
  readonly inventory = computed(() => {
    const rows = this.rows();
    const expiry = (item: CommitteeCase) => this.projects.expiryInfo(item);
    return INVENTORY_INDICATORS.map((item) => ({
      id: item.id,
      labelKey: item.labelKey,
      ariaKey: item.ariaKey,
      count: item.count(rows, expiry),
      query: item.query
    }));
  });
  readonly tiles = computed(() => {
    const user = this.user();
    const capabilities = this.granted();
    const reviewerAssignments = this.assignments.assignmentsFor(user?.id ?? '');
    const membership = new Set(user?.committees ?? []);
    const committeeProjects = this.rows().filter((row) =>
      projectBelongsToCommittees(row.cases.map((item) => item.committee), membership)
    ).length;
    const projects = showCommitteeProjects(capabilities, [...membership], this.workHome() || this.ownHome());
    return operationalTiles(capabilities, projects).map((tile) => ({
      id: tile.id,
      labelKey: tile.labelKey,
      ariaKey: tile.ariaKey,
      route: tile.route,
      query: tile.query,
      priority: tile.priority,
      count: tile.countOf === 'committee-projects'
        ? committeeProjects
        : tile.query['status'] === 'PENDING'
          ? pendingReviews(reviewerAssignments)
          : activeReviews(reviewerAssignments)
    }));
  });
  readonly unassignedReviewer = computed(() => {
    const user = this.user();
    if (!user || hasScope(user, 'GLOBAL') || hasScope(user, 'COMMITTEE') || hasScope(user, 'OWN_PROJECT')) {
      return false;
    }
    return !this.store.state().assignments.some((item) => item.reviewerId === user.id && !item.supersededAt);
  });
  readonly reviewAttention = computed((): ReviewAttentionView[] => {
    const user = this.user();
    if (!user || !this.granted().has('VIEW_OWN_REVIEW')) {
      return [];
    }
    const projects = new Map(this.store.snapshot().projects.map((project) => [project.id, project.title]));
    return this.assignments.assignmentsFor(user.id)
      .map((assignment) => {
        const copy = reviewAttentionCopy(assignment.status);
        const item = this.cases.get(assignment.caseId);
        const title = projects.get(item?.projectId ?? '') ?? '';
        if (!copy || !item || !title) {
          return null;
        }
        return {
          id: assignment.id,
          projectId: item.projectId,
          title,
          committeeKey: this.shortCommittees[item.committee] ?? item.committee,
          reasonKey: copy.reasonKey,
          statusKey: copy.statusKey
        };
      })
      .filter((item): item is ReviewAttentionView => !!item);
  });
  readonly ownAttention = computed((): OwnAttentionView[] =>
    this.rows()
      .flatMap((row) => row.cases.map((item) => this.toOwnAttention(item, row.project.title)))
      .filter((item): item is OwnAttentionView => !!item)
      .sort((a, b) => a.priority - b.priority || (b.date ?? '').localeCompare(a.date ?? ''))
  );
  readonly secretaryBuckets = computed(() => {
    const items = this.scopedCases();
    return {
      admissibility: items.filter((item) => item.status === 'ADMISSIBILITY').length,
      assignment: items.filter((item) => item.status === 'PENDING_ASSIGNMENT' || item.status === 'PENDING_ACCEPTANCE').length,
      review: items.filter((item) => item.status === 'IN_REVIEW').length,
      session: items.filter((item) => item.status === 'PENDING_SESSION').length,
      observations: items.filter((item) => item.status === 'WITH_OBSERVATIONS').length,
      resolution: items.filter((item) => item.status === 'PENDING_RESOLUTION' || item.status === 'PENDING_PRESIDENT_VALIDATION').length,
      expiring: items.filter((item) => this.projects.expiryInfo(item)?.semaphore === 'PROXIMO').length
    };
  });
  readonly indicators = computed((): WorkIndicatorView[] => {
    const items = this.scopedCases();
    const expiring = (item: CommitteeCase) => this.projects.expiryInfo(item)?.semaphore === 'PROXIMO';
    return HOME_INDICATORS
      .filter((indicator) => this.allowsAny(indicator.capabilities))
      .map((indicator) => ({
        id: indicator.id,
        labelKey: indicator.labelKey,
        count: indicator.count(items, expiring)
      }));
  });
  readonly attention = computed((): AttentionView[] => {
    const projects = new Map(this.store.snapshot().projects.map((project) => [project.id, project.title]));
    return this.scopedCases()
      .map((item) => this.toAttention(item, projects.get(item.projectId) ?? ''))
      .filter((item): item is AttentionView => !!item)
      .sort((a, b) => a.priority - b.priority || (b.activity?.date ?? '').localeCompare(a.activity?.date ?? ''));
  });
  readonly recent = computed((): RecentActivityView[] => {
    const scope = new Set(this.scopedCases().map((item) => item.id));
    const projects = new Map(this.store.snapshot().projects.map((project) => [project.id, project.title]));
    const user = this.user();
    return this.audit.list()
      .filter((event) => this.eventInScope(event, scope))
      .filter((event) => {
        const item = this.scopedCases().find((row) => row.id === event.caseId || row.id === event.objectId);
        if (!item || !user) {
          return false;
        }
        const visibility = workspaceVisibility(user.role, item.status, false);
        return historyEventVisible(event.action, visibility.history);
      })
      .slice(0, 5)
      .map((event) => ({
        id: event.id,
        date: event.date,
        projectId: event.projectId ?? '',
        title: projects.get(event.projectId ?? '') ?? '',
        action: event.action,
        fallback: event.description
      }))
      .filter((event) => event.projectId && event.title);
  });
  readonly institutionalTiles = computed(() => {
    const items = this.scopedCases();
    return institutionalModules(this.granted()).map((module) => ({
      ...module,
      count: this.countQueue(module.queue, items)
    }));
  });
  readonly institutionalAttention = computed((): AttentionView[] => {
    const capabilities = this.granted();
    const projects = new Map(this.store.snapshot().projects.map((project) => [project.id, project.title]));
    return this.scopedCases()
      .map((item) => {
        const reason = attentionForCapability(item.status, this.projects.expiryInfo(item)?.semaphore, capabilities);
        const title = projects.get(item.projectId) ?? '';
        if (!reason || !title) {
          return null;
        }
        return {
          id: item.id,
          projectId: item.projectId,
          title,
          committeeKey: this.shortCommittees[item.committee] ?? item.committee,
          status: item.status,
          reasonKey: reason.reasonKey,
          activity: this.latestActivity(item),
          priority: reason.priority
        };
      })
      .filter((item): item is AttentionView => !!item)
      .sort((a, b) => a.priority - b.priority || (b.activity?.date ?? '').localeCompare(a.activity?.date ?? ''));
  });
  readonly committees = COMMITTEE_LABELS;
  readonly shortCommittees = COMMITTEE_SHORT_LABELS;

  private scopedCases(): CommitteeCase[] {
    const user = this.user();
    if (!user) {
      return [];
    }
    if (this.granted().has('VIEW_AUDIT')) {
      return this.cases.list();
    }
    const active = this.auth.activeCommittee();
    const committees: EthicsCommittee[] = active ? [active] : user.committees;
    return this.cases.byCommittee(committees);
  }

  private countQueue(queue: string, items: CommitteeCase[]): number {
    if (queue === 'classification') {
      return this.submissions.pendingClassification().length;
    }
    if (queue === 'admissibility') {
      return items.filter((item) => matchesCaseQueue(item.status, 'admissibility')).length;
    }
    if (queue === 'resolutions') {
      return items.filter((item) => matchesCaseQueue(item.status, 'resolutions')).length;
    }
    return items.filter((item) => this.projects.expiryInfo(item)?.semaphore === 'PROXIMO').length;
  }

  private allowsAny(capabilities: readonly Capability[]): boolean {
    return capabilities.some((capability) => this.allows(capability));
  }

  private allows(capability: Capability): boolean {
    const user = this.user();
    const committee = this.auth.activeCommittee();
    return this.permissions.can(user, capability, committee ? { committeeType: committee } : {});
  }

  private toOwnAttention(item: CommitteeCase, title: string): OwnAttentionView | null {
    if (!title) {
      return null;
    }
    const allowed = (capability: Capability) => this.permissions.can(this.user(), capability, {
      projectId: item.projectId,
      committeeCaseId: item.id,
      committeeType: item.committee,
      workflowStatus: item.status
    });
    const match = ownAttentionFor(item.status, allowed);
    const expiring = this.projects.expiryInfo(item)?.semaphore === 'PROXIMO'
      && OWN_EXPIRY_ATTENTION.capabilities.some(allowed);
    const reason = match ?? (expiring ? { reasonKey: OWN_EXPIRY_ATTENTION.reasonKey, priority: OWN_EXPIRY_ATTENTION.priority } : null);
    if (!reason) {
      return null;
    }
    return {
      id: item.id,
      projectId: item.projectId,
      title,
      committeeKey: this.shortCommittees[item.committee] ?? item.committee,
      reasonKey: reason.reasonKey,
      date: this.ownDate(item, reason.reasonKey),
      priority: reason.priority
    };
  }

  private ownDate(item: CommitteeCase, reasonKey: string): string | null {
    if (reasonKey === OWN_EXPIRY_ATTENTION.reasonKey) {
      return item.approvalExpiresAt ?? null;
    }
    if (reasonKey === 'dashboard.respondObservations') {
      return this.latestActivity(item)?.date ?? item.notifiedAt ?? item.receivedAt;
    }
    return item.receivedAt;
  }

  private toAttention(item: CommitteeCase, title: string): AttentionView | null {
    if (!title) {
      return null;
    }
    const match = attentionFor(item.status, (capability) => this.allows(capability));
    const expiring = this.projects.expiryInfo(item)?.semaphore === 'PROXIMO'
      && EXPIRY_ATTENTION.capabilities.some((capability) => this.allows(capability));
    const reason = match ?? (expiring ? { reasonKey: EXPIRY_ATTENTION.reasonKey, priority: EXPIRY_ATTENTION.priority } : null);
    if (!reason) {
      return null;
    }
    const activity = this.latestActivity(item);
    return {
      id: item.id,
      projectId: item.projectId,
      title,
      committeeKey: this.shortCommittees[item.committee] ?? item.committee,
      status: item.status,
      reasonKey: reason.reasonKey,
      activity,
      priority: reason.priority
    };
  }

  private latestActivity(item: CommitteeCase): AttentionView['activity'] {
    const user = this.user();
    if (!user) {
      return null;
    }
    const visibility = workspaceVisibility(user.role, item.status, false);
    const event = this.audit.list()
      .filter((row) => row.caseId === item.id || row.objectId === item.id)
      .find((row) => historyEventVisible(row.action, visibility.history));
    return event ? { date: event.date, action: event.action, fallback: event.description } : null;
  }

  private eventInScope(event: AuditEvent, scope: Set<string>): boolean {
    return (!!event.caseId && scope.has(event.caseId)) || scope.has(event.objectId);
  }
}
