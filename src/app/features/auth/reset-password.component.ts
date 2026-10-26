import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthApiService } from '../../core/services/api/auth-api.service';
import { InstitutionalHeaderComponent } from '../../layout/institutional-header/institutional-header.component';
import { I18N_HOST, I18N_IMPORTS } from '../../shared/i18n/i18n.imports';
import { messageOf } from './login.component';

@Component({
  selector: 'os-reset-password',
  hostDirectives: [...I18N_HOST],
  imports: [...I18N_IMPORTS, ReactiveFormsModule, RouterLink, InstitutionalHeaderComponent],
  templateUrl: './reset-password.component.html',
  styleUrl: './auth-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ResetPasswordComponent {
  private readonly api = inject(AuthApiService);
  private readonly token = inject(ActivatedRoute).snapshot.queryParamMap.get('token') ?? '';
  readonly preview = signal<{ valid: boolean } | null>(null);
  readonly done = signal(false);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly form = new FormGroup({
    password: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(10)] }),
    confirm: new FormControl('', { nonNullable: true, validators: [Validators.required] })
  });

  constructor() {
    if (this.token) {
      this.api.previewReset(this.token).subscribe({
        next: (value) => this.preview.set(value),
        error: () => this.preview.set({ valid: false })
      });
    } else {
      this.preview.set({ valid: false });
    }
  }

  submit(): void {
    if (this.form.invalid || this.form.controls.password.value !== this.form.controls.confirm.value) {
      this.error.set('auth.passwordMismatch');
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api.resetPassword(this.token, this.form.controls.password.value).subscribe({
      next: () => {
        this.done.set(true);
        this.busy.set(false);
      },
      error: (err) => {
        this.error.set(messageOf(err, 'auth.tokenInvalid'));
        this.busy.set(false);
      }
    });
  }
}
