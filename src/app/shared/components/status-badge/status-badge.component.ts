import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { IconComponent } from '../icon/icon.component';
import { InfoHintComponent } from '../info-hint/info-hint.component';

export type OsBadgeTone =
  | 'ok'
  | 'review'
  | 'pending'
  | 'open'
  | 'info'
  | 'success'
  | 'warning'
  | 'danger'
  | 'neutral'
  | 'muted';

@Component({
  selector: 'os-status-badge',
  imports: [IconComponent, InfoHintComponent],
  templateUrl: './status-badge.component.html',
  styleUrl: './status-badge.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class StatusBadgeComponent {
  readonly label = input.required<string>();
  readonly tone = input<OsBadgeTone>('neutral');
  readonly icon = input<string | null>(null);
  readonly hint = input<string | null>(null);
  readonly hintAriaLabel = input<string | null>(null);
  readonly hintActionLabel = input<string | null>(null);
  readonly hintAction = output<void>();

  onHintAction(): void {
    this.hintAction.emit();
  }
}
