import { ChangeDetectionStrategy, Component, computed, effect, input, output } from '@angular/core';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';
import { ProjectResearcherOption, ResearcherComboboxComponent } from './researcher-combobox.component';

export type { ProjectResearcherOption } from './researcher-combobox.component';

export interface ProjectFilterValue {
  query: string;
  researcherId: string;
  from: string;
  to: string;
  status: string;
  validity: string;
}

export interface ProjectStatusOption {
  value: string;
  labelKey: string;
  count: number;
}

@Component({
  selector: 'eth-project-filters',
  imports: [ResearcherComboboxComponent, ...I18N_IMPORTS],
  template: `
    <div class="filters">
      <label class="field search">
        <span class="label-caption">{{ 'projects.searchLabel' | translate }}</span>
        <input
          type="search"
          [value]="value().query"
          [placeholder]="(showResearcher() ? 'projects.searchPlaceholderStaff' : 'projects.searchPlaceholder') | translate"
          (input)="patch({ query: $any($event.target).value })"
        />
      </label>
      @if (showResearcher()) {
        <div class="field person">
          <span class="label-caption" id="researcher-combobox-label">{{ 'projects.researcher' | translate }}</span>
          <eth-researcher-combobox
            [options]="researchers()"
            [value]="value().researcherId"
            (valueChange)="patch({ researcherId: $event })"
          />
        </div>
      }
      <label class="field validity">
        <span class="label-caption">{{ 'projects.validity' | translate }}</span>
        <select [value]="value().validity" (change)="patch({ validity: $any($event.target).value })">
          <option value="">{{ 'projects.validityAll' | translate }}</option>
          <option value="VIGENTE">{{ 'projects.validityCurrent' | translate }}</option>
          <option value="PROXIMO">{{ 'dashboard.expiringSoon' | translate }}</option>
          <option value="VENCIDO">{{ 'projects.validityExpired' | translate }}</option>
        </select>
      </label>
      <fieldset class="dates">
        <legend>{{ 'projects.filterDate' | translate }}</legend>
        <label class="field">
          <span class="label-caption">{{ 'projects.dateFrom' | translate }}</span>
          <input type="date" [value]="value().from" (input)="patch({ from: $any($event.target).value })" />
        </label>
        <label class="field">
          <span class="label-caption">{{ 'projects.dateTo' | translate }}</span>
          <input type="date" [value]="value().to" (input)="patch({ to: $any($event.target).value })" />
        </label>
      </fieldset>
      @if (active()) {
        <button type="button" class="ghost" (click)="clear()">{{ 'projects.clearFilters' | translate }}</button>
      }
    </div>
    <div class="status-row">
      <span class="label-caption" id="project-status-label">{{ 'projects.projectStatus' | translate }}</span>
      <div class="status-chips" role="group" aria-labelledby="project-status-label">
        <button type="button" [attr.aria-pressed]="!value().status" (click)="patch({ status: '' })">
          {{ 'projects.filterAll' | translate }}
          <span class="count">{{ statusTotal() }}</span>
        </button>
        @for (item of statuses(); track item.value) {
          <button type="button" [attr.aria-pressed]="value().status === item.value" (click)="patch({ status: item.value })">
            {{ item.labelKey | translate }}
            <span class="count">{{ item.count }}</span>
          </button>
        }
      </div>
    </div>
  `,
  styles: `
    :host {
      display: grid;
      gap: 0.7rem;
      margin: 0 0 0.85rem;
    }
    .filters {
      display: flex;
      flex-wrap: wrap;
      align-items: end;
      gap: 0.65rem 0.75rem;
    }
    .status-row {
      display: grid;
      gap: 0.35rem;
      min-width: 0;
    }
    .status-chips {
      display: flex;
      flex-wrap: wrap;
      gap: 0.35rem;
    }
    .status-chips button {
      height: 2.15rem;
      padding: 0 0.75rem;
      border: 1px solid var(--eth-border);
      border-radius: 999px;
      background: #f7f6f3;
      color: var(--eth-ink);
      font-weight: 600;
      white-space: nowrap;
    }
    .status-chips button .count {
      margin-left: 0.35rem;
      color: var(--eth-muted);
      font-variant-numeric: tabular-nums;
    }
    .status-chips button[aria-pressed='true'] {
      border-width: 2px;
      border-color: var(--eth-primary);
      background: #fff;
      color: var(--eth-primary);
      font-weight: 800;
    }
    .status-chips button[aria-pressed='true'] .count {
      color: var(--eth-primary);
    }
    .status-chips button:focus-visible {
      outline: 2px solid var(--eth-primary);
      outline-offset: 2px;
    }
    .field {
      margin: 0;
    }
    .search {
      flex: 1 1 16rem;
    }
    .person,
    .validity {
      flex: 1 1 12rem;
      display: grid;
      gap: 0.35rem;
      align-content: end;
    }
    .dates {
      display: flex;
      flex-wrap: wrap;
      align-items: end;
      gap: 0.65rem;
      margin: 0;
      padding: 0;
      border: 0;
      min-width: 0;
    }
    legend {
      flex: 1 0 100%;
      padding: 0;
      margin-bottom: 0.15rem;
      color: var(--eth-muted);
      font-size: 0.75rem;
      font-weight: 700;
    }
    .dates .field {
      flex: 1 1 8.5rem;
    }
    button {
      align-self: end;
    }
    @media (max-width: 720px) {
      .search,
      .person,
      .validity,
      .dates {
        flex-basis: 100%;
      }
      .status-chips {
        flex-wrap: nowrap;
        overflow-x: auto;
        padding-bottom: 0.2rem;
      }
      .status-chips button {
        flex: 0 0 auto;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ProjectFiltersComponent {
  readonly showResearcher = input(false);
  readonly researchers = input<ProjectResearcherOption[]>([]);
  readonly statuses = input<ProjectStatusOption[]>([]);
  readonly statusTotal = input(0);
  readonly value = input.required<ProjectFilterValue>();
  readonly valueChange = output<ProjectFilterValue>();
  private draft: ProjectFilterValue | null = null;

  constructor() {
    effect(() => {
      this.draft = this.value();
    });
  }

  readonly active = computed(() => {
    const current = this.value();
    return !!(current.query.trim() || current.researcherId || current.from || current.to || current.status || current.validity);
  });

  patch(partial: Partial<ProjectFilterValue>): void {
    const next = { ...(this.draft ?? this.value()), ...partial };
    this.draft = next;
    this.valueChange.emit(next);
  }

  clear(): void {
    const next = { query: '', researcherId: '', from: '', to: '', status: '', validity: '' };
    this.draft = next;
    this.valueChange.emit(next);
  }
}
