import { ChangeDetectionStrategy, Component, ElementRef, HostListener, computed, inject, signal } from '@angular/core';
import { I18nService } from '../../core/i18n/i18n.service';
import { AppLanguage } from '../../core/i18n/i18n.models';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { I18N_HOST } from '../../shared/i18n/i18n.imports';
import { TranslatePipe } from '../../shared/pipes/translate.pipe';

const LANGUAGE_OPTIONS: ReadonlyArray<{ code: AppLanguage; nativeName: string }> = [
  { code: 'es', nativeName: 'Español' },
  { code: 'en', nativeName: 'English' }
];

@Component({
  selector: 'os-language-switcher',
  hostDirectives: [...I18N_HOST],
  imports: [IconComponent, TranslatePipe],
  templateUrl: './language-switcher.component.html',
  styleUrl: './language-switcher.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LanguageSwitcherComponent {
  private readonly host = inject(ElementRef<HTMLElement>);
  readonly i18n = inject(I18nService);
  readonly options = LANGUAGE_OPTIONS;
  readonly open = signal(false);

  readonly currentNativeName = computed(
    () => this.options.find((option) => option.code === this.i18n.currentLanguage())?.nativeName ?? 'Español'
  );

  toggle(): void {
    this.open.update((value) => !value);
  }

  select(language: AppLanguage): void {
    this.i18n.setLanguage(language);
    this.open.set(false);
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: Event): void {
    if (!this.host.nativeElement.contains(event.target as Node)) {
      this.open.set(false);
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.open.set(false);
  }
}

