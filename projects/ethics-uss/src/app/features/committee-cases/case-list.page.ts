import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { COMMITTEE_LABELS } from '../../mocks/catalogs';
import { DemoAuthService } from '../../core/auth/demo-auth.service';
import { CommitteeCaseService } from '../../core/services/committee-case.service';
import { EthicsStore } from '../../core/services/ethics-store.service';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';

@Component({
  selector: 'eth-case-list-page',
  imports: [RouterLink, StatusChipComponent, ...I18N_IMPORTS],
  template: `
    <section>
      <h1>{{ 'cases.title' | translate }}</h1>
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
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CaseListPage {
  private readonly cases = inject(CommitteeCaseService);
  private readonly auth = inject(DemoAuthService);
  private readonly store = inject(EthicsStore);
  readonly committees = COMMITTEE_LABELS;
  readonly rows = computed(() => {
    const user = this.auth.currentUser();
    const active = this.auth.activeCommittee();
    const items = user?.role === 'ADMINISTRATOR'
      ? this.cases.list()
      : this.cases.byCommittee(active ? [active] : (user?.committees ?? []));
    return items.map((item) => {
      const project = this.store.snapshot().projects.find((row) => row.id === item.projectId);
      return {
        item,
        title: project?.title ?? item.projectId,
        diseiCode: project?.code ?? ''
      };
    });
  });
}
