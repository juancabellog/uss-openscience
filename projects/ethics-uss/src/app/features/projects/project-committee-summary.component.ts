import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';
import { ProjectCommitteeView } from './project-view.model';

@Component({
  selector: 'eth-project-committee-summary',
  imports: [StatusChipComponent, ...I18N_IMPORTS],
  template: `
    <section class="committees">
      <h3>{{ 'dashboard.committees' | translate }}</h3>
      <ul>
        @for (item of committees(); track item.id) {
          <li>
            <span class="name">
              <span [attr.title]="item.fullNameKey | translate">{{ item.shortNameKey | translate }}</span>
              <span class="code">{{ item.code }}</span>
            </span>
            <span class="status">
              <eth-status-chip [value]="item.status" [labelKey]="item.statusLabelKey" [tone]="item.statusLabelKey ? 'warning' : undefined" />
              @if (item.approvalUntil) {
                <span class="note">{{ 'projects.approvalValidUntil' | translate: { date: (item.approvalUntil | localeDate) } }}</span>
              }
            </span>
          </li>
        }
      </ul>
    </section>
  `,
  styles: `
    h3 {
      margin: 0.2rem 0 0.35rem;
      font-family: var(--eth-font);
      font-size: 0.72rem;
      font-weight: 700;
      letter-spacing: 0.04em;
      text-transform: uppercase;
      color: var(--eth-muted);
    }
    ul {
      list-style: none;
      margin: 0;
      padding: 0;
    }
    li {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.75rem;
      padding: 0.45rem 0;
      border-top: 1px solid var(--eth-border);
    }
    .name {
      min-width: 0;
    }
    .name > span:first-child {
      display: block;
    }
    .code,
    .note {
      display: block;
      margin-top: 0.1rem;
      color: var(--eth-muted);
      font-size: 0.75rem;
    }
    .status {
      display: grid;
      justify-items: end;
      gap: 0.2rem;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ProjectCommitteeSummaryComponent {
  readonly committees = input.required<ProjectCommitteeView[]>();
}
