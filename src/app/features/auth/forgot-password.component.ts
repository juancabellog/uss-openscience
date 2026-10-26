import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthApiService } from '../../core/services/api/auth-api.service';
import { InstitutionalHeaderComponent } from '../../layout/institutional-header/institutional-header.component';
import { I18N_HOST, I18N_IMPORTS } from '../../shared/i18n/i18n.imports';

@Component({
  selector: 'os-forgot-password',
  hostDirectives: [...I18N_HOST],
  imports: [...I18N_IMPORTS, ReactiveFormsModule, RouterLink, InstitutionalHeaderComponent],
  templateUrl: './forgot-password.component.html',
  styleUrl: './auth-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ForgotPasswordComponent {
  private readonly api = inject(AuthApiService);
  readonly email = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] });
  readonly sent = signal(false);
  readonly busy = signal(false);

  submit(): void {
    if (this.email.invalid || this.busy()) {
      this.email.markAsTouched();
      return;
    }
    this.busy.set(true);
    this.api.forgotPassword(this.email.value).subscribe({
      next: () => {
        this.sent.set(true);
        this.busy.set(false);
      },
      error: () => {
        this.sent.set(true);
        this.busy.set(false);
      }
    });
  }
}
