import { ChangeDetectionStrategy, Component } from '@angular/core';
import { COMMITTEE_LABELS } from '../../mocks/catalogs';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';

@Component({
  selector: 'eth-committees-page',
  imports: [...I18N_IMPORTS],
  template: `
    <section>
      <h1>{{ 'committees.title' | translate }}</h1>
      <div class="grid-2">
        @for (code of codes; track code) {
          <article class="card">
            <h2>{{ labels[code] | translate }}</h2>
            <p class="muted">{{ 'committees.independent' | translate }}</p>
          </article>
        }
      </div>
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CommitteesPage {
  readonly codes = ['CEC', 'CECUA', 'BIOSEGURIDAD'] as const;
  readonly labels = COMMITTEE_LABELS;
}
