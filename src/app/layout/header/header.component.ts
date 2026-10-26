import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map, startWith } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { LayoutService } from '../../core/services/layout.service';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { I18N_HOST } from '../../shared/i18n/i18n.imports';
import { TranslatePipe } from '../../shared/pipes/translate.pipe';
import { EnvironmentBadgeComponent } from '../environment-badge/environment-badge.component';
import { LanguageSwitcherComponent } from '../language-switcher/language-switcher.component';
import { UserMenuComponent } from '../user-menu/user-menu.component';

@Component({
  selector: 'os-header',
  hostDirectives: [...I18N_HOST],
  imports: [
    IconComponent,
    TranslatePipe,
    LanguageSwitcherComponent,
    UserMenuComponent,
    EnvironmentBadgeComponent
  ],
  templateUrl: './header.component.html',
  styleUrl: './header.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class HeaderComponent {
  private readonly layout = inject(LayoutService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  private readonly url = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map((event) => event.urlAfterRedirects),
      startWith(this.router.url)
    ),
    { initialValue: this.router.url }
  );

  readonly pageTitleKey = computed(() => titleKeyFromUrl(this.url()));

  toggleSidebar(): void {
    this.layout.toggleSidebar();
  }

  signOut(): void {
    void this.auth.signOut();
  }
}

function titleKeyFromUrl(url: string): string {
  const segment = url.split('?')[0]?.split('#')[0]?.split('/').filter(Boolean)[0] ?? 'dashboard';
  const keys: Record<string, string> = {
    dashboard: 'navigation.dashboard',
    people: 'navigation.people',
    researchers: 'navigation.people',
    'research-output': 'navigation.researchOutput',
    journals: 'navigation.journals',
    projects: 'navigation.projects',
    'data-quality': 'navigation.dataQuality',
    indicators: 'navigation.indicators',
    integrations: 'navigation.integrations',
    audit: 'navigation.audit',
    admin: 'navigation.administration',
    'not-found': 'notFound.title'
  };
  return keys[segment] ?? 'navigation.dashboard';
}
