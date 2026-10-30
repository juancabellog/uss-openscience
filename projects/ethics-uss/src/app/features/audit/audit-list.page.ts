import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { I18nService } from '../../core/i18n/i18n.service';
import { ROLE_LABELS } from '../../mocks/catalogs';
import { DemoAuthService } from '../../core/auth/demo-auth.service';
import { AuditService } from '../../core/services/audit.service';
import { EthicsStore } from '../../core/services/ethics-store.service';
import { PermissionService } from '../../core/services/permission.service';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';

@Component({
  selector: 'eth-audit-list-page',
  imports: [...I18N_IMPORTS],
  template: `
    <section>
      <h1>{{ 'audit.title' | translate }}</h1>
      <div class="timeline">
        @for (event of events(); track event.id) {
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
  private readonly auth = inject(DemoAuthService);
  private readonly store = inject(EthicsStore);
  private readonly permissions = inject(PermissionService);
  readonly i18n = inject(I18nService);
  readonly events = computed(() => {
    const user = this.auth.currentUser();
    const all = this.audit.list();
    if (!user) {
      return [];
    }
    if (this.permissions.can(user, 'VIEW_AUDIT')) {
      return all;
    }
    const state = this.store.snapshot();
    const ownCases = new Set(state.assignments.filter((item) => item.reviewerId === user.id && !item.supersededAt).map((item) => item.caseId));
    const ownProjects = new Set([
      ...state.projects.filter((item) => item.researcherId === user.id).map((item) => item.id),
      ...state.cases.filter((item) => ownCases.has(item.id)).map((item) => item.projectId)
    ]);
    return all.filter((event) => {
      const related = (!!event.projectId && ownProjects.has(event.projectId)) || (!!event.caseId && ownCases.has(event.caseId));
      if (!related) {
        return false;
      }
      const aboutReviewer = event.action.includes('REVIEWER') || event.action === 'ASSIGN_REVIEWERS' || event.action === 'REVIEW_SUBMITTED';
      if (!aboutReviewer || this.permissions.can(user, 'VIEW_REVIEWER_ASSIGNMENTS')) {
        return true;
      }
      const text = [event.actorId, event.actorName, event.description, event.oldValue, event.newValue].filter(Boolean).join(' ');
      return text.includes(user.id) || text.includes(user.displayName);
    });
  });

  roleKey(role: string): string {
    return ROLE_LABELS[role] ?? role;
  }
}
