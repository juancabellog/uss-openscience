import { ChangeDetectionStrategy, Component, effect, inject, input, numberAttribute, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  EventAttendance,
  EventAttendanceImport,
  EventsApiService
} from '../core/events-api.service';
import { I18nService } from '../core/i18n/i18n.service';
import { EventAdminTabsComponent } from '../shared/event-admin-tabs.component';
import { I18N_IMPORTS } from '../shared/i18n/i18n.imports';

@Component({
  selector: 'ose-admin-attendance-page',
  imports: [RouterLink, ReactiveFormsModule, EventAdminTabsComponent, ...I18N_IMPORTS],
  templateUrl: './admin-attendance.page.html',
  styleUrl: './page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AdminAttendancePage {
  private readonly api = inject(EventsApiService);
  private readonly i18n = inject(I18nService);
  readonly id = input<number, unknown>(0, { transform: numberAttribute });
  readonly items = signal<EventAttendance[]>([]);
  readonly preview = signal<EventAttendanceImport | null>(null);
  readonly errorKey = signal<string | null>(null);
  readonly messageKey = signal<string | null>(null);
  readonly busy = signal(false);
  readonly walkIn = new FormGroup({
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
    displayName: new FormControl('', { nonNullable: true }),
    facultyOrInstitution: new FormControl('', { nonNullable: true }),
    campusName: new FormControl('', { nonNullable: true }),
    positionTitle: new FormControl('', { nonNullable: true })
  });

  constructor() {
    effect(() => {
      if (this.id() > 0) {
        this.reload();
      }
    });
  }

  setStatus(item: EventAttendance, status: string): void {
    this.api.adminSetAttendance(this.id(), { registrationId: item.registrationId, status }).subscribe({
      next: () => this.reload(),
      error: (err: unknown) => this.errorKey.set(this.i18n.errorKey(err, 'attendance.updateFailed'))
    });
  }

  addWalkIn(): void {
    if (this.walkIn.invalid) {
      this.walkIn.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.api.adminWalkIn(this.id(), this.walkIn.getRawValue()).subscribe({
      next: () => {
        this.busy.set(false);
        this.messageKey.set('attendance.walkInAdded');
        this.walkIn.reset();
        this.reload();
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.errorKey.set(this.i18n.errorKey(err, 'attendance.walkInFailed'));
      }
    });
  }

  onImportFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }
    this.busy.set(true);
    this.api.adminPreviewImportFile(this.id(), file).subscribe({
      next: (preview) => {
        this.busy.set(false);
        this.preview.set(preview);
        this.messageKey.set('attendance.previewReady');
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.errorKey.set(this.i18n.errorKey(err, 'attendance.importFailed'));
      }
    });
  }

  confirmImport(): void {
    const preview = this.preview();
    if (!preview) {
      return;
    }
    this.busy.set(true);
    this.api.adminConfirmImport(this.id(), preview.id).subscribe({
      next: () => {
        this.busy.set(false);
        this.preview.set(null);
        this.messageKey.set('attendance.importConfirmed');
        this.reload();
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.errorKey.set(this.i18n.errorKey(err, 'attendance.confirmFailed'));
      }
    });
  }

  closeDocumentation(): void {
    this.api.adminCloseDocumentation(this.id()).subscribe({
      next: () => this.messageKey.set('attendance.closed'),
      error: (err: unknown) => this.errorKey.set(this.i18n.errorKey(err, 'attendance.closeFailed'))
    });
  }

  private reload(): void {
    this.api.adminAttendance(this.id()).subscribe({
      next: (items) => this.items.set(items),
      error: (err: unknown) => this.errorKey.set(this.i18n.errorKey(err, 'attendance.loadFailed'))
    });
  }
}
