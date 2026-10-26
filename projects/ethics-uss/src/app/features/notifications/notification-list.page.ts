import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { DemoAuthService } from '../../core/auth/demo-auth.service';
import { NotificationService } from '../../core/services/notification.service';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';

@Component({
  selector: 'eth-notification-list-page',
  imports: [...I18N_IMPORTS],
  template: `
    <section>
      <h1>{{ 'notifications.title' | translate }}</h1>
      @for (note of notes(); track note.item.id) {
        <button type="button" class="card" style="width:100%;text-align:left;margin-bottom:.7rem" (click)="open(note.item.id, note.item.link)">
          <strong>{{ note.text.title }}</strong>
          <p>{{ note.text.body }}</p>
        </button>
      }
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NotificationListPage {
  private readonly auth = inject(DemoAuthService);
  private readonly notifications = inject(NotificationService);
  private readonly router = inject(Router);
  readonly notes = computed(() =>
    this.notifications.forUser(this.auth.currentUser()?.id ?? '').map((item) => ({
      item,
      text: this.notifications.display(item)
    }))
  );

  open(id: string, link?: string): void {
    this.notifications.markRead(id);
    if (link) {
      void this.router.navigateByUrl(link);
    }
  }
}
