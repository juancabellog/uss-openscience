import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { EthicsStore } from '../../core/services/ethics-store.service';
import { SessionService } from '../../core/services/session.service';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';

@Component({
  selector: 'eth-session-detail-page',
  imports: [RouterLink, StatusChipComponent, ...I18N_IMPORTS],
  template: `
    @if (session(); as session) {
      <section class="panel">
        <h1>{{ 'sessions.detailTitle' | translate: { date: session.date, time: session.time } }}</h1>
        <p>{{ session.committee }} · {{ ('catalogs.sessionTypes.' + session.type) | translate }} · {{ ('catalogs.sessionStatus.' + session.status) | translate }}</p>
        @for (row of cases(); track row.id) {
          <p>
            <a [routerLink]="['/expedientes', row.id]">{{ row.code }}</a>
            · {{ row.title }}
            · <eth-status-chip [value]="row.status" />
          </p>
        }
      </section>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SessionDetailPage {
  readonly id = input.required<string>();
  private readonly sessions = inject(SessionService);
  private readonly store = inject(EthicsStore);
  readonly session = computed(() => this.sessions.get(this.id()));
  readonly cases = computed(() => {
    const session = this.session();
    const state = this.store.snapshot();
    return (session?.caseIds ?? []).map((caseId) => {
      const item = state.cases.find((row) => row.id === caseId);
      const project = state.projects.find((row) => row.id === item?.projectId);
      return { id: caseId, code: item?.code ?? caseId, title: project?.title ?? '', status: item?.status ?? 'PENDING' };
    });
  });
}
