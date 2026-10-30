import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { I18nService } from '../../core/i18n/i18n.service';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';
import { ProjectViewModel } from './project-view.model';

@Component({
  selector: 'eth-project-group',
  imports: [RouterLink, StatusChipComponent, ...I18N_IMPORTS],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <article class="project-group" [class.is-open]="expanded()">
      <header class="project-group__header">
        <button
          type="button"
          class="project-group__toggle"
          [attr.aria-expanded]="expanded()"
          [attr.aria-controls]="panelId()"
          [attr.aria-label]="(expanded() ? 'projects.collapseProject' : 'projects.expandProject') | translate"
          (click)="expandedChange.emit(!expanded())"
        >
          <span aria-hidden="true">{{ expanded() ? '▼' : '▶' }}</span>
        </button>
        <div class="project-group__summary" (click)="expandedChange.emit(!expanded())">
          <h2>{{ project().title }}</h2>
          <p class="muted">{{ project().code }} · {{ project().funding }}</p>
          <p class="project-group__meta">
            @if (project().researcherName; as name) {
              <span>{{ 'projects.responsibleShort' | translate }}: {{ name }}</span>
            }
            <span>
              <span class="sr-only">{{ 'projects.submissionDate' | translate }}</span>
              {{ 'projects.submittedLabel' | translate }}
              {{ project().submittedAt | localeDate }}
            </span>
          </p>
          @if (project().overallStatus || !expanded()) {
            <p class="project-group__status">
              @if (project().overallStatus; as status) {
                <span class="sr-only">{{ 'dashboard.globalStatus' | translate }}</span>
                <eth-status-chip [value]="status" />
              }
              @if (!expanded()) {
                <span class="muted">{{ committeeCount() }}</span>
                @if (project().nextExpiration; as next) {
                  <span class="muted">
                    {{ 'projects.nextExpiration' | translate }}:
                    {{ next.date | localeDate }} · {{ next.committeeKey | translate }}
                  </span>
                }
              }
            </p>
          }
        </div>
        <a class="project-group__open" [routerLink]="['/proyectos', project().id]" (click)="$event.stopPropagation()">
          {{ 'projects.viewProject' | translate }}
          <span aria-hidden="true"> →</span>
        </a>
      </header>
      @if (expanded()) {
        <div class="project-group__detail" [id]="panelId()">
          @if (project().committees.length) {
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>{{ 'cases.committee' | translate }}</th>
                    <th>{{ 'cases.committeeCode' | translate }}</th>
                    <th>{{ 'cases.status' | translate }}</th>
                    <th [attr.title]="'projects.committeeIntake' | translate">{{ 'projects.intakeColumn' | translate }}</th>
                    <th>{{ 'projects.expiration' | translate }}</th>
                  </tr>
                </thead>
                <tbody>
                  @for (committee of project().committees; track committee.id) {
                    <tr>
                      <td [attr.data-label]="'cases.committee' | translate">
                        <span [attr.title]="committee.fullNameKey | translate">{{ committee.shortNameKey | translate }}</span>
                      </td>
                      <td [attr.data-label]="'cases.committeeCode' | translate">{{ committee.code }}</td>
                      <td [attr.data-label]="'cases.status' | translate">
                        <eth-status-chip [value]="committee.status" [labelKey]="committee.statusLabelKey" [tone]="committee.statusLabelKey ? 'warning' : undefined" />
                        @if (committee.approvalUntil) {
                          <span class="note">{{ 'projects.approvalValidUntil' | translate: { date: (committee.approvalUntil | localeDate) } }}</span>
                        }
                      </td>
                      <td [attr.data-label]="'projects.committeeIntake' | translate">{{ committee.receivedAt | localeDate }}</td>
                      <td [attr.data-label]="'projects.expiration' | translate">
                        @if (committee.expiresAt) {
                          <span>{{ 'projects.expiresOn' | translate: { date: (committee.expiresAt | localeDate) } }}</span>
                          @if (committee.daysLeft !== null && committee.daysLeft >= 0) {
                            <span class="note">{{ 'projects.daysRemaining' | translate: { days: committee.daysLeft } }}</span>
                          }
                        } @else {
                          {{ 'common.dash' | translate }}
                        }
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          } @else {
            <p class="project-group__empty">{{ 'projects.noCommittees' | translate }}</p>
          }
        </div>
      }
    </article>
  `,
  styles: `
    .project-group {
      border: 1px solid var(--eth-border);
      border-radius: var(--eth-radius);
      background: #fbfaf7;
      overflow: hidden;
    }
    .project-group__header {
      display: grid;
      grid-template-columns: auto minmax(0, 1fr) auto;
      gap: 0.75rem 1rem;
      align-items: start;
      padding: 0.9rem 1rem;
    }
    .project-group__toggle {
      width: 2rem;
      height: 2rem;
      margin-top: 0.1rem;
      padding: 0;
      border: 1px solid var(--eth-border);
      border-radius: 999px;
      background: #fff;
      color: var(--eth-primary);
      font-size: 0.75rem;
      line-height: 1;
    }
    .project-group__toggle:focus-visible,
    .project-group__open:focus-visible {
      outline: 2px solid var(--eth-accent);
      outline-offset: 2px;
    }
    .project-group__summary {
      min-width: 0;
      cursor: pointer;
    }
    .project-group__summary h2 {
      margin: 0;
      font-size: 1.05rem;
    }
    .project-group__summary p {
      margin: 0.2rem 0 0;
    }
    .project-group__meta,
    .project-group__status {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.35rem 0.75rem;
    }
    .project-group__open {
      font-weight: 700;
      white-space: nowrap;
    }
    .project-group__detail {
      padding: 0 1rem 1rem 3.75rem;
      background: #fff;
      border-top: 1px solid var(--eth-border);
    }
    .project-group__empty {
      margin: 0.85rem 0 0;
      color: var(--eth-muted);
      font-size: 0.9rem;
    }
    .note {
      display: block;
      margin-top: 0.25rem;
      color: var(--eth-muted);
      font-size: 0.75rem;
    }
    .sr-only {
      position: absolute;
      width: 1px;
      height: 1px;
      padding: 0;
      margin: -1px;
      overflow: hidden;
      clip: rect(0, 0, 0, 0);
      white-space: nowrap;
      border: 0;
    }
    @media (max-width: 720px) {
      .project-group__header {
        grid-template-columns: auto minmax(0, 1fr);
      }
      .project-group__open {
        grid-column: 2;
      }
      .project-group__detail {
        padding-left: 1rem;
      }
      .project-group table,
      .project-group thead,
      .project-group tbody,
      .project-group tr,
      .project-group td {
        display: block;
      }
      .project-group thead {
        position: absolute;
        width: 1px;
        height: 1px;
        overflow: hidden;
        clip: rect(0, 0, 0, 0);
      }
      .project-group tr {
        padding: 0.7rem 0;
        border-bottom: 1px solid var(--eth-border);
      }
      .project-group td {
        display: flex;
        justify-content: space-between;
        gap: 1rem;
        padding: 0.2rem 0;
        border: 0;
      }
      .project-group td::before {
        content: attr(data-label);
        color: var(--eth-muted);
        font-size: 0.75rem;
        font-weight: 700;
      }
    }
  `
})
export class ProjectGroupComponent {
  private readonly i18n = inject(I18nService);
  readonly project = input.required<ProjectViewModel>();
  readonly expanded = input(false);
  readonly expandedChange = output<boolean>();

  panelId(): string {
    return `project-committees-${this.project().id}`;
  }

  committeeCount(): string {
    const count = this.project().committees.length;
    const key = count === 1 ? 'projects.committeeCountOne' : 'projects.committeeCount';
    return this.i18n.translate(key, { count });
  }
}
