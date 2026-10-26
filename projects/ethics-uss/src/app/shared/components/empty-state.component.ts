import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { I18N_IMPORTS } from '../i18n/i18n.imports';

@Component({
  selector: 'eth-empty-state',
  imports: [...I18N_IMPORTS],
  template: `
    <div class="empty">
      <strong>{{ title() | translate }}</strong>
      <p>{{ message() | translate }}</p>
      <ng-content />
    </div>
  `,
  styles: `
    .empty {
      padding: 1.5rem;
      border: 1px dashed var(--eth-border);
      border-radius: var(--eth-radius);
      background: #faf8f3;
      color: var(--eth-muted);
    }
    strong { display: block; color: var(--eth-ink); margin-bottom: 0.35rem; }
    p { margin: 0; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class EmptyStateComponent {
  readonly title = input('empty.none.title');
  readonly message = input('empty.none.message');
}
