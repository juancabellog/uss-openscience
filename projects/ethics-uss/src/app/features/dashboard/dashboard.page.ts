import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { COMMITTEE_LABELS, ROLE_LABELS } from '../../mocks/catalogs';
import { DemoAuthService } from '../../core/auth/demo-auth.service';
import { CommitteeCaseService } from '../../core/services/committee-case.service';
import { ProjectService } from '../../core/services/project.service';
import { ReviewerService } from '../../core/services/reviewer.service';
import { EthicsSubmissionService } from '../../core/services/ethics-submission.service';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';

@Component({
  selector: 'eth-dashboard-page',
  imports: [RouterLink, StatusChipComponent, ...I18N_IMPORTS],
  templateUrl: './dashboard.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DashboardPage {
  private readonly auth = inject(DemoAuthService);
  private readonly projects = inject(ProjectService);
  private readonly cases = inject(CommitteeCaseService);
  private readonly assignments = inject(ReviewerService);
  private readonly submissions = inject(EthicsSubmissionService);
  readonly user = this.auth.currentUser;
  readonly roleLabelKey = computed(() => ROLE_LABELS[this.user()?.role ?? ''] ?? '');
  readonly activeCommittee = this.auth.activeCommittee;
  readonly rows = computed(() => this.projects.listVisible());
  readonly kpis = computed(() => {
    const rows = this.rows();
    return {
      projects: rows.length,
      evaluation: rows.filter((row) => row.globalStatus === 'IN_EVALUATION' || row.globalStatus === 'MIXED').length,
      observations: rows.filter((row) => row.globalStatus === 'WITH_OBSERVATIONS').length,
      approved: rows.filter((row) => row.globalStatus === 'APPROVED').length,
      expiring: rows.flatMap((row) => row.cases).filter((item) => {
        const info = this.projects.expiryInfo(item);
        return info?.semaphore === 'PROXIMO';
      }).length
    };
  });
  readonly secretaryBuckets = computed(() => {
    const active = this.auth.activeCommittee();
    const committees = active ? [active] : (this.user()?.committees ?? []);
    const items = this.user()?.role === 'ADMINISTRATOR' ? this.cases.list() : this.cases.byCommittee(committees);
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
  readonly pendingClass = computed(() => this.submissions.pendingClassification().length);
  readonly myAssignments = computed(() => this.assignments.assignmentsFor(this.user()?.id ?? ''));
  readonly committees = COMMITTEE_LABELS;
}
