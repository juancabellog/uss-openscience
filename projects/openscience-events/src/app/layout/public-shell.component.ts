import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { EventsApiService } from '../core/events-api.service';
import { LanguageSwitcherComponent } from '../shared/i18n/language-switcher.component';
import { I18N_IMPORTS } from '../shared/i18n/i18n.imports';

@Component({
  selector: 'ose-public-shell',
  imports: [RouterLink, RouterLinkActive, RouterOutlet, LanguageSwitcherComponent, ...I18N_IMPORTS],
  templateUrl: './public-shell.component.html',
  styleUrl: './public-shell.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PublicShellComponent {
  private readonly api = inject(EventsApiService);
  private readonly router = inject(Router);

  readonly email = signal<string | null>(null);
  readonly canAdmin = signal(true);

  constructor() {
    this.api.me().subscribe({
      next: (user) => {
        this.email.set(user.email ?? user.displayName ?? null);
        this.canAdmin.set(this.api.hasEventsPermission(user));
      },
      error: () => {
        this.email.set(null);
        this.canAdmin.set(true);
      }
    });
  }

  logout(): void {
    this.api.logout().subscribe({
      next: () => this.clearSession(),
      error: () => this.clearSession()
    });
  }

  private clearSession(): void {
    this.email.set(null);
    this.canAdmin.set(true);
    void this.router.navigateByUrl('/');
  }
}
