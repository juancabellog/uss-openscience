import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'os-data-table-shell',
  templateUrl: './data-table-shell.component.html',
  styleUrl: './data-table-shell.component.scss',
  host: {
    '[class.table-shell-host--fluid]': 'fluid()',
    '[class.table-shell-host--sticky]': 'sticky()',
    '[class.table-shell-host--sticky-start]': 'stickyStart()',
    '[class.table-shell-host--sticky-end]': 'stickyEnd()',
    '[style.--os-table-viewport-max]': 'maxHeight() ? maxHeight() : null'
  },
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DataTableShellComponent {
  readonly caption = input('');
  readonly fluid = input(false);
  /** Local table viewport: sticky thead, overflow auto, horizontal scrollbar at the visible bottom. */
  readonly sticky = input(true);
  /** Stick `.col-sticky-start` when present; otherwise the first column. */
  readonly stickyStart = input(false);
  readonly stickyEnd = input(false);
  /** Optional CSS max-height override for the viewport, e.g. {@code min(60vh, 36rem)}. */
  readonly maxHeight = input('');
}
