import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, merge, of } from 'rxjs';
import { isAllowedEmailDomain, normalizeEmail, normalizePersonName } from '../../core/auth/allowed-email-domain';
import { AuthPublicConfigDto, RegisterResultDto, SAFE_AUTH_PUBLIC_CONFIG } from '../../core/services/api/auth-api.types';
import { AuthApiService } from '../../core/services/api/auth-api.service';
import { InstitutionalHeaderComponent } from '../../layout/institutional-header/institutional-header.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { I18N_HOST, I18N_IMPORTS } from '../../shared/i18n/i18n.imports';

@Component({
  selector: 'os-register',
  hostDirectives: [...I18N_HOST],
  imports: [...I18N_IMPORTS, ReactiveFormsModule, RouterLink, InstitutionalHeaderComponent, IconComponent],
  templateUrl: './register.component.html',
  styleUrl: './auth-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RegisterComponent {
  private readonly api = inject(AuthApiService);
  readonly busy = signal(false);
  readonly submitted = signal(false);
  readonly error = signal<string | null>(null);
  readonly result = signal<RegisterResultDto | null>(null);
  readonly resendBusy = signal(false);
  readonly resendDone = signal(false);
  readonly ussOnly = signal(SAFE_AUTH_PUBLIC_CONFIG.registrationUssOnly);
  readonly form = new FormGroup({
    firstName: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    lastName: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    email: new FormControl('', {
      nonNullable: true,
      validators: emailValidators(true)
    })
  });
  private readonly formTick = toSignal(merge(this.form.valueChanges, this.form.statusChanges), {
    initialValue: null
  });
  readonly firstNameError = computed(() => {
    this.formTick();
    return requiredMessage(this.form.controls.firstName, this.submitted(), 'auth.firstNameRequired');
  });
  readonly lastNameError = computed(() => {
    this.formTick();
    return requiredMessage(this.form.controls.lastName, this.submitted(), 'auth.lastNameRequired');
  });
  readonly emailError = computed(() => {
    this.formTick();
    return emailMessage(this.form.controls.email, this.submitted());
  });
  readonly created = computed(() => this.result()?.outcome === 'CREATED');
  readonly canResend = computed(() => this.result()?.canResend === true);
  readonly resultKey = computed(() => resultMessageKey(this.result()));
  readonly emailLabelKey = computed(() => registerEmailLabelKey(this.ussOnly()));
  readonly emailPlaceholderKey = computed(() => registerEmailPlaceholderKey(this.ussOnly()));

  constructor() {
    this.api
      .publicConfig()
      .pipe(catchError(() => of(SAFE_AUTH_PUBLIC_CONFIG)))
      .subscribe((config) => this.applyPublicConfig(config));
  }

  submit(): void {
    if (this.busy()) {
      return;
    }
    this.submitted.set(true);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api
      .register({
        firstName: normalizePersonName(this.form.controls.firstName.value),
        lastName: normalizePersonName(this.form.controls.lastName.value),
        email: normalizeEmail(this.form.controls.email.value)
      })
      .subscribe({
        next: (result) => {
          this.result.set(result);
          this.busy.set(false);
        },
        error: (err) => {
          this.error.set(registerErrorKey(err));
          this.busy.set(false);
        }
      });
  }

  resend(): void {
    const email = normalizeEmail(this.form.controls.email.value);
    if (!email || this.resendBusy()) {
      return;
    }
    this.resendBusy.set(true);
    this.api.resendActivation(email).subscribe({
      next: () => {
        this.resendDone.set(true);
        this.resendBusy.set(false);
      },
      error: () => {
        this.resendDone.set(true);
        this.resendBusy.set(false);
      }
    });
  }

  private applyPublicConfig(config: AuthPublicConfigDto): void {
    const ussOnly = config.registrationUssOnly !== false;
    this.ussOnly.set(ussOnly);
    this.form.controls.email.setValidators(emailValidators(ussOnly));
    this.form.controls.email.updateValueAndValidity({ emitEvent: false });
  }
}

export function emailValidators(ussOnly: boolean): ValidatorFn[] {
  return ussOnly
    ? [Validators.required, Validators.email, institutionalEmail]
    : [Validators.required, Validators.email];
}

export function registerEmailLabelKey(ussOnly: boolean): string {
  return ussOnly ? 'auth.institutionalEmailLabel' : 'auth.email';
}

export function registerEmailPlaceholderKey(ussOnly: boolean): string {
  return ussOnly ? 'auth.institutionalEmailPlaceholder' : 'auth.emailPlaceholder';
}

function institutionalEmail(control: AbstractControl): ValidationErrors | null {
  const value = normalizeEmail(String(control.value ?? ''));
  if (!value) {
    return null;
  }
  return isAllowedEmailDomain(value) ? null : { institutionalEmail: true };
}

function requiredMessage(control: FormControl<string>, submitted: boolean, key: string): string | null {
  if ((!submitted && !control.touched) || control.valid) {
    return null;
  }
  return key;
}

function emailMessage(control: FormControl<string>, submitted: boolean): string | null {
  if ((!submitted && !control.touched) || control.valid) {
    return null;
  }
  if (control.hasError('institutionalEmail')) {
    return 'auth.institutionalEmail';
  }
  if (control.hasError('email')) {
    return 'auth.emailInvalid';
  }
  return 'auth.emailRequired';
}

export function resultMessageKey(result: RegisterResultDto | null): string | null {
  if (!result || result.outcome === 'CREATED') {
    return null;
  }
  if (result.outcome === 'EXISTS_ACTIVE') {
    return 'auth.registerExistsActive';
  }
  if (result.outcome === 'EXISTS_PENDING') {
    return 'auth.registerExistsPending';
  }
  return 'auth.registerUnavailable';
}

export function registerErrorKey(error: unknown): string {
  const http = error instanceof HttpErrorResponse ? error : null;
  const code = String(http?.error?.code ?? '').toUpperCase();
  if (code === 'AUTH_EMAIL_DOMAIN' || code === 'EMAIL_DOMAIN_NOT_ALLOWED') {
    return 'auth.institutionalEmail';
  }
  if (code === 'AUTH_EMAIL_INVALID') {
    return 'auth.emailInvalid';
  }
  if (code === 'AUTH_USER_INVALID') {
    return 'auth.registerNameInvalid';
  }
  if (http && (http.status === 0 || http.status >= 500)) {
    return 'auth.registerUnavailable';
  }
  return 'auth.registerUnavailable';
}
