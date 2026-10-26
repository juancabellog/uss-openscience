import { ChangeDetectionStrategy, Component, effect, inject, input, numberAttribute, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { EventRegistrationList, EventsApiService } from '../core/events-api.service';
import { I18nService } from '../core/i18n/i18n.service';
import { EventAdminTabsComponent } from '../shared/event-admin-tabs.component';
import { I18N_IMPORTS } from '../shared/i18n/i18n.imports';

@Component({
  selector: 'ose-admin-registrants-page',
  imports: [RouterLink, ReactiveFormsModule, EventAdminTabsComponent, ...I18N_IMPORTS],
  templateUrl: './admin-registrants.page.html',
  styleUrl: './page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AdminRegistrantsPage {
  private readonly api = inject(EventsApiService);
  private readonly i18n = inject(I18nService);
  readonly id = input<number, unknown>(0, { transform: numberAttribute });
  readonly data = signal<EventRegistrationList | null>(null);
  readonly errorKey = signal<string | null>(null);
  readonly messageKey = signal<string | null>(null);
  readonly busy = signal(false);
  readonly form = new FormGroup({
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
    displayName: new FormControl('', { nonNullable: true }),
    facultyOrInstitution: new FormControl('', { nonNullable: true }),
    campusName: new FormControl('', { nonNullable: true }),
    positionTitle: new FormControl('', { nonNullable: true })
  });

  constructor() {
    effect(() => {
      const id = this.id();
      if (Number.isFinite(id) && id > 0) {
        this.reload(id);
      }
    });
  }

  add(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.errorKey.set(null);
    this.api.adminAddRegistration(this.id(), this.form.getRawValue()).subscribe({
      next: () => {
        this.busy.set(false);
        this.messageKey.set('registrants.added');
        this.form.reset();
        this.reload(this.id());
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.errorKey.set(this.i18n.errorKey(err, 'registrants.addFailed'));
      }
    });
  }

  originKey(origin?: string | null): string {
    if (origin === 'DIRECTORY_MATCH') {
      return 'registrants.originDirectory';
    }
    if (origin === 'SELF_DECLARED') {
      return 'registrants.originSelf';
    }
    return origin || 'common.dash';
  }

  private reload(id: number): void {
    this.api.adminRegistrations(id).subscribe({
      next: (result) => {
        this.data.set(result);
        this.errorKey.set(null);
      },
      error: (err: unknown) => {
        this.data.set(null);
        this.errorKey.set(this.i18n.errorKey(err, 'registrants.loadFailed'));
      }
    });
  }
}
