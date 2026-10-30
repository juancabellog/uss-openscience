import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { merge } from 'rxjs';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { I18nService } from '../../core/i18n/i18n.service';
import { AuthApiService } from '../../core/services/api/auth-api.service';
import { InstitutionalHeaderComponent } from '../../layout/institutional-header/institutional-header.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { I18N_HOST, I18N_IMPORTS } from '../../shared/i18n/i18n.imports';

type PreviewState = {
  status: 'loading' | 'VALID' | 'INVALID' | 'EXPIRED' | 'USED' | 'ALREADY_ACTIVE';
  maskedEmail?: string | null;
  profileRequired?: boolean;
  givenName?: string | null;
  familyName?: string | null;
};

const CAMPUSES = [
  'Santiago - Bellavista',
  'Santiago - Los Leones',
  'Santiago - Ciudad Universitaria (Huechuraba)',
  'Concepción - Tres Pascualas',
  'Concepción - Paicaví',
  'Valdivia - Sede',
  'Valdivia - Centro de Estudios Científicos',
  'Patagonia'
];
const FACULTIES = [
  'Arquitectura, Arte y Diseño',
  'Ciencias',
  'Ciencias de la Rehabilitación y Calidad de Vida',
  'Ciencias para el cuidado de la Salud',
  'Derecho y Ciencias Sociales',
  'Economía, Negocios y Gobierno',
  'Educación',
  'Ingeniería',
  'Medicina',
  'Medicina Veterinaria',
  'Odontología'
];
const POSITIONS = [
  'Estudiante de pregrado',
  'Estudiante de magister',
  'Estudiante de doctorado',
  'Postdoctorante',
  'Asistente de Investigación',
  'Personal Técnico',
  'Docente',
  'Co-Investigador',
  'Investigador',
  'Director Unidad Administrativa'
];

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
  readonly campuses = CAMPUSES;
  readonly faculties = FACULTIES;
  readonly positions = POSITIONS;
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
          givenName: value.givenName,
          familyName: value.familyName
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
    return control.hasError('minlength') ? 'auth.passwordPolicy' : 'auth.passwordRequired';
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
        this.error.set('auth.activateProfileHelp');
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
