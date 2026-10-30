import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { SharedSessionSignIn } from '../../core/auth/shared-session-sign-in';
import { I18nService } from '../../core/i18n/i18n.service';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';
import { LanguageSwitcherComponent } from '../../shared/i18n/language-switcher.component';

@Component({
  selector: 'eth-forgot-password-page',
  imports: [FormsModule, RouterLink, LanguageSwitcherComponent, ...I18N_IMPORTS],
  template: `
    <div class="eth-login">
      <div class="eth-login__lang">
        <eth-language-switcher />
      </div>
      <section class="eth-login__card">
        <header class="eth-login__intro">
          <h1>{{ 'brand.full' | translate }}</h1>
          <p class="eth-login__lead">{{ 'auth.forgotLead' | translate }}</p>
        </header>
        @if (sent()) {
          <p role="status">{{ 'auth.forgotSent' | translate }}</p>
        } @else {
          <form (submit)="$event.preventDefault(); submit()">
            <label class="eth-login__field"><span>{{ 'users.email' | translate }}</span><input type="email" [ngModel]="email()" (ngModelChange)="email.set($event)" name="email" autocomplete="username" /></label>
            <button type="submit" class="btn eth-login__submit" [disabled]="busy()" [attr.aria-busy]="busy()">{{ 'auth.forgot' | translate }}</button>
          </form>
        }
        <a class="eth-login__forgot eth-login__back" routerLink="/ingresar">{{ 'auth.backToLogin' | translate }}</a>
      </section>
    </div>
  `,
  styleUrl: './ethics-login.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ForgotPasswordPage {
  private readonly sharedSession = inject(SharedSessionSignIn);
  private readonly i18n = inject(I18nService);
  readonly email = signal('');
  readonly sent = signal(false);
  readonly busy = signal(false);

  async submit(): Promise<void> {
    if (this.busy()) {
      return;
    }
    this.busy.set(true);
    await this.sharedSession.requestPasswordReset(this.email(), this.i18n.currentLanguage());
    this.busy.set(false);
    this.sent.set(true);
  }
}
