import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';

export interface ProjectResearcherOption {
  id: string;
  name: string;
  email?: string;
}

const VISIBLE_LIMIT = 8;

@Component({
  selector: 'eth-researcher-combobox',
  imports: [...I18N_IMPORTS],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="combo">
      <input
        #field
        type="text"
        role="combobox"
        autocomplete="off"
        aria-autocomplete="list"
        aria-labelledby="researcher-combobox-label"
        [attr.aria-expanded]="open()"
        [attr.aria-controls]="open() ? listId : null"
        [attr.aria-activedescendant]="activeId()"
        [value]="display()"
        [placeholder]="(editing() ? 'projects.searchResearcher' : 'projects.allResearchers') | translate"
        (input)="onInput($any($event.target).value)"
        (focus)="openList()"
        (blur)="close()"
        (keydown)="onKey($event)"
      />
      @if (value()) {
        <button
          type="button"
          class="clear"
          [attr.aria-label]="'projects.clearResearcher' | translate"
          (mousedown)="$event.preventDefault()"
          (click)="clear(field)"
        >×</button>
      }
      @if (open()) {
        <ul [id]="listId" role="listbox" [attr.aria-label]="'projects.researcher' | translate">
          @for (person of visible(); track person.id; let index = $index) {
            <li
              role="option"
              [id]="optionId(index)"
              [attr.aria-selected]="person.id === value()"
              [class.is-active]="index === active()"
              (mousedown)="$event.preventDefault(); choose(person)"
            >
              <span>{{ person.name }}</span>
              @if (person.email) {
                <span class="muted">{{ person.email }}</span>
              }
            </li>
          } @empty {
            <li class="status" role="presentation">{{ 'projects.noResearcherMatches' | translate }}</li>
          }
          @if (more()) {
            <li class="status" role="presentation">{{ 'projects.researcherRefine' | translate }}</li>
          }
        </ul>
      }
    </div>
  `,
  styles: `
    .combo {
      position: relative;
    }
    input {
      padding-right: 2.2rem;
    }
    .clear {
      position: absolute;
      top: 0.35rem;
      right: 0.35rem;
      width: 1.7rem;
      height: 1.7rem;
      padding: 0;
      border: 0;
      border-radius: 999px;
      background: transparent;
      color: var(--eth-muted);
      font-size: 1.1rem;
      line-height: 1;
    }
    .clear:focus-visible,
    .combo input:focus-visible {
      outline: 2px solid var(--eth-accent);
      outline-offset: 2px;
    }
    ul {
      position: absolute;
      z-index: 6;
      top: calc(100% + 0.25rem);
      left: 0;
      right: 0;
      max-height: 16rem;
      margin: 0;
      padding: 0.25rem 0;
      overflow: auto;
      list-style: none;
      border: 1px solid var(--eth-border);
      border-radius: 8px;
      background: #fff;
    }
    li[role='option'] {
      display: grid;
      gap: 0.05rem;
      padding: 0.45rem 0.75rem;
      cursor: pointer;
    }
    li.is-active,
    li[role='option']:hover {
      background: #f6f3ec;
    }
    .status {
      padding: 0.45rem 0.75rem;
      color: var(--eth-muted);
      font-size: 0.8rem;
    }
  `
})
export class ResearcherComboboxComponent {
  readonly options = input<ProjectResearcherOption[]>([]);
  readonly value = input('');
  readonly valueChange = output<string>();
  readonly listId = 'researcher-combobox-list';
  readonly open = signal(false);
  readonly editing = signal(false);
  readonly query = signal('');
  readonly active = signal(-1);

  readonly display = computed(() => {
    if (this.editing()) {
      return this.query();
    }
    return this.options().find((person) => person.id === this.value())?.name ?? '';
  });

  private readonly filtered = computed(() => {
    const query = (this.editing() ? this.query() : '').trim().toLocaleLowerCase();
    const source = this.options();
    if (!query) {
      return source;
    }
    return source.filter((person) => {
      const name = person.name.toLocaleLowerCase();
      const email = (person.email ?? '').toLocaleLowerCase();
      return name.includes(query) || email.includes(query);
    });
  });

  readonly visible = computed(() => this.filtered().slice(0, VISIBLE_LIMIT));
  readonly more = computed(() => this.filtered().length > VISIBLE_LIMIT);

  optionId(index: number): string {
    return `${this.listId}-option-${index}`;
  }

  activeId(): string | null {
    const index = this.active();
    return this.open() && index >= 0 && index < this.visible().length ? this.optionId(index) : null;
  }

  openList(): void {
    this.open.set(true);
  }

  close(): void {
    this.open.set(false);
    this.editing.set(false);
    this.query.set('');
    this.active.set(-1);
  }

  onInput(text: string): void {
    this.editing.set(true);
    this.query.set(text);
    this.open.set(true);
    this.active.set(text.trim() ? 0 : -1);
    if (this.value()) {
      this.valueChange.emit('');
    }
  }

  choose(person: ProjectResearcherOption): void {
    this.editing.set(false);
    this.query.set('');
    this.open.set(false);
    this.active.set(-1);
    this.valueChange.emit(person.id);
  }

  clear(field: HTMLInputElement): void {
    this.editing.set(false);
    this.query.set('');
    this.open.set(false);
    this.active.set(-1);
    this.valueChange.emit('');
    field.focus();
  }

  onKey(event: KeyboardEvent): void {
    const items = this.visible();
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        this.open.set(true);
        this.active.update((index) => Math.min(index + 1, items.length - 1));
        if (this.active() < 0 && items.length) {
          this.active.set(0);
        }
        break;
      case 'ArrowUp':
        event.preventDefault();
        this.open.set(true);
        this.active.update((index) => (index <= 0 ? 0 : index - 1));
        break;
      case 'Enter':
        if (this.open() && items[this.active()]) {
          event.preventDefault();
          this.choose(items[this.active()]);
        }
        break;
      case 'Escape':
        event.preventDefault();
        this.close();
        break;
      case 'Tab':
        this.close();
        break;
      default:
        break;
    }
  }
}
