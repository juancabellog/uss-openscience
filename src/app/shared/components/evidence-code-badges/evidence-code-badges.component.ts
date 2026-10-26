import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { OsBadgeTone, StatusBadgeComponent } from '../status-badge/status-badge.component';
import { I18N_HOST, I18N_IMPORTS } from '../../i18n/i18n.imports';

export interface EvidenceCodeBadgeItem {
  code: string;
  labelKey: string;
  tone?: OsBadgeTone;
  icon?: string | null;
}

@Component({
  selector: 'os-evidence-code-badges',
  hostDirectives: [...I18N_HOST],
  imports: [...I18N_IMPORTS, StatusBadgeComponent],
  templateUrl: './evidence-code-badges.component.html',
  styleUrl: './evidence-code-badges.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class EvidenceCodeBadgesComponent {
  readonly items = input<EvidenceCodeBadgeItem[]>([]);
}
