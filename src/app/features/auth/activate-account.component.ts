import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { merge } from 'rxjs';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthApiService } from '../../core/services/api/auth-api.service';
import { InstitutionalHeaderComponent } from '../../layout/institutional-header/institutional-header.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { I18N_HOST, I18N_IMPORTS } from '../../shared/i18n/i18n.imports';

type PreviewState = {
  status: 'loading' | 'VALID' | 'INVALID' | 'EXPIRED' | 'USED' | 'ALREADY_ACTIVE';
  maskedEmail?: string | null;
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
  private readonly token = inject(ActivatedRoute).snapshot.queryParamMap.get('token') ?? '';
  readonly preview = signal<PreviewState>({ status: 'loading' });
  readonly done = signal(false);
  readonly busy = signal(false);
  readonly submitted = signal(false);
  readonly error = signal<string | null>(null);
  readonly showPassword = signal(false);
  readonly showConfirm = signal(false);
  readonly form = new FormGroup({
    password: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(10)] }),
    confirm: new FormControl('', { nonNullable: true, validators: [Validators.required] })
  });
  private readonly formTick = toSignal(merge(this.form.valueChanges, this.form.statusChanges), {
    initialValue: null
  });

  constructor() {
    if (!this.token) {
      this.preview.set({ status: 'INVALID' });
      return;
    }
    this.api.previewActivate(this.token).subscribe({
      next: (value) =>
        this.preview.set({
          status: normalizeStatus(value.status, value.valid),
          maskedEmail: value.maskedEmail
        }),
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
    return control.hasError('minlength') ? 'auth.passwordPolicy' : 'auth.passwordRequired';
  }

  submit(): void {
    if (this.busy()) {
      return;
    }
    this.submitted.set(true);
    if (this.form.invalid || this.mismatch()) {
      this.form.markAllAsTouched();
      if (this.mismatch()) {
        this.error.set('auth.passwordMismatch');
      }
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api.activate(this.token, this.form.controls.password.value).subscribe({
      next: () => {
        this.done.set(true);
        this.busy.set(false);
      },
      error: () => {
        this.error.set('auth.tokenInvalid');
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
