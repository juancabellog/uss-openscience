import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { NotificationAction, NotificationService } from '../../../core/services/notification.service';
import { I18N_HOST, I18N_IMPORTS } from '../../i18n/i18n.imports';

@Component({
  selector: 'os-toast-host',
  hostDirectives: [...I18N_HOST],
  imports: [...I18N_IMPORTS],
  templateUrl: './toast-host.component.html',
  styleUrl: './toast-host.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ToastHostComponent {
  readonly notifications = inject(NotificationService);

  dismiss(id: string): void {
    this.notifications.dismiss(id);
  }

  runAction(id: string, action: NotificationAction): void {
    action.run();
    this.dismiss(id);
  }
}
