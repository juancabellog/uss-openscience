import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';

export interface ReviewerOption {
  id: string;
  name: string;
  email?: string;
  specialty?: string;
  detail: string;
  meta: string;
}

const VISIBLE_LIMIT = 8;

@Component({
  selector: 'eth-reviewer-combobox',
  imports: [...I18N_IMPORTS],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="combo">
      <label class="label-caption" [attr.id]="labelId" [attr.for]="inputId">{{ label() }}</label>
      <input
        #field
        [id]="inputId"
        type="text"
        role="combobox"
        autocomplete="off"
        aria-autocomplete="list"
        [attr.aria-labelledby]="labelId"
        [attr.aria-expanded]="open()"
        [attr.aria-controls]="open() ? listId() : null"
        [attr.aria-activedescendant]="activeId()"
        [value]="display()"
        [placeholder]="'reviewers.search' | translate"
        (input)="onInput($any($event.target).value)"
        (focus)="openList()"
        (blur)="close()"
        (keydown)="onKey($event)"
      />
      @if (value()) {
        <button
          type="button"
          class="clear"
          [attr.aria-label]="'reviewers.clear' | translate"
          (mousedown)="$event.preventDefault()"
          (click)="clear(field)"
        >×</button>
      }
      @if (open()) {
        <ul [id]="listId()" role="listbox" [attr.aria-label]="label()">
          @for (person of visible(); track person.id; let index = $index) {
            <li
              role="option"
              [id]="optionId(index)"
              [attr.aria-selected]="person.id === value()"
              [class.is-active]="index === active()"
              (mousedown)="$event.preventDefault(); choose(person)"
            >
              <span>{{ person.name }}</span>
              <span class="muted">{{ person.detail }}</span>
              <span class="muted">{{ person.meta }}</span>
            </li>
          } @empty {
            <li class="status" role="status">{{ 'reviewers.noMatches' | translate }}</li>
          }
        </ul>
      }
    </div>
  `,
  styles: `
    .combo { position: relative; display: grid; gap: 0.35rem; }
    input { padding-right: 2.2rem; }
    .clear {
      position: absolute;
      top: 1.85rem;
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
    li[role='option']:hover { background: #f6f3ec; }
    .muted { color: var(--eth-muted); font-size: 0.8rem; }
    .status { padding: 0.45rem 0.75rem; color: var(--eth-muted); font-size: 0.8rem; }
  `
})
export class ReviewerComboboxComponent {
  readonly options = input<ReviewerOption[]>([]);
  readonly value = input('');
  readonly label = input.required<string>();
  readonly listId = input.required<string>();
  readonly valueChange = output<string>();
  readonly open = signal(false);
  readonly editing = signal(false);
  readonly query = signal('');
  readonly active = signal(-1);

  get inputId(): string {
    return `${this.listId()}-input`;
  }

  get labelId(): string {
    return `${this.listId()}-label`;
  }

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
      const specialty = (person.specialty ?? '').toLocaleLowerCase();
      return name.includes(query) || email.includes(query) || specialty.includes(query);
    });
  });

  readonly visible = computed(() => this.filtered().slice(0, VISIBLE_LIMIT));

  optionId(index: number): string {
    return `${this.listId()}-option-${index}`;
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

  choose(person: ReviewerOption): void {
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
        this.active.update((index) => Math.min(index + 1, Math.max(items.length - 1, 0)));
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
