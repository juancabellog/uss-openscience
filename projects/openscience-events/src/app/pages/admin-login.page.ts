import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { EventsApiService } from '../core/events-api.service';
import { I18N_IMPORTS } from '../shared/i18n/i18n.imports';

@Component({
  selector: 'ose-admin-login-page',
  imports: [ReactiveFormsModule, ...I18N_IMPORTS],
  templateUrl: './admin-login.page.html',
  styleUrl: './page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AdminLoginPage {
  private readonly api = inject(EventsApiService);
  private readonly router = inject(Router);
  readonly busy = signal(false);
  readonly errorKey = signal<string | null>(null);
  readonly form = new FormGroup({
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required] })
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.errorKey.set(null);
    this.api.login(this.form.controls.email.value, this.form.controls.password.value).subscribe({
      next: () => {
        this.busy.set(false);
        void this.router.navigateByUrl('/admin/eventos');
      },
      error: () => {
        this.busy.set(false);
        this.errorKey.set('login.failed');
      }
    });
  }
}
