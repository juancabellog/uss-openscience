import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { I18nService } from '../../core/i18n/i18n.service';
import { CommitteeCaseService } from '../../core/services/committee-case.service';
import { EthicsStore } from '../../core/services/ethics-store.service';
import { ProjectService } from '../../core/services/project.service';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';
import { expirySemaphore } from '../dashboard/home-institutional';

@Component({
  selector: 'eth-expiry-list-page',
  imports: [RouterLink, EmptyStateComponent, ...I18N_IMPORTS],
  template: `
    <section>
      <h1>{{ 'expirations.title' | translate }}</h1>
      <div class="queue-filter">
        <span class="label-caption" id="expiry-semaphore-label">{{ 'projects.validity' | translate }}</span>
        <div class="status-chips" role="group" aria-labelledby="expiry-semaphore-label">
          @for (option of options; track option.value) {
            <button type="button" [attr.aria-pressed]="filter() === option.value" (click)="setFilter(option.value)">
              {{ option.labelKey | translate }}
            </button>
          }
        </div>
      </div>
      @if (!rows().length) {
        <eth-empty-state title="expirations.empty" message="reviews.emptyDetail" />
      } @else {
      <div class="grid-2">
        @for (row of rows(); track row.id) {
          <article class="card">
            <h2>{{ row.title }}</h2>
            <p>{{ row.code }} · {{ 'expirations.issuedExpires' | translate: { issued: i18n.formatDate(row.issued), expires: i18n.formatDate(row.expires) } }}</p>
            <p>{{ 'expirations.days' | translate: { semaphore: i18n.catalog('semaphore', row.semaphore), days: row.days } }}</p>
            @if (row.semaphore === 'VENCIDO') {
              <p class="alert danger">{{ 'expirations.critical' | translate }}</p>
            }
            <a [routerLink]="['/proyectos', row.projectId]">{{ 'dashboard.viewProject' | translate }}</a>
          </article>
        }
      </div>
      }
    </section>
  `,
  styles: `
    .queue-filter { display: grid; gap: 0.35rem; margin: 0 0 1rem; }
    .status-chips { display: flex; flex-wrap: wrap; gap: 0.35rem; }
    .status-chips button {
      height: 2.15rem;
      padding: 0 0.75rem;
      border: 1px solid var(--eth-border);
      border-radius: 999px;
      background: #f7f6f3;
      color: var(--eth-ink);
      font-weight: 600;
    }
    .status-chips button[aria-pressed='true'] {
      border-width: 2px;
      border-color: var(--eth-primary);
      background: #fff;
      color: var(--eth-primary);
      font-weight: 800;
    }
    .status-chips button:focus-visible { outline: 2px solid var(--eth-primary); outline-offset: 2px; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ExpiryListPage {
  private readonly cases = inject(CommitteeCaseService);
  private readonly projects = inject(ProjectService);
  private readonly store = inject(EthicsStore);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly query = toSignal(this.route.queryParamMap, { initialValue: this.route.snapshot.queryParamMap });
  readonly i18n = inject(I18nService);
  readonly options: readonly { value: '' | 'PROXIMO' | 'VIGENTE' | 'VENCIDO'; labelKey: string }[] = [
    { value: '', labelKey: 'projects.validityAll' },
    { value: 'VIGENTE', labelKey: 'projects.validityCurrent' },
    { value: 'PROXIMO', labelKey: 'dashboard.expiring' },
    { value: 'VENCIDO', labelKey: 'projects.validityExpired' }
  ];
  readonly filter = signal<'' | 'PROXIMO' | 'VIGENTE' | 'VENCIDO'>(expirySemaphore(this.route.snapshot.queryParamMap.get('semaphore')));
  readonly rows = computed(() =>
    this.cases
      .list()
      .filter((item) => item.approvalExpiresAt)
      .map((item) => {
        const info = this.projects.expiryInfo(item)!;
        return {
          id: item.id,
          projectId: item.projectId,
          code: item.code,
          title: this.store.snapshot().projects.find((project) => project.id === item.projectId)?.title ?? '',
          issued: item.approvalIssuedAt,
          expires: item.approvalExpiresAt,
          semaphore: info.semaphore,
          days: info.daysLeft
        };
      })
      .filter((row) => !this.filter() || row.semaphore === this.filter())
  );

  constructor() {
    effect(() => {
      const next = expirySemaphore(this.query().get('semaphore'));
      if (next !== this.filter()) {
        this.filter.set(next);
      }
    });
  }

  setFilter(value: '' | 'PROXIMO' | 'VIGENTE' | 'VENCIDO'): void {
    this.filter.set(value);
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { semaphore: value || null },
      replaceUrl: true
    });
  }
}
