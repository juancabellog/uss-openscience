import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostListener,
  computed,
  effect,
  inject,
  signal,
  viewChild
} from '@angular/core';
import { AppLanguage } from '../../core/i18n/i18n.models';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../pipes/translate.pipe';

const LANGUAGE_OPTIONS: ReadonlyArray<{ id: AppLanguage; nativeName: string }> = [
  { id: 'es', nativeName: 'Español' },
  { id: 'en', nativeName: 'English' }
];

@Component({
  selector: 'ose-language-switcher',
  imports: [TranslatePipe],
  templateUrl: './language-switcher.component.html',
  styleUrl: './language-switcher.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LanguageSwitcherComponent {
  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly i18n = inject(I18nService);
  private readonly trigger = viewChild<ElementRef<HTMLButtonElement>>('trigger');
  readonly language = this.i18n.currentLanguage;
  readonly options = LANGUAGE_OPTIONS;
  readonly open = signal(false);
  readonly activeIndex = signal(0);
  readonly menuId = 'ose-language-menu';
  readonly currentNativeName = computed(
    () => this.options.find((option) => option.id === this.language())?.nativeName ?? 'Español'
  );

  constructor() {
    effect(() => {
      if (!this.open()) {
        return;
      }
      queueMicrotask(() => {
        const option = this.host.nativeElement.querySelector('.lang__option.is-focused');
        if (option instanceof HTMLButtonElement) {
          option.focus();
        }
      });
    });
  }

  toggle(): void {
    if (this.open()) {
      this.close();
      return;
    }
    this.openMenu();
  }

  select(language: AppLanguage): void {
    this.i18n.setLanguage(language);
    this.close(true);
  }

  onTriggerKeydown(event: KeyboardEvent): void {
    if (event.key === 'ArrowDown' || event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      this.openMenu();
    }
  }

  onMenuKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      event.preventDefault();
      this.close(true);
      return;
    }
    if (event.key === 'Tab') {
      this.close();
      return;
    }
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      this.move(1);
      return;
    }
    if (event.key === 'ArrowUp') {
      event.preventDefault();
      this.move(-1);
      return;
    }
    if (event.key === 'Home') {
      event.preventDefault();
      this.activeIndex.set(0);
      return;
    }
    if (event.key === 'End') {
      event.preventDefault();
      this.activeIndex.set(this.options.length - 1);
      return;
    }
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      const option = this.options[this.activeIndex()];
      if (option) {
        this.select(option.id);
      }
    }
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: Event): void {
    if (!this.host.nativeElement.contains(event.target as Node)) {
      this.close();
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.close();
  }

  private openMenu(): void {
    const current = this.options.findIndex((option) => option.id === this.language());
    this.activeIndex.set(current >= 0 ? current : 0);
    this.open.set(true);
  }

  private close(restoreFocus = false): void {
    if (!this.open()) {
      return;
    }
    this.open.set(false);
    if (restoreFocus) {
      this.trigger()?.nativeElement.focus();
    }
  }

  private move(delta: number): void {
    const next = (this.activeIndex() + delta + this.options.length) % this.options.length;
    this.activeIndex.set(next);
  }
}
