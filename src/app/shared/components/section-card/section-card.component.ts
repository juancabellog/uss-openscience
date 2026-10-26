import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'os-section-card',
  templateUrl: './section-card.component.html',
  styleUrl: './section-card.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SectionCardComponent {
  readonly title = input.required<string>();
  readonly description = input('');
}
