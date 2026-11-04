import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { OperationRun, watchOperation } from '../../../../../../shared/operation-feedback/operation-feedback';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { SharedSessionSignIn } from '../../core/auth/shared-session-sign-in';
import {
  INSTITUTIONAL_CAMPUSES,
  INSTITUTIONAL_FACULTIES,
  INSTITUTIONAL_POSITIONS
} from '../../core/forms/institutional-options';
import { AppLanguage } from '../../core/i18n/i18n.models';
import { I18nService } from '../../core/i18n/i18n.service';
import { IconComponent } from '../../shared/components/icon.component';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';
import { LanguageSwitcherComponent } from '../../shared/i18n/language-switcher.component';

type PreviewState = {
  status: 'loading' | 'VALID' | 'INVALID' | 'EXPIRED' | 'USED' | 'ALREADY_ACTIVE';
  maskedEmail?: string | null;
  profileRequired?: boolean;
  pureReadonly?: boolean;
  givenName?: string | null;
  familyName?: string | null;
  positionTitle?: string | null;
};

@Component({
  selector: 'eth-activate-account-page',
  imports: [ReactiveFormsModule, RouterLink, LanguageSwitcherComponent, IconComponent, ...I18N_IMPORTS],
  template: `
    <div class="eth-login">
      <section class="eth-login__card eth-login__card--wide">
        <div class="eth-login__lang">
          <eth-language-switcher />
        </div>
        <header class="eth-login__intro">
          <h1>{{ 'auth.activateTitle' | translate }}</h1>
          @if (!done() && preview().status === 'VALID') {
            <p class="eth-login__lead">{{ (preview().pureReadonly ? 'auth.activatePureSource' : preview().profileRequired ? 'auth.activateLocalIntro' : 'auth.activateHelp') | translate }}</p>
          }
        </header>
        @if (done()) {
          <div class="eth-login__status" role="status">
            <p>{{ 'auth.activateSuccess' | translate }}</p>
          </div>
          <a class="btn eth-login__submit" routerLink="/ingresar">{{ 'auth.goToLogin' | translate }}</a>
        } @else if (preview().status === 'loading') {
          <p role="status">{{ 'auth.validatingInvite' | translate }}</p>
        } @else if (preview().status === 'ALREADY_ACTIVE') {
          <div class="eth-login__status" role="status">
            <p>{{ 'auth.alreadyActivated' | translate }}</p>
          </div>
          <a class="btn eth-login__submit" routerLink="/ingresar">{{ 'auth.goToLogin' | translate }}</a>
        } @else if (preview().status === 'EXPIRED') {
          <p class="eth-login__error" role="alert">{{ 'auth.tokenExpired' | translate }}</p>
        } @else if (preview().status === 'USED') {
          <p class="eth-login__error" role="alert">{{ 'auth.tokenUsed' | translate }}</p>
        } @else if (preview().status === 'INVALID') {
          <p class="eth-login__error" role="alert">{{ 'auth.tokenInvalid' | translate }}</p>
        } @else {
          <form [formGroup]="form" (ngSubmit)="submit()">
            <label class="eth-login__field">
              <span>{{ 'auth.account' | translate }}</span>
              <input [value]="preview().maskedEmail || shown(null)" readonly />
            </label>
            @if (preview().pureReadonly) {
              <section class="eth-login__section">
                <h2>{{ 'auth.personalData' | translate }}</h2>
                <p class="eth-login__readonly"><span>{{ 'auth.givenName' | translate }}</span>{{ shown(preview().givenName) }}</p>
                <p class="eth-login__readonly"><span>{{ 'auth.familyName' | translate }}</span>{{ shown(preview().familyName) }}</p>
              </section>
              <section class="eth-login__section">
                <h2>{{ 'auth.institutionalData' | translate }}</h2>
                <p class="eth-login__readonly"><span>{{ 'auth.position' | translate }}</span>{{ shown(preview().positionTitle) }}</p>
              </section>
            } @else if (preview().profileRequired) {
              <section class="eth-login__section">
                <h2>{{ 'auth.personalData' | translate }}</h2>
                <label class="eth-login__field" for="activate-given">
                  <span>{{ 'auth.givenName' | translate }} *</span>
                  <input id="activate-given" formControlName="givenName" autocomplete="given-name" />
                  @if (fieldError('givenName'); as key) { <span class="eth-login__field-error">{{ key | translate }}</span> }
                </label>
                <label class="eth-login__field" for="activate-family">
                  <span>{{ 'auth.familyName' | translate }} *</span>
                  <input id="activate-family" formControlName="familyName" autocomplete="family-name" />
                  @if (fieldError('familyName'); as key) { <span class="eth-login__field-error">{{ key | translate }}</span> }
                </label>
              </section>
              <section class="eth-login__section">
                <h2>{{ 'auth.institutionalData' | translate }}</h2>
                <label class="eth-login__field" for="activate-campus">
                  <span>{{ 'auth.campus' | translate }} *</span>
                  <select id="activate-campus" formControlName="campus">
                    <option value="">{{ 'auth.selectPlaceholder' | translate }}</option>
                    @for (item of campuses; track item) { <option [value]="item">{{ item }}</option> }
                  </select>
                  @if (fieldError('campus'); as key) { <span class="eth-login__field-error">{{ key | translate }}</span> }
                </label>
                <label class="eth-login__field" for="activate-faculty">
                  <span>{{ 'auth.faculty' | translate }} *</span>
                  <select id="activate-faculty" formControlName="faculty">
                    <option value="">{{ 'auth.selectPlaceholder' | translate }}</option>
                    @for (item of faculties; track item) { <option [value]="item">{{ item }}</option> }
                  </select>
                  @if (fieldError('faculty'); as key) { <span class="eth-login__field-error">{{ key | translate }}</span> }
                </label>
                <label class="eth-login__field" for="activate-position">
                  <span>{{ 'auth.position' | translate }} *</span>
                  <select id="activate-position" formControlName="positionTitle">
                    <option value="">{{ 'auth.selectPlaceholder' | translate }}</option>
                    @for (item of positions; track item) { <option [value]="item">{{ item }}</option> }
                  </select>
                  @if (fieldError('positionTitle'); as key) { <span class="eth-login__field-error">{{ key | translate }}</span> }
                </label>
              </section>
            }
            <section class="eth-login__section">
              <h2>{{ 'auth.accessData' | translate }}</h2>
              <label class="eth-login__field" for="activate-password">
                <span>{{ 'auth.newPassword' | translate }} *</span>
                <span class="eth-login__password">
                  <input id="activate-password" [type]="showPassword() ? 'text' : 'password'" formControlName="password" autocomplete="new-password" />
                  <button type="button" class="eth-login__password-toggle" [attr.aria-label]="(showPassword() ? 'auth.hidePassword' : 'auth.showPassword') | translate" (click)="showPassword.update((value) => !value)">
                    <eth-icon [name]="showPassword() ? 'eye-off' : 'eye'" />
                  </button>
                </span>
                <span class="eth-login__note">{{ 'auth.passwordPolicy' | translate }}</span>
                @if (passwordError(); as key) { <span class="eth-login__field-error">{{ key | translate }}</span> }
              </label>
              <label class="eth-login__field" for="activate-confirm">
                <span>{{ 'auth.confirmPassword' | translate }} *</span>
                <span class="eth-login__password">
                  <input id="activate-confirm" [type]="showConfirm() ? 'text' : 'password'" formControlName="confirm" autocomplete="new-password" />
                  <button type="button" class="eth-login__password-toggle" [attr.aria-label]="(showConfirm() ? 'auth.hidePassword' : 'auth.showPassword') | translate" (click)="showConfirm.update((value) => !value)">
                    <eth-icon [name]="showConfirm() ? 'eye-off' : 'eye'" />
                  </button>
                </span>
                @if (confirmError(); as key) { <span class="eth-login__field-error">{{ key | translate }}</span> }
              </label>
            </section>
            @if (mismatch()) {
              <p class="eth-login__error" role="alert">{{ 'auth.passwordMismatch' | translate }}</p>
            } @else if (error(); as key) {
              <p class="eth-login__error" role="alert">{{ key | translate }}</p>
            }
            @if (activationView().showDetail) {
              <p class="eth-login__note" role="status">{{ 'auth.activatingDetail' | translate }}</p>
            }
            @if (activationView().showLong) {
              <p class="eth-login__note" role="status">{{ 'feedback.stillWorking' | translate }}</p>
            }
            <button type="submit" class="btn eth-login__submit uss-op-button" [disabled]="busy()" [attr.aria-busy]="busy() ? true : null">
              @if (busy()) { <span class="uss-op-spinner" aria-hidden="true"></span> }
              {{ (busy() ? 'auth.activating' : 'auth.activateAction') | translate }}
            </button>
          </form>
        }
      </section>
    </div>
  `,
  styleUrl: './ethics-login.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ActivateAccountPage {
  private readonly session = inject(SharedSessionSignIn);
  private readonly i18n = inject(I18nService);
  private readonly route = inject(ActivatedRoute);
  private readonly token = this.route.snapshot.queryParamMap.get('token') ?? '';
  readonly preview = signal<PreviewState>({ status: 'loading' });
  readonly done = signal(false);
  readonly busy = signal(false);
  private readonly activationRun = new OperationRun();
  private stopActivation: (() => void) | null = null;
  readonly activationView = signal(this.activationRun.view());
  readonly submitted = signal(false);
  readonly error = signal<string | null>(null);
  readonly showPassword = signal(false);
  readonly showConfirm = signal(false);
  readonly campuses = INSTITUTIONAL_CAMPUSES;
  readonly faculties = INSTITUTIONAL_FACULTIES;
  readonly positions = INSTITUTIONAL_POSITIONS;
  private readonly tick = signal(0);
  readonly form = new FormGroup({
    givenName: new FormControl('', { nonNullable: true }),
    familyName: new FormControl('', { nonNullable: true }),
    campus: new FormControl('', { nonNullable: true }),
    faculty: new FormControl('', { nonNullable: true }),
    positionTitle: new FormControl('', { nonNullable: true }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(10)] }),
    confirm: new FormControl('', { nonNullable: true, validators: [Validators.required] })
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => this.stopActivation?.());
    const requested = this.route.snapshot.queryParamMap.get('lang');
    if (requested === 'es' || requested === 'en') {
      this.i18n.setLanguage(requested);
    }
    this.form.valueChanges.subscribe(() => this.tick.update((value) => value + 1));
    if (!this.token) {
      this.preview.set({ status: 'INVALID' });
      return;
    }
    void this.load();
  }

  shown(value: string | null | undefined): string {
    const text = value?.trim();
    return text ? text : this.i18n.translate('auth.notAvailable');
  }

  mismatch(): boolean {
    this.tick();
    const password = this.form.controls.password.value;
    const confirm = this.form.controls.confirm.value;
    return confirm.length > 0 && password !== confirm;
  }

  passwordError(): string | null {
    this.tick();
    const control = this.form.controls.password;
    if (!(this.submitted() || control.touched) || control.valid) {
      return null;
    }
    return control.hasError('minlength') ? 'auth.passwordPolicy' : 'auth.newPasswordRequired';
  }

  confirmError(): string | null {
    this.tick();
    const control = this.form.controls.confirm;
    if (!(this.submitted() || control.touched) || control.value.length > 0) {
      return null;
    }
    return 'auth.confirmRequired';
  }

  fieldError(name: 'givenName' | 'familyName' | 'campus' | 'faculty' | 'positionTitle'): string | null {
    this.tick();
    if (!this.preview().profileRequired) {
      return null;
    }
    const control = this.form.controls[name];
    if (!(this.submitted() || control.touched) || control.value.trim().length > 0) {
      return null;
    }
    const keys = {
      givenName: 'auth.givenNameRequired',
      familyName: 'auth.familyNameRequired',
      campus: 'auth.campusRequired',
      faculty: 'auth.facultyRequired',
      positionTitle: 'auth.positionRequired'
    } as const;
    return keys[name];
  }

  private trackActivation(): void {
    if (!this.activationRun.start({
      loadingMessage: this.i18n.translate('auth.activating'),
      detailMessage: this.i18n.translate('auth.activatingDetail'),
      longRunningMessage: this.i18n.translate('feedback.stillWorking'),
      blocking: false
    })) {
      return;
    }
    this.activationView.set(this.activationRun.view());
    this.stopActivation?.();
    this.stopActivation = watchOperation(this.activationRun, () => this.activationView.set(this.activationRun.view()));
  }

  async submit(): Promise<void> {
    if (this.busy()) {
      return;
    }
    this.submitted.set(true);
    const profile = this.preview().profileRequired === true;
    if (profile) {
      const missing = !this.form.controls.givenName.value.trim()
        || !this.form.controls.familyName.value.trim()
        || !this.form.controls.campus.value
        || !this.form.controls.faculty.value
        || !this.form.controls.positionTitle.value;
      if (missing) {
        this.form.markAllAsTouched();
        this.error.set(null);
        return;
      }
    }
    if (this.form.controls.password.invalid || this.form.controls.confirm.invalid || this.mismatch()) {
      this.form.markAllAsTouched();
      this.error.set(this.mismatch() ? 'auth.passwordMismatch' : null);
      return;
    }
    this.busy.set(true);
    this.trackActivation();
    this.error.set(null);
    const failure = await this.session.submitActivation(
      this.token,
      this.form.controls.password.value,
      profile ? {
        givenName: this.form.controls.givenName.value.trim(),
        familyName: this.form.controls.familyName.value.trim(),
        campus: this.form.controls.campus.value,
        faculty: this.form.controls.faculty.value,
        positionTitle: this.form.controls.positionTitle.value
      } : undefined
    );
    this.busy.set(false);
    if (failure == null) {
      this.activationRun.succeed(this.i18n.translate('auth.activateAction'));
    } else {
      this.activationRun.fail(this.i18n.translate(failure === 'AUTH_PROFILE_REQUIRED' ? 'auth.activateProfileRejected' : failure === 'AUTH_TOKEN_INVALID' ? 'auth.tokenInvalid' : 'auth.activateFailed'));
    }
    this.activationView.set(this.activationRun.view());
    if (failure == null) {
      this.done.set(true);
      return;
    }
    if (failure === 'AUTH_PROFILE_REQUIRED') {
      this.form.markAllAsTouched();
      this.error.set('auth.activateProfileRejected');
      return;
    }
    if (failure === 'AUTH_TOKEN_INVALID') {
      this.preview.set({ status: 'INVALID' });
      return;
    }
    this.error.set('auth.activateFailed');
  }

  private async load(): Promise<void> {
    const value = await this.session.previewActivation(this.token);
    if (!value) {
      this.preview.set({ status: 'INVALID' });
      return;
    }
    this.applyLanguage(value.preferredLanguage);
    this.preview.set({
      status: normalizeStatus(value.status, value.valid),
      maskedEmail: value.maskedEmail,
      profileRequired: value.profileRequired === true,
      pureReadonly: value.pureReadonly === true,
      givenName: value.givenName,
      familyName: value.familyName,
      positionTitle: value.positionTitle
    });
  }

  private applyLanguage(language: string | null | undefined): void {
    if (language === 'es' || language === 'en') {
      this.i18n.setLanguage(language as AppLanguage);
    }
  }
}

function normalizeStatus(status: string | undefined, valid: boolean): PreviewState['status'] {
  const value = (status ?? '').toUpperCase();
  if (value === 'VALID' || value === 'INVALID' || value === 'EXPIRED' || value === 'USED' || value === 'ALREADY_ACTIVE') {
    return value;
  }
  return valid ? 'VALID' : 'INVALID';
}
