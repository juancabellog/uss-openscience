import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { COMMITTEE_LABELS } from '../../mocks/catalogs';
import { EthicsSubmissionService } from '../../core/services/ethics-submission.service';
import { EthicsStore } from '../../core/services/ethics-store.service';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';

@Component({
  selector: 'eth-submission-inbox-page',
  imports: [RouterLink, EmptyStateComponent, ...I18N_IMPORTS],
  template: `
    <section>
      <h1>{{ 'classification.inboxTitle' | translate }}</h1>
      @if (!rows().length) {
        <eth-empty-state title="classification.emptyTitle" message="classification.emptyMessage" />
      }
      <div class="table-wrap panel">
        <table>
          <thead>
            <tr>
              <th>{{ 'cases.diseiCode' | translate }}</th>
              <th>{{ 'classification.project' | translate }}</th>
              <th>{{ 'classification.researcher' | translate }}</th>
              <th>{{ 'classification.type' | translate }}</th>
              <th>{{ 'classification.date' | translate }}</th>
              <th>{{ 'classification.automaticCol' | translate }}</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            @for (row of rows(); track row.submission.id) {
              <tr>
                <td>{{ row.project?.code }}</td>
                <td>{{ row.project?.title }}</td>
                <td>{{ row.researcher }}</td>
                <td>{{ (row.submission.type === 'VALIDATION' ? 'dashboard.typeValidation' : 'dashboard.typeNew') | translate }}</td>
                <td>{{ row.submission.submittedAt | localeDate }}</td>
                <td>@for (code of row.submission.classification.automatic; track code) { {{ labels[code] | translate }} }</td>
                <td><a [routerLink]="['/solicitudes', row.submission.id]">{{ 'classification.review' | translate }}</a></td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SubmissionInboxPage {
  private readonly submissions = inject(EthicsSubmissionService);
  private readonly store = inject(EthicsStore);
  readonly labels = COMMITTEE_LABELS;
  readonly rows = computed(() => {
    const state = this.store.snapshot();
    return this.submissions.pendingClassification().map((submission) => {
      const project = state.projects.find((item) => item.id === submission.projectId);
      const researcher = state.users.find((item) => item.id === project?.researcherId);
      return { submission, project, researcher: researcher?.displayName ?? '—' };
    });
  });
}
