import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { EthicsStore } from '../../core/services/ethics-store.service';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';

@Component({
  selector: 'eth-reviewer-catalog-page',
  imports: [...I18N_IMPORTS],
  template: `
    <section class="panel">
      <h1>{{ 'reviewers.title' | translate }}</h1>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>{{ 'reviewers.name' | translate }}</th>
              <th>{{ 'reviewers.specialty' | translate }}</th>
              <th>{{ 'reviewers.committee' | translate }}</th>
              <th>{{ 'reviewers.load' | translate }}</th>
              <th>{{ 'reviewers.availability' | translate }}</th>
            </tr>
          </thead>
          <tbody>
            @for (user of reviewers; track user.id) {
              <tr>
                <td>{{ user.displayName }}</td>
                <td>{{ user.specialty }}</td>
                <td>{{ user.committees.join(', ') }}</td>
                <td>{{ user.activeLoad }}</td>
                <td>{{ ('catalogs.availability.' + user.availability) | translate }}</td>
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
  readonly reviewers = inject(EthicsStore).snapshot().users.filter((user) => user.role === 'COMMITTEE_MEMBER');
}
