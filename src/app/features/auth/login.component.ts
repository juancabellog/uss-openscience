import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { merge } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { InstitutionalHeaderComponent } from '../../layout/institutional-header/institutional-header.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { I18N_HOST, I18N_IMPORTS } from '../../shared/i18n/i18n.imports';

@Component({
  selector: 'os-login',
  hostDirectives: [...I18N_HOST],
  imports: [...I18N_IMPORTS, ReactiveFormsModule, RouterLink, InstitutionalHeaderComponent, IconComponent],
  templateUrl: './login.component.html',
  styleUrl: './auth-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly showPassword = signal(false);
  readonly submitted = signal(false);
  readonly passwordUpdated = computed(
    () => this.route.snapshot.queryParamMap.get('passwordUpdated') === '1'
  );
  readonly form = new FormGroup({
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required] })
  });
  private readonly formTick = toSignal(merge(this.form.valueChanges, this.form.statusChanges), {
    initialValue: null
  });
  readonly emailError = computed(() => {
    this.formTick();
    return fieldMessage(this.form.controls.email, this.submitted() || this.form.controls.email.touched, 'email');
  });
  readonly passwordError = computed(() => {
    this.formTick();
    return fieldMessage(this.form.controls.password, this.submitted() || this.form.controls.password.touched, 'password');
  });

  togglePassword(): void {
    this.showPassword.update((value) => !value);
  }

  async submit(): Promise<void> {
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
    try {
      await this.auth.signIn(this.form.controls.email.value, this.form.controls.password.value);
      const returnUrl = this.auth.safeReturnUrl(this.route.snapshot.queryParamMap.get('returnUrl'));
      await this.router.navigateByUrl(returnUrl);
    } catch (err) {
      this.error.set(loginErrorKey(err));
    } finally {
      this.busy.set(false);
    }
  }
}

function fieldMessage(
  control: FormControl<string>,
  visible: boolean,
  field: 'email' | 'password'
): string | null {
  if (!visible || control.valid) {
    return null;
  }
  if (field === 'email' && control.hasError('email')) {
    return 'auth.emailInvalid';
  }
  return field === 'email' ? 'auth.emailRequired' : 'auth.passwordRequired';
}

export function messageOf(error: unknown, fallback: string): string {
  const body = (error as { error?: { message?: string; code?: string } })?.error;
  return body?.message || fallback;
}

function loginErrorKey(error: unknown): string {
  const http = error instanceof HttpErrorResponse ? error : null;
  const code = String(http?.error?.code ?? '').toUpperCase();
  if (code === 'AUTH_PENDING') {
    return 'auth.pending';
  }
  if (code === 'AUTH_LOCKED') {
    return 'auth.locked';
  }
  if (code === 'AUTH_INVALID' || code === 'AUTH_RATE_LIMITED' || http?.status === 401) {
    return 'auth.loginError';
  }
  if (http && (http.status === 0 || http.status >= 500)) {
    return 'auth.loginUnavailable';
  }
  return http?.status ? 'auth.loginError' : 'auth.loginUnavailable';
}
