import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { I18nService } from '../../core/i18n/i18n.service';
import { PrivacyLevel } from '../../core/models/ethics.models';

@Component({
  selector: 'eth-privacy-badge',
  template: `<span class="badge" [attr.data-level]="level()">{{ text() }}</span>`,
  styles: `
    .badge {
      display: inline-flex;
      align-items: center;
      min-height: 1.7rem;
      padding: 0.2rem 0.7rem;
      border: 1px solid #e2d3b0;
      border-radius: 999px;
      background: #f6efe0;
      color: #6d5420;
      font-size: 0.8rem;
      font-weight: 600;
      line-height: 1;
    }
    .badge[data-level='COMMITTEE_ONLY'] {
      background: #eceae4;
      border-color: #d5d0c6;
      color: #4d5a66;
    }
    .badge[data-level='PUBLICABLE'] {
      background: #e4f3ef;
      border-color: #b9d9d1;
      color: #1f5f55;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PrivacyBadgeComponent {
  private readonly i18n = inject(I18nService);
  readonly level = input<PrivacyLevel>('COMMITTEE_ONLY');

  text(): string {
    if (this.level() === 'RESEARCHER_VISIBLE') {
      return this.i18n.translate('privacy.researcherVisible');
    }
    if (this.level() === 'PUBLICABLE') {
      return this.i18n.translate('privacy.publicable');
    }
    return this.i18n.translate('privacy.committeeOnly');
  }
}
