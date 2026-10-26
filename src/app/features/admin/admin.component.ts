import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { SectionCardComponent } from '../../shared/components/section-card/section-card.component';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';
import { I18N_HOST } from '../../shared/i18n/i18n.imports';
import { TranslatePipe } from '../../shared/pipes/translate.pipe';

@Component({
  selector: 'os-admin',
  hostDirectives: [...I18N_HOST],
  imports: [PageHeaderComponent, SectionCardComponent, StatusBadgeComponent, TranslatePipe, RouterLink],
  templateUrl: './admin.component.html',
  styleUrl: './admin.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AdminComponent {
  readonly auth = inject(AuthService);
  readonly ready = [
    {
      id: 'users',
      titleKey: 'admin.usersTitle',
      descriptionKey: 'admin.usersDescription',
      actionKey: 'admin.users.manage',
      link: '/admin/users'
    },
    {
      id: 'roles',
      titleKey: 'admin.rolesTitle',
      descriptionKey: 'admin.rolesDescription',
      actionKey: 'admin.roles.manage',
      link: '/admin/roles'
    }
  ] as const;
  readonly placeholders = [
    { id: 'params', titleKey: 'admin.paramsTitle', descriptionKey: 'admin.paramsDescription' },
    { id: 'rules', titleKey: 'admin.rulesTitle', descriptionKey: 'admin.rulesDescription' },
    { id: 'config', titleKey: 'admin.configTitle', descriptionKey: 'admin.configDescription' }
  ] as const;
}
