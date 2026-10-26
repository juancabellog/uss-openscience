import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'os-icon',
  templateUrl: './icon.component.html',
  styleUrl: './icon.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class IconComponent {
  readonly name = input.required<string>();
  readonly label = input('');
}
