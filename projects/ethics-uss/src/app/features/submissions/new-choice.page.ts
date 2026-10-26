import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';

@Component({
  selector: 'eth-new-choice-page',
  imports: [RouterLink, ...I18N_IMPORTS],
  template: `
    <section>
      <h1>{{ 'submission.choice.title' | translate }}</h1>
      <p>{{ 'submission.choice.intro' | translate }}</p>
      <div class="grid-2">
        <a class="choice-card" routerLink="/proyectos/nuevo/proyecto">
          <h2>{{ 'submission.choice.newTitle' | translate }}</h2>
          <p>{{ 'submission.choice.newBody' | translate }}</p>
        </a>
        <a class="choice-card" routerLink="/proyectos/nuevo/validacion">
          <h2>{{ 'submission.choice.validationTitle' | translate }}</h2>
          <p>{{ 'submission.choice.validationBody' | translate }}</p>
        </a>
      </div>
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NewChoicePage {}
