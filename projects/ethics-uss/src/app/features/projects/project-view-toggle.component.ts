import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';

@Component({
  selector: 'eth-project-view-toggle',
  imports: [...I18N_IMPORTS],
  template: `
    <div class="view-toggle" role="group" [attr.aria-label]="'projects.viewMode' | translate">
      <button
        type="button"
        [attr.aria-pressed]="mode() === 'cards'"
        [attr.aria-label]="'projects.cardView' | translate"
        [attr.title]="'projects.cardView' | translate"
        (click)="modeChange.emit('cards')"
      >
        <span aria-hidden="true">▦</span>
      </button>
      <button
        type="button"
        [attr.aria-pressed]="mode() === 'list'"
        [attr.aria-label]="'projects.listView' | translate"
        [attr.title]="'projects.listView' | translate"
        (click)="modeChange.emit('list')"
      >
        <span aria-hidden="true">☷</span>
      </button>
    </div>
  `,
  styles: `
    .view-toggle {
      display: inline-flex;
      border: 1px solid var(--eth-border);
      border-radius: 8px;
      overflow: hidden;
      background: #fff;
    }
    button {
      min-width: 2.5rem;
      min-height: 2.5rem;
      border: 0;
      background: transparent;
      color: var(--eth-primary);
      font-size: 1.05rem;
      line-height: 1;
      cursor: pointer;
    }
    button + button {
      border-left: 1px solid var(--eth-border);
    }
    button[aria-pressed='true'] {
      background: var(--eth-primary);
      color: #fff;
    }
    button:focus-visible {
      outline: 2px solid var(--eth-accent);
      outline-offset: -2px;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ProjectViewToggleComponent {
  readonly mode = input.required<'cards' | 'list'>();
  readonly modeChange = output<'cards' | 'list'>();
}
