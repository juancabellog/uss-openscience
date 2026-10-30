import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { I18nService } from '../../core/i18n/i18n.service';
import { AuthApiService } from '../../core/services/api/auth-api.service';
import { InstitutionalHeaderComponent } from '../../layout/institutional-header/institutional-header.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { I18N_HOST, I18N_IMPORTS } from '../../shared/i18n/i18n.imports';
import { messageOf } from './login.component';

@Component({
  selector: 'os-reset-password',
  hostDirectives: [...I18N_HOST],
  imports: [...I18N_IMPORTS, ReactiveFormsModule, RouterLink, InstitutionalHeaderComponent, IconComponent],
  templateUrl: './reset-password.component.html',
  styleUrl: './auth-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ResetPasswordComponent {
  private readonly api = inject(AuthApiService);
  private readonly i18n = inject(I18nService);
  private readonly route = inject(ActivatedRoute);
  private readonly token = this.route.snapshot.queryParamMap.get('token') ?? '';
  readonly preview = signal<{ valid: boolean } | null>(null);
  readonly done = signal(false);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly showPassword = signal(false);
  readonly showConfirm = signal(false);
  readonly form = new FormGroup({
    password: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(10)] }),
    confirm: new FormControl('', { nonNullable: true, validators: [Validators.required] })
  });

  constructor() {
    this.i18n.applyRequestedLanguage(this.route.snapshot.queryParamMap.get('lang'));
    if (this.token) {
      this.api.previewReset(this.token).subscribe({
        next: (value) => {
          this.i18n.applyRequestedLanguage(value.preferredLanguage);
          this.preview.set(value);
        },
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
