import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { eventStatusClass } from '../core/event-admin-actions';
import { EventAdmin, EventsApiService, EventsModuleStatus } from '../core/events-api.service';
import { I18nService } from '../core/i18n/i18n.service';
import { EventRowActionsComponent } from '../shared/event-row-actions.component';
import { I18N_IMPORTS } from '../shared/i18n/i18n.imports';

@Component({
  selector: 'ose-admin-home-page',
  imports: [RouterLink, EventRowActionsComponent, ...I18N_IMPORTS],
  templateUrl: './admin-home.page.html',
  styleUrl: './page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AdminHomePage {
  private readonly api = inject(EventsApiService);
  private readonly i18n = inject(I18nService);
  readonly items = signal<EventAdmin[]>([]);
  readonly status = signal<EventsModuleStatus | null>(null);
  readonly errorKey = signal<string | null>(null);
  readonly busy = signal(false);
  readonly pending = signal<{ kind: 'publish' | 'unpublish'; event: EventAdmin } | null>(null);

  constructor() {
    this.reload();
    this.api.adminStatus().subscribe({
      next: (status) => this.status.set(status),
      error: () => this.status.set(null)
    });
  }

  titleOf(event: EventAdmin): string {
    return this.i18n.localizedText(event.title, event.titleEn);
  }

  statusClass(event: EventAdmin): string {
    return eventStatusClass(event.publicationStatus);
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

  private reload(): void {
    this.api.adminList().subscribe({
      next: (result) => this.items.set(result.items),
      error: (err: unknown) => this.errorKey.set(this.i18n.errorKey(err, 'adminHome.loadFailed'))
    });
  }
}
