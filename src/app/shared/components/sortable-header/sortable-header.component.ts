import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { TableSortDirection } from '../../table/table-sort';
import { IconComponent } from '../icon/icon.component';
import { I18N_HOST, I18N_IMPORTS } from '../../i18n/i18n.imports';

@Component({
  selector: 'os-sortable-header',
  hostDirectives: [...I18N_HOST],
  imports: [...I18N_IMPORTS, IconComponent],
  templateUrl: './sortable-header.component.html',
  styleUrl: './sortable-header.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SortableHeaderComponent {
  readonly field = input.required<string>();
  readonly label = input.required<string>();
  readonly activeField = input('');
  readonly direction = input<TableSortDirection>('desc');
  readonly numeric = input(false);
  readonly sortChange = output<string>();

  isActive(): boolean {
    return this.activeField() === this.field();
  }

  iconName(): string {
    if (!this.isActive()) {
      return 'sort-neutral';
    }
    return this.direction() === 'asc' ? 'sort-asc' : 'sort-desc';
  }

  iconLabel(): string {
    if (!this.isActive()) {
      return '';
    }
    return this.direction() === 'asc' ? 'table.sortAscending' : 'table.sortDescending';
  }

  onClick(): void {
    this.sortChange.emit(this.field());
  }
}
