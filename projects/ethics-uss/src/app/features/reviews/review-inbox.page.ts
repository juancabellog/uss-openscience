import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DemoAuthService } from '../../core/auth/demo-auth.service';
import { CommitteeCaseService } from '../../core/services/committee-case.service';
import { EthicsStore } from '../../core/services/ethics-store.service';
import { ReviewerService } from '../../core/services/reviewer.service';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';

@Component({
  selector: 'eth-review-inbox-page',
  imports: [RouterLink, StatusChipComponent, ...I18N_IMPORTS],
  template: `
    <section>
      <h1>{{ 'reviews.title' | translate }}</h1>
      <div class="grid-2">
        @for (row of rows(); track row.assignment.id) {
          <article class="card">
            <h2>{{ row.title }}</h2>
            <p>{{ row.code }} · {{ row.session ? ('reviews.session' | translate: { session: row.session }) : ('reviews.sessionPending' | translate) }}</p>
            <eth-status-chip [value]="row.assignment.status" />
            <a [routerLink]="['/revisiones', row.assignment.id]">{{ 'reviews.openAssignment' | translate }}</a>
          </article>
        }
      </div>
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ReviewInboxPage {
  private readonly auth = inject(DemoAuthService);
  private readonly assignments = inject(ReviewerService);
  private readonly cases = inject(CommitteeCaseService);
  private readonly store = inject(EthicsStore);
  readonly rows = computed(() =>
    this.assignments.assignmentsFor(this.auth.currentUser()?.id ?? '').map((assignment) => {
      const item = this.cases.get(assignment.caseId);
      const project = this.store.snapshot().projects.find((row) => row.id === item?.projectId);
      return {
        assignment,
        title: project?.title ?? assignment.caseId,
        code: item?.code ?? '',
        session: assignment.sessionId ?? ''
      };
    })
  );
}
