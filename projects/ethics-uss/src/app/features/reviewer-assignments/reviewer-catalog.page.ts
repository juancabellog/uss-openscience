import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { I18nService } from '../../core/i18n/i18n.service';
import { ReviewerService } from '../../core/services/reviewer.service';
import { COMMITTEE_SHORT_LABELS } from '../../mocks/catalogs';
import { profileRoleKey } from '../../core/workflow/role-capabilities';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';

@Component({
  selector: 'eth-reviewer-catalog-page',
  imports: [...I18N_IMPORTS],
  template: `
    <section class="panel">
      <h1>{{ 'reviewers.title' | translate }}</h1>
      <p class="hint">{{ 'reviewers.catalogHint' | translate }}</p>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>{{ 'reviewers.name' | translate }}</th>
              <th>{{ 'reviewers.kind' | translate }}</th>
              <th>{{ 'reviewers.committee' | translate }}</th>
              <th>{{ 'reviewers.activeAssignments' | translate }}</th>
              <th>{{ 'reviewers.availability' | translate }}</th>
            </tr>
          </thead>
          <tbody>
            @for (row of rows(); track row.id) {
              <tr>
                <td>{{ row.name }}</td>
                <td>{{ row.kind }}</td>
                <td>{{ row.committee }}</td>
                <td>{{ row.active }}</td>
                <td>{{ row.availability }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ReviewerCatalogPage {
  private readonly reviewers = inject(ReviewerService);
  private readonly i18n = inject(I18nService);
  readonly rows = computed(() => {
    this.i18n.currentLanguage();
    return this.reviewers.pool().map((user) => {
      const committees = user.committees.map((code) => this.i18n.translate(COMMITTEE_SHORT_LABELS[code] ?? code));
      return {
        id: user.id,
        name: user.displayName,
        kind: this.i18n.translate(profileRoleKey(user)),
        committee: committees.length ? committees.join(', ') : '—',
        active: this.reviewers.activeReviewCount(user.id),
        availability: user.availability ? this.i18n.translate('catalogs.availability.' + user.availability) : '—'
      };
    });
  });
}
