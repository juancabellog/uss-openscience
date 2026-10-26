import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { COMMITTEE_LABELS, ROLE_LABELS } from '../../mocks/catalogs';
import { DemoAuthService } from '../auth/demo-auth.service';
import { I18nService } from '../i18n/i18n.service';
import { NotificationService } from '../services/notification.service';
import { Role } from '../models/ethics.models';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';
import { LanguageSwitcherComponent } from '../../shared/i18n/language-switcher.component';

interface NavItem {
  path: string;
  labelKey: string;
}

@Component({
  selector: 'eth-app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, FormsModule, LanguageSwitcherComponent, ...I18N_IMPORTS],
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AppShellComponent {
  private readonly auth = inject(DemoAuthService);
  private readonly notifications = inject(NotificationService);
  private readonly router = inject(Router);
  readonly i18n = inject(I18nService);
  readonly user = this.auth.currentUser;
  readonly users = this.auth.users();
  readonly roleLabelKey = computed(() => ROLE_LABELS[this.user()?.role ?? ''] ?? '');
  readonly committees = computed(() => this.user()?.committees ?? []);
  readonly activeCommittee = this.auth.activeCommittee;
  readonly committeeKey = (code: string) => COMMITTEE_LABELS[code] ?? code;
  readonly openNotes = signal(false);
  readonly notes = computed(() => {
    const id = this.user()?.id;
    return id ? this.notifications.forUser(id).map((item) => ({ item, text: this.notifications.display(item) })) : [];
  });
  readonly unread = computed(() => {
    const id = this.user()?.id;
    return id ? this.notifications.unreadCount(id) : 0;
  });
  readonly nav = computed(() => this.menuFor(this.user()?.role));

  assume(userId: string): void {
    this.auth.assume(userId);
    void this.router.navigateByUrl('/');
  }

  logout(): void {
    this.auth.logout();
    void this.router.navigateByUrl('/ingresar');
  }

  toggleNotes(): void {
    this.openNotes.update((value) => !value);
  }

  openNote(id: string, link?: string): void {
    this.notifications.markRead(id);
    this.openNotes.set(false);
    if (link) {
      void this.router.navigateByUrl(link);
    }
  }

  roleKey(role: Role): string {
    return ROLE_LABELS[role] ?? role;
  }

  setCommittee(code: string): void {
    this.auth.setCommittee(code);
  }

  private menuFor(role?: Role): NavItem[] {
    if (role === 'RESEARCHER') {
      return [
        { path: '/', labelKey: 'navigation.home' },
        { path: '/proyectos', labelKey: 'navigation.myProjects' },
        { path: '/proyectos/nuevo', labelKey: 'navigation.newApplication' },
        { path: '/notificaciones', labelKey: 'navigation.notifications' }
      ];
    }
    if (role === 'COMMITTEE_MEMBER') {
      return [
        { path: '/', labelKey: 'navigation.home' },
        { path: '/revisiones', labelKey: 'navigation.assignedReviews' },
        { path: '/historial', labelKey: 'navigation.history' }
      ];
    }
    if (role === 'EXECUTIVE_SECRETARY' || role === 'COMMITTEE_PRESIDENT') {
      return [
        { path: '/', labelKey: 'navigation.home' },
        { path: '/expedientes', labelKey: 'navigation.cases' },
        { path: '/sesiones', labelKey: 'navigation.sessions' },
        { path: '/revisores', labelKey: 'navigation.reviewers' },
        { path: '/vencimientos', labelKey: 'navigation.expirations' }
      ];
    }
    return [
      { path: '/', labelKey: 'navigation.home' },
      { path: '/solicitudes', labelKey: 'navigation.applications' },
      { path: '/expedientes', labelKey: 'navigation.cases' },
      { path: '/comites', labelKey: 'navigation.committees' },
      { path: '/sesiones', labelKey: 'navigation.sessions' },
      { path: '/revisores', labelKey: 'navigation.reviewers' },
      { path: '/vencimientos', labelKey: 'navigation.expirations' },
      { path: '/configuracion', labelKey: 'navigation.settings' },
      { path: '/auditoria', labelKey: 'navigation.audit' }
    ];
  }
}
