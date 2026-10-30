import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
import { I18nService } from '../../core/i18n/i18n.service';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';

export type HistoryCategory = 'documents' | 'observations' | 'resolutions' | 'communications' | 'other';

export interface CommitteeHistoryEventView {
  id: string;
  date: string;
  action: string;
  fallback: string;
  actorName: string;
  title?: string;
  previousName?: string;
  nextName?: string;
  detail?: string;
  objectLabel?: string;
  linkKey?: string;
  link?: 'document' | 'observation' | 'resolution';
  objectId?: string;
  cycle?: number;
}

const DOCUMENT_ACTIONS = new Set(['DOCUMENT_UPLOADED', 'UPLOAD_DOCUMENT']);
const OBSERVATION_ACTIONS = new Set([
  'OBSERVATIONS_PUBLISHED',
  'PUBLISH_OBSERVATIONS',
  'ANSWER_OBSERVATIONS',
  'SUBMIT_RESPONSES',
  'RESEARCHER_RESPONSE_SUBMITTED',
  'OBSERVATIONS_CONSOLIDATED',
  'OBSERVATION_NOTICE'
]);
const RESOLUTION_ACTIONS = new Set([
  'ISSUE_RESOLUTION',
  'RESOLUTION_VALIDATED',
  'VALIDATE_RESOLUTION',
  'RESOLUTION_PREPARED',
  'PREPARE_RESOLUTION',
  'RESOLUTION_RETURNED_TO_SECRETARY'
]);
const COMMUNICATION_ACTIONS = new Set([
  'NOTIFY_COMMITTEE',
  'COMMITTEE_NOTIFIED',
  'COMMITTEE_NOTIFICATION',
  'COMMITTEE_CONTACT',
  'AMENDMENT_REQUESTED',
  'FOLLOW_UP_REQUESTED'
]);

/** Agrupa el código de auditoría para un filtro futuro. No decide visibilidad. */
export function historyCategory(action: string): HistoryCategory {
  if (DOCUMENT_ACTIONS.has(action)) {
    return 'documents';
  }
  if (OBSERVATION_ACTIONS.has(action)) {
    return 'observations';
  }
  if (RESOLUTION_ACTIONS.has(action)) {
    return 'resolutions';
  }
  if (COMMUNICATION_ACTIONS.has(action)) {
    return 'communications';
  }
  return 'other';
}

@Component({
  selector: 'eth-committee-history-timeline',
  imports: [...I18N_IMPORTS],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="history" [attr.aria-label]="'projects.committeeHistory' | translate">
      <h2>{{ 'projects.history' | translate }}</h2>
      @if (!events().length) {
        <p class="empty">{{ 'projects.noHistory' | translate }}</p>
      } @else {
        <ol>
          @for (event of events(); track event.id) {
            <li [attr.data-category]="category(event.action)">
              <time [attr.datetime]="event.date">{{ stamp(event.date) }}</time>
              <span class="rail" aria-hidden="true"></span>
              <div class="event">
                <strong>{{ label(event) }}</strong>
                <span class="actor">{{ event.actorName }}</span>
                @if (event.previousName) {
                  <span class="meta">{{ 'reviewers.previousReviewer' | translate }}: {{ event.previousName }}</span>
                }
                @if (event.nextName) {
                  <span class="meta">{{ 'reviewers.nextReviewer' | translate }}: {{ event.nextName }}</span>
                }
                @if (event.objectLabel) {
                  <span class="meta">{{ event.objectLabel }}</span>
                }
                @if (event.detail) {
                  <span class="meta">{{ event.detail }}</span>
                }
                @if (event.linkKey) {
                  <button type="button" class="ghost" (click)="open.emit(event)">{{ event.linkKey | translate }}</button>
                }
              </div>
            </li>
          }
        </ol>
      }
    </section>
  `,
  styles: `
    :host {
      display: block;
    }
    .history {
      min-width: 0;
    }
    h2 {
      margin: 0 0 0.85rem;
      font-size: 0.78rem;
      font-weight: 700;
      letter-spacing: 0.04em;
      text-transform: uppercase;
      color: var(--eth-muted);
    }
    .empty {
      margin: 0;
      color: var(--eth-muted);
      font-size: 0.9rem;
    }
    ol {
      list-style: none;
      margin: 0;
      padding: 0;
    }
    li {
      display: grid;
      grid-template-columns: minmax(0, 1fr);
      grid-template-areas:
        'time'
        'event';
      gap: 0.15rem 0.75rem;
      position: relative;
      padding: 0 0 1.05rem 1.15rem;
    }
    time {
      grid-area: time;
      color: var(--eth-muted);
      font-size: 0.78rem;
    }
    .rail {
      position: absolute;
      top: 0.35rem;
      bottom: 0;
      left: 0.18rem;
      width: 1px;
      background: var(--eth-border);
    }
    .rail::before {
      content: '';
      position: absolute;
      top: 0;
      left: -0.22rem;
      width: 0.48rem;
      height: 0.48rem;
      border: 2px solid var(--eth-primary);
      border-radius: 50%;
      background: #fff;
    }
    li:last-child {
      padding-bottom: 0;
    }
    li:last-child .rail {
      bottom: auto;
      height: 0.5rem;
    }
    .event {
      grid-area: event;
      display: grid;
      gap: 0.12rem;
      min-width: 0;
    }
    strong {
      font-size: 0.92rem;
    }
    .actor,
    .meta {
      color: var(--eth-muted);
      font-size: 0.82rem;
    }
    button {
      justify-self: start;
      height: auto;
      margin-top: 0.2rem;
      padding: 0.15rem 0;
      font-size: 0.82rem;
    }
    button:focus-visible {
      outline: 2px solid var(--eth-accent);
      outline-offset: 2px;
    }
  `
})
export class CommitteeHistoryTimelineComponent {
  private readonly i18n = inject(I18nService);
  readonly events = input<CommitteeHistoryEventView[]>([]);
  readonly open = output<CommitteeHistoryEventView>();

  category(action: string): HistoryCategory {
    return historyCategory(action);
  }

  stamp(value: string): string {
    return this.i18n.formatStamp(value);
  }

  label(event: CommitteeHistoryEventView): string {
    return event.title ?? this.i18n.auditAction(event.action, event.fallback);
  }
}
