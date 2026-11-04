import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { I18nService } from '../../core/i18n/i18n.service';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';
import { ProjectCommitteeSummaryComponent } from './project-committee-summary.component';
import { ProjectViewModel } from './project-view.model';

@Component({
  selector: 'eth-project-card',
  imports: [RouterLink, StatusChipComponent, ProjectCommitteeSummaryComponent, ...I18N_IMPORTS],
  template: `
    <article class="project-card">
      <a
        class="project-card__link"
        [routerLink]="project().continueDraft ? ['/proyectos', project().id, 'borrador'] : ['/proyectos', project().id]"
        [attr.aria-label]="(project().continueDraft ? 'projects.continueDraft' : 'projects.viewProject') | translate"
      >
        <div class="project-card__main">
          <h2>{{ project().title || ('projects.untitled' | translate) }}</h2>
          <p class="muted">{{ project().code }} · {{ project().funding }}</p>
          @if (project().continueDraft) {
            <p class="draft-note">{{ 'projects.draftPending' | translate }}</p>
            <p class="draft-note">{{ 'projects.draftUpdated' | translate }} {{ i18n.formatStamp(project().updatedAt) }}</p>
          }
          @if (project().overallStatus; as status) {
            <p class="kicker">{{ 'dashboard.globalStatus' | translate }}</p>
            <eth-status-chip [value]="status" />
          }
          @if (project().committees.length) {
            <eth-project-committee-summary [committees]="project().committees" />
          }
          @if (project().nextExpiration; as next) {
            <p class="next-expiry">
              <span>{{ 'projects.nextExpiration' | translate }}</span>
              {{ next.date | localeDate }} · {{ next.committeeKey | translate }}
            </p>
          }
        </div>
        <span class="project-card__footer">
          {{ (project().continueDraft ? 'projects.continueDraft' : 'projects.viewProject') | translate }}
          <span aria-hidden="true"> →</span>
        </span>
      </a>
    </article>
  `,
  styles: `
    .project-card {
      display: flex;
      min-width: 0;
      height: 100%;
      background: var(--eth-surface);
      border: 1px solid var(--eth-border);
      border-radius: var(--eth-radius);
    }
    .project-card__link {
      display: flex;
      flex: 1;
      flex-direction: column;
      min-width: 0;
      padding: 1rem 1.1rem;
      color: inherit;
      text-decoration: none;
      border-radius: inherit;
    }
    .project-card__link:hover {
      background: #faf9f6;
    }
    .project-card__link:focus-visible {
      outline: 2px solid var(--eth-primary);
      outline-offset: 2px;
    }
    .project-card__main {
      display: grid;
      align-content: start;
      gap: 0.45rem;
    }
    h2 {
      margin: 0;
      font-size: 1.2rem;
      line-height: 1.25;
    }
    .muted,
    .draft-note {
      margin: 0;
    }
    .draft-note {
      color: var(--eth-muted);
      font-size: 0.85rem;
    }
    .kicker {
      margin: 0.35rem 0 0;
      font-size: 0.72rem;
      font-weight: 700;
      letter-spacing: 0.04em;
      text-transform: uppercase;
      color: var(--eth-muted);
    }
    .next-expiry {
      margin: 0.35rem 0 0;
      color: var(--eth-muted);
      font-size: 0.8rem;
    }
    .next-expiry span {
      display: block;
      font-weight: 650;
    }
    .project-card__footer {
      margin-top: auto;
      padding-top: 1rem;
      color: var(--eth-primary);
      font-weight: 650;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ProjectCardComponent {
  readonly project = input.required<ProjectViewModel>();
  readonly i18n = inject(I18nService);
}
