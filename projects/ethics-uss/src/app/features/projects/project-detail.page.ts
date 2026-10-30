import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { DemoAuthService } from '../../core/auth/demo-auth.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { AuditEvent, CommitteeCase, EthicsCommittee } from '../../core/models/ethics.models';
import { AuditService } from '../../core/services/audit.service';
import { EthicsStore } from '../../core/services/ethics-store.service';
import { EthicsSubmissionService } from '../../core/services/ethics-submission.service';
import { hasScope } from '../../core/workflow/role-capabilities';
import { PermissionService } from '../../core/services/permission.service';
import { ProjectService } from '../../core/services/project.service';
import { ReviewerService } from '../../core/services/reviewer.service';
import { historyEventVisible, workspaceVisibility } from '../../core/workflow/committee-workspace.policy';
import { isOperationalReviewStatus } from '../../core/workflow/review-assignment-status';
import { COMMITTEE_LABELS, COMMITTEE_SHORT_LABELS } from '../../mocks/catalogs';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';
import { currentProcessKey, presentCommittees } from './committee-presentation';
import { CommitteeWorkspaceComponent } from './committee-workspace.component';
import { ProjectCommitteeTabsComponent, ProjectSectionTab } from './project-committee-tabs.component';
import { RequestDocumentsComponent } from './request-documents.component';

interface SummaryActivity {
  date: string;
  action: string;
  fallback: string;
}

interface SummaryCommittee {
  committee: EthicsCommittee;
  code: string;
  status: string;
  statusLabelKey: string | null;
  receivedAt: string;
  expiresAt: string | null;
  approvalUntil: string | null;
  approvalIssuedAt: string | null;
  expiry: { daysLeft: number; semaphore: 'VIGENTE' | 'PROXIMO' | 'VENCIDO' } | null;
  processKey: string | null;
  activity: SummaryActivity | null;
  reviewerLine: string | null;
}

@Component({
  selector: 'eth-project-detail-page',
  imports: [StatusChipComponent, CommitteeWorkspaceComponent, ProjectCommitteeTabsComponent, RequestDocumentsComponent, ...I18N_IMPORTS],
  templateUrl: './project-detail.page.html',
  styles: `
    .kicker {
      margin: 0;
    }
    .title-row {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.45rem 0.75rem;
      margin: 0.1rem 0 0.85rem;
    }
    .title-row h1 {
      margin: 0;
    }
    .summary {
      container: summary / inline-size;
    }
    .summary-block {
      margin: 0 0 1rem;
    }
    .summary-block h2 {
      margin: 0 0 0.45rem;
      font-size: 0.78rem;
      font-weight: 700;
      letter-spacing: 0.04em;
      text-transform: uppercase;
      color: var(--eth-muted);
    }
    .facts {
      display: grid;
      grid-template-columns: minmax(0, 1fr);
      gap: 0.55rem 1.25rem;
      margin: 0;
    }
    .facts dt {
      color: var(--eth-muted);
      font-size: 0.875rem;
      font-weight: 650;
      line-height: 1.3;
    }
    .facts dd {
      margin: 0.1rem 0 0;
    }
    .committee-list {
      list-style: none;
      margin: 0;
      padding: 0;
      border-top: 1px solid var(--eth-border);
    }
    .committee-row {
      display: grid;
      grid-template-columns: minmax(0, 1fr);
      gap: 0.45rem 1rem;
      align-items: end;
      padding: 0.7rem 0;
      border-bottom: 1px solid var(--eth-border);
    }
    .committee-head {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.35rem 0.65rem;
    }
    .committee-body {
      container: committee / inline-size;
      min-width: 0;
    }
    .committee-head h3 {
      margin: 0;
      font-size: 1rem;
      line-height: 1.3;
    }
    .meta {
      margin: 0.35rem 0 0;
      color: var(--eth-ink);
      font-size: 0.9375rem;
      line-height: 1.4;
    }
    .committee-meta {
      display: grid;
      grid-template-columns: minmax(0, 1fr);
      gap: 0.85rem 1.5rem;
      margin: 0.85rem 0 0;
    }
    .committee-meta dt {
      margin: 0;
      color: var(--eth-muted);
      font-size: 0.875rem;
      font-weight: 650;
      line-height: 1.3;
    }
    .committee-meta dd {
      margin: 0.2rem 0 0;
      font-size: 1rem;
      line-height: 1.4;
    }
    .committee-meta .wide {
      grid-column: 1 / -1;
    }
    .mark {
      font-weight: 700;
    }
    .mark[data-semaphore='VIGENTE'] {
      color: var(--eth-success);
    }
    .mark[data-semaphore='VENCIDO'] {
      color: var(--eth-danger);
    }
    .committee-row .ghost {
      justify-self: start;
      white-space: nowrap;
    }
    .empty {
      margin: 0;
      color: var(--eth-muted);
    }
    @container committee (min-width: 28rem) {
      .committee-meta {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }
    }
    @container summary (min-width: 36rem) {
      .facts {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }
      .committee-row {
        grid-template-columns: minmax(0, 1fr) auto;
      }
      .committee-row .ghost {
        justify-self: end;
      }
    }
    @container summary (min-width: 52rem) {
      .facts {
        grid-template-columns: repeat(3, minmax(0, 1fr));
      }
    }
    .case-meta {
      display: grid;
      gap: 0.2rem;
      margin: 0.7rem 0 1rem;
    }
    .case-meta p {
      margin: 0;
    }
    h3 {
      margin: 1.15rem 0 0.55rem;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ProjectDetailPage {
  readonly id = input.required<string>();
  private readonly projects = inject(ProjectService);
  private readonly submissions = inject(EthicsSubmissionService);
  private readonly audit = inject(AuditService);
  private readonly auth = inject(DemoAuthService);
  private readonly permissions = inject(PermissionService);
  private readonly reviewers = inject(ReviewerService);
  private readonly store = inject(EthicsStore);
  readonly i18n = inject(I18nService);
  readonly tab = signal('resumen');
  readonly shortCommittees = COMMITTEE_SHORT_LABELS;
  readonly row = computed(() => this.projects.get(this.id()));
  readonly committees = COMMITTEE_LABELS;
  readonly groups = computed(() => {
    const row = this.row();
    const user = this.auth.currentUser();
    const cases = row?.cases ?? [];
    if (!user || hasScope(user, 'GLOBAL') || hasScope(user, 'OWN_PROJECT')) {
      return presentCommittees(cases);
    }
    if (hasScope(user, 'COMMITTEE')) {
      return presentCommittees(cases.filter((item) => user.committees.includes(item.committee)));
    }
    return presentCommittees(cases.filter((item) =>
      this.permissions.can(user, 'VIEW_COMMITTEE_WORKSPACE', {
        projectId: item.projectId,
        committeeCaseId: item.id,
        committeeType: item.committee,
        workflowStatus: item.status
      })
    ));
  });
  readonly requestDate = computed(() => {
    const project = this.row()?.project;
    const submission = this.submissions.byProject(this.id());
    return submission?.submittedAt ?? project?.createdAt ?? null;
  });
  readonly summaryCommittees = computed(() => this.groups().map((group) => this.toSummary(group)));
  readonly navTabs = computed<ProjectSectionTab[]>(() => [
    { id: 'resumen', labelKey: 'projects.summary', titleKey: 'projects.summary' },
    ...this.groups().map((group) => ({
      id: group.committee,
      labelKey: COMMITTEE_SHORT_LABELS[group.committee] ?? group.committee,
      titleKey: COMMITTEE_LABELS[group.committee] ?? group.committee
    }))
  ]);

  private toSummary(group: ReturnType<typeof presentCommittees>[number]): SummaryCommittee {
    const user = this.auth.currentUser();
    const approval = group.records
      .filter((item) => item.approvalExpiresAt)
      .sort((a, b) => (b.approvalExpiresAt ?? '').localeCompare(a.approvalExpiresAt ?? ''))[0];
    const ids = new Set(group.records.map((item) => item.id));
    const visibility = workspaceVisibility(
      user?.role,
      group.active.status,
      !!user && this.reviewers.currentBySlot(group.active.id).some((slot) =>
        !!slot.assignment && slot.assignment.reviewerId === user.id && isOperationalReviewStatus(slot.assignment.status)
      )
    );
    const activity = this.audit.list()
      .filter((event) => this.eventOfCommittee(event, ids))
      .filter((event) => historyEventVisible(event.action, visibility.history))
      .sort((a, b) => b.date.localeCompare(a.date))[0];
    return {
      committee: group.committee,
      code: group.active.code,
      status: group.active.status,
      statusLabelKey: group.statusLabelKey,
      receivedAt: group.active.receivedAt,
      expiresAt: group.expiresAt,
      approvalUntil: group.approvalUntil,
      approvalIssuedAt: approval?.approvalIssuedAt ?? null,
      expiry: approval ? this.projects.expiryInfo(approval) : null,
      processKey: currentProcessKey(group.active),
      activity: activity ? { date: activity.date, action: activity.action, fallback: activity.description } : null,
      reviewerLine: this.reviewerLine(group.active)
    };
  }

  private reviewerLine(item: CommitteeCase): string | null {
    this.i18n.currentLanguage();
    const user = this.auth.currentUser();
    if (!user || !this.permissions.can(user, 'VIEW_REVIEWER_ASSIGNMENTS', {
      projectId: item.projectId,
      committeeCaseId: item.id,
      committeeType: item.committee,
      workflowStatus: item.status
    })) {
      return null;
    }
    const slots = this.reviewers.currentBySlot(item.id)
      .map((slot) => slot.assignment)
      .filter((assignment) => !!assignment && assignment.status !== 'REJECTED' && assignment.status !== 'CONFLICT');
    if (!slots.length) {
      return null;
    }
    const pending = slots.filter((assignment) => assignment!.status === 'PENDING').length;
    if (pending > 0) {
      const key = pending === 1 ? 'reviewers.summaryPending' : 'reviewers.summaryPendingMany';
      return this.i18n.translate(key, { assigned: slots.length, pending });
    }
    const names = slots
      .map((assignment) => this.store.snapshot().users.find((person) => person.id === assignment!.reviewerId)?.displayName ?? '')
      .filter((name) => name.length > 0)
      .map((name) => name.replace(/^(Dr\.|Dra\.)\s+/i, ''));
    return names.join(' · ');
  }

  private eventOfCommittee(event: AuditEvent, ids: Set<string>): boolean {
    return (!!event.caseId && ids.has(event.caseId)) || ids.has(event.objectId);
  }
}
