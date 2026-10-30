import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { COMMITTEE_LABELS } from '../../mocks/catalogs';
import { hasScope, profileRoleKey } from '../../core/workflow/role-capabilities';
import { DemoAuthService } from '../../core/auth/demo-auth.service';
import { SessionIdentityService } from '../../core/auth/session-identity.service';
import { User } from '../../core/models/ethics.models';
import { EthicsStore } from '../../core/services/ethics-store.service';
import { ethicsOpen, UserAdminService } from '../../core/services/user-admin.service';
import { institutionalDomain, isAllowedInstitutionalEmail } from '../../core/identity/institutional-email';
import { demoSelectorVisible } from '../../core/auth/demo-mode';
import { SharedSessionSignIn } from '../../core/auth/shared-session-sign-in';
import { I18nService } from '../../core/i18n/i18n.service';
import { environment } from '../../../environments/environment';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';
import { IconComponent } from '../../shared/components/icon.component';
import { LanguageSwitcherComponent } from '../../shared/i18n/language-switcher.component';

@Component({
  selector: 'eth-role-select-page',
  imports: [LanguageSwitcherComponent, IconComponent, FormsModule, RouterLink, ...I18N_IMPORTS],
  template: `
    @if (demoSelector) {
    <section class="panel enter demo-wrap">
      <div class="actions" style="margin-top:0;justify-content:space-between">
        <p class="muted">{{ 'auth.prototypeNote' | translate }}</p>
        <eth-language-switcher />
      </div>
      <h1>{{ 'brand.full' | translate }}</h1>
      <p>{{ 'auth.selectProfile' | translate }}</p>
      <div class="grid-2">
        @for (user of users(); track user.id) {
          <button type="button" class="choice-card" (click)="enter(user.id)">
            <strong>{{ user.displayName }}</strong>
            <span class="muted">
              {{ profile(user) | translate }}
              @if (committeeOf(user)) {
                · {{ committeeLabels[user.committees[0]] | translate }}
              }
            </span>
            <span>{{ user.email }}</span>
          </button>
        }
      </div>
      <form class="panel" (submit)="$event.preventDefault(); register()">
        <h2>{{ 'auth.enterInstitutional' | translate }}</h2>
        <label class="field"><span>{{ 'users.institutionalEmail' | translate }}</span><input type="email" [ngModel]="email()" (ngModelChange)="email.set($event)" name="email" /></label>
        @if (externalAttempt()) {
          <p class="field-error" role="alert">{{ 'users.registrationDenied' | translate }}</p>
        } @else {
          <label class="field"><span>{{ 'users.givenName' | translate }}</span><input [ngModel]="givenName()" (ngModelChange)="givenName.set($event)" name="given" /></label>
          <label class="field"><span>{{ 'users.familyName' | translate }}</span><input [ngModel]="familyName()" (ngModelChange)="familyName.set($event)" name="family" /></label>
          <button type="submit" class="btn">{{ 'auth.enterInstitutional' | translate }}</button>
          @if (message()) {
            <p class="field-error" role="alert">{{ message()! | translate }}</p>
          }
        }
      </form>
    </section>
    } @else {
      <div class="eth-login">
        <section class="eth-login__card">
          <div class="eth-login__top">
            <div class="eth-login__lang">
              <eth-language-switcher />
            </div>
            <header class="eth-login__intro">
              <h1>{{ 'brand.full' | translate }}</h1>
              <p class="eth-login__lead">{{ 'auth.loginLead' | translate }}</p>
              <p class="eth-login__hint">{{ 'auth.sameCredentials' | translate }}</p>
            </header>
          </div>
          @if (panel() === 'denied') {
            <p class="eth-login__error" role="alert">{{ 'auth.ethicsDenied' | translate }}</p>
            <button type="button" class="btn eth-login__submit" (click)="leaveDenied()">{{ 'common.logout' | translate }}</button>
          } @else if (panel() === 'account') {
            <p class="eth-login__note">{{ 'auth.ussOnly' | translate }}</p>
            <form (submit)="$event.preventDefault(); createAccount()">
              <label class="eth-login__field"><span>{{ 'users.institutionalEmail' | translate }}</span><input type="email" [ngModel]="email()" (ngModelChange)="email.set($event)" name="accountEmail" autocomplete="username" /></label>
              @if (message()) {
                <p class="eth-login__error" role="alert">{{ message()! | translate }}</p>
              }
              <button type="submit" class="btn eth-login__submit" [disabled]="busy()" [attr.aria-busy]="busy()">{{ 'auth.createAccount' | translate }}</button>
            </form>
            <button type="button" class="eth-login__link eth-login__back" (click)="showLogin()">{{ 'auth.backToLogin' | translate }}</button>
          } @else {
            <form (submit)="$event.preventDefault(); signIn()">
              <label class="eth-login__field"><span>{{ 'users.email' | translate }}</span><input type="email" [ngModel]="email()" (ngModelChange)="email.set($event)" name="email" autocomplete="username" /></label>
              <label class="eth-login__field">
                <span>{{ 'auth.password' | translate }}</span>
                <span class="eth-login__password">
                  <input [type]="showPassword() ? 'text' : 'password'" [ngModel]="password()" (ngModelChange)="password.set($event)" name="password" autocomplete="current-password" />
                  <button type="button" class="eth-login__password-toggle" [attr.aria-label]="(showPassword() ? 'auth.hidePassword' : 'auth.showPassword') | translate" [attr.aria-pressed]="showPassword()" (click)="showPassword.update((visible) => !visible)">
                    <eth-icon [name]="showPassword() ? 'eye-off' : 'eye'" />
                  </button>
                </span>
              </label>
              <a class="eth-login__forgot" routerLink="/ingresar/recuperar">{{ 'auth.forgot' | translate }}</a>
              @if (message()) {
                <p class="eth-login__error" role="alert">{{ message()! | translate }}</p>
              }
              <button type="submit" class="btn eth-login__submit" [disabled]="busy()" [attr.aria-busy]="busy()">{{ 'auth.signIn' | translate }}</button>
            </form>
            <div class="eth-login__switch">
              <p class="eth-login__switch-row">
                <span>{{ 'auth.noAccount' | translate }}</span>
                <button type="button" class="eth-login__link" (click)="showAccount()">{{ 'auth.createAccount' | translate }}</button>
              </p>
              <p class="eth-login__note">{{ 'auth.ussOnly' | translate }}</p>
            </div>
          }
        </section>
      </div>
    }
  `,
  styleUrl: './ethics-login.scss',
  styles: `
    .demo-wrap { max-width: 56rem; margin: 3rem auto; padding: 0 1rem; }
    .demo-wrap h1 { margin-top: 0.3rem; }
    .choice-card { text-align: left; display: grid; gap: 0.2rem; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RoleSelectPage {
  private readonly auth = inject(DemoAuthService);
  private readonly router = inject(Router);
  private readonly store = inject(EthicsStore);
  private readonly admin = inject(UserAdminService);
  private readonly route = inject(ActivatedRoute);
  private readonly session = inject(SessionIdentityService);
  private readonly sharedSession = inject(SharedSessionSignIn);
  private readonly i18n = inject(I18nService);
  readonly demoSelector = demoSelectorVisible(environment.demoEnabled, environment.production);
  readonly panel = signal<'form' | 'denied' | 'account'>('form');
  readonly password = signal('');
  readonly showPassword = signal(false);
  readonly busy = signal(false);
  readonly users = computed(() => this.store.state().users.filter((user) => ethicsOpen(user)));
  readonly email = signal('');
  readonly externalAttempt = computed(() => {
    const domain = institutionalDomain(this.email());
    return domain != null && !isAllowedInstitutionalEmail(this.email());
  });
  readonly givenName = signal('');
  readonly familyName = signal('');
  readonly message = signal<string | null>(this.route.snapshot.queryParamMap.get('acceso') === 'denegado' ? 'users.registrationDenied' : null);
  readonly committeeLabels = COMMITTEE_LABELS;
  readonly profile = profileRoleKey;
  readonly committeeOf = (user: User) => hasScope(user, 'COMMITTEE') && user.committees.length > 0;

  constructor() {
    if (!this.demoSelector) {
      void this.session.ensure().then((user) => {
        if (user) {
          void this.router.navigateByUrl('/');
          return;
        }
        if (this.session.denied()) {
          this.panel.set('denied');
        }
      });
    }
  }

  enter(id: string): void {
    if (!this.demoSelector) {
      return;
    }
    this.auth.assume(id);
    void this.router.navigateByUrl('/');
  }

  async signIn(): Promise<void> {
    if (this.busy()) {
      return;
    }
    this.busy.set(true);
    this.message.set(null);
    const failure = await this.sharedSession.signIn(this.email(), this.password());
    if (failure) {
      this.message.set(failure === 'invalid' ? 'auth.invalidCredentials' : failure === 'pending' ? 'auth.pendingAccount' : failure === 'locked' ? 'auth.lockedAccount' : 'auth.loginUnavailable');
      this.busy.set(false);
      return;
    }
    const user = await this.session.refresh();
    this.busy.set(false);
    if (user) {
      void this.router.navigateByUrl('/');
      return;
    }
    this.panel.set(this.session.denied() ? 'denied' : 'form');
    if (!this.session.denied()) {
      this.message.set('auth.loginUnavailable');
    }
  }

  showAccount(): void {
    this.message.set(null);
    this.panel.set('account');
  }

  showLogin(): void {
    this.message.set(null);
    this.panel.set('form');
  }

  async createAccount(): Promise<void> {
    if (this.busy()) {
      return;
    }
    this.busy.set(true);
    this.message.set(null);
    const result = await this.sharedSession.startInstitutional(this.email(), this.i18n.currentLanguage());
    this.busy.set(false);
    if (result === 'domain') {
      this.message.set('auth.directRegistrationDenied');
      return;
    }
    if (result === 'login') {
      this.message.set('auth.useExistingLogin');
      return;
    }
    if (result === 'rejected') {
      this.message.set('auth.loginUnavailable');
      return;
    }
    this.message.set('auth.institutionalNextSteps');
  }

  async leaveDenied(): Promise<void> {
    await this.sharedSession.signOut();
    this.session.clear();
    this.password.set('');
    this.panel.set('form');
  }

  register(): void {
    if (this.externalAttempt()) {
      this.message.set('users.registrationDenied');
      return;
    }
    const result = this.admin.selfRegister(this.email(), this.givenName(), this.familyName());
    if (!result.ok) {
      this.message.set(result.code === 'not-found' ? 'users.nameRequired' : 'users.registrationDenied');
      return;
    }
    this.enter(result.userId);
  }
}
