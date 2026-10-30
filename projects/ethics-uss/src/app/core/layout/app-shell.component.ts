import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { COMMITTEE_LABELS } from '../../mocks/catalogs';
import { hasScope, profileRoleKey, rolesOf } from '../workflow/role-capabilities';
import { capabilitiesForRoles } from '../../features/dashboard/home-institutional';
import { DemoAuthService } from '../auth/demo-auth.service';
import { SharedSessionSignIn } from '../auth/shared-session-sign-in';
import { SessionIdentityService } from '../auth/session-identity.service';
import { demoSelectorVisible } from '../auth/demo-mode';
import { environment } from '../../../environments/environment';
import { I18nService } from '../i18n/i18n.service';
import { NotificationService } from '../services/notification.service';
import { EthicsStore } from '../services/ethics-store.service';
import { ethicsOpen } from '../services/user-admin.service';
import { User } from '../models/ethics.models';
import { IconComponent } from '../../shared/components/icon.component';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';
import { LanguageSwitcherComponent } from '../../shared/i18n/language-switcher.component';
import { NavSection, SIDEBAR_SECTIONS_KEY, parseSidebarSections, resolveOpenSections, toggleStoredSection, visibleNavigation } from './navigation';

@Component({
  selector: 'eth-app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, FormsModule, LanguageSwitcherComponent, IconComponent, ...I18N_IMPORTS],
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AppShellComponent {
  private readonly auth = inject(DemoAuthService);
  private readonly session = inject(SessionIdentityService);
  private readonly sharedSession = inject(SharedSessionSignIn);
  private readonly notifications = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly store = inject(EthicsStore);
  readonly i18n = inject(I18nService);
  readonly user = this.auth.currentUser;
  readonly demoSelector = demoSelectorVisible(environment.demoEnabled, environment.production);
  readonly users = computed(() => this.store.state().users.filter((item) => ethicsOpen(item)));
  readonly roleLabelKey = computed(() => {
    const user = this.user();
    if (!user || user.hideRoleLabel) {
      return '';
    }
    return profileRoleKey(user);
  });
  readonly committees = computed(() => this.user()?.committees ?? []);
  readonly activeCommittee = this.auth.activeCommittee;
  readonly committeeKey = (code: string) => COMMITTEE_LABELS[code] ?? code;
  readonly unread = computed(() => {
    const id = this.user()?.id;
    return id ? this.notifications.unreadCount(id) : 0;
  });
  readonly nav = computed(() => {
    this.store.state();
    return this.menuFor(this.user());
  });
  private readonly url = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map(() => this.router.url)
    ),
    { initialValue: this.router.url }
  );
  private readonly storedSections = signal<Record<string, boolean> | null>(readSidebarSections());
  readonly openSections = computed(() => resolveOpenSections(this.nav(), this.url(), this.storedSections()));

  assume(userId: string): void {
    if (!this.demoSelector) {
      return;
    }
    this.auth.assume(userId);
    void this.router.navigateByUrl('/');
  }

  logout(): void {
    if (!this.demoSelector) {
      void this.sharedSession.signOut().finally(() => {
        this.session.clear();
        void this.router.navigateByUrl('/ingresar');
      });
      return;
    }
    this.auth.logout();
    void this.router.navigateByUrl('/ingresar');
  }

  roleKey(user: User): string {
    return profileRoleKey(user);
  }

  setCommittee(code: string): void {
    this.auth.setCommittee(code);
  }

  showCommittee(user: User): boolean {
    return hasScope(user, 'COMMITTEE') && user.committees.length > 0;
  }

  sectionOpen(id: string): boolean {
    return this.openSections()[id] !== false;
  }

  sectionToggleLabel(section: NavSection): string {
    const name = this.i18n.translate(section.labelKey ?? '');
    const key = this.sectionOpen(section.id) ? 'navigation.sections.collapse' : 'navigation.sections.expand';
    return this.i18n.translate(key, { section: name });
  }

  toggleSection(id: string): void {
    const next = toggleStoredSection(this.storedSections(), this.nav(), id, this.url());
    if (next === this.storedSections()) {
      return;
    }
    this.storedSections.set(next);
    writeSidebarSections(next);
  }

  private menuFor(user: User | null | undefined): NavSection[] {
    if (!user) {
      return [];
    }
    const caps = user.grantedCapabilities ? new Set(user.grantedCapabilities) : capabilitiesForRoles(rolesOf(user));
    const caseOnly = !hasScope(user, 'GLOBAL') && !hasScope(user, 'COMMITTEE') && !hasScope(user, 'OWN_PROJECT');
    const assigned = this.store.snapshot().assignments.some((item) => item.reviewerId === user.id && !item.supersededAt);
    return visibleNavigation(caps, { hideUnassignedCasePaths: caseOnly && !assigned });
  }
}

function readSidebarSections(): Record<string, boolean> | null {
  if (typeof localStorage === 'undefined') {
    return null;
  }
  return parseSidebarSections(localStorage.getItem(SIDEBAR_SECTIONS_KEY));
}

function writeSidebarSections(value: Record<string, boolean> | null): void {
  if (typeof localStorage === 'undefined' || value === null) {
    return;
  }
  localStorage.setItem(SIDEBAR_SECTIONS_KEY, JSON.stringify(value));
}
