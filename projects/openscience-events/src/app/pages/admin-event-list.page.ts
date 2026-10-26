import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { eventStatusClass } from '../core/event-admin-actions';
import {
  EVENT_STATUSES,
  EVENT_TYPES,
  EventAdmin,
  EventsApiService
} from '../core/events-api.service';
import { I18nService } from '../core/i18n/i18n.service';
import { EventRowActionsComponent } from '../shared/event-row-actions.component';
import { I18N_IMPORTS } from '../shared/i18n/i18n.imports';

@Component({
  selector: 'ose-admin-event-list-page',
  imports: [ReactiveFormsModule, RouterLink, EventRowActionsComponent, ...I18N_IMPORTS],
  templateUrl: './admin-event-list.page.html',
  styleUrl: './page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AdminEventListPage {
  private readonly api = inject(EventsApiService);
  private readonly i18n = inject(I18nService);
  readonly types = EVENT_TYPES;
  readonly statuses = EVENT_STATUSES;
  readonly items = signal<EventAdmin[]>([]);
  readonly errorKey = signal<string | null>(null);
  readonly busy = signal(false);
  readonly toast = signal<string | null>(null);
  readonly pending = signal<{ kind: 'publish' | 'unpublish'; event: EventAdmin } | null>(null);
  readonly filters = new FormGroup({
    q: new FormControl('', { nonNullable: true }),
    status: new FormControl('', { nonNullable: true }),
    type: new FormControl('', { nonNullable: true })
  });
  private toastTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    this.reload();
  }

  titleOf(event: EventAdmin): string {
    return this.i18n.localizedText(event.title, event.titleEn);
  }

  statusClass(event: EventAdmin): string {
    return eventStatusClass(event.publicationStatus);
  }

  reload(): void {
    const value = this.filters.getRawValue();
    this.api.adminList({
      q: value.q || undefined,
      status: value.status || undefined,
      type: value.type || undefined
    }).subscribe({
      next: (result) => {
        this.items.set(result.items);
        this.errorKey.set(null);
      },
      error: (err: unknown) => {
        this.items.set([]);
        this.errorKey.set(this.i18n.errorKey(err, 'adminHome.loadFailed'));
      }
    });
  }

  askPublish(event: EventAdmin): void {
    this.pending.set({ kind: 'publish', event });
  }

  askUnpublish(event: EventAdmin): void {
    this.pending.set({ kind: 'unpublish', event });
  }

  dismissPending(): void {
    this.pending.set(null);
  }

  confirmPending(): void {
    const action = this.pending();
    if (!action || this.busy()) {
      return;
    }
    this.busy.set(true);
    const request = action.kind === 'publish'
      ? this.api.adminPublish(action.event.id)
      : this.api.adminUnpublish(action.event.id);
    request.subscribe({
      next: () => {
        this.busy.set(false);
        this.pending.set(null);
        this.reload();
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.errorKey.set(this.i18n.errorKey(err, 'adminHome.loadFailed'));
      }
    });
  }

  onCopied(): void {
    this.showToast(this.i18n.translate('common.copied'));
  }

  private showToast(message: string): void {
    this.toast.set(message);
    if (this.toastTimer) {
      clearTimeout(this.toastTimer);
    }
    this.toastTimer = setTimeout(() => {
      this.toast.set(null);
      this.toastTimer = null;
    }, 2000);
  }
}
