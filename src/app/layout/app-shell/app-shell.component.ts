import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRouteSnapshot, NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter, map, startWith } from 'rxjs';
import { LayoutService } from '../../core/services/layout.service';
import { SyncMonitorService } from '../../core/services/sync-monitor.service';
import { I18N_HOST } from '../../shared/i18n/i18n.imports';
import { SyncDetailDialogComponent } from '../../shared/components/sync-detail-dialog/sync-detail-dialog.component';
import { ToastHostComponent } from '../../shared/components/toast-host/toast-host.component';
import { FooterComponent } from '../footer/footer.component';
import { HeaderComponent } from '../header/header.component';
import { InstitutionalHeaderComponent } from '../institutional-header/institutional-header.component';
import { SidebarComponent } from '../sidebar/sidebar.component';

@Component({
  selector: 'os-app-shell',
  hostDirectives: [...I18N_HOST],
  imports: [
    RouterOutlet,
    InstitutionalHeaderComponent,
    HeaderComponent,
    SidebarComponent,
    FooterComponent,
    ToastHostComponent,
    SyncDetailDialogComponent
  ],
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AppShellComponent {
  private readonly router = inject(Router);
  readonly layout = inject(LayoutService);

  constructor() {
    inject(SyncMonitorService);
  }
  readonly contentWidth = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map(() => this.resolveContentWidth()),
      startWith(this.resolveContentWidth())
    ),
    { initialValue: this.resolveContentWidth() }
  );

  private resolveContentWidth(): 'standard' | 'wide' {
    let snapshot: ActivatedRouteSnapshot | null =
      this.router.routerState.snapshot?.root ?? null;
    let width: 'standard' | 'wide' = 'standard';
    while (snapshot) {
      const value = snapshot.data?.['contentWidth'];
      if (value === 'wide' || value === 'standard') {
        width = value;
      }
      snapshot = snapshot.firstChild;
    }
    return width;
  }
}
