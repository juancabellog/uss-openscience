import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { merge } from 'rxjs';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { activationFailureKey } from '../../core/auth/auth-public.util';
import {
  INSTITUTIONAL_CAMPUSES,
  INSTITUTIONAL_FACULTIES,
  INSTITUTIONAL_POSITIONS
} from '../../../../projects/ethics-uss/src/app/core/forms/institutional-options';
import { I18nService } from '../../core/i18n/i18n.service';
import { AuthApiService } from '../../core/services/api/auth-api.service';
import { InstitutionalHeaderComponent } from '../../layout/institutional-header/institutional-header.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { I18N_HOST, I18N_IMPORTS } from '../../shared/i18n/i18n.imports';

type PreviewState = {
  status: 'loading' | 'VALID' | 'INVALID' | 'EXPIRED' | 'USED' | 'ALREADY_ACTIVE';
  maskedEmail?: string | null;
  profileRequired?: boolean;
  ethicsInvitation?: boolean;
  pureReadonly?: boolean;
  givenName?: string | null;
  familyName?: string | null;
  positionTitle?: string | null;
};


@Component({
  selector: 'os-activate-account',
  hostDirectives: [...I18N_HOST],
  imports: [...I18N_IMPORTS, ReactiveFormsModule, RouterLink, InstitutionalHeaderComponent, IconComponent],
  templateUrl: './activate-account.component.html',
  styleUrl: './auth-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ActivateAccountComponent {
  private readonly api = inject(AuthApiService);
  private readonly i18n = inject(I18nService);
  private readonly route = inject(ActivatedRoute);
  private readonly token = this.route.snapshot.queryParamMap.get('token') ?? '';
  readonly preview = signal<PreviewState>({ status: 'loading' });
  readonly done = signal(false);
  readonly busy = signal(false);
  readonly submitted = signal(false);
  readonly error = signal<string | null>(null);
  readonly showPassword = signal(false);
  readonly showConfirm = signal(false);
  readonly campuses = INSTITUTIONAL_CAMPUSES;
  readonly faculties = INSTITUTIONAL_FACULTIES;
  readonly positions = INSTITUTIONAL_POSITIONS;
  readonly form = new FormGroup({
    givenName: new FormControl('', { nonNullable: true }),
    familyName: new FormControl('', { nonNullable: true }),
    campus: new FormControl('', { nonNullable: true }),
    faculty: new FormControl('', { nonNullable: true }),
    positionTitle: new FormControl('', { nonNullable: true }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(10)] }),
    confirm: new FormControl('', { nonNullable: true, validators: [Validators.required] })
  });
  private readonly formTick = toSignal(merge(this.form.valueChanges, this.form.statusChanges), {
    initialValue: null
  });

  constructor() {
    this.i18n.applyRequestedLanguage(this.route.snapshot.queryParamMap.get('lang'));
    if (!this.token) {
      this.preview.set({ status: 'INVALID' });
      return;
    }
    this.api.previewActivate(this.token).subscribe({
      next: (value) => {
        this.i18n.applyRequestedLanguage(value.preferredLanguage);
        this.preview.set({
          status: normalizeStatus(value.status, value.valid),
          maskedEmail: value.maskedEmail,
          profileRequired: value.profileRequired === true,
          ethicsInvitation: value.ethicsInvitation === true,
          pureReadonly: value.pureReadonly === true,
          givenName: value.givenName,
          familyName: value.familyName,
          positionTitle: value.positionTitle
        });
      },
      error: () => this.preview.set({ status: 'INVALID' })
    });
  }

  mismatch(): boolean {
    this.formTick();
    const password = this.form.controls.password.value;
    const confirm = this.form.controls.confirm.value;
    return confirm.length > 0 && password !== confirm;
  }

  passwordError(): string | null {
    this.formTick();
    const control = this.form.controls.password;
    if (!(this.submitted() || control.touched) || control.valid) {
      return null;
    }
    return control.hasError('minlength') ? 'auth.passwordPolicy' : 'auth.newPasswordRequired';
  }

  confirmError(): string | null {
    this.formTick();
    const control = this.form.controls.confirm;
    if (!(this.submitted() || control.touched) || control.value.length > 0) {
      return null;
    }
    return 'auth.confirmRequired';
  }

  fieldError(name: 'givenName' | 'familyName' | 'campus' | 'faculty' | 'positionTitle'): string | null {
    this.formTick();
    if (!this.preview().profileRequired) {
      return null;
    }
    const control = this.form.controls[name];
    const value = control.value.trim();
    if (!(this.submitted() || control.touched) || value.length > 0) {
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

  ethics(): boolean {
    return this.preview().ethicsInvitation === true;
  }

  shown(value: string | null | undefined): string {
    const text = value?.trim();
    return text ? text : this.i18n.translate('auth.notAvailable');
  }

  submit(): void {
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
    if (this.form.invalid || this.mismatch()) {
      this.form.markAllAsTouched();
      if (this.mismatch()) {
        this.error.set('auth.passwordMismatch');
      }
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api.activate(this.token, this.form.controls.password.value, profile ? {
      givenName: this.form.controls.givenName.value.trim(),
      familyName: this.form.controls.familyName.value.trim(),
      campus: this.form.controls.campus.value,
      faculty: this.form.controls.faculty.value,
      positionTitle: this.form.controls.positionTitle.value
    } : undefined).subscribe({
      next: () => {
        this.done.set(true);
        this.busy.set(false);
      },
      error: (error: unknown) => {
        const code = error instanceof HttpErrorResponse
          ? (error.error as { code?: string } | null)?.code
          : undefined;
        if (code === 'AUTH_PROFILE_REQUIRED') {
          this.form.markAllAsTouched();
        }
        this.error.set(activationFailureKey(code));
        this.busy.set(false);
      }
    });
  }
}

function normalizeStatus(status: string | undefined, valid: boolean): PreviewState['status'] {
  const value = (status ?? '').toUpperCase();
  if (value === 'VALID' || value === 'INVALID' || value === 'EXPIRED' || value === 'USED' || value === 'ALREADY_ACTIVE') {
    return value;
  }
  return valid ? 'VALID' : 'INVALID';
}
