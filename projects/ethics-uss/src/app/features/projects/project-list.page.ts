import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { COMMITTEE_LABELS } from '../../mocks/catalogs';
import { ProjectService } from '../../core/services/project.service';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';

@Component({
  selector: 'eth-project-list-page',
  imports: [RouterLink, StatusChipComponent, EmptyStateComponent, ...I18N_IMPORTS],
  template: `
    <section>
      <div class="actions" style="margin-top:0">
        <h1>{{ 'projects.title' | translate }}</h1>
        <a class="btn" routerLink="/proyectos/nuevo">
          <span class="button-icon" aria-hidden="true">+</span>
          <span>{{ 'dashboard.newEthicsApplication' | translate }}</span>
        </a>
      </div>
      @if (!rows().length) {
        <eth-empty-state title="projects.emptyTitle" message="projects.emptyMessage" />
      }
      <div class="grid-2">
        @for (row of rows(); track row.project.id) {
          <article class="card">
            <h2>{{ row.project.title }}</h2>
            <p class="muted">{{ row.project.code }} · {{ row.project.fundingSource }}</p>
            <eth-status-chip [value]="row.globalStatus" />
            <div>
              @for (item of row.cases; track item.id) {
                <p>{{ committees[item.committee] | translate }}: <eth-status-chip [value]="item.status" /></p>
              }
            </div>
            <a routerLink="/proyectos/{{ row.project.id }}">{{ 'projects.viewProject' | translate }}</a>
          </article>
        }
      </div>
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ProjectListPage {
  private readonly projects = inject(ProjectService);
  readonly rows = computed(() => this.projects.listVisible());
  readonly committees = COMMITTEE_LABELS;
}
