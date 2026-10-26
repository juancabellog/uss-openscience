import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, combineLatest, of, switchMap } from 'rxjs';
import { DataQualityIssueStatus } from '../../core/models/openscience.models';
import { AuthService } from '../../core/services/auth.service';
import { DataQualityService } from '../../core/services/platform-services';
import { ErrorStateComponent } from '../../shared/components/error-state/error-state.component';
import { LoadingStateComponent } from '../../shared/components/loading-state/loading-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { SectionCardComponent } from '../../shared/components/section-card/section-card.component';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';
import { I18N_HOST, I18N_IMPORTS } from '../../shared/i18n/i18n.imports';
import { issueKey, issueTone } from '../../shared/utils/status.util';

@Component({
  selector: 'os-data-quality-detail',
  hostDirectives: [...I18N_HOST],
  imports: [
    ...I18N_IMPORTS,
    ErrorStateComponent,
    LoadingStateComponent,
    PageHeaderComponent,
    SectionCardComponent,
    StatusBadgeComponent
  ],
  templateUrl: './data-quality-detail.component.html',
  styleUrl: './data-quality-detail.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DataQualityDetailComponent {
  private readonly quality = inject(DataQualityService);
  readonly auth = inject(AuthService);
  readonly id = input.required<string>();
  readonly issueKey = issueKey;
  readonly issueTone = issueTone;
  readonly busy = signal(false);
  readonly noticeKey = signal('');
  readonly refresh = signal(0);

  readonly issue = toSignal(
    combineLatest([toObservable(this.id), toObservable(this.refresh)]).pipe(
      switchMap(([id]) => this.quality.getById(id).pipe(catchError(() => of(null))))
    )
  );

  update(status: DataQualityIssueStatus): void {
    const current = this.issue();
    if (!current || this.busy() || !this.auth.canAdminister('DATA_QUALITY')) {
      return;
    }
    this.busy.set(true);
    this.quality.updateStatus(current.id, status).subscribe({
      next: () => {
        this.busy.set(false);
        this.noticeKey.set('dataQuality.noticeUpdated');
        this.refresh.update((value) => value + 1);
      },
      error: () => {
        this.busy.set(false);
        this.noticeKey.set('dataQuality.noticeError');
      }
    });
  }
}
