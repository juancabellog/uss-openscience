import { ChangeDetectionStrategy, Component, ElementRef, HostListener, computed, inject, signal } from '@angular/core';
import { AuthService } from '../../core/services/auth.service';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { I18N_HOST } from '../../shared/i18n/i18n.imports';
import { TranslatePipe } from '../../shared/pipes/translate.pipe';

@Component({
  selector: 'os-user-menu',
  hostDirectives: [...I18N_HOST],
  imports: [IconComponent, TranslatePipe],
  templateUrl: './user-menu.component.html',
  styleUrl: './user-menu.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class UserMenuComponent {
  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly auth = inject(AuthService);

  readonly user = this.auth.user;
  readonly open = signal(false);

  readonly initials = computed(() => {
    const parts = (this.user()?.displayName ?? '').split(/\s+/).filter(Boolean);
    if (parts.length === 0) {
      return 'U';
    }
    const first = parts[0]?.charAt(0) ?? '';
    const last = parts.length > 1 ? (parts[parts.length - 1]?.charAt(0) ?? '') : '';
    return (first + last).toUpperCase();
  });

  toggle(): void {
    this.open.update((value) => !value);
  }

  closeMenu(): void {
    this.open.set(false);
  }

  signOut(): void {
    this.auth.signOut();
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
