import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { EventsApiService } from '../core/events-api.service';
import { LanguageSwitcherComponent } from '../shared/i18n/language-switcher.component';
import { I18N_IMPORTS } from '../shared/i18n/i18n.imports';

@Component({
  selector: 'ose-admin-shell',
  imports: [RouterLink, RouterLinkActive, RouterOutlet, LanguageSwitcherComponent, ...I18N_IMPORTS],
  templateUrl: './admin-shell.component.html',
  styleUrl: './admin-shell.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AdminShellComponent {
  private readonly api = inject(EventsApiService);
  private readonly router = inject(Router);
  readonly email = signal<string | null>(null);

  constructor() {
    this.api.me().subscribe({
      next: (user) => this.email.set(user.email ?? user.displayName ?? null),
      error: () => this.email.set(null)
    });
  }

  logout(): void {
    this.api.logout().subscribe({
      next: () => {
        this.email.set(null);
        void this.router.navigateByUrl('/admin/login');
      },
      error: () => void this.router.navigateByUrl('/admin/login')
    });
  }
}
