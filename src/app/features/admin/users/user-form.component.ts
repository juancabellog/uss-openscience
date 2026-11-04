import { ChangeDetectionStrategy, Component, DestroyRef, effect, inject, input, output, signal } from '@angular/core';
import { OperationFeedbackPanelComponent } from '../../../../../shared/operation-feedback/operation-feedback-panel.component';
import { OperationRun, watchOperation } from '../../../../../shared/operation-feedback/operation-feedback';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { AdminRole, AdminUserSummary } from '../../../core/models/openscience.models';
import { AdminUserApiService } from '../../../core/services/api/admin-user-api.service';
import { I18N_HOST, I18N_IMPORTS } from '../../../shared/i18n/i18n.imports';
import { messageOf } from '../../auth/login.component';
import { I18nService } from '../../../core/i18n/i18n.service';

@Component({
  selector: 'os-user-form',
  hostDirectives: [...I18N_HOST],
  imports: [...I18N_IMPORTS, ReactiveFormsModule, OperationFeedbackPanelComponent],
  templateUrl: './user-form.component.html',
  styleUrl: './user-form.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class UserFormComponent {
  private readonly api = inject(AdminUserApiService);
  private readonly i18n = inject(I18nService);
  private readonly saveRun = new OperationRun();
  private stopSave: (() => void) | null = null;
  readonly saveView = signal(this.saveRun.view());
  readonly user = input<AdminUserSummary | null>(null);
  readonly roles = input<AdminRole[]>([]);
  readonly closed = output<void>();
  readonly saved = output<string>();
  readonly error = signal<string | null>(null);
  readonly busy = signal(false);
  readonly form = new FormGroup({
    firstName: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    lastName: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
    preferredLanguage: new FormControl('es', { nonNullable: true }),
    roleIds: new FormControl<number[]>([], { nonNullable: true })
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => this.stopSave?.());
    effect(() => {
      const current = this.user();
      if (current) {
        this.form.setValue({
          firstName: current.firstName,
          lastName: current.lastName,
          email: current.email,
          preferredLanguage: current.preferredLanguage || 'es',
          roleIds: current.roleIds ?? []
        });
        this.form.controls.email.disable();
      } else {
        this.form.reset({ firstName: '', lastName: '', email: '', preferredLanguage: 'es', roleIds: [] });
        this.form.controls.email.enable();
      }
    });
  }

  toggleRole(id: number, checked: boolean): void {
    const current = this.form.controls.roleIds.value;
    this.form.controls.roleIds.setValue(
      checked ? [...current, id] : current.filter((item) => item !== id)
    );
  }

  submit(): void {
    if (this.busy()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const current = this.user();
    if (!this.saveRun.start({
      loadingMessage: this.i18n.translate(current ? 'admin.users.saving' : 'admin.users.creating'),
      detailMessage: current ? '' : this.i18n.translate('admin.users.creatingDetail'),
      longRunningMessage: this.i18n.translate('admin.users.stillWorking'),
      blocking: !current
    })) {
      return;
    }
    this.saveView.set(this.saveRun.view());
    this.stopSave?.();
    this.stopSave = watchOperation(this.saveRun, () => this.saveView.set(this.saveRun.view()));
    this.busy.set(true);
    const request = current ? this.api.update(current.id, value) : this.api.create(value);
    request.subscribe({
      next: (result) => {
        const key = current
          ? 'admin.users.updated'
          : result.emailSent
            ? 'admin.users.created'
            : 'admin.users.createdNoMail';
        this.saveRun.succeed(this.i18n.translate(key));
        this.saveView.set(this.saveRun.view());
        this.saved.emit(key);
        this.busy.set(false);
      },
      error: (err) => {
        const key = messageOf(err, 'admin.users.saveError');
        this.error.set(key);
        this.saveRun.fail(this.i18n.translate(key));
        this.saveView.set(this.saveRun.view());
        this.busy.set(false);
      }
    });
  }
}
