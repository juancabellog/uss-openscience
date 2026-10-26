import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/services/auth.service';
import { LayoutService } from '../../core/services/layout.service';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { I18N_HOST } from '../../shared/i18n/i18n.imports';
import { TranslatePipe } from '../../shared/pipes/translate.pipe';
import { APP_NAV_ITEMS } from './nav-catalog';

@Component({
  selector: 'os-sidebar',
  hostDirectives: [...I18N_HOST],
  imports: [RouterLink, RouterLinkActive, IconComponent, TranslatePipe],
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SidebarComponent {
  readonly layout = inject(LayoutService);
  private readonly auth = inject(AuthService);

  readonly version = environment.appVersion;
  readonly shortVersion = environment.appVersion.split('.').slice(0, 2).join('.');

  readonly items = computed(() => APP_NAV_ITEMS.filter((item) => this.auth.canConsult(item.module)));
}
