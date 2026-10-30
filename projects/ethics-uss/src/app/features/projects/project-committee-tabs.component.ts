import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';

export interface ProjectSectionTab {
  id: string;
  labelKey: string;
  titleKey: string;
}

@Component({
  selector: 'eth-project-committee-tabs',
  imports: [...I18N_IMPORTS],
  template: `
    <div class="tabs" role="tablist" [attr.aria-label]="'projects.sections' | translate">
      @for (tab of tabs(); track tab.id; let index = $index) {
        <button
          type="button"
          role="tab"
          [id]="'project-tab-' + tab.id"
          [attr.aria-selected]="active() === tab.id"
          [attr.aria-controls]="'project-panel-' + tab.id"
          [attr.aria-label]="tab.titleKey | translate"
          [attr.title]="tab.titleKey | translate"
          [attr.tabindex]="active() === tab.id ? 0 : -1"
          (click)="activeChange.emit(tab.id)"
          (keydown)="onKey($event, index)"
        >
          {{ tab.labelKey | translate }}
        </button>
      }
    </div>
  `,
  styles: `
    .tabs {
      display: flex;
      flex-wrap: nowrap;
      gap: 0.15rem;
      overflow-x: auto;
      margin: 0 0 1rem;
      border-bottom: 1px solid var(--eth-border);
    }
    button {
      flex: 0 0 auto;
      height: auto;
      margin: 0;
      padding: 0.7rem 0.95rem;
      border: 0;
      border-bottom: 3px solid transparent;
      border-radius: 0;
      background: transparent;
      color: var(--eth-muted);
      font-weight: 600;
      white-space: nowrap;
    }
    button[aria-selected='true'] {
      background: #f7f4ee;
      color: var(--eth-primary);
      border-bottom-color: var(--eth-primary);
      font-weight: 700;
    }
    button:hover {
      color: var(--eth-primary);
    }
    button:focus-visible {
      outline: 2px solid var(--eth-accent);
      outline-offset: -2px;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ProjectCommitteeTabsComponent {
  readonly tabs = input.required<ProjectSectionTab[]>();
  readonly active = input.required<string>();
  readonly activeChange = output<string>();

  onKey(event: KeyboardEvent, index: number): void {
    const tabs = this.tabs();
    let next = index;
    if (event.key === 'ArrowRight') {
      next = (index + 1) % tabs.length;
    } else if (event.key === 'ArrowLeft') {
      next = (index - 1 + tabs.length) % tabs.length;
    } else if (event.key === 'Home') {
      next = 0;
    } else if (event.key === 'End') {
      next = tabs.length - 1;
    } else {
      return;
    }
    event.preventDefault();
    const id = tabs[next]?.id;
    if (!id) {
      return;
    }
    this.activeChange.emit(id);
    queueMicrotask(() => document.getElementById(`project-tab-${id}`)?.focus());
  }
}
