import { ChangeDetectionStrategy, Component, effect, inject, input, output, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { AdminRole, AppModuleCode, AuthPermission } from '../../../core/models/openscience.models';
import { AdminRoleApiService } from '../../../core/services/api/admin-role-api.service';
import { I18N_HOST, I18N_IMPORTS } from '../../../shared/i18n/i18n.imports';
import { messageOf } from '../../auth/login.component';

const MODULES: AppModuleCode[] = [
  'DASHBOARD',
  'PEOPLE',
  'RESEARCH_OUTPUTS',
  'JOURNALS',
  'PROJECTS',
  'ORGANIZATIONS',
  'DIRECTORY',
  'EVENTS',
  'DATA_QUALITY',
  'INDICATORS',
  'INTEGRATIONS',
  'AUDIT',
  'ADMINISTRATION'
];

@Component({
  selector: 'os-role-form',
  hostDirectives: [...I18N_HOST],
  imports: [...I18N_IMPORTS, ReactiveFormsModule],
  templateUrl: './role-form.component.html',
  styleUrl: '../users/user-form.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RoleFormComponent {
  private readonly api = inject(AdminRoleApiService);
  readonly role = input<AdminRole | null>(null);
  readonly closed = output<void>();
  readonly saved = output<void>();
  readonly error = signal<string | null>(null);
  readonly confirm = signal(false);
  readonly busy = signal(false);
  readonly modules = MODULES;
  readonly grants = signal<AuthPermission[]>([]);
  readonly form = new FormGroup({
    code: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    name: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    description: new FormControl('', { nonNullable: true }),
    active: new FormControl(true, { nonNullable: true })
  });

  constructor() {
    effect(() => {
      const current = this.role();
      if (current) {
        this.form.setValue({
          code: current.code,
          name: current.name,
          description: current.description ?? '',
          active: current.active
        });
        this.form.controls.code.disable();
        this.grants.set(current.permissions ?? []);
      } else {
        this.form.reset({ code: '', name: '', description: '', active: true });
        this.form.controls.code.enable();
        this.grants.set([]);
      }
    });
  }

  has(module: AppModuleCode, permission: 'CONSULT' | 'ADMINISTER'): boolean {
    return this.grants().some(
      (item) =>
        item.module === module &&
        (item.permission === permission || (permission === 'CONSULT' && item.permission === 'ADMINISTER'))
    );
  }

  toggle(module: AppModuleCode, permission: 'CONSULT' | 'ADMINISTER', checked: boolean): void {
    let next = this.grants().filter((item) => item.module !== module || item.permission !== permission);
    if (checked && permission === 'ADMINISTER') {
      next = next.filter((item) => item.module !== module);
      next.push({ module, permission: 'CONSULT' }, { module, permission: 'ADMINISTER' });
    } else if (checked) {
      next.push({ module, permission: 'CONSULT' });
    } else if (permission === 'CONSULT') {
      next = next.filter((item) => item.module !== module);
    }
    this.grants.set(next);
  }

  requestSave(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (this.role()) {
      this.confirm.set(true);
      return;
    }
    this.save();
  }

  save(): void {
    const value = this.form.getRawValue();
    this.busy.set(true);
    const current = this.role();
    const request = current
      ? this.api.update(current.id, {
          name: value.name,
          description: value.description,
          active: value.active,
          permissions: this.grants()
        })
      : this.api.create({
          code: value.code,
          name: value.name,
          description: value.description,
          active: value.active,
          permissions: this.grants()
        });
    request.subscribe({
      next: () => {
        this.saved.emit();
        this.busy.set(false);
      },
      error: (err) => {
        this.error.set(messageOf(err, 'admin.roles.saveError'));
        this.busy.set(false);
        this.confirm.set(false);
      }
    });
  }
}
