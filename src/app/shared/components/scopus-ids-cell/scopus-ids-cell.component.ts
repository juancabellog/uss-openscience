import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  HostListener,
  computed,
  inject,
  input,
  output,
  signal,
  viewChild
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { catchError, of } from 'rxjs';
import { PersonScopusIdentifierItem, PersonScopusIdentifiers, PureCorrectionResult } from '../../../core/models/openscience.models';
import { PersonApiService } from '../../../core/services/api/person-api.service';
import { AuthService } from '../../../core/services/auth.service';
import { I18nService } from '../../../core/i18n/i18n.service';
import { NotificationService } from '../../../core/services/notification.service';
import { apiErrorCode } from '../../../core/utils/api-error';
import { CopyButtonComponent } from '../copy-button/copy-button.component';
import { IconComponent } from '../icon/icon.component';
import { StatusBadgeComponent } from '../status-badge/status-badge.component';
import { PureCorrectionDialogComponent } from '../pure-correction-dialog/pure-correction-dialog.component';
import { I18N_HOST, I18N_IMPORTS } from '../../i18n/i18n.imports';
import { DetailPopoverCoordinator } from '../../popover/detail-popover-coordinator';
import { positionFixedPopover } from '../../popover/position-fixed-popover';

@Component({
  selector: 'os-scopus-ids-cell',
  hostDirectives: [...I18N_HOST],
  imports: [
    ...I18N_IMPORTS,
    CopyButtonComponent,
    IconComponent,
    StatusBadgeComponent,
    PureCorrectionDialogComponent
  ],
  templateUrl: './scopus-ids-cell.component.html',
  styleUrl: './scopus-ids-cell.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ScopusIdsCellComponent {
  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly peopleApi = inject(PersonApiService);
  private readonly coordinator = inject(DetailPopoverCoordinator);
  private readonly destroyRef = inject(DestroyRef);
  private readonly auth = inject(AuthService);
  private readonly i18n = inject(I18nService);
  private readonly notifications = inject(NotificationService);

  readonly personId = input.required<number>();
  readonly personName = input<string | null>(null);
  readonly count = input(0);
  readonly displayValue = input<string | null>(null);
  readonly panel = viewChild<ElementRef<HTMLElement>>('panel');
  readonly corrected = output<void>();

  readonly loading = signal(false);
  readonly error = signal(false);
  readonly detail = signal<PersonScopusIdentifiers | null>(null);
  readonly panelStyle = signal<Record<string, string>>({});
  readonly pending = signal<PersonScopusIdentifierItem | null>(null);
  readonly removing = signal(false);
  private trigger: HTMLElement | null = null;

  readonly popoverId = computed(() => `scopus-${this.personId()}`);
  readonly open = computed(() => this.coordinator.activeId() === this.popoverId());
  readonly interactive = computed(() => this.count() > 1);
  readonly panelId = computed(() => `scopus-ids-${this.personId()}`);
  readonly canCorrect = computed(() => this.auth.canCorrectPure());

  constructor() {
    const onScroll = (event: Event) => this.onCapturedScroll(event);
    document.addEventListener('scroll', onScroll, true);
    this.destroyRef.onDestroy(() => document.removeEventListener('scroll', onScroll, true));
  }

  readonly items = computed(() => {
    const rows = this.detail()?.items ?? [];
    const frequencies = new Map<string, number>();
    for (const row of rows) {
      const key = normalizeScopusId(row.value);
      if (key) {
        frequencies.set(key, (frequencies.get(key) ?? 0) + 1);
      }
    }
    const allSame = isSingleSharedScopusId(rows);
    return rows.map((row) => ({
      ...row,
      showDuplicate: !allSame && isRepeated(row.value, frequencies)
    }));
  });

  readonly recordCount = computed(() => this.detail()?.count ?? this.count());

  readonly distinctCount = computed(() => this.detail()?.distinctCount ?? 0);

  readonly matchedCount = computed(() => this.detail()?.matchedCount ?? 0);

  readonly showCoverage = computed(
    () => this.recordCount() > 1 && this.matchedCount() < this.recordCount()
  );

  readonly titleKey = computed(() =>
    this.recordCount() === 1 ? 'people.scopusPopover.oneRecord' : 'people.scopusPopover.manyRecords'
  );

  readonly subtitleKey = computed(() => {
    if (this.recordCount() <= 1) {
      return null;
    }
    if (this.distinctCount() === 1) {
      return 'people.scopusPopover.sameIdAllRecords';
    }
    if (this.distinctCount() > 1) {
      return 'people.scopusPopover.distinctIds';
    }
    return null;
  });

  readonly subtitleCount = computed(() =>
    this.subtitleKey() === 'people.scopusPopover.sameIdAllRecords'
      ? this.recordCount()
      : this.distinctCount()
  );

  itemTrack(item: PersonScopusIdentifierItem, index: number): string {
    return `${item.pureId ?? 'none'}-${item.id ?? index}`;
  }

  toggle(event: Event): void {
    event.stopPropagation();
    if (!this.interactive() || this.pending()) {
      return;
    }
    if (this.open()) {
      this.close();
      return;
    }
    this.coordinator.activeId.set(this.popoverId());
    this.positionPanel();
    this.load();
    queueMicrotask(() => this.panel()?.nativeElement.focus());
  }

  close(): void {
    if (this.pending()) {
      return;
    }
    if (this.open()) {
      this.coordinator.activeId.set(null);
    }
  }

  askRemove(event: Event, item: PersonScopusIdentifierItem): void {
    event.stopPropagation();
    if (!this.canCorrect() || item.pureId == null || this.removing()) {
      return;
    }
    this.trigger = event.currentTarget instanceof HTMLElement ? event.currentTarget : null;
    this.pending.set(item);
  }

  closeConfirm(): void {
    if (this.removing()) {
      return;
    }
    this.pending.set(null);
    this.restoreTrigger();
  }

  confirmRemove(): void {
    const item = this.pending();
    const childPureId = item?.pureId;
    if (item == null || childPureId == null || this.removing()) {
      return;
    }
    this.removing.set(true);
    this.peopleApi
      .removeScopusIdentifier(this.personId(), childPureId, item.value)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.removing.set(false);
          this.pending.set(null);
          if (result.status === 'REMOTE_SUCCESS_LOCAL_SYNC_FAILED') {
            this.notifyLocalFailed(childPureId);
            return;
          }
          this.finishLocalRefresh(result, item.value);
        },
        error: (error: unknown) => {
          this.removing.set(false);
          this.notifyError(error);
        }
      });
  }

  retryLocal(identifierPureId: number): void {
    if (this.removing()) {
      return;
    }
    this.removing.set(true);
    this.peopleApi
      .reconcileLocalScopusIdentifier(this.personId(), identifierPureId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.removing.set(false);
          if (result.status === 'REMOTE_SUCCESS_LOCAL_SYNC_FAILED') {
            this.notifyLocalFailed(identifierPureId);
            return;
          }
          this.finishLocalRefresh(result, this.pending()?.value ?? null, true);
        },
        error: (error: unknown) => {
          this.removing.set(false);
          this.notifyError(error);
        }
      });
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: Event): void {
    if (this.pending()) {
      return;
    }
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) {
      this.close();
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.pending()) {
      this.closeConfirm();
      return;
    }
    this.close();
  }

  @HostListener('window:resize')
  onResize(): void {
    if (this.pending()) {
      return;
    }
    this.close();
  }

  private onCapturedScroll(event: Event): void {
    if (!this.open() || this.pending()) {
      return;
    }
    const target = event.target;
    if (target instanceof Node && this.panel()?.nativeElement.contains(target)) {
      return;
    }
    this.close();
  }

  private load(): void {
    if (this.detail()) {
      return;
    }
    this.loading.set(true);
    this.error.set(false);
    this.peopleApi
      .getScopusIdentifiers(this.personId())
      .pipe(catchError(() => of(null)), takeUntilDestroyed(this.destroyRef))
      .subscribe((result) => {
        this.loading.set(false);
        if (result == null) {
          this.error.set(true);
          return;
        }
        this.detail.set(result);
      });
  }

  private positionPanel(): void {
    this.panelStyle.set(positionFixedPopover(this.host.nativeElement.getBoundingClientRect(), {
      widthRem: 20,
      heightRem: 18
    }));
  }

  private closeAfterCorrection(): void {
    this.trigger = null;
    if (this.open()) {
      this.coordinator.activeId.set(null);
    }
  }

  private restoreTrigger(): void {
    const trigger = this.trigger;
    this.trigger = null;
    queueMicrotask(() => trigger?.focus());
  }

  private finishLocalRefresh(
    result: PureCorrectionResult,
    value: string | null,
    localRetry = false
  ): void {
    this.detail.set(null);
    this.closeAfterCorrection();
    this.notifyResult(result, value, localRetry);
    this.corrected.emit();
  }

  private notifyLocalFailed(identifierPureId: number): void {
    this.notifications.show(
      'warning',
      this.i18n.translate('pureCorrection.localPendingTitle'),
      this.i18n.translate('pureCorrection.localPendingBody'),
      null,
      {
        label: this.i18n.translate('pureCorrection.retryLocal'),
        run: () => this.retryLocal(identifierPureId)
      }
    );
  }

  private notifyResult(result: PureCorrectionResult, value: string | null, localRetry = false): void {
    if (localRetry || result.status === 'LOCAL_RECONCILED') {
      this.notifications.show(
        'success',
        this.i18n.translate('pureCorrection.localRetrySuccessTitle'),
        this.i18n.translate('pureCorrection.localRetrySuccessBody')
      );
      return;
    }
    if (result.status === 'ALREADY_REMOVED') {
      this.notifications.show(
        'warning',
        this.i18n.translate('pureCorrection.alreadyTitle'),
        this.i18n.translate('pureCorrection.alreadyBody')
      );
      return;
    }
    this.notifications.show(
      'success',
      this.i18n.translate('pureCorrection.scopusSuccessTitle'),
      this.i18n.translate('pureCorrection.scopusSuccessBody', { value: value || '' })
    );
  }

  private notifyError(error: unknown): void {
    const code = apiErrorCode(error) ?? (error instanceof HttpErrorResponse ? String(error.status) : null);
    if (code === 'SYNC_IN_PROGRESS') {
      this.notifications.show(
        'warning',
        this.i18n.translate('pureCorrection.syncInProgressTitle'),
        this.i18n.translate('pureCorrection.syncInProgressPeople')
      );
      return;
    }
    if (code === 'PURE_RECORD_CHANGED') {
      this.notifications.show(
        'error',
        this.i18n.translate('pureCorrection.changedTitle'),
        this.i18n.translate('pureCorrection.changedBody')
      );
      return;
    }
    if (code === 'PURE_VERIFICATION_FAILED') {
      this.notifications.show(
        'error',
        this.i18n.translate('pureCorrection.verifyFailedTitle'),
        this.i18n.translate('pureCorrection.verifyFailedBody')
      );
      return;
    }
    if (code === 'CORRECTION_FORBIDDEN') {
      this.notifications.show(
        'error',
        this.i18n.translate('pureCorrection.pureFailedTitle'),
        this.i18n.translate('pureCorrection.forbidden')
      );
      return;
    }
    this.notifications.show(
      'error',
      this.i18n.translate('pureCorrection.pureFailedTitle'),
      this.i18n.translate('pureCorrection.pureFailedBody')
    );
  }
}

function normalizeScopusId(value: string | null | undefined): string | null {
  if (!value || value.trim() === '') {
    return null;
  }
  return value.trim();
}

function isRepeated(value: string | null, frequencies: Map<string, number>): boolean {
  const key = normalizeScopusId(value);
  return key != null && (frequencies.get(key) ?? 0) > 1;
}

function isSingleSharedScopusId(rows: PersonScopusIdentifiers['items']): boolean {
  if (rows.length < 2) {
    return false;
  }
  const keys = rows.map((row) => normalizeScopusId(row.value));
  if (keys.some((key) => key == null)) {
    return false;
  }
  return new Set(keys).size === 1;
}
