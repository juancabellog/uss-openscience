import { ChangeDetectionStrategy, Component, computed, effect, inject, input, numberAttribute, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { eventAdminActionFlags, eventStatusClass } from '../core/event-admin-actions';
import { EventAdmin, EventsApiService } from '../core/events-api.service';
import { I18nService } from '../core/i18n/i18n.service';
import { PublicEventLinks } from '../core/public-event-links';
import { EventAdminTabsComponent } from '../shared/event-admin-tabs.component';
import { EventStatusMenuComponent } from '../shared/event-status-menu.component';
import { CopyButtonComponent } from '../shared/copy-button.component';
import { I18N_IMPORTS } from '../shared/i18n/i18n.imports';

type StatusAction = 'publish' | 'unpublish' | 'finish' | 'cancel';

@Component({
  selector: 'ose-admin-event-detail-page',
  imports: [
    RouterLink,
    EventAdminTabsComponent,
    EventStatusMenuComponent,
    CopyButtonComponent,
    ...I18N_IMPORTS
  ],
  templateUrl: './admin-event-detail.page.html',
  styleUrl: './page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AdminEventDetailPage {
  private readonly api = inject(EventsApiService);
  private readonly i18n = inject(I18nService);
  private readonly publicLinks = inject(PublicEventLinks);
  readonly id = input<number, unknown>(0, { transform: numberAttribute });
  readonly event = signal<EventAdmin | null>(null);
  readonly errorKey = signal<string | null>(null);
  readonly successKey = signal<string | null>(null);
  readonly busy = signal(false);
  readonly pending = signal<StatusAction | null>(null);
  readonly publicUrl = computed(() => this.publicLinks.eventUrl(this.publicLinks.slugOf(this.event())));
  readonly registrationUrl = computed(() => this.publicLinks.registrationUrl(this.publicLinks.slugOf(this.event())));

  constructor() {
    effect(() => {
      const id = this.id();
      if (Number.isFinite(id) && id > 0) {
        this.reload(id);
      }
    });
  }

  titleOf(event: EventAdmin): string {
    return this.i18n.localizedText(event.title, event.titleEn);
  }

  descriptionOf(event: EventAdmin): string {
    return this.i18n.localizedText(event.description, event.descriptionEn);
  }

  unitOf(event: EventAdmin): string {
    return this.i18n.localizedText(event.organizingUnit, event.organizingUnitEn);
  }

  placeOf(event: EventAdmin): string {
    return [event.campusName, event.locationName].filter((value) => !!value && value.trim()).join(' · ');
  }

  statusClass(event: EventAdmin): string {
    return eventStatusClass(event.publicationStatus);
  }

  canEdit(event: EventAdmin): boolean {
    return eventAdminActionFlags(event.publicationStatus).canEdit;
  }

  ask(kind: StatusAction): void {
    this.pending.set(kind);
    this.errorKey.set(null);
    this.successKey.set(null);
  }

  dismissPending(): void {
    this.pending.set(null);
  }

  confirmPending(): void {
    const action = this.pending();
    const event = this.event();
    if (!action || !event || this.busy()) {
      return;
    }
    this.busy.set(true);
    const request = action === 'publish'
      ? this.api.adminPublish(event.id)
      : action === 'unpublish'
        ? this.api.adminUnpublish(event.id)
        : action === 'finish'
          ? this.api.adminFinish(event.id)
          : this.api.adminCancel(event.id);
    request.subscribe({
      next: (updated) => {
        this.busy.set(false);
        this.pending.set(null);
        this.event.set(updated);
        this.successKey.set(
          action === 'publish'
            ? 'form.published'
            : action === 'unpublish'
              ? 'form.unpublished'
              : action === 'finish'
                ? 'form.finished'
                : 'form.cancelled'
        );
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.errorKey.set(this.i18n.errorKey(err, 'form.saveFailed'));
      }
    });
  }

  confirmTitleKey(kind: StatusAction): string {
    if (kind === 'publish') {
      return 'form.confirmPublish';
    }
    if (kind === 'unpublish') {
      return 'form.confirmUnpublish';
    }
    if (kind === 'finish') {
      return 'form.confirmFinish';
    }
    return 'form.confirmCancelEvent';
  }

  confirmHintKey(kind: StatusAction): string {
    if (kind === 'publish') {
      return 'form.confirmPublishHint';
    }
    if (kind === 'unpublish') {
      return 'form.confirmUnpublishHint';
    }
    if (kind === 'finish') {
      return 'form.confirmFinishHint';
    }
    return 'form.confirmCancelEventHint';
  }

  confirmActionKey(kind: StatusAction): string {
    if (kind === 'publish') {
      return 'form.confirmPublishAction';
    }
    if (kind === 'unpublish') {
      return 'form.backToDraft';
    }
    if (kind === 'finish') {
      return 'form.confirmFinishAction';
    }
    return 'form.confirmCancelEventAction';
  }

  confirmTone(kind: StatusAction): string {
    if (kind === 'cancel') {
      return 'is-danger';
    }
    if (kind === 'finish') {
      return 'is-warning';
    }
    return '';
  }

  private reload(id: number): void {
    this.api.adminGet(id).subscribe({
      next: (event) => {
        this.event.set(event);
        this.errorKey.set(null);
      },
      error: (err: unknown) => {
        this.event.set(null);
        this.errorKey.set(this.i18n.errorKey(err, 'adminHome.loadFailed'));
      }
    });
  }
}
