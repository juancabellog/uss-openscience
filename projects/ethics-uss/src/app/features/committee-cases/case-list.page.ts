import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { COMMITTEE_LABELS } from '../../mocks/catalogs';
import { DemoAuthService } from '../../core/auth/demo-auth.service';
import { CommitteeCaseService } from '../../core/services/committee-case.service';
import { EthicsStore } from '../../core/services/ethics-store.service';
import { PermissionService } from '../../core/services/permission.service';
import { hasScope } from '../../core/workflow/role-capabilities';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';
import { CaseQueue, caseQueue, matchesCaseQueue } from '../dashboard/home-institutional';

@Component({
  selector: 'eth-case-list-page',
  imports: [RouterLink, StatusChipComponent, EmptyStateComponent, ...I18N_IMPORTS],
  template: `
    <section>
      <h1>{{ 'cases.title' | translate }}</h1>
      <div class="queue-filter">
        <span class="label-caption" id="case-queue-label">{{ 'cases.status' | translate }}</span>
        <div class="status-chips" role="group" aria-labelledby="case-queue-label">
          @for (option of options; track option.value) {
            <button type="button" [attr.aria-pressed]="filter() === option.value" (click)="setFilter(option.value)">
              {{ option.labelKey | translate }}
            </button>
          }
        </div>
      </div>
      @if (!rows().length) {
        <eth-empty-state title="cases.emptyQueue" message="reviews.emptyDetail" />
      } @else {
      <div class="table-wrap panel">
        <table>
          <thead>
            <tr>
              <th>{{ 'cases.diseiCode' | translate }}</th>
              <th>{{ 'classification.project' | translate }}</th>
              <th>{{ 'cases.committee' | translate }}</th>
              <th>{{ 'cases.committeeCode' | translate }}</th>
              <th>{{ 'cases.status' | translate }}</th>
              <th>{{ 'cases.action' | translate }}</th>
            </tr>
          </thead>
          <tbody>
            @for (row of rows(); track row.item.id) {
              <tr>
                <td>{{ row.diseiCode }}</td>
                <td>{{ row.title }}</td>
                <td>{{ committees[row.item.committee] | translate }}</td>
                <td>{{ row.item.code }}</td>
                <td><eth-status-chip [value]="row.item.status" /></td>
                <td><a [routerLink]="['/expedientes', row.item.id]">{{ 'cases.open' | translate }}</a></td>
              </tr>
            }
          </tbody>
        </table>
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
export class CaseListPage {
  private readonly cases = inject(CommitteeCaseService);
  private readonly auth = inject(DemoAuthService);
  private readonly permissions = inject(PermissionService);
  private readonly store = inject(EthicsStore);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly query = toSignal(this.route.queryParamMap, { initialValue: this.route.snapshot.queryParamMap });
  readonly committees = COMMITTEE_LABELS;
  readonly options: readonly { value: CaseQueue | ''; labelKey: string }[] = [
    { value: '', labelKey: 'reviews.filterAll' },
    { value: 'admissibility', labelKey: 'dashboard.admissibility' },
    { value: 'resolutions', labelKey: 'dashboard.resolutions' }
  ];
  readonly filter = signal<CaseQueue | ''>(caseQueue(this.route.snapshot.queryParamMap.get('queue')));
  readonly rows = computed(() => {
    const user = this.auth.currentUser();
    const active = this.auth.activeCommittee();
    const items = !user
      ? []
      : hasScope(user, 'GLOBAL')
        ? this.cases.list()
        : hasScope(user, 'COMMITTEE')
          ? this.cases.byCommittee(active ? [active] : user.committees)
          : this.cases.list().filter((item) => this.permissions.can(user, 'VIEW_PROJECTS', {
              projectId: item.projectId,
              committeeCaseId: item.id,
              committeeType: item.committee,
              workflowStatus: item.status
            }));
    return items.filter((item) => matchesCaseQueue(item.status, this.filter())).map((item) => {
      const project = this.store.snapshot().projects.find((row) => row.id === item.projectId);
      return {
        item,
        title: project?.title ?? item.projectId,
        diseiCode: project?.code ?? ''
      };
    });
  });

  constructor() {
    effect(() => {
      const next = caseQueue(this.query().get('queue'));
      if (next !== this.filter()) {
        this.filter.set(next);
      }
    });
  }

  setFilter(value: CaseQueue | ''): void {
    this.filter.set(value);
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { queue: value || null },
      replaceUrl: true
    });
  }
}
