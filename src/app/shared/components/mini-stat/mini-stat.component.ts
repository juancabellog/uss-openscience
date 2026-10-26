import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'os-mini-stat',
  templateUrl: './mini-stat.component.html',
  styleUrl: './mini-stat.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MiniStatComponent {
  readonly label = input.required<string>();
  readonly value = input.required<string>();
  readonly tooltip = input('');
}
