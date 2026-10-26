import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { I18nService } from '../../core/i18n/i18n.service';
import { ROLE_LABELS } from '../../mocks/catalogs';
import { AuditService } from '../../core/services/audit.service';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';

@Component({
  selector: 'eth-audit-list-page',
  imports: [...I18N_IMPORTS],
  template: `
    <section>
      <h1>{{ 'audit.title' | translate }}</h1>
      <div class="timeline">
        @for (event of events; track event.id) {
          <article class="card">
            <strong>{{ event.date | localeDate }} · {{ event.actorName }} · {{ roleKey(event.role) | translate }}</strong>
            <p>{{ i18n.auditAction(event.action, event.description) }}</p>
            @if (event.oldValue || event.newValue) {
              <p class="muted">{{ event.oldValue }} → {{ event.newValue }}</p>
            }
            @if (event.reason) { <p class="muted">{{ 'audit.reason' | translate: { reason: event.reason } }}</p> }
          </article>
        }
      </div>
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AuditListPage {
  private readonly audit = inject(AuditService);
  readonly i18n = inject(I18nService);
  readonly events = this.audit.list();

  roleKey(role: string): string {
    return ROLE_LABELS[role] ?? role;
  }
}
