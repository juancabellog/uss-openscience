import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { EthicsCommittee, SessionType } from '../../core/models/ethics.models';
import { DemoAuthService } from '../../core/auth/demo-auth.service';
import { SessionService } from '../../core/services/session.service';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';

@Component({
  selector: 'eth-session-list-page',
  imports: [RouterLink, ...I18N_IMPORTS],
  template: `
    <section>
      <h1>{{ 'sessions.title' | translate }}</h1>
      <article class="panel">
        <h2>{{ 'sessions.new' | translate }}</h2>
        <div class="grid-2">
          <label class="field"><span class="label-caption">{{ 'sessions.committee' | translate }}</span>
            <select [value]="committee()" (change)="committee.set($any($event.target).value)">
              <option value="CEC">CEC</option><option value="CECUA">CECUA</option><option value="BIOSEGURIDAD">BIOSEGURIDAD</option>
            </select>
          </label>
          <label class="field"><span class="label-caption">{{ 'sessions.type' | translate }}</span>
            <select [value]="type()" (change)="type.set($any($event.target).value)">
              <option value="ORDINARY">{{ 'catalogs.sessionTypes.ORDINARY' | translate }}</option>
              <option value="EXTRAORDINARY">{{ 'catalogs.sessionTypes.EXTRAORDINARY' | translate }}</option>
            </select>
          </label>
          <label class="field"><span class="label-caption">{{ 'sessions.date' | translate }}</span><input type="date" [value]="date()" (input)="date.set($any($event.target).value)" /></label>
          <label class="field"><span class="label-caption">{{ 'sessions.time' | translate }}</span><input type="time" [value]="time()" (input)="time.set($any($event.target).value)" /></label>
        </div>
        <button type="button" class="btn" (click)="create()">{{ 'sessions.create' | translate }}</button>
      </article>
      <div class="grid-2">
        @for (session of sessions(); track session.id) {
          <a class="card" [routerLink]="['/sesiones', session.id]">
            <h2>{{ session.date }} {{ session.time }}</h2>
            <p>{{ session.committee }} · {{ ('catalogs.sessionTypes.' + session.type) | translate }} · {{ 'sessions.projectsCount' | translate: { count: session.caseIds.length } }}</p>
          </a>
        }
      </div>
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SessionListPage {
  private readonly sessionsApi = inject(SessionService);
  private readonly auth = inject(DemoAuthService);
  readonly committee = signal<EthicsCommittee>(this.auth.currentUser()?.committees[0] ?? 'CEC');
  readonly type = signal<SessionType>('ORDINARY');
  readonly date = signal('');
  readonly time = signal('10:00');
  readonly sessions = computed(() => this.sessionsApi.list());

  create(): void {
    if (this.date()) {
      this.sessionsApi.create(this.committee(), this.date(), this.time(), this.type());
    }
  }
}
