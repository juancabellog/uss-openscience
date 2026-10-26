import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { I18N_IMPORTS } from './i18n/i18n.imports';

@Component({
  selector: 'ose-event-admin-tabs',
  imports: [RouterLink, RouterLinkActive, ...I18N_IMPORTS],
  template: `
    <nav class="event-tabs" [attr.aria-label]="'adminEvent.tabsAria' | translate">
      <a
        routerLinkActive="is-active"
        [routerLink]="['/admin/eventos', eventId()]"
        [routerLinkActiveOptions]="{ exact: true }"
      >
        {{ 'adminEvent.summary' | translate }}
      </a>
      <a routerLinkActive="is-active" [routerLink]="['/admin/eventos', eventId(), 'inscritos']">
        {{ 'nav.registrants' | translate }}
      </a>
      <a routerLinkActive="is-active" [routerLink]="['/admin/eventos', eventId(), 'acta']">
        {{ 'adminEvent.minutesTab' | translate }}
      </a>
      <a routerLinkActive="is-active" [routerLink]="['/admin/eventos', eventId(), 'evidencias']">
        {{ 'nav.documents' | translate }}
      </a>
      <a routerLinkActive="is-active" [routerLink]="['/admin/eventos', eventId(), 'asistencia']">
        {{ 'nav.attendance' | translate }}
      </a>
    </nav>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class EventAdminTabsComponent {
  readonly eventId = input.required<number>();
}
