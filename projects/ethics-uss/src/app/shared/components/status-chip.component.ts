import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { CASE_STATUS_LABELS } from '../../mocks/catalogs';
import { I18nService } from '../../core/i18n/i18n.service';

const STATUS_TONES: Record<string, string> = {
  APPROVED: 'success',
  ACCEPTED: 'success',
  COMPLETED: 'success',
  EXEMPT: 'success',
  CLASSIFIED: 'success',
  PENDING_DOCUMENTS: 'warning',
  RETURNED: 'warning',
  WITH_OBSERVATIONS: 'warning',
  AWAITING_RESEARCHER: 'warning',
  PENDING: 'warning',
  REJECTED: 'danger',
  WITHDRAWN: 'danger',
  INVALID: 'danger',
  CONFLICT: 'danger',
  DRAFT: 'muted',
  TO_BE_DETERMINED: 'muted',
  ADMISSIBILITY: 'info',
  PENDING_ASSIGNMENT: 'info',
  PENDING_ACCEPTANCE: 'info',
  IN_REVIEW: 'info',
  IN_EVALUATION: 'info',
  MIXED: 'info',
  NOTIFIED: 'info',
  PENDING_SESSION: 'info',
  PENDING_RESOLUTION: 'info',
  PENDING_PRESIDENT_VALIDATION: 'info',
  PENDING_CLASSIFICATION: 'info',
  SUBMITTED: 'info'
};

@Component({
  selector: 'eth-status-chip',
  template: `<span class="chip" [attr.data-tone]="resolvedTone()">{{ label() }}</span>`,
  styles: `
    .chip {
      display: inline-flex;
      align-items: center;
      min-height: 1.55rem;
      padding: 0.15rem 0.65rem;
      border: 1px solid transparent;
      border-radius: 999px;
      background: #e7eef4;
      color: var(--eth-primary);
      font-size: 0.75rem;
      font-weight: 600;
      line-height: 1;
      white-space: nowrap;
      pointer-events: none;
    }
    .chip[data-tone='success'] { background: #e3f4ea; color: var(--eth-success); border-color: #c5e4d0; }
    .chip[data-tone='warning'] { background: #fff3d6; color: var(--eth-warning); border-color: #ead6a2; }
    .chip[data-tone='danger'] { background: #f8e4e2; color: var(--eth-danger); border-color: #ecc4c0; }
    .chip[data-tone='info'] { background: #dceaf6; color: var(--eth-info); border-color: #c0d6e8; }
    .chip[data-tone='muted'] { background: #eceae4; color: var(--eth-muted); border-color: #ddd8ce; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class StatusChipComponent {
  private readonly i18n = inject(I18nService);
  readonly value = input('');
  readonly tone = input<string | undefined>(undefined);
  readonly resolvedTone = computed(() => this.tone() ?? STATUS_TONES[this.value()] ?? 'info');

  label(): string {
    const key = CASE_STATUS_LABELS[this.value()];
    return key ? this.i18n.translate(key) : this.value();
  }
}
