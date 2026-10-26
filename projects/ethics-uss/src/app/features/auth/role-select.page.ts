import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { COMMITTEE_LABELS, ROLE_LABELS } from '../../mocks/catalogs';
import { DemoAuthService } from '../../core/auth/demo-auth.service';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';
import { LanguageSwitcherComponent } from '../../shared/i18n/language-switcher.component';

@Component({
  selector: 'eth-role-select-page',
  imports: [LanguageSwitcherComponent, ...I18N_IMPORTS],
  template: `
    <section class="panel enter">
      <div class="actions" style="margin-top:0;justify-content:space-between">
        <p class="muted">{{ 'auth.prototypeNote' | translate }}</p>
        <eth-language-switcher />
      </div>
      <h1>{{ 'brand.full' | translate }}</h1>
      <p>{{ 'auth.selectProfile' | translate }}</p>
      <div class="grid-2">
        @for (user of users; track user.id) {
          <button type="button" class="choice-card" (click)="enter(user.id)">
            <strong>{{ user.displayName }}</strong>
            <span class="muted">
              {{ labels[user.role] | translate }}
              @if (user.role === 'COMMITTEE_PRESIDENT' || user.role === 'EXECUTIVE_SECRETARY') {
                · {{ committeeLabels[user.committees[0]] | translate }}
              }
            </span>
            <span>{{ user.email }}</span>
          </button>
        }
      </div>
    </section>
  `,
  styles: `
    :host { display: block; max-width: 56rem; margin: 3rem auto; padding: 0 1rem; }
    h1 { margin-top: 0.3rem; }
    .choice-card { text-align: left; display: grid; gap: 0.2rem; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RoleSelectPage {
  private readonly auth = inject(DemoAuthService);
  private readonly router = inject(Router);
  readonly users = this.auth.users();
  readonly labels = ROLE_LABELS;
  readonly committeeLabels = COMMITTEE_LABELS;

  enter(id: string): void {
    this.auth.assume(id);
    void this.router.navigateByUrl('/');
  }
}
