import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'os-filter-bar',
  templateUrl: './filter-bar.component.html',
  styleUrl: './filter-bar.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class FilterBarComponent {}
